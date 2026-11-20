package com.wish.client.features;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiningAbilityTracker {

    public enum AbilityState {
        READY,
        ACTIVE,
        COOLDOWN
    }

    public static final String DEFAULT_CUSTOM_FORMAT = "\u26CF {ability}";
    private static final String DEFAULT_ABILITY = "Mining Speed Boost";

    private static final Pattern USED_PATTERN = Pattern.compile(
            "^You used your (.+?) Pickaxe Ability!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPIRED_PATTERN = Pattern.compile(
            "^Your (.+?)(?:\\s+Pickaxe Ability)?\\s+has expired!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCELLED_NAMED_PATTERN = Pattern.compile(
            "^Your (.+?)(?:\\s+Pickaxe Ability)?\\s+(?:has been |was )?(?:cancelled|canceled|deactivated|stopped|ended|disabled)[!.]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCELLED_BARE_PATTERN = Pattern.compile(
            "^(.+?)\\s+(?:has been |was )?(?:cancelled|canceled|deactivated|stopped|ended|disabled)[!.]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCELLED_BY_YOU_PATTERN = Pattern.compile(
            "^You\\s+(?:cancelled|canceled|stopped|deactivated)\\s+your\\s+(.+?)(?:\\s+Pickaxe Ability)?[!.]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCELLED_GENERIC_PATTERN = Pattern.compile(
            "^(?:Your\\s+)?Pickaxe Ability\\s+(?:has expired|has been cancelled|has been canceled|was cancelled|was canceled|was deactivated|stopped|ended|disabled)[!.]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AVAILABLE_PATTERN = Pattern.compile(
            "^(.+?) is now available!$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_COOLDOWN_NAMED_PATTERN = Pattern.compile(
            "^Your (.+?) is on cooldown for (?:(\\d+)m\\s*)?(\\d+(?:\\.\\d+)?)s\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_COOLDOWN_GENERIC_PATTERN = Pattern.compile(
            "^This ability is on cooldown for (?:(\\d+)m\\s*)?(\\d+(?:\\.\\d+)?) more seconds\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LORE_ABILITY_HEADER_PATTERN = Pattern.compile(
            "^(?:[^\\p{Alnum}\\s]+\\s*)?(?:(Pickaxe|Item)\\s+)?Ability:\\s*(.+?)(?:\\s+(?:RIGHT CLICK|LEFT CLICK|\\(Right Click\\)|\\(Left Click\\)))?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LORE_COOLDOWN_PATTERN = Pattern.compile(
            "^Cooldown:\\s*(?:(\\d+)m\\s*)?(\\d+(?:\\.\\d+)?)s\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LORE_FOR_DURATION_PATTERN = Pattern.compile(
            "\\bfor\\s+(\\d+(?:\\.\\d+)?)\\s*(?:s|seconds?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAB_PICKAXE_LINE_PATTERN = Pattern.compile(
            "^(.+?):\\s*(Available|Ready|(?:\\d+m\\s*)?\\d+(?:\\.\\d+)?s)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_PARSE_PATTERN = Pattern.compile(
            "^(?:(\\d+)m\\s*)?(\\d+(?:\\.\\d+)?)s$", Pattern.CASE_INSENSITIVE);

    private static final long BLUE_CHEESE_BONUS_MS = 5_000L;

    private static final Set<String> KNOWN_ABILITIES = Set.of(
            "Mining Speed Boost",
            "Pickobulus",
            "Vein Seeker",
            "Maniac Miner",
            "Gemstone Infusion",
            "Hazardous Miner",
            "Anomalous Desire",
            "Tunnel Vision",
            "Sheer Force"
    );

    private static final Set<String> INSTANT_ABILITIES = Set.of("Pickobulus");

    private static final Map<String, Long> DEFAULT_DURATIONS_MS = Map.of(
            "Mining Speed Boost", 15_000L,
            "Vein Seeker", 12_000L,
            "Maniac Miner", 10_000L,
            "Gemstone Infusion", 16_000L,
            "Hazardous Miner", 12_000L,
            "Anomalous Desire", 30_000L,
            "Tunnel Vision", 30_000L,
            "Sheer Force", 20_000L
    );

    private static final Map<String, Long> DEFAULT_COOLDOWNS_MS = Map.of(
            "Mining Speed Boost", 120_000L,
            "Pickobulus", 110_000L,
            "Vein Seeker", 60_000L,
            "Maniac Miner", 60_000L,
            "Gemstone Infusion", 120_000L,
            "Hazardous Miner", 60_000L,
            "Anomalous Desire", 120_000L,
            "Tunnel Vision", 120_000L,
            "Sheer Force", 120_000L
    );

    private String abilityName = DEFAULT_ABILITY;
    private AbilityState state = AbilityState.READY;
    private long stateChangedAtMs = 0L;
    private long activatedAtMs = 0L;
    private long activeCooldownMs = 0L;
    private long cooldownEndsAtMs = 0L;
    private long activeEndsAtMs = 0L;
    private long observedCooldownEndsAtMs = 0L;
    private long configuredActiveDurationMs = 15_000L;
    private long configuredCooldownMs = 120_000L;
    private boolean hasLoreActiveDuration = false;
    private boolean hasLoreCooldown = false;
    private boolean sawAnyPickaxeAbility = false;

    public synchronized void reset() {
        abilityName = DEFAULT_ABILITY;
        state = AbilityState.READY;
        stateChangedAtMs = 0L;
        activatedAtMs = 0L;
        activeCooldownMs = 0L;
        cooldownEndsAtMs = 0L;
        activeEndsAtMs = 0L;
        observedCooldownEndsAtMs = 0L;
        configuredActiveDurationMs = 15_000L;
        configuredCooldownMs = 120_000L;
        hasLoreActiveDuration = false;
        hasLoreCooldown = false;
        sawAnyPickaxeAbility = false;
    }

    public synchronized boolean handleChatLine(String rawCleanLine, long nowMs) {
        if (rawCleanLine == null || rawCleanLine.isEmpty()) {
            return false;
        }
        String line = rawCleanLine.trim();

        Matcher used = USED_PATTERN.matcher(line);
        if (used.matches()) {
            String name = canonicalizeAbilityName(used.group(1));
            setAbilityDefaultsIfChanged(name);
            sawAnyPickaxeAbility = true;
            stateChangedAtMs = nowMs;
            activatedAtMs = nowMs;
            activeCooldownMs = configuredCooldownMs;
            observedCooldownEndsAtMs = 0L;
            if (INSTANT_ABILITIES.contains(name)) {
                state = AbilityState.COOLDOWN;
                cooldownEndsAtMs = nowMs + activeCooldownMs;
                activeEndsAtMs = 0L;
            } else {
                state = AbilityState.ACTIVE;
                activeEndsAtMs = nowMs + configuredActiveDurationMs;
                cooldownEndsAtMs = 0L;
            }
            return false;
        }

        if (state == AbilityState.ACTIVE && CANCELLED_GENERIC_PATTERN.matcher(line).matches()) {
            transitionToCooldownFromEndMessage(null, false, nowMs);
            return false;
        }

        Matcher expired = EXPIRED_PATTERN.matcher(line);
        if (expired.matches()) {
            String name = canonicalizeAbilityName(expired.group(1));
            if (isKnownOrCurrentAbility(name)) {
                transitionToCooldownFromEndMessage(name, true, nowMs);
                return false;
            }
        }

        Matcher cancelledNamed = CANCELLED_NAMED_PATTERN.matcher(line);
        if (cancelledNamed.matches()) {
            String name = canonicalizeAbilityName(cancelledNamed.group(1));
            if (isKnownOrCurrentAbility(name)) {
                transitionToCooldownFromEndMessage(name, false, nowMs);
                return false;
            }
        }

        Matcher cancelledByYou = CANCELLED_BY_YOU_PATTERN.matcher(line);
        if (cancelledByYou.matches()) {
            String name = canonicalizeAbilityName(cancelledByYou.group(1));
            if (isKnownOrCurrentAbility(name)) {
                transitionToCooldownFromEndMessage(name, false, nowMs);
                return false;
            }
        }

        if (!line.contains(": ")) {
            Matcher cancelledBare = CANCELLED_BARE_PATTERN.matcher(line);
            if (cancelledBare.matches()) {
                String name = canonicalizeAbilityName(cancelledBare.group(1));
                if (isKnownOrCurrentAbility(name)) {
                    transitionToCooldownFromEndMessage(name, false, nowMs);
                    return false;
                }
            }
        }

        Matcher avail = AVAILABLE_PATTERN.matcher(line);
        if (avail.matches()) {
            String name = canonicalizeAbilityName(avail.group(1));
            if (isKnownOrCurrentAbility(name)) {
                boolean wasNotReady = state != AbilityState.READY;
                if (state == AbilityState.COOLDOWN && !hasLoreCooldown && activatedAtMs > 0L && nowMs > activatedAtMs) {
                    long measuredCd = nowMs - activatedAtMs;
                    if (measuredCd >= 5_000L && measuredCd <= 600_000L) {
                        configuredCooldownMs = measuredCd;
                    }
                }
                setAbilityDefaultsIfChanged(name);
                sawAnyPickaxeAbility = true;
                state = AbilityState.READY;
                stateChangedAtMs = nowMs;
                activatedAtMs = 0L;
                activeCooldownMs = 0L;
                activeEndsAtMs = 0L;
                cooldownEndsAtMs = 0L;
                observedCooldownEndsAtMs = 0L;
                return wasNotReady;
            }
            return false;
        }

        Matcher cdNamed = ON_COOLDOWN_NAMED_PATTERN.matcher(line);
        if (cdNamed.matches()) {
            String name = canonicalizeAbilityName(cdNamed.group(1));
            if (isKnownOrCurrentAbility(name)) {
                setAbilityDefaultsIfChanged(name);
                sawAnyPickaxeAbility = true;
                long remMs = parseMinutesSecondsMs(cdNamed.group(2), cdNamed.group(3));
                applyCooldownRemaining(remMs, nowMs);
            }
            return false;
        }

        Matcher cdGeneric = ON_COOLDOWN_GENERIC_PATTERN.matcher(line);
        if (cdGeneric.matches() && sawAnyPickaxeAbility) {
            long remMs = parseMinutesSecondsMs(cdGeneric.group(1), cdGeneric.group(2));
            applyCooldownRemaining(remMs, nowMs);
        }
        return false;
    }

    public static boolean isPickaxeAbilityLoreHeader(String cleanLine) {
        if (cleanLine == null || cleanLine.isEmpty()) {
            return false;
        }
        Matcher m = LORE_ABILITY_HEADER_PATTERN.matcher(cleanLine.trim());
        if (!m.matches()) {
            return false;
        }
        String kind = m.group(1);
        if ("Pickaxe".equalsIgnoreCase(kind)) {
            return true;
        }
        String candidate = canonicalizeAbilityName(m.group(2));
        return KNOWN_ABILITIES.contains(candidate);
    }

    public synchronized void handleToolLore(Iterable<String> cleanLines) {
        handleToolLore(cleanLines, false);
    }

    public synchronized void handleToolLore(Iterable<String> cleanLines, boolean hasBlueCheesePart) {
        if (cleanLines == null) {
            return;
        }
        String detectedName = null;
        long detectedCd = -1L;
        long detectedDur = -1L;
        boolean sawBlueCheese = hasBlueCheesePart;
        boolean inAbilityBlock = false;
        StringBuilder abilityDesc = new StringBuilder(96);

        for (String raw : cleanLines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.toLowerCase(Locale.ROOT).contains("blue cheese goblin omelette")) {
                sawBlueCheese = true;
            }

            Matcher header = LORE_ABILITY_HEADER_PATTERN.matcher(line);
            if (header.matches()) {
                String kind = header.group(1);
                String candidate = canonicalizeAbilityName(header.group(2));
                if ("Pickaxe".equalsIgnoreCase(kind) || KNOWN_ABILITIES.contains(candidate)) {
                    detectedName = candidate;
                    inAbilityBlock = true;
                    abilityDesc.setLength(0);
                } else {
                    inAbilityBlock = false;
                }
                continue;
            }

            if (inAbilityBlock) {
                Matcher cdm = LORE_COOLDOWN_PATTERN.matcher(line);
                if (cdm.matches()) {
                    detectedCd = parseMinutesSecondsMs(cdm.group(1), cdm.group(2));
                    inAbilityBlock = false;
                    continue;
                }
                if (isAbilitySectionTerminator(line)) {
                    inAbilityBlock = false;
                    continue;
                }
                if (abilityDesc.length() > 0) {
                    abilityDesc.append(' ');
                }
                abilityDesc.append(line);
            }
        }

        if (abilityDesc.length() > 0) {
            Matcher durm = LORE_FOR_DURATION_PATTERN.matcher(abilityDesc.toString());
            if (durm.find()) {
                detectedDur = parseSecondsMs(durm.group(1));
            }
        }

        if (detectedName != null) {
            setAbilityDefaultsIfChanged(detectedName);
            sawAnyPickaxeAbility = true;
            if (detectedDur > 0L) {
                configuredActiveDurationMs = detectedDur;
                hasLoreActiveDuration = true;
            } else if (sawBlueCheese && !INSTANT_ABILITIES.contains(detectedName)) {
                long baseDur = DEFAULT_DURATIONS_MS.getOrDefault(detectedName, 15_000L);
                configuredActiveDurationMs = baseDur + BLUE_CHEESE_BONUS_MS;
                hasLoreActiveDuration = false;
            }
            if (detectedCd > 0L) {
                configuredCooldownMs = detectedCd;
                hasLoreCooldown = true;
            }
        }
    }

    public synchronized void handleTabPickaxeLine(String cleanLine, long nowMs) {
        if (cleanLine == null || cleanLine.isEmpty()) {
            return;
        }
        Matcher m = TAB_PICKAXE_LINE_PATTERN.matcher(cleanLine.trim());
        if (!m.matches()) {
            return;
        }
        String name = canonicalizeAbilityName(m.group(1));
        if (!KNOWN_ABILITIES.contains(name) && !sawAnyPickaxeAbility) {
            return;
        }
        setAbilityDefaultsIfChanged(name);
        sawAnyPickaxeAbility = true;
        String status = m.group(2).trim();
        if (status.equalsIgnoreCase("Available") || status.equalsIgnoreCase("Ready")) {
            if (state == AbilityState.COOLDOWN) {
                state = AbilityState.READY;
                stateChangedAtMs = nowMs;
                activatedAtMs = 0L;
                cooldownEndsAtMs = 0L;
                observedCooldownEndsAtMs = 0L;
            }
        } else {
            long remMs = parseDurationStringMs(status);
            if (remMs > 0L) {
                if (state == AbilityState.ACTIVE) {
                    observedCooldownEndsAtMs = nowMs + remMs;
                    if (!hasLoreCooldown && activatedAtMs > 0L) {
                        long estimatedTotalCd = (nowMs - activatedAtMs) + remMs;
                        if (estimatedTotalCd >= 10_000L && estimatedTotalCd <= 600_000L) {
                            configuredCooldownMs = estimatedTotalCd;
                        }
                    }
                } else {
                    applyCooldownRemaining(remMs, nowMs);
                }
            }
        }
    }

    public synchronized void tick(long nowMs) {
        if (state == AbilityState.ACTIVE && activeEndsAtMs > 0L && nowMs >= activeEndsAtMs + 1_000L) {
            long endMs = resolveCooldownEndMs(nowMs, activeEndsAtMs);
            state = AbilityState.COOLDOWN;
            stateChangedAtMs = activeEndsAtMs;
            activeEndsAtMs = 0L;
            cooldownEndsAtMs = endMs;
        }
        if (state == AbilityState.COOLDOWN && cooldownEndsAtMs > 0L && nowMs >= cooldownEndsAtMs + 1_500L) {
            state = AbilityState.READY;
            stateChangedAtMs = nowMs;
            activatedAtMs = 0L;
            cooldownEndsAtMs = 0L;
            observedCooldownEndsAtMs = 0L;
        }
    }

    public synchronized AbilityState getState() {
        return state;
    }

    public synchronized String getAbilityName() {
        return abilityName;
    }

    public synchronized boolean hasSeenAbility() {
        return sawAnyPickaxeAbility;
    }

    public synchronized String formatLabelPrefix(String customTemplate) {
        String template = (customTemplate == null || customTemplate.trim().isEmpty())
                ? DEFAULT_CUSTOM_FORMAT
                : customTemplate;
        String shortName = getShortAbilityName(abilityName);
        return template
                .replace("{ability}", shortName)
                .replace("{full}", abilityName)
                .trim();
    }

    public synchronized String formatStatusText(long nowMs) {
        return switch (state) {
            case READY -> "READY";
            case ACTIVE -> {
                if (activeEndsAtMs > 0L) {
                    long remMs = Math.max(0L, activeEndsAtMs - nowMs);
                    long secs = Math.max(1L, (remMs + 999L) / 1000L);
                    yield secs + "s Active";
                }
                long elapsedSecs = Math.max(1L, Math.max(0L, nowMs - stateChangedAtMs) / 1000L);
                yield elapsedSecs + "s Active";
            }
            case COOLDOWN -> {
                long remMs = Math.max(0L, cooldownEndsAtMs - nowMs);
                long secs = Math.max(1L, (remMs + 999L) / 1000L);
                yield secs + "s Cooldown";
            }
        };
    }

    public synchronized String formatFullLine(String customTemplate, long nowMs) {
        return formatLabelPrefix(customTemplate) + ": " + formatStatusText(nowMs);
    }

    public static String getShortAbilityName(String fullName) {
        if ("Mining Speed Boost".equalsIgnoreCase(fullName)) {
            return "Speed Boost";
        }
        return fullName == null || fullName.isEmpty() ? "Speed Boost" : fullName;
    }

    private boolean isKnownOrCurrentAbility(String name) {
        return KNOWN_ABILITIES.contains(name) || name.equalsIgnoreCase(abilityName);
    }

    private void transitionToCooldownFromEndMessage(String matchedName, boolean allowDurationLearning, long nowMs) {
        if (allowDurationLearning && !hasLoreActiveDuration && state == AbilityState.ACTIVE
                && activatedAtMs > 0L && nowMs > activatedAtMs) {
            long measured = nowMs - activatedAtMs;
            if (measured >= 2_000L && measured <= 120_000L) {
                configuredActiveDurationMs = measured;
            }
        }
        if (matchedName != null && !matchedName.isEmpty()) {
            setAbilityDefaultsIfChanged(matchedName);
        }
        sawAnyPickaxeAbility = true;
        long cdMs = activeCooldownMs > 0L ? activeCooldownMs : configuredCooldownMs;
        if (state == AbilityState.COOLDOWN && activatedAtMs > 0L && nowMs - activatedAtMs < cdMs) {
            long accurateEnd = resolveCooldownEndMs(nowMs, nowMs);
            if (accurateEnd > nowMs) {
                cooldownEndsAtMs = accurateEnd;
            }
            return;
        }
        long endMs = resolveCooldownEndMs(nowMs, nowMs);
        state = AbilityState.COOLDOWN;
        stateChangedAtMs = nowMs;
        activeEndsAtMs = 0L;
        cooldownEndsAtMs = endMs;
    }

    private long resolveCooldownEndMs(long nowMs, long referenceEndMs) {
        if (observedCooldownEndsAtMs > nowMs) {
            return observedCooldownEndsAtMs;
        }
        long cdMs = activeCooldownMs > 0L ? activeCooldownMs : configuredCooldownMs;
        if (activatedAtMs > 0L) {
            return Math.max(nowMs + 1_000L, activatedAtMs + cdMs);
        }
        return referenceEndMs + Math.max(5_000L, cdMs - configuredActiveDurationMs);
    }

    private static boolean isAbilitySectionTerminator(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("bonus")
                || lower.contains("soulbound")
                || lower.startsWith("fuel:")
                || lower.startsWith("compacted blocks:")
                || lower.contains("goblin omelette")
                || lower.contains("drill engine")
                || lower.contains("fuel tank");
    }

    private void setAbilityDefaultsIfChanged(String newName) {
        if (!newName.equalsIgnoreCase(abilityName)) {
            abilityName = newName;
            configuredActiveDurationMs = DEFAULT_DURATIONS_MS.getOrDefault(newName, 15_000L);
            configuredCooldownMs = DEFAULT_COOLDOWNS_MS.getOrDefault(newName, 120_000L);
            hasLoreActiveDuration = false;
            hasLoreCooldown = false;
        }
    }

    private void applyCooldownRemaining(long remMs, long nowMs) {
        if (remMs <= 0L) {
            return;
        }
        state = AbilityState.COOLDOWN;
        stateChangedAtMs = nowMs;
        activeEndsAtMs = 0L;
        cooldownEndsAtMs = nowMs + remMs;
        observedCooldownEndsAtMs = cooldownEndsAtMs;
    }

    private static String canonicalizeAbilityName(String raw) {
        if (raw == null) return DEFAULT_ABILITY;
        String trimmed = raw.trim();
        for (String known : KNOWN_ABILITIES) {
            if (known.equalsIgnoreCase(trimmed)) {
                return known;
            }
        }
        return trimmed;
    }

    private static long parseDurationStringMs(String raw) {
        Matcher m = TIME_PARSE_PATTERN.matcher(raw.trim());
        if (!m.matches()) {
            return 0L;
        }
        return parseMinutesSecondsMs(m.group(1), m.group(2));
    }

    private static long parseMinutesSecondsMs(String minGroup, String secGroup) {
        long totalMs = 0L;
        if (minGroup != null && !minGroup.isEmpty()) {
            try {
                totalMs += Long.parseLong(minGroup) * 60_000L;
            } catch (NumberFormatException ignored) {
            }
        }
        totalMs += parseSecondsMs(secGroup);
        return totalMs;
    }

    private static long parseSecondsMs(String secGroup) {
        if (secGroup == null || secGroup.isEmpty()) {
            return 0L;
        }
        try {
            return Math.round(Double.parseDouble(secGroup) * 1000.0);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
