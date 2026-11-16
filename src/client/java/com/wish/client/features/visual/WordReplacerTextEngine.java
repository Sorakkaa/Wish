package com.wish.client.features.visual;

import com.wish.client.features.visual.WordReplacerManager.CompiledRule;
import com.wish.client.features.visual.WordReplacerManager.Snapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WordReplacerTextEngine {
    private static final Pattern TRANSLATION_FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private WordReplacerTextEngine() {
    }

    private record ReplacedPlainContents(String text, long snapshotVersion) implements PlainTextContents {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
            return consumer.accept(this.text);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) {
            return consumer.accept(style, this.text);
        }

        @Override
        public String toString() {
            return "literal{" + this.text + "}";
        }
    }

    private record StyledSegment(String text, Style explicitStyle) {
    }

    private static final class PlainStyleState {
        private String colorPrefix = "";
        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        void applySectionCode(String text, int index, int step) {
            if (step == 14) {
                colorPrefix = text.substring(index, index + 14);
                clearFormats();
                return;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            if (code == 'r') {
                colorPrefix = "";
                clearFormats();
            } else if (isLegacyColorChar(code)) {
                colorPrefix = text.substring(index, index + 2);
                clearFormats();
            } else {
                switch (code) {
                    case 'k' -> obfuscated = true;
                    case 'l' -> bold = true;
                    case 'm' -> strikethrough = true;
                    case 'n' -> underlined = true;
                    case 'o' -> italic = true;
                    default -> {}
                }
            }
        }

        void appendResetAndRestore(StringBuilder sb) {
            sb.append("\u00A7r");
            if (!colorPrefix.isEmpty()) {
                sb.append(colorPrefix);
            }
            if (obfuscated) sb.append("\u00A7k");
            if (bold) sb.append("\u00A7l");
            if (strikethrough) sb.append("\u00A7m");
            if (underlined) sb.append("\u00A7n");
            if (italic) sb.append("\u00A7o");
        }

        boolean isEmpty() {
            return colorPrefix.isEmpty() && !obfuscated && !bold && !strikethrough && !underlined && !italic;
        }

        private void clearFormats() {
            obfuscated = false;
            bold = false;
            strikethrough = false;
            underlined = false;
            italic = false;
        }
    }

    static String replacePlainString(String text, Snapshot snapshot) {
        if (text == null || snapshot == null || !snapshot.enabled() || snapshot.rules().isEmpty() || text.length() < snapshot.minFromLength()) {
            return text;
        }
        int len = text.length();
        StringBuilder sb = null;
        PlainStyleState styleState = new PlainStyleState();
        int i = 0;
        while (i < len) {
            if (text.charAt(i) == '\u00A7' && i + 1 < len) {
                int step = isSectionHexSeq(text, i) ? 14 : 2;
                styleState.applySectionCode(text, i, step);
                if (sb != null) {
                    sb.append(text, i, i + step);
                }
                i += step;
                continue;
            }
            int urlEnd = scanUrlEndIfAtStart(text, i);
            if (urlEnd > i) {
                if (sb != null) {
                    sb.append(text, i, urlEnd);
                }
                i = urlEnd;
                continue;
            }
            CompiledRule match = findMatchingRule(text, i, snapshot);
            if (match != null) {
                if (sb == null) {
                    sb = new StringBuilder(len + 16);
                    sb.append(text, 0, i);
                }
                int nextIdx = i + match.from().length();
                appendPlainReplacement(sb, match, styleState, nextIdx < len);
                i = nextIdx;
                continue;
            }
            if (sb != null) {
                sb.append(text.charAt(i));
            }
            i++;
        }
        return sb != null ? sb.toString() : text;
    }

    private static void appendPlainReplacement(StringBuilder sb, CompiledRule match, PlainStyleState styleState, boolean hasTrailing) {
        if (match.to().isEmpty()) {
            return;
        }
        if (!match.hasFormatting()) {
            sb.append(match.to());
            return;
        }
        String converted = convertAmpersandToSection(match.to());
        sb.append(converted);
        if (hasTrailing && (!converted.endsWith("\u00A7r") || !styleState.isEmpty())) {
            styleState.appendResetAndRestore(sb);
        }
    }

    static Component replaceComponent(Component component, Snapshot snapshot) {
        if (component == null || snapshot == null || !snapshot.enabled() || snapshot.rules().isEmpty()) {
            return component;
        }
        return replaceNode(component, component.getStyle(), snapshot);
    }

    private static final Style EDITOR_CODE_TAG_STYLE = Style.EMPTY.withColor(ChatFormatting.GRAY);

    static Component buildPreviewComponent(String to) {
        if (to == null || to.isEmpty()) {
            return Component.empty();
        }
        if (!containsFormattingCode(to)) {
            return Component.literal(to);
        }
        List<StyledSegment> segments = new ArrayList<>(4);
        appendFormattedSegments(segments, to, Style.EMPTY, null, false);
        return buildLeafFromSegments(segments, Style.EMPTY, 0L);
    }

    static Component buildEditorPreviewComponent(String to, boolean appendCaret) {
        String safeTo = to != null ? to : "";
        if (safeTo.isEmpty()) {
            return appendCaret ? Component.literal("_") : Component.empty();
        }
        if (!containsFormattingCode(safeTo)) {
            return Component.literal(appendCaret ? safeTo + "_" : safeTo);
        }
        List<StyledSegment> segments = new ArrayList<>(4);
        appendFormattedSegments(segments, safeTo, Style.EMPTY, EDITOR_CODE_TAG_STYLE, appendCaret);
        return buildLeafFromSegments(segments, Style.EMPTY, 0L);
    }

    static boolean containsFormattingCode(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return false;
        }
        int len = text.length();
        for (int i = 0; i < len - 1; i++) {
            if (text.charAt(i) != '&') {
                continue;
            }
            if ((i + 7 < len && text.charAt(i + 1) == '#' && isHexSpan(text, i + 2, 6)) || isLegacyCode(text.charAt(i + 1))) {
                return true;
            }
        }
        return false;
    }

    static String stripFormattingCodes(String text) {
        if (text == null) {
            return "";
        }
        if (text.indexOf('&') < 0) {
            return text;
        }
        int len = text.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; ) {
            char c = text.charAt(i);
            if (c == '&' && i + 7 < len && text.charAt(i + 1) == '#' && isHexSpan(text, i + 2, 6)) {
                i += 8;
                continue;
            }
            if (c == '&' && i + 1 < len && isLegacyCode(text.charAt(i + 1))) {
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static Component replaceNode(Component component, Style inheritedStyle, Snapshot snapshot) {
        ComponentContents contents = component.getContents();
        Style ownStyle = component.getStyle();
        Style effectiveStyle = ownStyle.applyTo(inheritedStyle);

        List<Component> origSiblings = component.getSiblings();
        List<Component> newSiblings = transformSiblings(origSiblings, effectiveStyle, snapshot);
        boolean siblingsChanged = newSiblings != origSiblings;

        if (contents instanceof ReplacedPlainContents rpc && rpc.snapshotVersion() == snapshot.version()) {
            return siblingsChanged ? rebuildWithSiblings(MutableComponent.create(contents).withStyle(ownStyle), newSiblings) : component;
        }
        if (contents instanceof PlainTextContents ptc) {
            MutableComponent replacedLeaf = replacePlainLeaf(ptc.text(), ownStyle, effectiveStyle, snapshot);
            if (replacedLeaf != null) {
                return rebuildWithSiblings(replacedLeaf, newSiblings);
            }
        } else if (contents instanceof TranslatableContents tc) {
            MutableComponent rebuiltTranslatable = replaceTranslatableNode(tc, ownStyle, effectiveStyle, newSiblings, snapshot);
            if (rebuiltTranslatable != null) {
                return rebuiltTranslatable;
            }
        }
        return siblingsChanged ? rebuildWithSiblings(MutableComponent.create(contents).withStyle(ownStyle), newSiblings) : component;
    }

    private static List<Component> transformSiblings(List<Component> origSiblings, Style parentEffectiveStyle, Snapshot snapshot) {
        if (origSiblings.isEmpty()) {
            return origSiblings;
        }
        List<Component> modified = null;
        for (int i = 0; i < origSiblings.size(); i++) {
            Component child = origSiblings.get(i);
            Component nextChild = replaceNode(child, parentEffectiveStyle, snapshot);
            if (nextChild != child) {
                if (modified == null) {
                    modified = new ArrayList<>(origSiblings);
                }
                modified.set(i, nextChild);
            }
        }
        return modified != null ? modified : origSiblings;
    }

    private static MutableComponent replacePlainLeaf(String text, Style ownStyle, Style effectiveStyle, Snapshot snapshot) {
        if (text == null || text.length() < snapshot.minFromLength()) {
            return null;
        }
        List<StyledSegment> segments = null;
        int len = text.length();
        int literalStart = 0;
        int i = 0;

        while (i < len) {
            if (text.charAt(i) == '\u00A7' && i + 1 < len) {
                i += 2;
                continue;
            }
            int urlEnd = scanUrlEndIfAtStart(text, i);
            if (urlEnd > i) {
                i = urlEnd;
                continue;
            }
            CompiledRule match = findMatchingRule(text, i, snapshot);
            if (match == null) {
                i++;
                continue;
            }
            if (segments == null) {
                segments = new ArrayList<>(4);
            }
            if (i > literalStart) {
                segments.add(new StyledSegment(text.substring(literalStart, i), null));
            }
            appendRuleSegments(segments, match, effectiveStyle);
            i += match.from().length();
            literalStart = i;
        }

        if (segments == null) {
            return null;
        }
        if (literalStart < len) {
            segments.add(new StyledSegment(text.substring(literalStart), null));
        }
        return buildLeafFromSegments(segments, ownStyle, snapshot.version());
    }

    private static MutableComponent buildLeafFromSegments(List<StyledSegment> segments, Style ownStyle, long version) {
        if (segments.isEmpty()) {
            return MutableComponent.create(new ReplacedPlainContents("", version)).withStyle(ownStyle);
        }
        MutableComponent mergedUnstyled = tryMergeUnstyledLeaf(segments, ownStyle, version);
        if (mergedUnstyled != null) {
            return mergedUnstyled;
        }

        StyledSegment first = segments.getFirst();
        MutableComponent root = first.explicitStyle() == null
                ? MutableComponent.create(new ReplacedPlainContents(first.text(), version)).withStyle(ownStyle)
                : MutableComponent.create(new ReplacedPlainContents("", version)).withStyle(ownStyle);
        int startIdx = first.explicitStyle() == null ? 1 : 0;

        for (int idx = startIdx; idx < segments.size(); idx++) {
            StyledSegment seg = segments.get(idx);
            if (seg.explicitStyle() == null && idx + 1 < segments.size() && segments.get(idx + 1).explicitStyle() == null) {
                StringBuilder combined = new StringBuilder(seg.text());
                while (idx + 1 < segments.size() && segments.get(idx + 1).explicitStyle() == null) {
                    idx++;
                    combined.append(segments.get(idx).text());
                }
                root.append(MutableComponent.create(new ReplacedPlainContents(combined.toString(), version)).withStyle(Style.EMPTY));
                continue;
            }
            Style childStyle = seg.explicitStyle() != null ? seg.explicitStyle() : Style.EMPTY;
            root.append(MutableComponent.create(new ReplacedPlainContents(seg.text(), version)).withStyle(childStyle));
        }
        return root;
    }

    private static MutableComponent tryMergeUnstyledLeaf(List<StyledSegment> segments, Style ownStyle, long version) {
        int totalLen = 0;
        for (StyledSegment seg : segments) {
            if (seg.explicitStyle() != null) {
                return null;
            }
            totalLen += seg.text().length();
        }
        StringBuilder merged = new StringBuilder(totalLen);
        for (StyledSegment seg : segments) {
            merged.append(seg.text());
        }
        return MutableComponent.create(new ReplacedPlainContents(merged.toString(), version)).withStyle(ownStyle);
    }

    private static MutableComponent replaceTranslatableNode(
            TranslatableContents tc,
            Style ownStyle,
            Style effectiveStyle,
            List<Component> newSiblings,
            Snapshot snapshot
    ) {
        Object[] origArgs = tc.getArgs();
        Object[] newArgs = origArgs;
        boolean argsChanged = false;
        for (int i = 0; i < origArgs.length; i++) {
            Object arg = origArgs[i];
            Object repArg = arg;
            if (arg instanceof Component argComp) {
                repArg = replaceNode(argComp, effectiveStyle, snapshot);
            } else if (arg instanceof String argStr) {
                repArg = replacePlainString(argStr, snapshot);
            }
            if (repArg != arg) {
                if (!argsChanged) {
                    newArgs = origArgs.clone();
                    argsChanged = true;
                }
                newArgs[i] = repArg;
            }
        }

        MutableComponent expanded = expandTemplateIfMatched(resolveTemplate(tc), newArgs, ownStyle, effectiveStyle, snapshot);
        if (expanded != null) {
            return rebuildWithSiblings(expanded, newSiblings);
        }
        if (!argsChanged) {
            return null;
        }
        MutableComponent rebuilt = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), newArgs).withStyle(ownStyle);
        return rebuildWithSiblings(rebuilt, newSiblings);
    }

    private static String resolveTemplate(TranslatableContents tc) {
        try {
            Language lang = Language.getInstance();
            if (lang != null) {
                return tc.getFallback() != null
                        ? lang.getOrDefault(tc.getKey(), tc.getFallback())
                        : lang.getOrDefault(tc.getKey());
            }
        } catch (RuntimeException ignored) {
        }
        return tc.getFallback();
    }

    private static MutableComponent expandTemplateIfMatched(
            String template,
            Object[] resolvedArgs,
            Style ownStyle,
            Style effectiveStyle,
            Snapshot snapshot
    ) {
        if (template == null || template.length() < snapshot.minFromLength()) {
            return null;
        }
        Matcher matcher = TRANSLATION_FORMAT_PATTERN.matcher(template);
        List<Component> parts = new ArrayList<>(4);
        boolean matched = false;
        int cursor = 0;
        int autoArgIdx = 0;

        while (matcher.find()) {
            if (matcher.start() > cursor) {
                matched |= appendTemplateChunk(parts, template.substring(cursor, matcher.start()), effectiveStyle, snapshot);
            }
            String whole = matcher.group();
            if ("%%".equals(whole)) {
                parts.add(Component.literal("%"));
            } else if ("s".equals(matcher.group(2))) {
                String argIdxStr = matcher.group(1);
                int idx = argIdxStr != null ? parseArgIndex(argIdxStr) : autoArgIdx++;
                appendResolvedArg(parts, resolvedArgs, idx);
            } else {
                parts.add(Component.literal(whole));
            }
            cursor = matcher.end();
        }
        if (cursor < template.length()) {
            matched |= appendTemplateChunk(parts, template.substring(cursor), effectiveStyle, snapshot);
        }
        if (!matched) {
            return null;
        }
        MutableComponent container = MutableComponent.create(new ReplacedPlainContents("", snapshot.version())).withStyle(ownStyle);
        parts.forEach(container::append);
        return container;
    }

    private static boolean appendTemplateChunk(List<Component> parts, String chunk, Style effectiveStyle, Snapshot snapshot) {
        MutableComponent replaced = replacePlainLeaf(chunk, Style.EMPTY, effectiveStyle, snapshot);
        if (replaced != null) {
            parts.add(replaced);
            return true;
        }
        parts.add(Component.literal(chunk));
        return false;
    }

    private static int parseArgIndex(String argIdxStr) {
        try {
            return Integer.parseInt(argIdxStr) - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void appendResolvedArg(List<Component> out, Object[] args, int idx) {
        if (idx < 0 || idx >= args.length || args[idx] == null) {
            return;
        }
        Object val = args[idx];
        out.add(val instanceof Component comp ? comp : Component.literal(String.valueOf(val)));
    }

    private static MutableComponent rebuildWithSiblings(MutableComponent root, List<Component> siblings) {
        for (Component sibling : siblings) {
            root.append(sibling);
        }
        return root;
    }

    private static CompiledRule findMatchingRule(String text, int index, Snapshot snapshot) {
        boolean ignoreCase = !snapshot.caseSensitive();
        int textLen = text.length();
        for (CompiledRule rule : snapshot.rules()) {
            int fromLen = rule.from().length();
            if (index + fromLen > textLen || !text.regionMatches(ignoreCase, index, rule.from(), 0, fromLen)) {
                continue;
            }
            if (snapshot.wholeWord() && !matchesWordBoundaries(text, index, index + fromLen, rule.from())) {
                continue;
            }
            return rule;
        }
        return null;
    }

    private static boolean matchesWordBoundaries(String text, int start, int end, String pattern) {
        if (isWordChar(pattern.charAt(0)) && !isBoundaryBefore(text, start)) {
            return false;
        }
        return !isWordChar(pattern.charAt(pattern.length() - 1)) || isBoundaryAfter(text, end);
    }

    private static boolean isBoundaryBefore(String text, int start) {
        return start <= 0 || isPrecededBySectionCode(text, start) || !isWordChar(text.charAt(start - 1));
    }

    private static boolean isBoundaryAfter(String text, int end) {
        return end >= text.length() || text.charAt(end) == '\u00A7' || !isWordChar(text.charAt(end));
    }

    private static boolean isPrecededBySectionCode(String text, int index) {
        if (index < 2 || text.charAt(index - 2) != '\u00A7') {
            return false;
        }
        int sectionCount = 0;
        for (int cursor = index - 2; cursor >= 0 && text.charAt(cursor) == '\u00A7'; cursor--) {
            sectionCount++;
        }
        return (sectionCount % 2) == 1;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int scanUrlEndIfAtStart(String text, int index) {
        if (index > 0 && isWordChar(text.charAt(index - 1)) && !isPrecededBySectionCode(text, index)) {
            return -1;
        }
        boolean isHttp = text.regionMatches(true, index, "http://", 0, 7);
        boolean isHttps = !isHttp && text.regionMatches(true, index, "https://", 0, 8);
        if (!isHttp && !isHttps) {
            return -1;
        }
        int len = text.length();
        int end = index + (isHttps ? 8 : 7);
        while (end < len) {
            char c = text.charAt(end);
            if (Character.isWhitespace(c) || c == '\u00A7' || c == '"' || c == '\'' || c == '<' || c == '>' || c == ')' || c == ']') {
                break;
            }
            end++;
        }
        return end;
    }

    private static void appendRuleSegments(List<StyledSegment> out, CompiledRule match, Style effectiveStyle) {
        if (!match.hasFormatting() || match.to().isEmpty()) {
            out.add(new StyledSegment(match.to(), null));
            return;
        }
        appendFormattedSegments(out, match.to(), effectiveStyle, null, false);
    }

    private static void appendFormattedSegments(
            List<StyledSegment> out,
            String to,
            Style resetStyle,
            Style codeTagStyle,
            boolean appendCaret
    ) {
        int len = to.length();
        Style activeStyle = resetStyle;
        StringBuilder buf = new StringBuilder(len);
        int i = 0;
        while (i < len) {
            char c = to.charAt(i);
            if (c == '&' && i + 7 < len && to.charAt(i + 1) == '#' && isHexSpan(to, i + 2, 6)) {
                flushSegment(out, buf, activeStyle, resetStyle);
                if (codeTagStyle != null) {
                    out.add(new StyledSegment(to.substring(i, i + 8), codeTagStyle));
                }
                int rgb = Integer.parseInt(to, i + 2, i + 8, 16);
                activeStyle = resetStyle.applyLegacyFormat(ChatFormatting.WHITE).withColor(rgb);
                i += 8;
                continue;
            }
            if (c == '&' && i + 1 < len && isLegacyCode(to.charAt(i + 1))) {
                char code = Character.toLowerCase(to.charAt(i + 1));
                flushSegment(out, buf, activeStyle, resetStyle);
                if (codeTagStyle != null) {
                    out.add(new StyledSegment(to.substring(i, i + 2), codeTagStyle));
                }
                if (code == 'r') {
                    activeStyle = resetStyle;
                } else {
                    ChatFormatting fmt = ChatFormatting.getByCode(code);
                    boolean isColor = isLegacyColorChar(code);
                    activeStyle = isColor ? resetStyle.applyLegacyFormat(fmt) : activeStyle.applyLegacyFormat(fmt);
                }
                i += 2;
                continue;
            }
            buf.append(c);
            i++;
        }
        flushSegment(out, buf, activeStyle, resetStyle);
        if (appendCaret) {
            Style caretStyle = activeStyle.isObfuscated() ? activeStyle.withObfuscated(false) : activeStyle;
            out.add(new StyledSegment("_", caretStyle.equals(resetStyle) ? null : caretStyle));
        }
    }

    private static void flushSegment(List<StyledSegment> out, StringBuilder buf, Style activeStyle, Style resetStyle) {
        if (buf.isEmpty()) {
            return;
        }
        out.add(new StyledSegment(buf.toString(), activeStyle.equals(resetStyle) ? null : activeStyle));
        buf.setLength(0);
    }

    private static boolean isSectionHexSeq(String text, int start) {
        if (start + 13 >= text.length()) {
            return false;
        }
        if (text.charAt(start) != '\u00A7' || Character.toLowerCase(text.charAt(start + 1)) != 'x') {
            return false;
        }
        for (int offset = 2; offset < 14; offset += 2) {
            if (text.charAt(start + offset) != '\u00A7' || Character.digit(text.charAt(start + offset + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLegacyColorChar(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static String convertAmpersandToSection(String text) {
        int len = text.length();
        StringBuilder sb = new StringBuilder(len + 4);
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (c == '&' && i + 7 < len && text.charAt(i + 1) == '#' && isHexSpan(text, i + 2, 6)) {
                sb.append("\u00A7x");
                for (int k = i + 2; k < i + 8; k++) {
                    sb.append('\u00A7').append(Character.toLowerCase(text.charAt(k)));
                }
                i += 8;
                continue;
            }
            if (c == '&' && i + 1 < len && isLegacyCode(text.charAt(i + 1))) {
                sb.append('\u00A7').append(Character.toLowerCase(text.charAt(i + 1)));
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static boolean isLegacyCode(char rawCode) {
        char code = Character.toLowerCase(rawCode);
        return code == 'r' || ChatFormatting.getByCode(code) != null;
    }

    private static boolean isHexSpan(String s, int start, int count) {
        for (int i = start; i < start + count; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
