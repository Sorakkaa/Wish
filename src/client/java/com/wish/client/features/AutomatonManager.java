package com.wish.client.features;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wish.client.WishClient;
import com.wish.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutomatonManager {

    public static final String PART_FTX = "FTX 3070";
    public static final String PART_HEART = "Synthetic Heart";
    public static final String PART_REFLECTOR = "Robotron Reflector";
    public static final String PART_TRANSMITTER = "Electron Transmitter";
    public static final String PART_SWITCH = "Control Switch";
    public static final String PART_MOTOR = "Superlite Motor";
    public static final String PART_BOBOMB = "Bob-omb";

    public static final List<String> PRECURSOR_PARTS = List.of(
            PART_FTX, PART_HEART, PART_REFLECTOR, PART_TRANSMITTER, PART_SWITCH, PART_MOTOR, PART_BOBOMB
    );

    
    public static final String GEM_ROUGH_SAPPHIRE = "Rough Sapphire Gemstone";
    public static final String GEM_FLAWED_SAPPHIRE = "Flawed Sapphire Gemstone";
    public static final String GEM_FINE_SAPPHIRE = "Fine Sapphire Gemstone";
    public static final String GEM_ROUGH_RUBY = "Rough Ruby Gemstone";
    public static final String GEM_FLAWED_RUBY = "Flawed Ruby Gemstone";
    public static final String GEM_ROUGH_AMBER = "Rough Amber Gemstone";
    public static final String GEM_FLAWED_AMBER = "Flawed Amber Gemstone";
    public static final String GEM_ROUGH_JADE = "Rough Jade Gemstone";
    public static final String GEM_FLAWED_JADE = "Flawed Jade Gemstone";
    public static final String GEM_ROUGH_AMETHYST = "Rough Amethyst Gemstone";
    public static final String GEM_FLAWED_AMETHYST = "Flawed Amethyst Gemstone";
    public static final String GEM_ROUGH_TOPAZ = "Rough Topaz Gemstone";
    public static final String GEM_FLAWED_TOPAZ = "Flawed Topaz Gemstone";
    public static final String GEM_ROUGH_JASPER = "Rough Jasper Gemstone";
    public static final String GEM_FLAWED_JASPER = "Flawed Jasper Gemstone";
    public static final String GEM_ROUGH_OPAL = "Rough Opal Gemstone";
    public static final String GEM_FLAWED_OPAL = "Flawed Opal Gemstone";

    public static final List<String> GEMSTONES = List.of(
            GEM_ROUGH_SAPPHIRE, GEM_FLAWED_SAPPHIRE, GEM_FINE_SAPPHIRE,
            GEM_ROUGH_RUBY, GEM_FLAWED_RUBY,
            GEM_ROUGH_AMBER, GEM_FLAWED_AMBER,
            GEM_ROUGH_JADE, GEM_FLAWED_JADE,
            GEM_ROUGH_AMETHYST, GEM_FLAWED_AMETHYST,
            GEM_ROUGH_TOPAZ, GEM_FLAWED_TOPAZ,
            GEM_ROUGH_JASPER, GEM_FLAWED_JASPER,
            GEM_ROUGH_OPAL, GEM_FLAWED_OPAL
    );

    public static final List<String> ALL_TRACKED_ITEMS = new ArrayList<>();
    static {
        ALL_TRACKED_ITEMS.addAll(PRECURSOR_PARTS);
        ALL_TRACKED_ITEMS.addAll(GEMSTONES);
    }

    public static final String[] ALL_PARTS = ALL_TRACKED_ITEMS.toArray(new String[0]);

    private static final Map<String, String> PART_TO_BAZAAR_ID = new HashMap<>();
    static {
        PART_TO_BAZAAR_ID.put(PART_FTX, "FTX_3070");
        PART_TO_BAZAAR_ID.put(PART_HEART, "SYNTHETIC_HEART");
        PART_TO_BAZAAR_ID.put(PART_REFLECTOR, "ROBOTRON_REFLECTOR");
        PART_TO_BAZAAR_ID.put(PART_TRANSMITTER, "ELECTRON_TRANSMITTER");
        PART_TO_BAZAAR_ID.put(PART_SWITCH, "CONTROL_SWITCH");
        PART_TO_BAZAAR_ID.put(PART_MOTOR, "SUPERLITE_MOTOR");
        PART_TO_BAZAAR_ID.put(PART_BOBOMB, "BOB_OMB");
        for (String gem : GEMSTONES) {
            String bzId = gem.toUpperCase().replace(" GEMSTONE", "_GEM").replace(" ", "_");
            PART_TO_BAZAAR_ID.put(gem, bzId);
        }
    }

    
    private static final Map<String, Double> DEFAULT_SELL_OFFER_PRICES = Map.ofEntries(
            Map.entry(PART_FTX, 216700.0),
            Map.entry(PART_HEART, 223700.0),
            Map.entry(PART_REFLECTOR, 203200.0),
            Map.entry(PART_TRANSMITTER, 105000.0),
            Map.entry(PART_SWITCH, 132000.0),
            Map.entry(PART_MOTOR, 95500.0),
            Map.entry(PART_BOBOMB, 1488.0),
            Map.entry(GEM_ROUGH_SAPPHIRE, 3.7),
            Map.entry(GEM_FLAWED_SAPPHIRE, 390.0),
            Map.entry(GEM_FINE_SAPPHIRE, 28700.0),
            Map.entry(GEM_ROUGH_RUBY, 8.8),
            Map.entry(GEM_FLAWED_RUBY, 1240.0),
            Map.entry(GEM_ROUGH_AMBER, 3.3),
            Map.entry(GEM_FLAWED_AMBER, 500.0),
            Map.entry(GEM_ROUGH_JADE, 2.1),
            Map.entry(GEM_FLAWED_JADE, 399.0),
            Map.entry(GEM_ROUGH_AMETHYST, 2.1),
            Map.entry(GEM_FLAWED_AMETHYST, 368.0),
            Map.entry(GEM_ROUGH_TOPAZ, 2.9),
            Map.entry(GEM_FLAWED_TOPAZ, 475.0),
            Map.entry(GEM_ROUGH_JASPER, 28.9),
            Map.entry(GEM_FLAWED_JASPER, 1097.0),
            Map.entry(GEM_ROUGH_OPAL, 19.2),
            Map.entry(GEM_FLAWED_OPAL, 997.0)
    );

    
    private static final Map<String, Double> DEFAULT_INSTASELL_PRICES = Map.ofEntries(
            Map.entry(PART_FTX, 157500.0),
            Map.entry(PART_HEART, 184500.0),
            Map.entry(PART_REFLECTOR, 158500.0),
            Map.entry(PART_TRANSMITTER, 77700.0),
            Map.entry(PART_SWITCH, 90000.0),
            Map.entry(PART_MOTOR, 67200.0),
            Map.entry(PART_BOBOMB, 805.0),
            Map.entry(GEM_ROUGH_SAPPHIRE, 3.0),
            Map.entry(GEM_FLAWED_SAPPHIRE, 288.0),
            Map.entry(GEM_FINE_SAPPHIRE, 21450.0),
            Map.entry(GEM_ROUGH_RUBY, 3.0),
            Map.entry(GEM_FLAWED_RUBY, 212.0),
            Map.entry(GEM_ROUGH_AMBER, 3.0),
            Map.entry(GEM_FLAWED_AMBER, 280.0),
            Map.entry(GEM_ROUGH_JADE, 3.0),
            Map.entry(GEM_FLAWED_JADE, 101.0),
            Map.entry(GEM_ROUGH_AMETHYST, 3.0),
            Map.entry(GEM_FLAWED_AMETHYST, 243.0),
            Map.entry(GEM_ROUGH_TOPAZ, 3.0),
            Map.entry(GEM_FLAWED_TOPAZ, 316.0),
            Map.entry(GEM_ROUGH_JASPER, 3.0),
            Map.entry(GEM_FLAWED_JASPER, 700.0),
            Map.entry(GEM_ROUGH_OPAL, 3.0),
            Map.entry(GEM_FLAWED_OPAL, 267.0)
    );

    
    private static final Map<String, Double> NPC_MIN_PRICES = Map.ofEntries(
            Map.entry(GEM_ROUGH_SAPPHIRE, 3.0),
            Map.entry(GEM_FLAWED_SAPPHIRE, 240.0),
            Map.entry(GEM_FINE_SAPPHIRE, 19200.0),
            Map.entry(GEM_ROUGH_RUBY, 3.0),
            Map.entry(GEM_FLAWED_RUBY, 240.0),
            Map.entry(GEM_ROUGH_AMBER, 3.0),
            Map.entry(GEM_FLAWED_AMBER, 240.0),
            Map.entry(GEM_ROUGH_JADE, 3.0),
            Map.entry(GEM_FLAWED_JADE, 240.0),
            Map.entry(GEM_ROUGH_AMETHYST, 3.0),
            Map.entry(GEM_FLAWED_AMETHYST, 240.0),
            Map.entry(GEM_ROUGH_TOPAZ, 3.0),
            Map.entry(GEM_FLAWED_TOPAZ, 240.0),
            Map.entry(GEM_ROUGH_JASPER, 4.0),
            Map.entry(GEM_FLAWED_JASPER, 320.0),
            Map.entry(GEM_ROUGH_OPAL, 4.0),
            Map.entry(GEM_FLAWED_OPAL, 320.0)
    );

    private static final Map<String, Integer> dropCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> lastKnownInventoryCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> lastKnownSackCounts = new ConcurrentHashMap<>();

    private static final Pattern STORED_PATTERN = Pattern.compile("(?:Stored|Amount|Quantity|Count|Total|In Sack)[:\\s]+([0-9,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORED_SLASH_PATTERN = Pattern.compile("([0-9,]+)\\s*/\\s*[0-9,]+");

    private static final Set<Integer> trackedAutomatonIds = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Long> recentlyKilledEntityIds = new ConcurrentHashMap<>();

    private static final Map<String, Double> bazaarSellOfferPrices = new ConcurrentHashMap<>();
    private static final Map<String, Double> bazaarInstaSellPrices = new ConcurrentHashMap<>();

    public static class AutomatonInfo {
        public final int entityId;
        public double lastX, lastY, lastZ;
        public int maxHp = 15000;
        public int currentHp = 15000;
        public boolean damagedByPlayer = false;
        public long lastDamagedTime = 0;
        public long firstSeenTime;

        public AutomatonInfo(int entityId, double x, double y, double z) {
            this.entityId = entityId;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.firstSeenTime = System.currentTimeMillis();
        }
    }

    private static final Map<Integer, AutomatonInfo> trackedAutomatons = new ConcurrentHashMap<>();
    private static final Pattern HP_PATTERN = Pattern.compile("([0-9,]+)/([0-9,]+)");

    
    private static int totalKills = 0;
    private static long sessionStartTime = 0;
    private static long lastKillTime = 0;
    private static long accumulatedActiveMs = 0;
    private static long lastActiveTickTime = 0;
    private static long lastConfigAutoSave = 0;
    private static volatile boolean isPaused = false;
    private static long lastBazaarFetch = 0;
    private static final ScheduledExecutorService BAZAAR_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    public static void init() {
        fetchBazaarPricesAsync(false);
        BAZAAR_EXECUTOR.scheduleAtFixedRate(() -> {
            try {
                fetchBazaarPricesAsync(false);
            } catch (Throwable ignored) {}
        }, 10, 30, TimeUnit.SECONDS);
    }

    public static void fetchBazaarPricesAsync() {
        fetchBazaarPricesAsync(false);
    }

    public static void fetchBazaarPricesAsync(boolean force) {
        CompletableFuture.runAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                long cooldown = force ? 3_000 : (bazaarSellOfferPrices.isEmpty() ? 4_000 : 20_000);
                if (now - lastBazaarFetch < cooldown) return;

                URI uri = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
                HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Wish-Client/1.0.0");
                conn.setRequestProperty("Accept-Encoding", "gzip");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    lastBazaarFetch = now;
                    java.io.InputStream is = conn.getInputStream();
                    if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) {
                        is = new java.util.zip.GZIPInputStream(is);
                    }
                    try (var reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        if (root.has("success") && root.get("success").getAsBoolean() && root.has("products")) {
                            JsonObject products = root.getAsJsonObject("products");
                            for (Map.Entry<String, String> entry : PART_TO_BAZAAR_ID.entrySet()) {
                                String partName = entry.getKey();
                                String bzId = entry.getValue();
                                if (products.has(bzId)) {
                                    JsonObject item = products.getAsJsonObject(bzId);

                                    double sellOffer = 0.0;
                                    double instaSell = 0.0;

                                    if (item.has("buy_summary")) {
                                        try {
                                            var buys = item.getAsJsonArray("buy_summary");
                                            if (buys != null && !buys.isEmpty() && buys.get(0).isJsonObject()) {
                                                var top = buys.get(0).getAsJsonObject();
                                                if (top.has("pricePerUnit")) {
                                                    sellOffer = top.get("pricePerUnit").getAsDouble();
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }

                                    if (item.has("sell_summary")) {
                                        try {
                                            var sells = item.getAsJsonArray("sell_summary");
                                            if (sells != null && !sells.isEmpty() && sells.get(0).isJsonObject()) {
                                                var top = sells.get(0).getAsJsonObject();
                                                if (top.has("pricePerUnit")) {
                                                    instaSell = top.get("pricePerUnit").getAsDouble();
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }

                                    if (item.has("quick_status")) {
                                        JsonObject qs = item.getAsJsonObject("quick_status");
                                        if (sellOffer <= 0 && qs.has("buyPrice")) {
                                            sellOffer = qs.get("buyPrice").getAsDouble();
                                        }
                                        if (instaSell <= 0 && qs.has("sellPrice")) {
                                            instaSell = qs.get("sellPrice").getAsDouble();
                                        }
                                    }

                                    if (sellOffer > 0) bazaarSellOfferPrices.put(partName, sellOffer);
                                    if (instaSell > 0) bazaarInstaSellPrices.put(partName, instaSell);
                                }
                            }
                            
                            refreshHudState();
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static int getItemColor(String item) {
        if (item == null) return 0xFFFFFFFF;
        if (item.contains("FTX 3070") || item.contains("Jade")) {
            return 0xFF55FF55; 
        } else if (item.contains("Synthetic Heart") || item.contains("Ruby")) {
            return 0xFFFF5555; 
        } else if (item.contains("Robotron Reflector") || item.contains("Jasper")) {
            return 0xFFFF55FF; 
        } else if (item.contains("Electron Transmitter") || item.contains("Topaz")) {
            return 0xFFFFFF55; 
        } else if (item.contains("Control Switch") || item.contains("Sapphire")) {
            return 0xFF55FFFF; 
        } else if (item.contains("Superlite Motor")) {
            return 0xFF33AAFF; 
        } else if (item.contains("Amber")) {
            return 0xFFFFAA00; 
        } else if (item.contains("Amethyst")) {
            return 0xFFAA00AA; 
        } else if (item.contains("Opal")) {
            return 0xFFFFFFFF; 
        } else if (item.contains("Bob-omb")) {
            return 0xFFAAAAAA; 
        }
        return 0xFFFFAA00; 
    }

    public static String getChatColorCode(String item) {
        if (item == null) return "§f";
        if (item.contains("FTX 3070") || item.contains("Jade")) {
            return "§a";
        } else if (item.contains("Synthetic Heart") || item.contains("Ruby")) {
            return "§c";
        } else if (item.contains("Robotron Reflector") || item.contains("Jasper")) {
            return "§d";
        } else if (item.contains("Electron Transmitter") || item.contains("Topaz")) {
            return "§e";
        } else if (item.contains("Control Switch") || item.contains("Sapphire")) {
            return "§b";
        } else if (item.contains("Superlite Motor")) {
            return "§9";
        } else if (item.contains("Amber")) {
            return "§6";
        } else if (item.contains("Amethyst")) {
            return "§5";
        } else if (item.contains("Opal")) {
            return "§f";
        } else if (item.contains("Bob-omb")) {
            return "§7";
        }
        return "§6";
    }

    
    public static String findTrackedItem(String rawName) {
        if (rawName == null) return null;
        String clean = SkyblockDetector.stripColorCodes(rawName).trim().toLowerCase();
        
        clean = clean.replace("⬡", "").replace("•", "").trim();

        for (String item : ALL_TRACKED_ITEMS) {
            String itemLower = item.toLowerCase();
            String shortLower = itemLower.endsWith(" gemstone") ? itemLower.substring(0, itemLower.length() - 9) : itemLower;

            if (clean.equals(itemLower) || clean.equals(shortLower)) {
                return item;
            }
            if (clean.contains(shortLower) && shortLower.length() >= 4) {
                return item;
            }
        }
        return null;
    }

    public static boolean isAutomaton(Entity entity) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || entity == null) return false;
        if (entity instanceof Player || entity instanceof ArmorStand) return false;
        if (entity instanceof net.minecraft.world.entity.Display || entity instanceof net.minecraft.world.entity.item.ItemEntity) return false;

        int id = entity.getId();
        if (trackedAutomatonIds.contains(id)) return true;

        if (entity instanceof IronGolem) {
            if (entity.hasCustomName()) {
                String name = SkyblockDetector.stripColorCodes(entity.getCustomName().getString()).toLowerCase();
                if (name.contains("automaton")) {
                    trackedAutomatonIds.add(id);
                    return true;
                }
            }
            if (entity.level() != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    for (Entity nearby : mc.level.entitiesForRendering()) {
                        if (nearby instanceof ArmorStand && nearby.hasCustomName()) {
                            if (nearby.distanceToSqr(entity) <= 12.25) { 
                                String name = SkyblockDetector.stripColorCodes(nearby.getCustomName().getString()).toLowerCase();
                                if (name.contains("automaton")) {
                                    trackedAutomatonIds.add(id);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void onPlayerAttack(Entity target) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || target == null) return;
        if (isAutomaton(target)) {
            int id = target.getId();
            if (recentlyKilledEntityIds.containsKey(id)) return;
            AutomatonInfo info = trackedAutomatons.computeIfAbsent(id, k -> new AutomatonInfo(id, target.getX(), target.getY(), target.getZ()));
            info.damagedByPlayer = true;
            info.lastDamagedTime = System.currentTimeMillis();
            long now = System.currentTimeMillis();
            if (sessionStartTime == 0) {
                sessionStartTime = now;
                lastKillTime = now;
                lastActiveTickTime = now;
                accumulatedActiveMs = 0;
                isPaused = false;
            } else if (isPaused) {
                isPaused = false;
                lastActiveTickTime = now;
                lastKillTime = now;
                refreshHudState();
            }
        }
    }

    private static long lastContainerClosedTime = 0;
    private static boolean wasContainerOpen = false;

    public static void checkInventoryDrops(Minecraft mc) {
        if (mc.player == null) return;

        var screen = mc.gui != null ? mc.gui.screen() : null;
        boolean isContainerOpen = screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
        if (isContainerOpen) {
            wasContainerOpen = true;
            lastContainerClosedTime = System.currentTimeMillis();
        } else if (wasContainerOpen) {
            wasContainerOpen = false;
            lastContainerClosedTime = System.currentTimeMillis();
        }

        try {
            int invSize = mc.player.getInventory().getContainerSize();
            Map<String, Integer> currentInvCounts = new HashMap<>();

            for (int i = 0; i < invSize; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack == null || stack.isEmpty()) continue;
                String hoverName = stack.getHoverName().getString();
                String matched = findTrackedItem(hoverName);
                if (matched != null) {
                    currentInvCounts.merge(matched, stack.getCount(), Integer::sum);
                }
            }

            long now = System.currentTimeMillis();
            cleanExpiredClaims(now);

            
            
            
            
            boolean canRecordDrops = !ModConfig.INSTANCE.automatonSackMode
                    && SkyblockDetector.isInPrecursorCity
                    && !isContainerOpen
                    && (now - lastContainerClosedTime >= 3000L)
                    && (lastKillTime > 0 && now - lastKillTime < 15_000L);

            for (String part : ALL_PARTS) {
                int count = currentInvCounts.getOrDefault(part, 0);
                Integer prev = lastKnownInventoryCounts.get(part);
                if (prev == null) {
                    lastKnownInventoryCounts.put(part, count);
                } else if (count > prev) {
                    int gained = count - prev;
                    lastKnownInventoryCounts.put(part, count);

                    if (canRecordDrops) {
                        int chatClaimed = recentChatClaims.getOrDefault(part, 0);
                        if (chatClaimed >= gained) {
                            recentChatClaims.put(part, chatClaimed - gained);
                        } else {
                            if (chatClaimed > 0) {
                                gained -= chatClaimed;
                                recentChatClaims.remove(part);
                            }
                            recordDrop(part, gained);
                            recentInventoryClaims.merge(part, gained, Integer::sum);
                            recentInventoryClaimTimes.put(part, now);
                        }
                    }
                } else if (count < prev) {
                    lastKnownInventoryCounts.put(part, count);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void checkContainerSack(Minecraft mc) {
        if (mc.player == null) return;
        var currentScreen = mc.gui != null ? mc.gui.screen() : null;
        if (currentScreen == null || currentScreen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            return;
        }

        var menu = mc.player.containerMenu;
        if (menu == null) return;

        
        if (mc.gui != null && mc.gui.screen() != null) {
            String title = mc.gui.screen().getTitle().getString().toLowerCase();
            if (title.contains("bazaar") || title.contains("auction")) {
                return;
            }
        }

        try {
            int size = menu.slots.size();
            for (int i = 0; i < size; i++) {
                Slot slot = menu.slots.get(i);
                if (slot != null && slot.hasItem()) {
                    parseItemStackForSack(slot.getItem());
                }
            }
            ItemStack carried = menu.getCarried();
            if (carried != null && !carried.isEmpty()) {
                parseItemStackForSack(carried);
            }
        } catch (Exception ignored) {}
    }

    public static void syncSacksFromContainer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        checkInventoryDrops(mc);
        checkContainerSack(mc);
    }

    public static void parseItemStackForSack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        String hoverName = stack.getHoverName().getString();
        String hoverLower = SkyblockDetector.stripColorCodes(hoverName).toLowerCase().trim();

        
        if (hoverLower.contains("bazaar") || hoverLower.contains("sell sacks now") || hoverLower.contains("instant sell")) {
            return;
        }

        
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                parseLoreLineForSack(line.getString());
            }
        }

        
        String tracked = findTrackedItem(hoverName);
        if (tracked != null && lore != null) {
            int stored = -1;
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String text = SkyblockDetector.stripColorCodes(line.getString()).trim();
                Matcher m = STORED_PATTERN.matcher(text);
                if (m.find()) {
                    try {
                        stored = Integer.parseInt(m.group(1).replace(",", ""));
                        break;
                    } catch (Exception ignored) {}
                }
                Matcher mSlash = STORED_SLASH_PATTERN.matcher(text);
                if (mSlash.find()) {
                    try {
                        stored = Integer.parseInt(mSlash.group(1).replace(",", ""));
                        break;
                    } catch (Exception ignored) {}
                }
            }
            if (stored != -1) {
                recordSackCount(tracked, stored);
            }
        }
    }

    public static void parseLoreLineForSack(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) return;
        String clean = SkyblockDetector.stripColorCodes(rawLine).trim();
        if (clean.isEmpty()) return;

        String lower = clean.toLowerCase();
        if (lower.contains("bazaar") || lower.contains("coins")) return;

        for (String part : ALL_TRACKED_ITEMS) {
            String partLower = part.toLowerCase();
            String shortLower = partLower.endsWith(" gemstone") ? partLower.substring(0, partLower.length() - 9) : partLower;

            int partIdx = lower.indexOf(partLower);
            int matchLen = partLower.length();
            if (partIdx == -1) {
                partIdx = lower.indexOf(shortLower);
                matchLen = shortLower.length();
            }

            if (partIdx != -1) {
                int count = -1;

                
                String before = clean.substring(0, partIdx).trim();
                Matcher mPrefix = Pattern.compile("([0-9,]+)\\s*(?:x\\s*)?[^0-9a-zA-Z]*$", Pattern.CASE_INSENSITIVE).matcher(before);
                if (mPrefix.find()) {
                    try {
                        count = Integer.parseInt(mPrefix.group(1).replace(",", ""));
                    } catch (Exception ignored) {}
                } else {
                    Matcher mAnyNum = Pattern.compile("([0-9,]+)(?!.*[0-9])").matcher(before);
                    if (mAnyNum.find()) {
                        try {
                            count = Integer.parseInt(mAnyNum.group(1).replace(",", ""));
                        } catch (Exception ignored) {}
                    }
                }

                
                if (count == -1) {
                    String after = clean.substring(partIdx + matchLen).trim();
                    Matcher mAfterCount = Pattern.compile("^(?:\\s*[x:–\\-\\+]\\s*|\\s*\\(x?\\s*)([0-9,]+)", Pattern.CASE_INSENSITIVE).matcher(after);
                    if (mAfterCount.find()) {
                        try {
                            count = Integer.parseInt(mAfterCount.group(1).replace(",", ""));
                        } catch (Exception ignored) {}
                    } else {
                        Matcher mAfterNum = Pattern.compile("^\\s*([0-9,]+)").matcher(after);
                        if (mAfterNum.find()) {
                            try {
                                count = Integer.parseInt(mAfterNum.group(1).replace(",", ""));
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (count >= 0) {
                    recordSackCount(part, count);
                    return;
                }
            }
        }
    }

    public static void recordSackCount(String part, int totalStored) {
        if (totalStored < 0 || part == null) return;

        Integer prevKnown = lastKnownSackCounts.get(part);
        if (prevKnown == null) {
            
            lastKnownSackCounts.put(part, totalStored);
        } else {
            if (totalStored > prevKnown) {
                int gained = totalStored - prevKnown;
                lastKnownSackCounts.put(part, totalStored);

                long now = System.currentTimeMillis();
                cleanExpiredClaims(now);

                int chatClaimed = recentChatClaims.getOrDefault(part, 0);
                int invClaimed = recentInventoryClaims.getOrDefault(part, 0);
                int alreadyClaimed = chatClaimed + invClaimed;

                if (alreadyClaimed >= gained) {
                    if (chatClaimed >= gained) {
                        recentChatClaims.put(part, chatClaimed - gained);
                    } else {
                        recentChatClaims.remove(part);
                        recentInventoryClaims.put(part, invClaimed - (gained - chatClaimed));
                    }
                } else {
                    if (alreadyClaimed > 0) {
                        gained -= alreadyClaimed;
                        recentChatClaims.remove(part);
                        recentInventoryClaims.remove(part);
                    }
                    recordDrop(part, gained);
                }
            } else if (totalStored < prevKnown) {
                lastKnownSackCounts.put(part, totalStored);
            }
        }
    }

    private static final Map<String, Integer> recentChatClaims = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentChatClaimTimes = new ConcurrentHashMap<>();
    private static final Map<String, Integer> recentInventoryClaims = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentInventoryClaimTimes = new ConcurrentHashMap<>();
    private static final Map<String, Integer> recentRareDropClaims = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentRareDropClaimTimes = new ConcurrentHashMap<>();
    private static final Map<String, Integer> recentSackClaims = new ConcurrentHashMap<>();
    private static final Map<String, Long> recentSackClaimTimes = new ConcurrentHashMap<>();

    private static void cleanExpiredClaims(long now) {
        recentChatClaimTimes.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 4000) {
                recentChatClaims.remove(entry.getKey());
                return true;
            }
            return false;
        });
        recentInventoryClaimTimes.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 4000) {
                recentInventoryClaims.remove(entry.getKey());
                return true;
            }
            return false;
        });
        recentRareDropClaimTimes.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 4000) {
                recentRareDropClaims.remove(entry.getKey());
                return true;
            }
            return false;
        });
        recentSackClaimTimes.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 4000) {
                recentSackClaims.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    
    private static final Map<String, Long> processedLineCache = new ConcurrentHashMap<>();

    public static void onChatMessage(Component message) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || message == null) return;

        String mainText = message.getString();
        if (mainText == null || mainText.isEmpty()) return;

        String clean = SkyblockDetector.stripColorCodes(mainText).trim();
        
        if (!isSackNotification(clean)) {
            return;
        }

        
        Set<String> collectedTooltips = new LinkedHashSet<>();
        Set<HoverEvent> seenEvents = Collections.newSetFromMap(new IdentityHashMap<>());
        collectAllHoverTexts(message, collectedTooltips, seenEvents);

        boolean hadHoverDrops = false;
        for (String tooltip : collectedTooltips) {
            if (processSackTooltip(tooltip)) {
                hadHoverDrops = true;
            }
        }

        
        if (!hadHoverDrops) {
            onChatMessage(clean);
        }
    }

    private static void collectAllHoverTexts(Component comp, Set<String> results, Set<HoverEvent> seenEvents) {
        if (comp == null) return;

        net.minecraft.network.chat.Style style = comp.getStyle();
        if (style != null && style.getHoverEvent() != null) {
            HoverEvent hover = style.getHoverEvent();
            if (seenEvents.add(hover)) {
                if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowText showText) {
                    Component val = showText.value();
                    if (val != null) {
                        String str = val.getString();
                        if (str != null && !str.trim().isEmpty()) {
                            results.add(str.trim());
                        }
                    }
                }
            }
        }

        for (Component sibling : comp.getSiblings()) {
            collectAllHoverTexts(sibling, results, seenEvents);
        }
    }

    public static boolean processSackTooltip(String tooltip) {
        if (tooltip == null || tooltip.isEmpty()) return false;

        long now = System.currentTimeMillis();
        processedLineCache.entrySet().removeIf(e -> now - e.getValue() > 3000);

        boolean anyMatched = false;
        String[] lines = tooltip.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (processedLineCache.putIfAbsent(trimmed, now) != null) {
                continue;
            }
            if (parseDropLine(trimmed)) {
                anyMatched = true;
            }
        }
        return anyMatched;
    }

    public static boolean onChatMessage(String rawMessage) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton || rawMessage == null) return false;
        String clean = SkyblockDetector.stripColorCodes(rawMessage).replace('\u00A0', ' ').trim();
        if (clean.isEmpty() || !isSackNotification(clean)) return false;

        long now = System.currentTimeMillis();
        processedLineCache.entrySet().removeIf(e -> now - e.getValue() > 3000);

        boolean anyMatched = false;
        String[] lines = clean.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            
            if (processedLineCache.putIfAbsent(trimmed, now) != null) {
                continue;
            }
            if (parseDropLine(trimmed)) {
                anyMatched = true;
            }
        }
        return anyMatched;
    }

    public static boolean isSackNotification(String rawText) {
        if (rawText == null || rawText.isEmpty()) return false;
        String lower = rawText.toLowerCase();

        
        if (lower.startsWith("party >") || lower.startsWith("guild >") || lower.startsWith("co-op >")
                || lower.startsWith("from ") || lower.startsWith("to ") || lower.contains("[wish]")) {
            return false;
        }

        
        
        if (lower.contains("sack") || lower.contains("sacks")) {
            return true;
        }
        return false;
    }

    public static boolean processSackDropMessage(String rawText) {
        if (!isSackNotification(rawText)) return false;
        return parseDropLine(rawText);
    }

    public static boolean parseDropLine(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) return false;
        String clean = SkyblockDetector.stripColorCodes(rawLine).replace('\u00A0', ' ').trim();
        if (clean.isEmpty()) return false;

        
        clean = clean.replaceAll("(?i)\\(\\+[0-9,]+%?\\s*[^)]*magic find[^)]*\\)", "").trim();

        String lower = clean.toLowerCase();
        boolean isRareDrop = lower.contains("rare drop");
        boolean matchedAny = false;

        for (String item : ALL_TRACKED_ITEMS) {
            String itemLower = item.toLowerCase();
            String shortLower = itemLower.endsWith(" gemstone") ? itemLower.substring(0, itemLower.length() - 9) : itemLower;

            int idx = lower.indexOf(itemLower);
            String matchedTarget = itemLower;
            if (idx == -1 && !shortLower.equals(itemLower)) {
                idx = lower.indexOf(shortLower);
                matchedTarget = shortLower;
            }

            if (idx != -1) {
                int count = 1;

                
                String before = clean.substring(0, idx).trim();
                Matcher mBefore = Pattern.compile("([0-9,]+)\\s*(?:x\\s*)?[^0-9a-zA-Z]*$", Pattern.CASE_INSENSITIVE).matcher(before);
                if (mBefore.find()) {
                    try {
                        count = Integer.parseInt(mBefore.group(1).replace(",", ""));
                    } catch (Exception ignored) {}
                } else {
                    
                    Matcher mAnyNum = Pattern.compile("([0-9,]+)(?!.*[0-9])").matcher(before);
                    if (mAnyNum.find()) {
                        try {
                            count = Integer.parseInt(mAnyNum.group(1).replace(",", ""));
                        } catch (Exception ignored) {}
                    } else {
                        
                        String after = clean.substring(idx + matchedTarget.length()).trim();
                        Matcher mAfter = Pattern.compile("^(?:\\s*[x:–\\-\\+]\\s*|\\s*\\(x?\\s*)([0-9,]+)", Pattern.CASE_INSENSITIVE).matcher(after);
                        if (mAfter.find()) {
                            try {
                                count = Integer.parseInt(mAfter.group(1).replace(",", ""));
                            } catch (Exception ignored) {}
                        } else {
                            Matcher mAfterNum = Pattern.compile("^\\s*([0-9,]+)").matcher(after);
                            if (mAfterNum.find()) {
                                try {
                                    count = Integer.parseInt(mAfterNum.group(1).replace(",", ""));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                int dropAmount = Math.max(1, count);

                long now = System.currentTimeMillis();
                cleanExpiredClaims(now);

                
                if (isRareDrop) {
                    int sackClaim = recentSackClaims.getOrDefault(item, 0);
                    if (sackClaim >= dropAmount) {
                        recentSackClaims.put(item, sackClaim - dropAmount);
                        return true; 
                    } else if (sackClaim > 0) {
                        dropAmount -= sackClaim;
                        recentSackClaims.remove(item);
                    }
                    recentRareDropClaims.merge(item, dropAmount, Integer::sum);
                    recentRareDropClaimTimes.put(item, now);
                } else {
                    int rareClaim = recentRareDropClaims.getOrDefault(item, 0);
                    if (rareClaim >= dropAmount) {
                        recentRareDropClaims.put(item, rareClaim - dropAmount);
                        return true; 
                    } else if (rareClaim > 0) {
                        dropAmount -= rareClaim;
                        recentRareDropClaims.remove(item);
                    }
                    recentSackClaims.merge(item, dropAmount, Integer::sum);
                    recentSackClaimTimes.put(item, now);
                }

                
                int invClaim = recentInventoryClaims.getOrDefault(item, 0);
                if (invClaim >= dropAmount) {
                    recentInventoryClaims.put(item, invClaim - dropAmount);
                    
                    return true;
                } else if (invClaim > 0) {
                    dropAmount -= invClaim;
                    recentInventoryClaims.remove(item);
                }

                recordDrop(item, dropAmount);

                
                recentChatClaims.merge(item, dropAmount, Integer::sum);
                recentChatClaimTimes.put(item, now);

                Integer prevVal = lastKnownSackCounts.get(item);
                lastKnownSackCounts.put(item, prevVal == null ? dropAmount : prevVal + dropAmount);
                return true;
            }
        }
        return false;
    }

    public static void recordDrop(String partName) {
        recordDrop(partName, 1);
    }

    public static void recordDrop(String partName, int count) {
        if (count <= 0) count = 1;
        long now = System.currentTimeMillis();
        if (sessionStartTime == 0) {
            sessionStartTime = now;
            lastKillTime = now;
            lastActiveTickTime = now;
            accumulatedActiveMs = 0;
            isPaused = false;
        }
        dropCounts.merge(partName, count, Integer::sum);
        if (ModConfig.INSTANCE.automatonTotalDrops == null) {
            ModConfig.INSTANCE.automatonTotalDrops = new java.util.HashMap<>();
        }
        ModConfig.INSTANCE.automatonTotalDrops.merge(partName, count, Integer::sum);
        ModConfig.INSTANCE.save();

        if (totalKills == 0) {
            totalKills = 1;
        }
    }

    public static void recordKill() {
        long now = System.currentTimeMillis();
        if (sessionStartTime == 0) {
            sessionStartTime = now;
            accumulatedActiveMs = 0;
        }
        totalKills++;
        ModConfig.INSTANCE.automatonTotalKills++;
        ModConfig.INSTANCE.save();
        lastKillTime = now;
        lastActiveTickTime = now;
        boolean wasPaused = isPaused;
        isPaused = false;
        if (wasPaused) {
            refreshHudState();
        }
    }

    public static void resetStats() {
        resetSessionStats();
    }

    public static void resetSessionStats() {
        totalKills = 0;
        sessionStartTime = 0;
        lastKillTime = 0;
        accumulatedActiveMs = 0;
        lastActiveTickTime = 0;
        isPaused = false;
        dropCounts.clear();
        lastKnownInventoryCounts.clear();
        lastKnownSackCounts.clear();
        recentlyKilledEntityIds.clear();
        recentChatClaims.clear();
        recentChatClaimTimes.clear();
        recentInventoryClaims.clear();
        recentInventoryClaimTimes.clear();
        recentRareDropClaims.clear();
        recentRareDropClaimTimes.clear();
        recentSackClaims.clear();
        recentSackClaimTimes.clear();
        processedLineCache.clear();
        refreshHudState();
    }

    public static void resetTotalStats() {
        ModConfig.INSTANCE.automatonTotalKills = 0;
        ModConfig.INSTANCE.automatonTotalActiveSeconds = 0;
        ModConfig.INSTANCE.automatonTotalActiveMs = 0;
        if (ModConfig.INSTANCE.automatonTotalDrops != null) {
            ModConfig.INSTANCE.automatonTotalDrops.clear();
        }
        ModConfig.INSTANCE.saveImmediately();
        refreshHudState();
    }

    public static void onClientTick(Minecraft mc) {
        if (!ModConfig.INSTANCE.enableMiningAutomaton) {
            trackedAutomatonIds.clear();
            trackedAutomatons.clear();
            HudStateContainer.updateAutomatonState(false, 0, 0, 0, 0, 0, null);
            return;
        }

        if (mc.level == null || mc.player == null) {
            trackedAutomatonIds.clear();
            trackedAutomatons.clear();
            HudStateContainer.updateAutomatonState(false, 0, 0, 0, 0, 0, null);
            return;
        }

        
        if (SkyblockDetector.isInSkyblock) {
            checkInventoryDrops(mc);
            checkContainerSack(mc);
        }

        long now = System.currentTimeMillis();

        
        if (bazaarSellOfferPrices.isEmpty() && now - lastBazaarFetch > 5000) {
            fetchBazaarPricesAsync(false);
        }

        
        if (sessionStartTime > 0 && lastKillTime > 0) {
            if (now - lastKillTime > 30_000L) {
                if (!isPaused) {
                    isPaused = true;
                    ModConfig.INSTANCE.save();
                    refreshHudState();
                }
                lastActiveTickTime = 0;
            } else {
                if (isPaused) {
                    isPaused = false;
                    refreshHudState();
                }
                if (lastActiveTickTime > 0) {
                    long delta = now - lastActiveTickTime;
                    if (delta > 0 && delta < 5000) {
                        accumulatedActiveMs += delta;
                        ModConfig.INSTANCE.automatonTotalActiveMs += delta;
                        ModConfig.INSTANCE.automatonTotalActiveSeconds = ModConfig.INSTANCE.automatonTotalActiveMs / 1000L;
                    }
                }
                lastActiveTickTime = now;
                if (now - lastConfigAutoSave > 30_000L) {
                    lastConfigAutoSave = now;
                    ModConfig.INSTANCE.save();
                }
            }
        }

        
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        boolean inPrecursor = SkyblockDetector.isInPrecursorCity
                || SkyblockDetector.isInPrecursorZone
                || (SkyblockDetector.isInCrystalHollows && px >= 513 && pz >= 513);

        if (!inPrecursor) {
            trackedAutomatonIds.clear();
            trackedAutomatons.clear();
            HudStateContainer.updateAutomatonState(false, 0, 0, 0, 0, 0, null);
            return;
        }

        recentlyKilledEntityIds.entrySet().removeIf(e -> now - e.getValue() > 30_000);

        
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand && entity.hasCustomName()) {
                String rawName = entity.getCustomName().getString();
                String cleanName = SkyblockDetector.stripColorCodes(rawName);
                if (cleanName.toLowerCase().contains("automaton")) {
                    int curHp = 15000;
                    int maxHp = 15000;
                    Matcher m = HP_PATTERN.matcher(cleanName);
                    if (m.find()) {
                        try {
                            curHp = Integer.parseInt(m.group(1).replace(",", ""));
                            maxHp = Integer.parseInt(m.group(2).replace(",", ""));
                        } catch (Exception ignored) {}
                    }

                    
                    IronGolem closestGolem = null;
                    double minDistSq = 12.25; 
                    for (Entity other : mc.level.entitiesForRendering()) {
                        if (other instanceof IronGolem golem && golem.isAlive() && !golem.isDeadOrDying()) {
                            int gid = golem.getId();
                            if (recentlyKilledEntityIds.containsKey(gid)) {
                                continue;
                            }
                            double dSq = other.distanceToSqr(entity);
                            if (dSq < minDistSq) {
                                minDistSq = dSq;
                                closestGolem = golem;
                            }
                        }
                    }

                    if (closestGolem != null) {
                        final IronGolem targetGolem = closestGolem;
                        int mobId = targetGolem.getId();
                        trackedAutomatonIds.add(mobId);
                        AutomatonInfo info = trackedAutomatons.computeIfAbsent(mobId, k -> new AutomatonInfo(mobId, targetGolem.getX(), targetGolem.getY(), targetGolem.getZ()));
                        info.lastX = targetGolem.getX();
                        info.lastY = targetGolem.getY();
                        info.lastZ = targetGolem.getZ();
                        info.currentHp = curHp;
                        info.maxHp = maxHp;

                        if (curHp < maxHp && mc.player.distanceToSqr(info.lastX, info.lastY, info.lastZ) < 144.0) { 
                            info.damagedByPlayer = true;
                            info.lastDamagedTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }

        
        trackedAutomatons.entrySet().removeIf(entry -> {
            int id = entry.getKey();
            AutomatonInfo info = entry.getValue();
            Entity mob = mc.level.getEntity(id);

            boolean isDeadOrGone = (mob == null || !mob.isAlive() || (mob instanceof LivingEntity living && living.isDeadOrDying()));
            if (isDeadOrGone) {
                trackedAutomatonIds.remove(id);
                if (!recentlyKilledEntityIds.containsKey(id)) {
                    recentlyKilledEntityIds.put(id, now);
                    boolean wasAttacked = info.damagedByPlayer && (now - info.lastDamagedTime < 10_000);
                    double distSq = mc.player.distanceToSqr(info.lastX, info.lastY, info.lastZ);
                    boolean wasNear = distSq < 144.0; 
                    boolean wasVeryNear = distSq < 64.0; 

                    if (wasAttacked || (wasNear && info.currentHp <= 0) || wasVeryNear) {
                        recordKill();
                    }
                }
                return true;
            }
            return false;
        });

        
        refreshHudState();
    }

    public static void refreshHudState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        boolean inCity = SkyblockDetector.isInPrecursorCity
                || (PrecursorCityLocator.hasExactAltar() && PrecursorCityLocator.getDistanceToPlayer(mc.player) <= 30);
        boolean showHud = ModConfig.INSTANCE.enableMiningAutomaton
                && ModConfig.INSTANCE.enableAutomatonTracker
                && inCity;

        long elapsedSec = getSessionElapsedSeconds();
        double kph = getKillsPerHour();
        double totalProfit = getTotalProfit();
        double pph = getProfitPerHour();

        HudStateContainer.updateAutomatonState(
                showHud,
                totalKills,
                kph,
                totalProfit,
                pph,
                elapsedSec,
                mc.font
        );
    }

    public static long getSessionElapsedSeconds() {
        if (sessionStartTime == 0) return 0;
        return Math.max(1, accumulatedActiveMs / 1000L);
    }

    public static boolean isPaused() {
        return isPaused && sessionStartTime > 0 && totalKills > 0;
    }

    public static double getKillsPerHour() {
        long sec = getSessionElapsedSeconds();
        if (sec == 0 || totalKills == 0) return 0.0;
        return (totalKills * 3600.0) / (double) sec;
    }

    public static double getAverageProfitPerKill() {
        double sum = 0;
        int count = 0;
        for (String part : PRECURSOR_PARTS) {
            if (!part.equals(PART_BOBOMB)) {
                sum += getPrice(part);
                count++;
            }
        }
        double avgPartPrice = count > 0 ? (sum / count) : 167000.0;
        return avgPartPrice * 0.1526;
    }

    public static double getTotalProfit() {
        return getSessionProfit();
    }

    public static double getSessionProfit() {
        double actualProfit = 0.0;
        for (Map.Entry<String, Integer> entry : dropCounts.entrySet()) {
            double price = getPrice(entry.getKey());
            actualProfit += price * entry.getValue();
        }
        return actualProfit;
    }

    public static double getProfitPerHour() {
        return getSessionProfitPerHour();
    }

    public static double getSessionProfitPerHour() {
        long sec = getSessionElapsedSeconds();
        if (sec == 0) return 0.0;
        return (getSessionProfit() * 3600.0) / (double) sec;
    }

    public static double getTotalLifetimeProfit() {
        if (ModConfig.INSTANCE.automatonTotalDrops == null) return 0.0;
        double actualProfit = 0.0;
        for (Map.Entry<String, Integer> entry : ModConfig.INSTANCE.automatonTotalDrops.entrySet()) {
            double price = getPrice(entry.getKey());
            actualProfit += price * entry.getValue();
        }
        return actualProfit;
    }

    public static long getTotalLifetimeActiveSeconds() {
        long sec = ModConfig.INSTANCE.automatonTotalActiveMs / 1000L;
        if (sec == 0 && ModConfig.INSTANCE.automatonTotalActiveSeconds > 0) {
            sec = ModConfig.INSTANCE.automatonTotalActiveSeconds;
        }
        if (sec == 0 && ModConfig.INSTANCE.automatonTotalKills > 0) {
            return Math.max(1, accumulatedActiveMs / 1000L);
        }
        return sec;
    }

    public static double getTotalLifetimeKillsPerHour() {
        long sec = getTotalLifetimeActiveSeconds();
        if (sec == 0 || ModConfig.INSTANCE.automatonTotalKills == 0) return 0.0;
        return (ModConfig.INSTANCE.automatonTotalKills * 3600.0) / (double) sec;
    }

    public static double getTotalLifetimeProfitPerHour() {
        long sec = getTotalLifetimeActiveSeconds();
        if (sec == 0) return 0.0;
        return (getTotalLifetimeProfit() * 3600.0) / (double) sec;
    }

    public static int getTotalLifetimeKills() {
        return ModConfig.INSTANCE.automatonTotalKills;
    }

    public static Map<String, Integer> getTotalLifetimeDrops() {
        if (ModConfig.INSTANCE.automatonTotalDrops == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(ModConfig.INSTANCE.automatonTotalDrops);
    }

    public static double getPrice(String partName) {
        boolean insta = ModConfig.INSTANCE.automatonUseInstaSell;
        Map<String, Double> liveMap = insta ? bazaarInstaSellPrices : bazaarSellOfferPrices;
        Map<String, Double> fallbackMap = insta ? DEFAULT_INSTASELL_PRICES : DEFAULT_SELL_OFFER_PRICES;

        double p = 0.0;
        Double val = liveMap.get(partName);
        if (val != null && val > 0) {
            p = val;
        } else {
            p = fallbackMap.getOrDefault(partName, 0.0);
        }

        double npcMin = NPC_MIN_PRICES.getOrDefault(partName, 0.0);
        return Math.max(p, npcMin);
    }

    public static int getDropCount(String partName) {
        return dropCounts.getOrDefault(partName, 0);
    }

    public static int getTotalKills() {
        return totalKills;
    }

    public static Map<String, Integer> getAllDropCounts() {
        return Collections.unmodifiableMap(dropCounts);
    }

    public static Map<String, Double> getAllPrices() {
        Map<String, Double> result = new HashMap<>();
        for (String item : ALL_TRACKED_ITEMS) {
            result.put(item, getPrice(item));
        }
        return Collections.unmodifiableMap(result);
    }

    public static String getDisplayName(String itemName) {
        if (itemName == null) return "";
        if (itemName.endsWith(" Gemstone")) {
            return itemName.substring(0, itemName.length() - " Gemstone".length());
        }
        return itemName;
    }

    public static String formatCoins(double coins) {
        if (coins >= 1_000_000_000) {
            return String.format(Locale.US, "%.2fB", coins / 1_000_000_000.0);
        } else if (coins >= 1_000_000) {
            return String.format(Locale.US, "%.2fM", coins / 1_000_000.0);
        } else if (coins >= 1_000) {
            return String.format(Locale.US, "%.1fk", coins / 1_000.0);
        } else {
            return String.format(Locale.US, "%.0f", coins);
        }
    }

    public static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.US, "%dh %02dm %02ds", h, m, s);
        } else {
            return String.format(Locale.US, "%02dm %02ds", m, s);
        }
    }
}
