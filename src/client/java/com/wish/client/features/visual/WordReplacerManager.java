package com.wish.client.features.visual;

import com.wish.client.config.ModConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class WordReplacerManager {
    public static final int MAX_RULES = 200;
    public static final int MAX_TERM_LENGTH = 64;
    public static final int MAX_REPLACEMENT_LENGTH = 256;

    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\r\\n\\u0000]");
    private static final AtomicLong VERSION_SEQ = new AtomicLong(1L);
    private static volatile Snapshot cachedSnapshot = Snapshot.EMPTY;

    private static final ThreadLocal<DepthCounter> GUI_BYPASS_DEPTH = ThreadLocal.withInitial(DepthCounter::new);
    private static final ThreadLocal<DepthCounter> ALREADY_REPLACED_DEPTH = ThreadLocal.withInitial(DepthCounter::new);

    private WordReplacerManager() {
    }

    public record CompiledRule(String from, String to, boolean hasFormatting) {
        public boolean hasExplicitStyle() {
            return hasFormatting;
        }
    }

    public record Snapshot(
            boolean enabled,
            boolean wholeWord,
            boolean caseSensitive,
            List<CompiledRule> rules,
            int minFromLength,
            long version
    ) {
        public static final Snapshot EMPTY = new Snapshot(false, true, false, List.of(), Integer.MAX_VALUE, 0L);
    }

    private static final class DepthCounter {
        int depth;
    }

    public static void beginGuiBypass() {
        GUI_BYPASS_DEPTH.get().depth++;
    }

    public static void endGuiBypass() {
        DepthCounter counter = GUI_BYPASS_DEPTH.get();
        if (counter.depth > 0) {
            counter.depth--;
        }
    }

    public static boolean isBypassed() {
        return GUI_BYPASS_DEPTH.get().depth > 0;
    }

    public static void beginAlreadyReplaced() {
        ALREADY_REPLACED_DEPTH.get().depth++;
    }

    public static void endAlreadyReplaced() {
        DepthCounter counter = ALREADY_REPLACED_DEPTH.get();
        if (counter.depth > 0) {
            counter.depth--;
        }
    }

    public static boolean isAlreadyReplaced() {
        return ALREADY_REPLACED_DEPTH.get().depth > 0;
    }

    public static CopyOnWriteArrayList<ModConfig.WordReplacementRule> sanitizeRules(List<ModConfig.WordReplacementRule> raw) {
        CopyOnWriteArrayList<ModConfig.WordReplacementRule> sanitized = new CopyOnWriteArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return sanitized;
        }
        for (ModConfig.WordReplacementRule rule : raw) {
            if (rule == null) {
                continue;
            }
            String cleanFrom = sanitizeTerm(rule.from, true);
            if (cleanFrom.isBlank()) {
                continue;
            }
            String cleanTo = sanitizeReplacement(rule.to);
            sanitized.add(new ModConfig.WordReplacementRule(cleanFrom, cleanTo, rule.enabled));
            if (sanitized.size() >= MAX_RULES) {
                break;
            }
        }
        return sanitized;
    }

    public static long getVersion() {
        return cachedSnapshot.version();
    }

    public static List<CompiledRule> getCompiledRulesSnapshot() {
        return cachedSnapshot.rules();
    }

    public static Snapshot getActiveSnapshot() {
        return cachedSnapshot;
    }

    public static void invalidateCache() {
        ModConfig cfg = ModConfig.INSTANCE;
        cachedSnapshot = compileSnapshot(cfg.enableWordReplacer, cfg.wordReplacerWholeWord, cfg.wordReplacerCaseSensitive, cfg.wordReplacements);
    }

    public static Snapshot compileSnapshot(
            boolean enabled,
            boolean wholeWord,
            boolean caseSensitive,
            List<ModConfig.WordReplacementRule> rules
    ) {
        if (!enabled || rules == null || rules.isEmpty()) {
            return new Snapshot(false, wholeWord, caseSensitive, List.of(), Integer.MAX_VALUE, VERSION_SEQ.getAndIncrement());
        }
        List<CompiledRule> compiled = new ArrayList<>(rules.size());
        int minLen = Integer.MAX_VALUE;
        for (ModConfig.WordReplacementRule rule : rules) {
            if (rule == null || !rule.enabled) {
                continue;
            }
            String cleanFrom = sanitizeTerm(rule.from, true);
            if (cleanFrom.isBlank()) {
                continue;
            }
            String cleanTo = sanitizeReplacement(rule.to);
            compiled.add(new CompiledRule(cleanFrom, cleanTo, WordReplacerTextEngine.containsFormattingCode(cleanTo)));
            if (cleanFrom.length() < minLen) {
                minLen = cleanFrom.length();
            }
        }
        if (compiled.isEmpty()) {
            return new Snapshot(false, wholeWord, caseSensitive, List.of(), Integer.MAX_VALUE, VERSION_SEQ.getAndIncrement());
        }
        compiled.sort((a, b) -> Integer.compare(b.from.length(), a.from.length()));
        return new Snapshot(true, wholeWord, caseSensitive, Collections.unmodifiableList(compiled), minLen, VERSION_SEQ.getAndIncrement());
    }

    public static boolean isEnabled() {
        return ModConfig.INSTANCE.enableWordReplacer;
    }

    public static void setEnabled(boolean enabled) {
        ModConfig.INSTANCE.enableWordReplacer = enabled;
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static boolean isWholeWord() {
        return ModConfig.INSTANCE.wordReplacerWholeWord;
    }

    public static void setWholeWord(boolean wholeWord) {
        ModConfig.INSTANCE.wordReplacerWholeWord = wholeWord;
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static boolean isCaseSensitive() {
        return ModConfig.INSTANCE.wordReplacerCaseSensitive;
    }

    public static void setCaseSensitive(boolean caseSensitive) {
        ModConfig.INSTANCE.wordReplacerCaseSensitive = caseSensitive;
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static List<ModConfig.WordReplacementRule> getRules() {
        if (ModConfig.INSTANCE.wordReplacements == null) {
            ModConfig.INSTANCE.wordReplacements = new CopyOnWriteArrayList<>();
        }
        return ModConfig.INSTANCE.wordReplacements;
    }

    public static boolean addRule(String from, String to) {
        String cleanFrom = sanitizeTerm(from, true);
        if (cleanFrom.isBlank()) {
            return false;
        }
        String cleanTo = sanitizeReplacement(to);
        List<ModConfig.WordReplacementRule> list = getRules();
        for (ModConfig.WordReplacementRule existing : list) {
            if (existing != null && sameRuleSource(cleanFrom, existing.from)) {
                existing.to = cleanTo;
                existing.enabled = true;
                invalidateCache();
                ModConfig.INSTANCE.save();
                return true;
            }
        }
        if (list.size() >= MAX_RULES) {
            return false;
        }
        list.add(new ModConfig.WordReplacementRule(cleanFrom, cleanTo, true));
        if (!ModConfig.INSTANCE.enableWordReplacer) {
            ModConfig.INSTANCE.enableWordReplacer = true;
        }
        invalidateCache();
        ModConfig.INSTANCE.save();
        return true;
    }

    public static boolean updateRule(int index, String from, String to) {
        List<ModConfig.WordReplacementRule> list = getRules();
        if (index < 0 || index >= list.size()) {
            return false;
        }
        String cleanFrom = sanitizeTerm(from, true);
        if (cleanFrom.isBlank()) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (i == index) {
                continue;
            }
            ModConfig.WordReplacementRule other = list.get(i);
            if (other != null && sameRuleSource(cleanFrom, other.from)) {
                return false;
            }
        }
        ModConfig.WordReplacementRule existing = list.get(index);
        if (existing == null) {
            return false;
        }
        existing.from = cleanFrom;
        existing.to = sanitizeReplacement(to);
        invalidateCache();
        ModConfig.INSTANCE.save();
        return true;
    }

    private static boolean sameRuleSource(String cleanFrom, String otherFrom) {
        if (otherFrom == null) {
            return false;
        }
        return ModConfig.INSTANCE.wordReplacerCaseSensitive
                ? cleanFrom.equals(otherFrom)
                : cleanFrom.equalsIgnoreCase(otherFrom);
    }

    public static String stripFormattingCodes(String text) {
        return WordReplacerTextEngine.stripFormattingCodes(text);
    }

    public static Component buildPreviewComponent(String to) {
        return WordReplacerTextEngine.buildPreviewComponent(sanitizeReplacement(to));
    }

    public static Component buildEditorPreviewComponent(String to, boolean appendCaret) {
        return WordReplacerTextEngine.buildEditorPreviewComponent(sanitizeReplacement(to), appendCaret);
    }

    public static void removeRuleAt(int index) {
        List<ModConfig.WordReplacementRule> list = getRules();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        invalidateCache();
        ModConfig.INSTANCE.save();
    }

    public static void toggleRuleAt(int index) {
        List<ModConfig.WordReplacementRule> list = getRules();
        if (index < 0 || index >= list.size()) {
            return;
        }
        ModConfig.WordReplacementRule rule = list.get(index);
        if (rule != null) {
            rule.enabled = !rule.enabled;
            invalidateCache();
            ModConfig.INSTANCE.save();
        }
    }

    public static String replacePlainString(String text) {
        if (text == null || isBypassed() || isAlreadyReplaced()) {
            return text;
        }
        return WordReplacerTextEngine.replacePlainString(text, cachedSnapshot);
    }

    public static String replacePlainString(String text, Snapshot snapshot) {
        return WordReplacerTextEngine.replacePlainString(text, snapshot);
    }

    public static Component replaceComponent(Component component) {
        if (component == null || isBypassed() || isAlreadyReplaced()) {
            return component;
        }
        return WordReplacerTextEngine.replaceComponent(component, cachedSnapshot);
    }

    public static FormattedCharSequence replaceCharSequence(FormattedCharSequence seq) {
        if (seq == null) {
            return null;
        }
        if (isBypassed()) {
            return markBypassed(seq);
        }
        if (isAlreadyReplaced()) {
            return markReplaced(seq);
        }
        return WordReplacerTextEngine.replaceCharSequence(seq, cachedSnapshot);
    }

    public static FormattedCharSequence replaceCharSequence(FormattedCharSequence seq, Snapshot snapshot) {
        return WordReplacerTextEngine.replaceCharSequence(seq, snapshot);
    }

    public static FormattedCharSequence markBypassed(FormattedCharSequence seq) {
        return WordReplacerTextEngine.wrapVersion(seq, WordReplacerTextEngine.BYPASSED_VERSION);
    }

    public static FormattedCharSequence markReplaced(FormattedCharSequence seq) {
        return WordReplacerTextEngine.wrapVersion(seq, cachedSnapshot.version());
    }

    public static MutableComponent createMarkedLiteral(String text, Style style) {
        return WordReplacerTextEngine.createMarkedLiteral(text, style, cachedSnapshot.version());
    }

    public static boolean isComponentMarkedReplaced(Component component) {
        return WordReplacerTextEngine.isComponentMarkedReplaced(component, cachedSnapshot.version());
    }

    public static List<Component> replaceComponentList(List<Component> lines) {
        if (lines == null || lines.isEmpty() || isBypassed()) {
            return lines;
        }
        Snapshot snapshot = cachedSnapshot;
        if (!snapshot.enabled || snapshot.rules.isEmpty()) {
            return lines;
        }
        List<Component> copy = null;
        for (int i = 0; i < lines.size(); i++) {
            Component orig = lines.get(i);
            Component rep = WordReplacerTextEngine.replaceComponent(orig, snapshot);
            if (rep != orig) {
                if (copy == null) {
                    copy = new ArrayList<>(lines);
                }
                copy.set(i, rep);
            }
        }
        return copy != null ? copy : lines;
    }

    public static Component replaceComponent(Component component, Snapshot snapshot) {
        return WordReplacerTextEngine.replaceComponent(component, snapshot);
    }

    public static String sanitizeTerm(String raw, boolean stripSectionSign) {
        return sanitizeTerm(raw, stripSectionSign, MAX_TERM_LENGTH);
    }

    public static String sanitizeReplacement(String raw) {
        return sanitizeTerm(raw, false, MAX_REPLACEMENT_LENGTH);
    }

    public static String sanitizeTerm(String raw, boolean stripSectionSign, int maxLength) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String clean = CONTROL_CHARS_PATTERN.matcher(raw).replaceAll("");
        if (stripSectionSign) {
            clean = clean.replace("\u00A7", "");
        }
        if (clean.length() > maxLength) {
            clean = clean.substring(0, maxLength);
        }
        return clean;
    }
}
