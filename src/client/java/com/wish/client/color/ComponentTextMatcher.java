package com.wish.client.color;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class ComponentTextMatcher {

    private static final int MAX_MINIMESSAGE_TAG_LEN = 128;
    private static final int MAX_PLAIN_WRAPPER_DEPTH = 4;

    private static final Pattern MM_HEX_EXTENDED = Pattern.compile("<[^>]*#[0-9A-Fa-f\\u00A7rRk-oK-O]{6,12}[^>]*>");
    private static final Pattern MM_HEX_SIMPLE = Pattern.compile("<#/?[0-9A-Fa-f]{6}>");
    private static final Pattern MM_COMPLEX_TAGS = Pattern.compile("<(?:color|c|font|gradient|rainbow|hover|click):[^>]+>");
    private static final Pattern MM_CLOSE_TAGS = Pattern.compile("</(?:color|c|font|gradient|rainbow|hover|click|bold|italic|underlined|strikethrough|obfuscated|reset)>");
    private static final Pattern MM_FORMAT_TAGS = Pattern.compile("<(?:bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf|reset|r)>");
    private static final Pattern MM_ANY_CLOSE = Pattern.compile("</[^>]*>");

    private static final Pattern TRAILING_COLOR_CODE_PATTERN = Pattern.compile("(\\u00A7[0-9a-fA-Fk-rK-R])+$");

    private static final ThreadLocal<ScanBuffer> SCAN_BUFFER = ThreadLocal.withInitial(ScanBuffer::new);
    private static final ThreadLocal<TagMatchers> TAG_MATCHERS = ThreadLocal.withInitial(TagMatchers::new);

    public record TargetSpanMatch(int targetIndex, String matchedCleanName, String before, String after) {}

    public record CoalesceResult(String selfText, List<Component> siblings, boolean changed) {
        public static final CoalesceResult UNCHANGED = new CoalesceResult(null, List.of(), false);
    }

    private ComponentTextMatcher() {}

    public static String stripMiniMessageTags(String text) {
        if (text == null || text.isEmpty() || text.indexOf('<') == -1) {
            return text;
        }
        String s = MM_HEX_EXTENDED.matcher(text).replaceAll("");
        s = MM_HEX_SIMPLE.matcher(s).replaceAll("");
        s = MM_COMPLEX_TAGS.matcher(s).replaceAll("");
        s = MM_CLOSE_TAGS.matcher(s).replaceAll("");
        s = MM_FORMAT_TAGS.matcher(s).replaceAll("");
        return MM_ANY_CLOSE.matcher(s).replaceAll("");
    }

    public static Component stripMiniMessageFromComponent(Component component) {
        if (component == null || "wish:tab".equals(component.getStyle().getInsertion())) {
            return component;
        }
        if (!mightContainAngleBracket(component)) {
            return component;
        }
        return stripComponentRecursive(component);
    }

    public static TargetSpanMatch findFirstTargetMatch(String text, List<String> targetNames) {
        if (text == null || text.isEmpty() || targetNames == null || targetNames.isEmpty()) {
            return null;
        }
        ScanBuffer buf = SCAN_BUFFER.get();
        buf.reset();
        appendRawToScanBuffer(buf, text, 0, false);
        if (buf.clean.isEmpty()) {
            return null;
        }

        for (int t = 0, tn = targetNames.size(); t < tn; t++) {
            String target = targetNames.get(t);
            if (target == null || target.isEmpty()) {
                continue;
            }
            int matchCleanStart = findMatchInBuffer(buf, target, false);
            if (matchCleanStart != -1) {
                int matchCleanEnd = matchCleanStart + target.length();
                int rawStart = buf.rawStarts[matchCleanStart];
                int rawEnd = buf.rawEnds[matchCleanEnd - 1];
                String matchedClean = extractCleanSlice(buf, matchCleanStart, matchCleanEnd);
                return new TargetSpanMatch(t, matchedClean, text.substring(0, rawStart), text.substring(rawEnd));
            }
        }
        return null;
    }

    public static CoalesceResult coalesceSplitTargetNames(
            String selfRawText,
            Style selfStyle,
            List<Component> siblings,
            List<String> targetNames) {
        if (targetNames == null || targetNames.isEmpty() || siblings == null || siblings.isEmpty()) {
            return CoalesceResult.UNCHANGED;
        }

        boolean selfEligible = selfRawText != null && !selfRawText.isEmpty() && !containsUrl(selfRawText);
        ScanBuffer buf = SCAN_BUFFER.get();
        buf.clearWorkParts();

        if (selfEligible) {
            buf.workParts.add(new WorkPart(-1, selfRawText, selfStyle != null ? selfStyle : Style.EMPTY, false));
        }

        for (int i = 0, n = siblings.size(); i < n; i++) {
            Component sib = siblings.get(i);
            PlainLeaf leaf = tryAsPlainLeaf(sib);
            if (leaf != null) {
                buf.workParts.add(new WorkPart(i, leaf.text(), leaf.style(), false));
            } else {
                buf.workParts.add(new WorkPart(i, null, Style.EMPTY, true));
            }
        }

        boolean anyChanged = coalesceWorkParts(buf, targetNames);
        if (!anyChanged) {
            return CoalesceResult.UNCHANGED;
        }

        String newSelfText = selfRawText;
        boolean selfClearedOrModified = selfEligible;
        int startIdx = 0;
        if (selfEligible && !buf.workParts.isEmpty() && buf.workParts.get(0).sourceIndex == -1) {
            newSelfText = buf.workParts.get(0).rawText;
            startIdx = 1;
            selfClearedOrModified = false;
        } else if (selfEligible) {
            newSelfText = "";
        }

        int outCap = Math.max(0, buf.workParts.size() - startIdx + (selfClearedOrModified ? 1 : 0));
        List<Component> newSiblings = new ArrayList<>(outCap);
        for (int i = startIdx, n = buf.workParts.size(); i < n; i++) {
            WorkPart part = buf.workParts.get(i);
            if (part.isBarrier) {
                newSiblings.add(siblings.get(part.sourceIndex));
            } else if (part.rawText != null && !part.rawText.isEmpty()) {
                newSiblings.add(Component.literal(part.rawText).withStyle(part.style));
            }
        }
        return new CoalesceResult(newSelfText, newSiblings, true);
    }

    public static boolean isUsernameChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_';
    }

    private static boolean mightContainAngleBracket(Component comp) {
        if (comp == null || "wish:tab".equals(comp.getStyle().getInsertion())) {
            return false;
        }
        ComponentContents contents = comp.getContents();
        if (contents instanceof PlainTextContents plain) {
            if (plain.text().indexOf('<') != -1) {
                return true;
            }
        } else if (contents instanceof TranslatableContents trans) {
            Object[] args = trans.getArgs();
            for (Object arg : args) {
                if (arg instanceof Component argComp && mightContainAngleBracket(argComp)) {
                    return true;
                }
                if (arg instanceof String argStr && argStr.indexOf('<') != -1) {
                    return true;
                }
            }
        }
        List<Component> siblings = comp.getSiblings();
        for (int i = 0, n = siblings.size(); i < n; i++) {
            if (mightContainAngleBracket(siblings.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static Component stripComponentRecursive(Component comp) {
        if (comp == null || "wish:tab".equals(comp.getStyle().getInsertion())) {
            return comp;
        }

        boolean selfChanged = false;
        MutableComponent rebuilt = null;
        ComponentContents contents = comp.getContents();

        if (contents instanceof PlainTextContents plain) {
            String raw = plain.text();
            if (!containsUrl(raw)) {
                String cleaned = stripMiniMessageTags(raw);
                if (!cleaned.equals(raw)) {
                    selfChanged = true;
                    rebuilt = Component.literal(cleaned).withStyle(comp.getStyle());
                }
            }
        } else if (contents instanceof TranslatableContents trans) {
            Object[] args = trans.getArgs();
            Object[] newArgs = null;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof Component argComp) {
                    Component strippedArg = stripComponentRecursive(argComp);
                    if (strippedArg != argComp) {
                        if (newArgs == null) {
                            newArgs = args.clone();
                        }
                        newArgs[i] = strippedArg;
                    }
                } else if (arg instanceof String argStr && !containsUrl(argStr)) {
                    String cleaned = stripMiniMessageTags(argStr);
                    if (!cleaned.equals(argStr)) {
                        if (newArgs == null) {
                            newArgs = args.clone();
                        }
                        newArgs[i] = cleaned;
                    }
                }
            }
            if (newArgs != null) {
                selfChanged = true;
                rebuilt = Component.translatable(trans.getKey(), newArgs).withStyle(comp.getStyle());
            }
        }

        List<Component> origSiblings = comp.getSiblings();
        for (int i = 0, n = origSiblings.size(); i < n; i++) {
            Component origChild = origSiblings.get(i);
            Component strippedChild = stripComponentRecursive(origChild);
            if (strippedChild != origChild && rebuilt == null) {
                rebuilt = comp.plainCopy().withStyle(comp.getStyle());
                for (int j = 0; j < i; j++) {
                    rebuilt.append(origSiblings.get(j));
                }
            }
            if (rebuilt != null) {
                rebuilt.append(strippedChild);
            }
        }

        return (selfChanged || rebuilt != null) ? rebuilt : comp;
    }

    private static boolean coalesceWorkParts(ScanBuffer buf, List<String> targetNames) {
        boolean anyChanged = false;
        int groupStart = 0;
        while (groupStart < buf.workParts.size()) {
            WorkPart first = buf.workParts.get(groupStart);
            if (first.isBarrier || containsUrl(first.rawText)) {
                groupStart++;
                continue;
            }
            int groupEnd = findGroupEnd(buf.workParts, groupStart);
            if (groupEnd - groupStart >= 2) {
                CoalesceStep step = tryCoalesceOneRun(buf, groupStart, groupEnd, targetNames);
                if (step.changed) {
                    anyChanged = true;
                    groupEnd += step.deltaSize;
                    continue;
                }
            }
            groupStart = groupEnd;
        }
        return anyChanged;
    }

    private static int findGroupEnd(List<WorkPart> parts, int start) {
        Style anchor = parts.get(start).style;
        ClickEvent click = anchor.getClickEvent();
        HoverEvent hover = anchor.getHoverEvent();
        String insertion = anchor.getInsertion();

        int idx = start + 1;
        while (idx < parts.size()) {
            WorkPart next = parts.get(idx);
            if (next.isBarrier || containsUrl(next.rawText)) {
                break;
            }
            Style ns = next.style;
            if (!Objects.equals(click, ns.getClickEvent())
                    || !Objects.equals(hover, ns.getHoverEvent())
                    || !Objects.equals(insertion, ns.getInsertion())) {
                break;
            }
            idx++;
        }
        return idx;
    }

    private static CoalesceStep tryCoalesceOneRun(
            ScanBuffer buf, int groupStart, int groupEnd, List<String> targetNames) {
        buf.reset();
        for (int p = groupStart; p < groupEnd; p++) {
            WorkPart part = buf.workParts.get(p);
            appendRawToScanBuffer(buf, part.rawText, p, true);
        }
        if (buf.clean.isEmpty()) {
            return CoalesceStep.NO_CHANGE;
        }

        for (int t = 0, tn = targetNames.size(); t < tn; t++) {
            String target = targetNames.get(t);
            if (target == null || target.isEmpty()) {
                continue;
            }
            int matchCleanStart = findMatchInBuffer(buf, target, true);
            if (matchCleanStart == -1) {
                continue;
            }
            int matchCleanEnd = matchCleanStart + target.length();
            if (matchCleanEnd + 1 < buf.clean.length()
                    && buf.clean.charAt(matchCleanEnd) == '\''
                    && (buf.clean.charAt(matchCleanEnd + 1) == 's' || buf.clean.charAt(matchCleanEnd + 1) == 'S')) {
                int afterPossessive = matchCleanEnd + 2;
                if (afterPossessive == buf.clean.length() || !isUsernameChar(buf.clean.charAt(afterPossessive))) {
                    matchCleanEnd = afterPossessive;
                }
            }

            int firstPartIdx = buf.partIndices[matchCleanStart];
            int lastPartIdx = buf.partIndices[matchCleanEnd - 1];
            if (firstPartIdx == lastPartIdx) {
                continue;
            }

            int sizeBefore = buf.workParts.size();
            applyGroupMerge(buf, groupStart, groupEnd, firstPartIdx, lastPartIdx, matchCleanStart, matchCleanEnd);
            return new CoalesceStep(true, buf.workParts.size() - sizeBefore);
        }
        return CoalesceStep.NO_CHANGE;
    }

    private static void applyGroupMerge(
            ScanBuffer buf,
            int groupStart,
            int groupEnd,
            int firstPartIdx,
            int lastPartIdx,
            int matchCleanStart,
            int matchCleanEnd) {
        WorkPart firstPart = buf.workParts.get(firstPartIdx);
        WorkPart lastPart = buf.workParts.get(lastPartIdx);
        int firstRawStart = buf.rawStarts[matchCleanStart];
        int lastRawEnd = buf.rawEnds[matchCleanEnd - 1];

        String prefixRaw = stripTrailingColorCodes(stripMiniMessageTags(firstPart.rawText.substring(0, firstRawStart)));
        String suffixRaw = stripMiniMessageTags(lastPart.rawText.substring(lastRawEnd));
        String matchedName = extractCleanSlice(buf, matchCleanStart, matchCleanEnd);

        int minClearStart = (matchCleanStart > 0 ? buf.partIndices[matchCleanStart - 1] : groupStart - 1) + 1;
        int clearStart = firstPartIdx;
        while (clearStart > minClearStart) {
            WorkPart candidate = buf.workParts.get(clearStart - 1);
            if (candidate.sourceIndex != -1 && stripMiniMessageTags(candidate.rawText).isEmpty()) {
                clearStart--;
            } else {
                break;
            }
        }
        int maxClearEnd = (matchCleanEnd < buf.clean.length() ? buf.partIndices[matchCleanEnd] : groupEnd) - 1;
        int clearEnd = lastPartIdx;
        while (clearEnd < maxClearEnd) {
            WorkPart candidate = buf.workParts.get(clearEnd + 1);
            if (candidate.sourceIndex != -1 && stripMiniMessageTags(candidate.rawText).isEmpty()) {
                clearEnd++;
            } else {
                break;
            }
        }

        List<WorkPart> replacement = new ArrayList<>(3);
        if (!prefixRaw.isEmpty() || firstPart.sourceIndex == -1) {
            replacement.add(new WorkPart(firstPart.sourceIndex, prefixRaw, firstPart.style, false));
        }
        replacement.add(new WorkPart(0, matchedName, firstPart.style, false));
        if (!suffixRaw.isEmpty()) {
            replacement.add(new WorkPart(0, suffixRaw, lastPart.style, false));
        }

        buf.workParts.subList(clearStart, clearEnd + 1).clear();
        buf.workParts.addAll(clearStart, replacement);
    }

    private static void appendRawToScanBuffer(ScanBuffer buf, String raw, int partIndex, boolean skipMiniMessageTags) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        boolean allowTags = skipMiniMessageTags && raw.indexOf('<') != -1 && !containsUrl(raw);
        TagMatchers matchers = allowTags ? TAG_MATCHERS.get() : null;
        boolean hadFormatBefore = false;

        int i = 0;
        int len = raw.length();
        while (i < len) {
            char c = raw.charAt(i);
            if (c == '\u00A7' && i + 1 < len) {
                hadFormatBefore = true;
                i += 2;
                continue;
            }
            if (allowTags && c == '<') {
                int tagEnd = findMiniMessageTagEnd(raw, i, matchers);
                if (tagEnd != -1) {
                    hadFormatBefore = true;
                    i = tagEnd;
                    continue;
                }
            }
            buf.appendChar(c, partIndex, i, i + 1, hadFormatBefore);
            hadFormatBefore = false;
            i++;
        }
    }

    private static int findMiniMessageTagEnd(String raw, int ltPos, TagMatchers matchers) {
        int gtPos = raw.indexOf('>', ltPos + 1);
        if (gtPos == -1 || gtPos - ltPos > MAX_MINIMESSAGE_TAG_LEN) {
            return -1;
        }
        int endExclusive = gtPos + 1;
        if (matchers.matchesAny(raw, ltPos, endExclusive)) {
            return endExclusive;
        }
        return -1;
    }

    private static int findMatchInBuffer(ScanBuffer buf, String target, boolean requireMultiPart) {
        StringBuilder clean = buf.clean;
        int cleanLen = clean.length();
        int targetLen = target.length();
        int searchPos = 0;

        while ((searchPos = indexOfIgnoreCase(clean, target, searchPos)) != -1) {
            int matchEnd = searchPos + targetLen;
            if (startsWithIgnoreCaseAt(clean, matchEnd, " head")) {
                searchPos = matchEnd;
                continue;
            }
            char charBefore = searchPos > 0 ? clean.charAt(searchPos - 1) : ' ';
            boolean beforeOk = (searchPos == 0) || buf.formatBefore[searchPos] || !isUsernameChar(charBefore);
            boolean afterOk = (matchEnd == cleanLen) || !isUsernameChar(clean.charAt(matchEnd));
            if (beforeOk && afterOk) {
                if (!requireMultiPart || buf.partIndices[searchPos] != buf.partIndices[matchEnd - 1]) {
                    return searchPos;
                }
            }
            searchPos += targetLen;
        }
        return -1;
    }

    private static int indexOfIgnoreCase(StringBuilder haystack, String needle, int fromIndex) {
        int hLen = haystack.length();
        int nLen = needle.length();
        if (nLen == 0 || fromIndex + nLen > hLen) {
            return -1;
        }
        char firstLower = Character.toLowerCase(needle.charAt(0));
        int maxStart = hLen - nLen;
        for (int i = Math.max(0, fromIndex); i <= maxStart; i++) {
            if (Character.toLowerCase(haystack.charAt(i)) != firstLower) {
                continue;
            }
            boolean match = true;
            for (int j = 1; j < nLen; j++) {
                if (Character.toLowerCase(haystack.charAt(i + j)) != Character.toLowerCase(needle.charAt(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static boolean startsWithIgnoreCaseAt(StringBuilder text, int offset, String prefix) {
        int pLen = prefix.length();
        if (offset < 0 || offset + pLen > text.length()) {
            return false;
        }
        for (int i = 0; i < pLen; i++) {
            if (Character.toLowerCase(text.charAt(offset + i)) != Character.toLowerCase(prefix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String extractCleanSlice(ScanBuffer buf, int startInclusive, int endExclusive) {
        char[] chars = new char[endExclusive - startInclusive];
        buf.clean.getChars(startInclusive, endExclusive, chars, 0);
        return new String(chars);
    }

    private static String stripTrailingColorCodes(String text) {
        if (text == null || text.isEmpty() || text.indexOf('\u00A7') == -1) {
            return text;
        }
        return TRAILING_COLOR_CODE_PATTERN.matcher(text).replaceAll("");
    }

    private static boolean containsUrl(String s) {
        return s != null && (s.contains("http://") || s.contains("https://"));
    }

    private static PlainLeaf tryAsPlainLeaf(Component comp) {
        if (comp == null || "wish:tab".equals(comp.getStyle().getInsertion())) {
            return null;
        }
        Component cur = comp;
        Style wrapperStyle = Style.EMPTY;
        int depth = 0;
        while (cur.getContents() instanceof PlainTextContents plain) {
            if (cur.getSiblings().isEmpty()) {
                Style effective = wrapperStyle.isEmpty() ? cur.getStyle() : cur.getStyle().applyTo(wrapperStyle);
                return new PlainLeaf(plain.text(), effective);
            }
            if (plain.text().isEmpty() && cur.getSiblings().size() == 1 && depth < MAX_PLAIN_WRAPPER_DEPTH) {
                Style curStyle = cur.getStyle();
                if (curStyle.getClickEvent() != null || curStyle.getHoverEvent() != null || curStyle.getInsertion() != null) {
                    return null;
                }
                wrapperStyle = curStyle.isEmpty() ? wrapperStyle : (wrapperStyle.isEmpty() ? curStyle : curStyle.applyTo(wrapperStyle));
                cur = cur.getSiblings().get(0);
                if ("wish:tab".equals(cur.getStyle().getInsertion())) {
                    return null;
                }
                depth++;
                continue;
            }
            return null;
        }
        return null;
    }

    private record PlainLeaf(String text, Style style) {}

    private record CoalesceStep(boolean changed, int deltaSize) {
        static final CoalesceStep NO_CHANGE = new CoalesceStep(false, 0);
    }

    private static final class WorkPart {
        final int sourceIndex;
        final String rawText;
        final Style style;
        final boolean isBarrier;

        WorkPart(int sourceIndex, String rawText, Style style, boolean isBarrier) {
            this.sourceIndex = sourceIndex;
            this.rawText = rawText;
            this.style = style;
            this.isBarrier = isBarrier;
        }
    }

    private static final class TagMatchers {
        final Matcher hexExtended = MM_HEX_EXTENDED.matcher("");
        final Matcher hexSimple = MM_HEX_SIMPLE.matcher("");
        final Matcher complexTags = MM_COMPLEX_TAGS.matcher("");
        final Matcher closeTags = MM_CLOSE_TAGS.matcher("");
        final Matcher formatTags = MM_FORMAT_TAGS.matcher("");
        final Matcher anyClose = MM_ANY_CLOSE.matcher("");

        boolean matchesAny(String text, int start, int end) {
            return hexExtended.reset(text).region(start, end).matches()
                    || hexSimple.reset(text).region(start, end).matches()
                    || complexTags.reset(text).region(start, end).matches()
                    || closeTags.reset(text).region(start, end).matches()
                    || formatTags.reset(text).region(start, end).matches()
                    || anyClose.reset(text).region(start, end).matches();
        }
    }

    private static final class ScanBuffer {
        private static final int INITIAL_CAP = 256;
        final StringBuilder clean = new StringBuilder(INITIAL_CAP);
        final List<WorkPart> workParts = new ArrayList<>(32);
        int[] partIndices = new int[INITIAL_CAP];
        int[] rawStarts = new int[INITIAL_CAP];
        int[] rawEnds = new int[INITIAL_CAP];
        boolean[] formatBefore = new boolean[INITIAL_CAP];

        void reset() {
            clean.setLength(0);
        }

        void clearWorkParts() {
            workParts.clear();
        }

        void appendChar(char c, int partIdx, int rawStart, int rawEnd, boolean hasFormatBefore) {
            int idx = clean.length();
            if (idx >= partIndices.length) {
                int newCap = partIndices.length * 2;
                partIndices = java.util.Arrays.copyOf(partIndices, newCap);
                rawStarts = java.util.Arrays.copyOf(rawStarts, newCap);
                rawEnds = java.util.Arrays.copyOf(rawEnds, newCap);
                formatBefore = java.util.Arrays.copyOf(formatBefore, newCap);
            }
            clean.append(c);
            partIndices[idx] = partIdx;
            rawStarts[idx] = rawStart;
            rawEnds[idx] = rawEnd;
            formatBefore[idx] = hasFormatBefore;
        }
    }
}
