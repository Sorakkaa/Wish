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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WordReplacerTextEngine {
    static final long BYPASSED_VERSION = -1L;
    private static final Pattern TRANSLATION_FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");
    private static final ThreadLocal<CharSeqScratch> CHAR_SEQ_SCRATCH = ThreadLocal.withInitial(CharSeqScratch::new);

    private WordReplacerTextEngine() {
    }

    private interface MarkedCharSequence extends FormattedCharSequence {
        long version();
    }

    private record VersionedCharSequence(FormattedCharSequence delegate, long version) implements MarkedCharSequence {
        @Override
        public boolean accept(FormattedCharSink sink) {
            return delegate.accept(sink);
        }
    }

    private static final class ReplacedCharSequence implements MarkedCharSequence {
        private final String[] texts;
        private final Style[] styles;
        private final long version;

        ReplacedCharSequence(String[] texts, Style[] styles, long version) {
            this.texts = texts;
            this.styles = styles;
            this.version = version;
        }

        @Override
        public long version() {
            return version;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            for (int i = 0; i < texts.length; i++) {
                if (!StringDecomposer.iterate(texts[i], styles[i], sink)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class CharSeqScratch implements CharSequence {
        char[] chars = new char[128];
        Style[] styles = new Style[128];
        int len;
        private final FormattedCharSink collector = (pos, style, codePoint) -> {
            ensureCapacity(len + Character.charCount(codePoint));
            Style safeStyle = style != null ? style : Style.EMPTY;
            if (Character.isBmpCodePoint(codePoint)) {
                chars[len] = (char) codePoint;
                styles[len] = safeStyle;
                len++;
            } else {
                chars[len] = Character.highSurrogate(codePoint);
                styles[len] = safeStyle;
                len++;
                chars[len] = Character.lowSurrogate(codePoint);
                styles[len] = safeStyle;
                len++;
            }
            return true;
        };

        void resetAndCollect(FormattedCharSequence seq) {
            len = 0;
            seq.accept(collector);
        }

        private void ensureCapacity(int required) {
            if (required <= chars.length) {
                return;
            }
            int next = Math.max(required, chars.length * 2);
            chars = Arrays.copyOf(chars, next);
            styles = Arrays.copyOf(styles, next);
        }

        @Override
        public int length() {
            return len;
        }

        @Override
        public char charAt(int index) {
            return chars[index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new String(chars, start, end - start);
        }
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

    private record FlatPlainSpan(String text, Style relativeStyle, boolean alreadyReplaced) {
    }

    private record CoalesceResult(MutableComponent rebuiltRoot, List<Component> rebuiltSiblings) {
    }

    private static final class PlainStyleState {
        private String colorPrefix = "";
        private Integer hexColorRgb;
        private ChatFormatting legacyColor;
        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        void applySectionCode(CharSequence text, int index, int step) {
            if (step == 14) {
                StringBuilder prefix = new StringBuilder(14);
                int rgb = 0;
                for (int k = 0; k < 14; k++) {
                    char ch = text.charAt(index + k);
                    prefix.append(ch);
                    if (k >= 2 && (k % 2) == 1) {
                        rgb = (rgb << 4) | Character.digit(ch, 16);
                    }
                }
                colorPrefix = prefix.toString();
                hexColorRgb = rgb;
                legacyColor = null;
                clearFormats();
                return;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            if (code == 'r') {
                colorPrefix = "";
                hexColorRgb = null;
                legacyColor = null;
                clearFormats();
            } else if (isLegacyColorChar(code)) {
                colorPrefix = "\u00A7" + code;
                legacyColor = ChatFormatting.getByCode(code);
                hexColorRgb = null;
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

        Style applyTo(Style base) {
            Style result = base != null ? base : Style.EMPTY;
            if (hexColorRgb != null) {
                result = result.withColor(hexColorRgb);
            } else if (legacyColor != null) {
                result = result.withColor(legacyColor);
            }
            if (obfuscated) result = result.withObfuscated(true);
            if (bold) result = result.withBold(true);
            if (strikethrough) result = result.withStrikethrough(true);
            if (underlined) result = result.withUnderlined(true);
            if (italic) result = result.withItalic(true);
            return result;
        }

        void appendSectionCodes(StringBuilder sb) {
            if (!colorPrefix.isEmpty()) {
                sb.append(colorPrefix);
            }
            if (obfuscated) sb.append("\u00A7k");
            if (bold) sb.append("\u00A7l");
            if (strikethrough) sb.append("\u00A7m");
            if (underlined) sb.append("\u00A7n");
            if (italic) sb.append("\u00A7o");
        }

        String toSectionString() {
            if (isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(24);
            appendSectionCodes(sb);
            return sb.toString();
        }

        void appendResetAndRestore(StringBuilder sb) {
            sb.append("\u00A7r");
            appendSectionCodes(sb);
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

    static FormattedCharSequence wrapVersion(FormattedCharSequence seq, long version) {
        if (seq == null) {
            return null;
        }
        if (seq instanceof MarkedCharSequence marked) {
            if (marked.version() == version) {
                return seq;
            }
            FormattedCharSequence inner = marked instanceof VersionedCharSequence vcs ? vcs.delegate() : marked;
            return new VersionedCharSequence(inner, version);
        }
        return new VersionedCharSequence(seq, version);
    }

    static long getVersion(FormattedCharSequence seq) {
        return seq instanceof MarkedCharSequence marked ? marked.version() : Long.MIN_VALUE;
    }

    static MutableComponent createMarkedLiteral(String text, Style style, long version) {
        return MutableComponent.create(new ReplacedPlainContents(text != null ? text : "", version))
                .withStyle(style != null ? style : Style.EMPTY);
    }

    static boolean isComponentMarkedReplaced(Component component, long version) {
        if (component == null) {
            return false;
        }
        if (!(component.getContents() instanceof ReplacedPlainContents rpc) || rpc.snapshotVersion() != version) {
            return false;
        }
        for (Component sibling : component.getSiblings()) {
            if (!isComponentMarkedReplaced(sibling, version)) {
                return false;
            }
        }
        return true;
    }

    static FormattedCharSequence replaceCharSequence(FormattedCharSequence seq, Snapshot snapshot) {
        if (seq == null || snapshot == null || !snapshot.enabled() || snapshot.rules().isEmpty()) {
            return seq;
        }
        long existingVersion = getVersion(seq);
        if (existingVersion == BYPASSED_VERSION || existingVersion == snapshot.version()) {
            return seq;
        }
        CharSeqScratch scratch = CHAR_SEQ_SCRATCH.get();
        scratch.resetAndCollect(seq);
        int len = scratch.len;
        if (len < snapshot.minFromLength()) {
            return seq;
        }

        List<String> outTexts = null;
        List<Style> outStyles = null;
        int literalStart = 0;
        int lastVisibleChar = -1;
        int i = 0;
        while (i < len) {
            char c = scratch.chars[i];
            if (c == '\u00A7' && i + 1 < len) {
                i += isSectionHexSeq(scratch, i) ? 14 : 2;
                continue;
            }
            int urlEnd = scanUrlEndIfAtStart(scratch, i, lastVisibleChar);
            if (urlEnd > i) {
                lastVisibleChar = scratch.chars[urlEnd - 1];
                i = urlEnd;
                continue;
            }
            RuleMatch match = findMatchingRule(scratch, i, lastVisibleChar, snapshot);
            if (match == null) {
                lastVisibleChar = c;
                i++;
                continue;
            }
            if (outTexts == null) {
                outTexts = new ArrayList<>(4);
                outStyles = new ArrayList<>(4);
            }
            if (i > literalStart) {
                appendScratchSlice(outTexts, outStyles, scratch, literalStart, i);
            }
            Style baseStyle = scratch.styles[i];
            CompiledRule rule = match.rule();
            if (!rule.hasFormatting() || rule.to().isEmpty()) {
                appendMergedRun(outTexts, outStyles, rule.to(), baseStyle);
            } else {
                List<StyledSegment> segs = new ArrayList<>(4);
                appendFormattedSegments(segs, rule.to(), baseStyle, null, false);
                for (StyledSegment seg : segs) {
                    appendMergedRun(outTexts, outStyles, seg.text(), seg.explicitStyle() != null ? seg.explicitStyle() : baseStyle);
                }
            }
            lastVisibleChar = lastVisibleReplacementChar(rule.to(), lastVisibleChar);
            i = match.end();
            literalStart = i;
        }

        if (outTexts == null) {
            return seq;
        }
        if (literalStart < len) {
            appendScratchSlice(outTexts, outStyles, scratch, literalStart, len);
        }
        if (outTexts.isEmpty()) {
            return new ReplacedCharSequence(new String[]{""}, new Style[]{Style.EMPTY}, snapshot.version());
        }
        return new ReplacedCharSequence(
                outTexts.toArray(String[]::new),
                outStyles.toArray(Style[]::new),
                snapshot.version()
        );
    }

    private static void appendScratchSlice(List<String> outTexts, List<Style> outStyles, CharSeqScratch scratch, int start, int end) {
        int runStart = start;
        Style runStyle = scratch.styles[start];
        for (int idx = start + 1; idx < end; idx++) {
            Style s = scratch.styles[idx];
            if (!runStyle.equals(s)) {
                appendMergedRun(outTexts, outStyles, new String(scratch.chars, runStart, idx - runStart), runStyle);
                runStart = idx;
                runStyle = s;
            }
        }
        appendMergedRun(outTexts, outStyles, new String(scratch.chars, runStart, end - runStart), runStyle);
    }

    private static void appendMergedRun(List<String> outTexts, List<Style> outStyles, String text, Style style) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Style safeStyle = style != null ? style : Style.EMPTY;
        int lastIdx = outTexts.size() - 1;
        if (lastIdx >= 0 && outStyles.get(lastIdx).equals(safeStyle)) {
            outTexts.set(lastIdx, outTexts.get(lastIdx) + text);
            return;
        }
        outTexts.add(text);
        outStyles.add(safeStyle);
    }

    static String replacePlainString(String text, Snapshot snapshot) {
        if (text == null || snapshot == null || !snapshot.enabled() || snapshot.rules().isEmpty() || text.length() < snapshot.minFromLength()) {
            return text;
        }
        int len = text.length();
        StringBuilder sb = null;
        PlainStyleState styleState = new PlainStyleState();
        int lastVisibleChar = -1;
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
            int urlEnd = scanUrlEndIfAtStart(text, i, lastVisibleChar);
            if (urlEnd > i) {
                if (sb != null) {
                    sb.append(text, i, urlEnd);
                }
                lastVisibleChar = text.charAt(urlEnd - 1);
                i = urlEnd;
                continue;
            }
            RuleMatch match = findMatchingRule(text, i, lastVisibleChar, snapshot);
            if (match != null) {
                if (sb == null) {
                    sb = new StringBuilder(len + 16);
                    sb.append(text, 0, i);
                }
                advanceStyleState(text, i, match.end(), styleState);
                appendPlainReplacement(sb, match.rule(), styleState, match.end() < len);
                lastVisibleChar = lastVisibleReplacementChar(match.rule().to(), lastVisibleChar);
                i = match.end();
                continue;
            }
            char c = text.charAt(i);
            if (sb != null) {
                sb.append(c);
            }
            lastVisibleChar = c;
            i++;
        }
        return sb != null ? sb.toString() : text;
    }

    private static void advanceStyleState(CharSequence text, int start, int end, PlainStyleState state) {
        int i = start;
        while (i < end) {
            if (text.charAt(i) == '\u00A7' && i + 1 < end) {
                int step = isSectionHexSeq(text, i) ? 14 : 2;
                state.applySectionCode(text, i, step);
                i += step;
                continue;
            }
            i++;
        }
    }

    private static int lastVisibleReplacementChar(String to, int fallback) {
        if (to == null || to.isEmpty()) {
            return fallback;
        }
        String clean = stripFormattingCodes(to);
        return clean.isEmpty() ? fallback : clean.charAt(clean.length() - 1);
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

    private record RuleMatch(int end, CompiledRule rule) {
    }

    private sealed interface CoalesceToken permits SpanToken, NodeToken {
    }

    private record SpanToken(String text, Style relStyle, Component sourceNode) implements CoalesceToken {
    }

    private record NodeToken(Component node) implements CoalesceToken {
    }

    private record PendingMatch(int start, int end, CompiledRule rule) {
    }

    private static Component replaceNode(Component component, Style inheritedStyle, Snapshot snapshot) {
        ComponentContents contents = component.getContents();
        Style ownStyle = component.getStyle();
        Style effectiveStyle = ownStyle.applyTo(inheritedStyle);

        List<Component> origSiblings = component.getSiblings();
        List<Component> newSiblings = transformSiblings(origSiblings, effectiveStyle, snapshot);
        boolean siblingsChanged = newSiblings != origSiblings;

        if (!newSiblings.isEmpty()) {
            Component coalesced = coalesceComponentIfMatched(contents, ownStyle, effectiveStyle, newSiblings, snapshot);
            if (coalesced != null) {
                return coalesced;
            }
        }

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

    private static Component coalesceComponentIfMatched(
            ComponentContents rootContents,
            Style ownStyle,
            Style effectiveStyle,
            List<Component> siblings,
            Snapshot snapshot
    ) {
        long version = snapshot.version();
        boolean selfPlain = rootContents instanceof PlainTextContents ptc
                && !(rootContents instanceof ReplacedPlainContents rpc && rpc.snapshotVersion() == version);
        List<CoalesceToken> tokens = new ArrayList<>(siblings.size() + 2);
        if (selfPlain) {
            String rootText = ((PlainTextContents) rootContents).text();
            if (!rootText.isEmpty()) {
                tokens.add(new SpanToken(rootText, Style.EMPTY, null));
            }
        }
        for (Component sibling : siblings) {
            List<SpanToken> flat = new ArrayList<>(4);
            if (collectPurePlainSubtree(sibling, Style.EMPTY, version, sibling, flat)) {
                tokens.addAll(flat);
            } else {
                tokens.add(new NodeToken(sibling));
            }
        }

        boolean anyCoalesced = false;
        boolean selfConsumedByRun = false;
        List<Component> outSiblings = new ArrayList<>(tokens.size());
        int idx = 0;
        while (idx < tokens.size()) {
            CoalesceToken tok = tokens.get(idx);
            if (tok instanceof NodeToken nt) {
                outSiblings.add(nt.node());
                idx++;
                continue;
            }
            int runEnd = idx + 1;
            while (runEnd < tokens.size() && tokens.get(runEnd) instanceof SpanToken) {
                runEnd++;
            }
            boolean runIncludesSelf = selfPlain && idx == 0 && !((PlainTextContents) rootContents).text().isEmpty();
            if (runEnd - idx >= 2) {
                List<SpanToken> runSpans = new ArrayList<>(runEnd - idx);
                for (int k = idx; k < runEnd; k++) {
                    runSpans.add((SpanToken) tokens.get(k));
                }
                List<Component> coalescedRun = tryCoalesceSpanRun(runSpans, effectiveStyle, snapshot);
                if (coalescedRun != null) {
                    anyCoalesced = true;
                    if (runIncludesSelf) {
                        selfConsumedByRun = true;
                    }
                    outSiblings.addAll(coalescedRun);
                    idx = runEnd;
                    continue;
                }
            }
            for (int k = idx; k < runEnd; k++) {
                SpanToken st = (SpanToken) tokens.get(k);
                if (st.sourceNode() != null) {
                    if (k == idx || tokens.get(k - 1) instanceof NodeToken || ((SpanToken) tokens.get(k - 1)).sourceNode() != st.sourceNode()) {
                        outSiblings.add(st.sourceNode());
                    }
                }
            }
            idx = runEnd;
        }

        if (!anyCoalesced) {
            return null;
        }
        MutableComponent rebuiltRoot = selfConsumedByRun
                ? MutableComponent.create(new ReplacedPlainContents("", version)).withStyle(ownStyle)
                : MutableComponent.create(rootContents).withStyle(ownStyle);
        return rebuildWithSiblings(rebuiltRoot, outSiblings);
    }

    private static boolean collectPurePlainSubtree(
            Component node,
            Style parentRelStyle,
            long version,
            Component topLevelSource,
            List<SpanToken> out
    ) {
        ComponentContents c = node.getContents();
        if (!(c instanceof PlainTextContents ptc)) {
            return false;
        }
        if (c instanceof ReplacedPlainContents rpc && rpc.snapshotVersion() == version) {
            return false;
        }
        Style relStyle = node.getStyle().applyTo(parentRelStyle);
        if (!ptc.text().isEmpty()) {
            out.add(new SpanToken(ptc.text(), relStyle, topLevelSource));
        }
        for (Component child : node.getSiblings()) {
            if (!collectPurePlainSubtree(child, relStyle, version, topLevelSource, out)) {
                return false;
            }
        }
        return true;
    }

    private static List<Component> tryCoalesceSpanRun(
            List<SpanToken> spans,
            Style rootEffectiveStyle,
            Snapshot snapshot
    ) {
        int count = spans.size();
        int[] starts = new int[count];
        int[] ends = new int[count];
        StringBuilder combined = new StringBuilder();
        for (int k = 0; k < count; k++) {
            starts[k] = combined.length();
            combined.append(spans.get(k).text());
            ends[k] = combined.length();
        }
        int totalLen = combined.length();
        if (totalLen < snapshot.minFromLength()) {
            return null;
        }

        List<PendingMatch> matches = null;
        boolean hasCrossBoundaryMatch = false;
        int lastVisibleChar = -1;
        int i = 0;
        while (i < totalLen) {
            if (combined.charAt(i) == '\u00A7' && i + 1 < totalLen) {
                i += isSectionHexSeq(combined, i) ? 14 : 2;
                continue;
            }
            int urlEnd = scanUrlEndIfAtStart(combined, i, lastVisibleChar);
            if (urlEnd > i) {
                lastVisibleChar = combined.charAt(urlEnd - 1);
                i = urlEnd;
                continue;
            }
            RuleMatch match = findMatchingRule(combined, i, lastVisibleChar, snapshot);
            if (match == null) {
                lastVisibleChar = combined.charAt(i);
                i++;
                continue;
            }
            if (matches == null) {
                matches = new ArrayList<>(4);
            }
            matches.add(new PendingMatch(i, match.end(), match.rule()));
            if (!hasCrossBoundaryMatch) {
                int startSpan = findSpanAt(starts, ends, i);
                int endSpan = findSpanAt(starts, ends, match.end() - 1);
                if (startSpan != endSpan) {
                    hasCrossBoundaryMatch = true;
                }
            }
            lastVisibleChar = lastVisibleReplacementChar(match.rule().to(), lastVisibleChar);
            i = match.end();
        }

        if (!hasCrossBoundaryMatch || matches == null) {
            return null;
        }

        long version = snapshot.version();
        List<Component> emitted = new ArrayList<>(count + matches.size());
        int cursor = 0;
        for (PendingMatch m : matches) {
            if (m.start() > cursor) {
                emitSpanSliceRange(spans, starts, ends, cursor, m.start(), version, emitted);
            }
            emitMatchedReplacement(spans, starts, ends, m, rootEffectiveStyle, version, emitted);
            cursor = m.end();
        }
        if (cursor < totalLen) {
            emitSpanSliceRange(spans, starts, ends, cursor, totalLen, version, emitted);
        }
        return emitted;
    }

    private static int findSpanAt(int[] starts, int[] ends, int pos) {
        for (int k = 0; k < starts.length; k++) {
            if (pos >= starts[k] && pos < ends[k]) {
                return k;
            }
        }
        return starts.length - 1;
    }

    private static void emitSpanSliceRange(
            List<SpanToken> spans,
            int[] starts,
            int[] ends,
            int rangeStart,
            int rangeEnd,
            long version,
            List<Component> out
    ) {
        for (int k = 0; k < spans.size(); k++) {
            int overlapStart = Math.max(rangeStart, starts[k]);
            int overlapEnd = Math.min(rangeEnd, ends[k]);
            if (overlapEnd <= overlapStart) {
                continue;
            }
            SpanToken span = spans.get(k);
            int localStart = overlapStart - starts[k];
            int localEnd = overlapEnd - starts[k];
            String sub = span.text().substring(localStart, localEnd);
            PlainStyleState stateBefore = computeStyleStateAt(span.text(), localStart);
            Style relStyle = stateBefore.isEmpty() ? span.relStyle() : stateBefore.applyTo(span.relStyle());
            out.add(createMarkedLiteral(sub, relStyle, version));
        }
    }

    private static void emitMatchedReplacement(
            List<SpanToken> spans,
            int[] starts,
            int[] ends,
            PendingMatch match,
            Style rootEffectiveStyle,
            long version,
            List<Component> out
    ) {
        CompiledRule rule = match.rule();
        if (rule.to().isEmpty()) {
            return;
        }
        int startSpanIdx = findSpanAt(starts, ends, match.start());
        SpanToken startSpan = spans.get(startSpanIdx);
        int localStart = match.start() - starts[startSpanIdx];
        PlainStyleState styleAtStart = computeStyleStateAt(startSpan.text(), localStart);
        Style matchRelStyle = styleAtStart.applyTo(startSpan.relStyle());
        if (!rule.hasFormatting()) {
            out.add(createMarkedLiteral(rule.to(), matchRelStyle, version));
            return;
        }
        Style matchAbsStyle = matchRelStyle.applyTo(rootEffectiveStyle);
        List<StyledSegment> segs = new ArrayList<>(4);
        appendFormattedSegments(segs, rule.to(), matchAbsStyle, null, false);
        for (StyledSegment seg : segs) {
            Style segStyle = seg.explicitStyle() != null ? seg.explicitStyle() : matchAbsStyle;
            out.add(createMarkedLiteral(seg.text(), segStyle, version));
        }
    }

    private static PlainStyleState computeStyleStateAt(String text, int limit) {
        PlainStyleState state = new PlainStyleState();
        advanceStyleState(text, 0, Math.min(limit, text.length()), state);
        return state;
    }

    private static MutableComponent replacePlainLeaf(String text, Style ownStyle, Style effectiveStyle, Snapshot snapshot) {
        if (text == null || text.length() < snapshot.minFromLength()) {
            return null;
        }
        List<StyledSegment> segments = null;
        int len = text.length();
        int literalStart = 0;
        PlainStyleState styleState = new PlainStyleState();
        PlainStyleState literalStartStyle = new PlainStyleState();
        boolean emittedStyledSegment = false;
        int lastVisibleChar = -1;
        int i = 0;

        while (i < len) {
            if (text.charAt(i) == '\u00A7' && i + 1 < len) {
                int step = isSectionHexSeq(text, i) ? 14 : 2;
                styleState.applySectionCode(text, i, step);
                i += step;
                continue;
            }
            int urlEnd = scanUrlEndIfAtStart(text, i, lastVisibleChar);
            if (urlEnd > i) {
                lastVisibleChar = text.charAt(urlEnd - 1);
                i = urlEnd;
                continue;
            }
            RuleMatch match = findMatchingRule(text, i, lastVisibleChar, snapshot);
            if (match == null) {
                lastVisibleChar = text.charAt(i);
                i++;
                continue;
            }
            if (segments == null) {
                segments = new ArrayList<>(4);
            }
            if (i > literalStart) {
                Style litStyle = emittedStyledSegment && !literalStartStyle.isEmpty()
                        ? literalStartStyle.applyTo(Style.EMPTY)
                        : null;
                segments.add(new StyledSegment(text.substring(literalStart, i), litStyle));
            }
            if (match.rule().hasFormatting()) {
                emittedStyledSegment = true;
                appendRuleSegments(segments, match.rule(), styleState.applyTo(effectiveStyle));
            } else {
                Style matchStyle = emittedStyledSegment && !styleState.isEmpty()
                        ? styleState.applyTo(Style.EMPTY)
                        : null;
                segments.add(new StyledSegment(match.rule().to(), matchStyle));
            }
            advanceStyleState(text, i, match.end(), styleState);
            copyStyleState(styleState, literalStartStyle);
            lastVisibleChar = lastVisibleReplacementChar(match.rule().to(), lastVisibleChar);
            i = match.end();
            literalStart = i;
        }

        if (segments == null) {
            return null;
        }
        if (literalStart < len) {
            Style trailingStyle = emittedStyledSegment && !literalStartStyle.isEmpty()
                    ? literalStartStyle.applyTo(Style.EMPTY)
                    : null;
            segments.add(new StyledSegment(text.substring(literalStart), trailingStyle));
        }
        return buildLeafFromSegments(segments, ownStyle, snapshot.version());
    }

    private static void copyStyleState(PlainStyleState src, PlainStyleState dst) {
        dst.colorPrefix = src.colorPrefix;
        dst.hexColorRgb = src.hexColorRgb;
        dst.legacyColor = src.legacyColor;
        dst.obfuscated = src.obfuscated;
        dst.bold = src.bold;
        dst.strikethrough = src.strikethrough;
        dst.underlined = src.underlined;
        dst.italic = src.italic;
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

    private static RuleMatch findMatchingRule(CharSequence text, int index, int lastVisibleChar, Snapshot snapshot) {
        boolean ignoreCase = !snapshot.caseSensitive();
        for (CompiledRule rule : snapshot.rules()) {
            int matchEnd = matchRuleAt(text, index, rule.from(), ignoreCase);
            if (matchEnd < 0) {
                continue;
            }
            if (snapshot.wholeWord() && !matchesWordBoundaries(text, matchEnd, lastVisibleChar, rule.from())) {
                continue;
            }
            return new RuleMatch(matchEnd, rule);
        }
        return null;
    }

    private static int matchRuleAt(CharSequence text, int startIndex, String pattern, boolean ignoreCase) {
        int textLen = text.length();
        int patLen = pattern.length();
        if (patLen == 0 || startIndex + patLen > textLen) {
            return -1;
        }
        if (!charsMatch(text.charAt(startIndex), pattern.charAt(0), ignoreCase)) {
            return -1;
        }
        int ti = startIndex + 1;
        int fi = 1;
        while (fi < patLen) {
            char fc = pattern.charAt(fi);
            if (fc != '\u00A7') {
                while (ti + 1 < textLen && text.charAt(ti) == '\u00A7') {
                    ti += isSectionHexSeq(text, ti) ? 14 : 2;
                }
            }
            if (ti >= textLen || !charsMatch(text.charAt(ti), fc, ignoreCase)) {
                return -1;
            }
            ti++;
            fi++;
        }
        return ti;
    }

    private static boolean charsMatch(char rawTextChar, char rawPatternChar, boolean ignoreCase) {
        char tc = rawTextChar == '\u00A0' ? ' ' : rawTextChar;
        char fc = rawPatternChar == '\u00A0' ? ' ' : rawPatternChar;
        if (tc == fc) {
            return true;
        }
        if (!ignoreCase) {
            return false;
        }
        return Character.toLowerCase(tc) == Character.toLowerCase(fc)
                || Character.toUpperCase(tc) == Character.toUpperCase(fc);
    }

    private static boolean matchesWordBoundaries(CharSequence text, int end, int lastVisibleChar, String pattern) {
        if (isWordChar(pattern.charAt(0)) && lastVisibleChar != -1 && isWordChar((char) lastVisibleChar)) {
            return false;
        }
        return !isWordChar(pattern.charAt(pattern.length() - 1)) || isBoundaryAfterSkippingSection(text, end);
    }

    private static boolean isBoundaryAfterSkippingSection(CharSequence text, int end) {
        int len = text.length();
        int cursor = end;
        while (cursor + 1 < len && text.charAt(cursor) == '\u00A7') {
            cursor += isSectionHexSeq(text, cursor) ? 14 : 2;
        }
        return cursor >= len || !isWordChar(text.charAt(cursor));
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int scanUrlEndIfAtStart(CharSequence text, int index, int lastVisibleChar) {
        if (lastVisibleChar != -1 && isWordChar((char) lastVisibleChar)) {
            return -1;
        }
        boolean isHttp = regionMatchesIgnoreCase(text, index, "http://");
        boolean isHttps = !isHttp && regionMatchesIgnoreCase(text, index, "https://");
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

    private static boolean regionMatchesIgnoreCase(CharSequence text, int offset, String needle) {
        int needleLen = needle.length();
        if (offset + needleLen > text.length()) {
            return false;
        }
        for (int i = 0; i < needleLen; i++) {
            if (!charsMatch(text.charAt(offset + i), needle.charAt(i), true)) {
                return false;
            }
        }
        return true;
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

    private static boolean isSectionHexSeq(CharSequence text, int start) {
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
