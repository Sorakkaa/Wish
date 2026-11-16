package com.wish.client.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wish.client.config.ModConfig;
import com.wish.client.util.HttpClientUtils;
import com.wish.client.util.WishPaths;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CustomModelManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Wish-CustomModels");
    private static final Path CONFIG_DIR = WishPaths.getConfigDir();
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("models.json");
    private static final Path OLD_CONFIG_PATH = WishPaths.getLegacyConfigFile("wish_models.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ScheduledExecutorService SAVE_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Wish-Models-Save");
            t.setDaemon(true);
            return t;
        });
    private static final Object SAVE_LOCK = new Object();
    private static ScheduledFuture<?> pendingSaveTask = null;

    public static volatile boolean bypassReplacement = false;
    private static volatile boolean toolHighlightRefreshPending = false;
    private static final Map<String, String> overrides = new ConcurrentHashMap<>();

    private static final Map<ItemStack, SkyblockData> sbDataCache = Collections.synchronizedMap(
        new LinkedHashMap<ItemStack, SkyblockData>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ItemStack, SkyblockData> eldest) {
                return size() > 256;
            }
        }
    );

    private static final Map<ItemStack, ItemStack> replacementCache = Collections.synchronizedMap(
        new LinkedHashMap<ItemStack, ItemStack>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ItemStack, ItemStack> eldest) {
                return size() > 256;
            }
        }
    );

    private static final Map<String, Item> modelItemCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> glowOverrides = new ConcurrentHashMap<>();
    private static final Map<String, String> nameOverrides = new ConcurrentHashMap<>();
    private static final Map<String, Component> compiledNameCache = Collections.synchronizedMap(
        new LinkedHashMap<String, Component>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                return size() > 128;
            }
        }
    );

    public static class SkyblockData {
        public final String id;
        public final String uuid;

        public SkyblockData(String id, String uuid) {
            this.id = id != null ? id : "";
            this.uuid = uuid != null ? uuid : "";
        }
    }

    public static class SkyblockItem {
        public final String id;
        public final String name;
        public final Item baseItem;
        public final String colorPrefix;

        public SkyblockItem(String id, String name, Item baseItem, String colorPrefix) {
            this.id = id;
            this.name = name;
            this.baseItem = baseItem;
            this.colorPrefix = colorPrefix;
        }
    }

    public static final Map<String, SkyblockItem> SKYBLOCK_MAP = new HashMap<>();

    private static void registerSb(String id, String name, Item item, String color) {
        SkyblockItem sb = new SkyblockItem(id, name, item, color);
        SKYBLOCK_MAP.put(id.toUpperCase(Locale.ROOT), sb);
    }

    static {

        registerSb("DARK_CLAYMORE", "Dark Claymore", Items.STONE_SWORD, "§6");
        registerSb("HYPERION", "Hyperion", Items.IRON_SWORD, "§6");
        registerSb("ASTRAEA", "Astraea", Items.IRON_SWORD, "§6");
        registerSb("SCYLLA", "Scylla", Items.IRON_SWORD, "§6");
        registerSb("VALKYRIE", "Valkyrie", Items.IRON_SWORD, "§6");
        registerSb("NECRON_BLADE", "Necron's Blade", Items.IRON_SWORD, "§6");
        registerSb("ASPECT_OF_THE_END", "Aspect of the End", Items.DIAMOND_SWORD, "§9");
        registerSb("ASPECT_OF_THE_VOID", "Aspect of the Void", Items.DIAMOND_SHOVEL, "§5");
        registerSb("SHADOW_FURY", "Shadow Fury", Items.DIAMOND_SWORD, "§6");
        registerSb("LIVID_DAGGER", "Livid Dagger", Items.IRON_SWORD, "§6");
        registerSb("GIANTS_SWORD", "Giant's Sword", Items.IRON_SWORD, "§6");
        registerSb("FLOWER_OF_TRUTH", "Flower of Truth", Items.POPPY, "§6");
        registerSb("YETI_SWORD", "Yeti Sword", Items.IRON_SWORD, "§6");
        registerSb("MIDAS_SWORD", "Midas' Sword", Items.GOLDEN_SWORD, "§6");
        registerSb("ROGUE_SWORD", "Rogue Sword", Items.GOLDEN_SWORD, "§a");
        registerSb("REAPER_SCYTHE", "Reaper Scythe", Items.DIAMOND_HOE, "§6");
        registerSb("WARPED_AOTE", "Warped AOTE", Items.DIAMOND_SWORD, "§5");

        registerSb("TERMINATOR", "Terminator", Items.BOW, "§6");
        registerSb("JUJU_SHORTBOW", "Juju Shortbow", Items.BOW, "§5");
        registerSb("SPIRIT_BOW", "Spirit Bow", Items.BOW, "§6");
        registerSb("RUNAANS_BOW", "Runaan's Bow", Items.BOW, "§6");
        registerSb("MOSQUITO_BOW", "Mosquito Bow", Items.BOW, "§6");
        registerSb("MAGMA_BOW", "Magma Bow", Items.BOW, "§a");
        registerSb("EXPLOSIVE_BOW", "Explosive Bow", Items.BOW, "§5");
        registerSb("BONEMERANG", "Bonemerang", Items.BONE, "§9");

        registerSb("BONZO_STAFF", "Bonzo's Staff", Items.BLAZE_ROD, "§9");
        registerSb("SPIRIT_SCEPTRE", "Spirit Sceptre", Items.ALLIUM, "§6");
        registerSb("MIDAS_STAFF", "Midas Staff", Items.GOLDEN_SHOVEL, "§6");
        registerSb("GYROKINETIC_WAND", "Gyrokinetic Wand", Items.STICK, "§5");
        registerSb("ICE_SPRAY_WAND", "Ice Spray Wand", Items.STICK, "§6");
        registerSb("BAT_WAND", "Bat Wand", Items.STICK, "§9");
        registerSb("FIRE_FREEZE_STAFF", "Fire Freeze Staff", Items.BLAZE_ROD, "§5");
        registerSb("WAND_OF_ATONEMENT", "Wand of Atonement", Items.STICK, "§6");
        registerSb("AURORA_STAFF", "Aurora Staff", Items.BLAZE_ROD, "§5");

        registerSb("DIVAN_DRILL", "Divan's Drill", Items.PRISMARINE_SHARD, "§6");
        registerSb("GEMSTONE_GAUNTLET", "Gemstone Gauntlet", Items.GOLDEN_SHOVEL, "§6");
        registerSb("DRILL_TITANIUM", "Titanium Drill", Items.PRISMARINE_SHARD, "§5");
        registerSb("TREECAPITATOR", "Treecapitator", Items.GOLDEN_AXE, "§5");
        registerSb("CHOPPER_AXE", "Chopper Axe", Items.GOLDEN_AXE, "§6");
        registerSb("PUMPKIN_DICER", "Pumpkin Dicer", Items.IRON_AXE, "§9");
        registerSb("MELON_DICER", "Melon Dicer", Items.IRON_AXE, "§9");
        registerSb("COCO_CHOPPER", "Coco Chopper", Items.IRON_AXE, "§9");
        registerSb("CACTUS_KNIFE", "Cactus Knife", Items.IRON_SWORD, "§9");
        registerSb("SUGAR_CANE_HOE", "Sugar Cane Hoe", Items.DIAMOND_HOE, "§9");
        registerSb("POTATO_HOE", "Potato Hoe", Items.DIAMOND_HOE, "§9");
        registerSb("CARROT_HOE", "Carrot Hoe", Items.DIAMOND_HOE, "§9");
        registerSb("NETHER_WART_HOE", "Nether Wart Hoe", Items.DIAMOND_HOE, "§9");
        registerSb("MUSHROOM_COW_AXE", "Mushroom Cow Axe", Items.IRON_AXE, "§9");
        registerSb("ROD_OF_THE_SEA", "Rod of the Sea", Items.FISHING_ROD, "§6");
        registerSb("SHREDDER", "Shredder", Items.FISHING_ROD, "§6");

        registerSb("SUPER_BOOM_TNT", "Superboom TNT", Items.TNT, "§9");
        registerSb("SPIRIT_LEAP", "Spirit Leap", Items.ENDER_PEARL, "§9");
        registerSb("WARDEN_HELMET", "Warden Helmet", Items.PLAYER_HEAD, "§6");
        registerSb("CONJURING", "The Conjuring", Items.BOW, "§9");

        loadJson();
    }

    public static synchronized void loadJson() {
        overrides.clear();
        glowOverrides.clear();
        nameOverrides.clear();
        compiledNameCache.clear();
        replacementCache.clear();
        sbDataCache.clear();
        try {
            Files.createDirectories(CONFIG_DIR);
            Path toRead = null;
            if (Files.exists(CONFIG_PATH)) {
                toRead = CONFIG_PATH;
            } else if (Files.exists(OLD_CONFIG_PATH)) {
                toRead = OLD_CONFIG_PATH;
            }

            if (toRead != null) {
                String content = Files.readString(toRead, StandardCharsets.UTF_8);
                JsonObject obj = HttpClientUtils.parseJsonObject(content);
                if (obj == null) {
                    LOGGER.warn("Custom item models file {} is not a valid JSON object", toRead);
                    return;
                }
                JsonObject modelsObj = HttpClientUtils.getObjectOrNull(obj, "models");
                if (modelsObj != null) {
                    for (String key : modelsObj.keySet()) {
                        JsonElement val = modelsObj.get(key);
                        if (val != null && val.isJsonPrimitive()) {
                            overrides.put(key, val.getAsString());
                        }
                    }
                }
                JsonObject glowObj = HttpClientUtils.getObjectOrNull(obj, "glow");
                if (glowObj != null) {
                    for (String key : glowObj.keySet()) {
                        JsonElement val = glowObj.get(key);
                        if (val != null && val.isJsonPrimitive()) {
                            glowOverrides.put(key, val.getAsBoolean());
                        }
                    }
                }
                JsonObject namesObj = HttpClientUtils.getObjectOrNull(obj, "names");
                if (namesObj != null) {
                    for (String key : namesObj.keySet()) {
                        JsonElement val = namesObj.get(key);
                        if (val != null && val.isJsonPrimitive()) {
                            String sanitized = ItemNameFormatter.sanitizeInput(val.getAsString());
                            if (!sanitized.isEmpty()) {
                                nameOverrides.put(key, sanitized);
                            }
                        }
                    }
                }
                if (modelsObj == null) {
                    for (String key : obj.keySet()) {
                        JsonElement val = obj.get(key);
                        if (val != null && val.isJsonPrimitive() && !key.equals("description")) {
                            overrides.put(key, val.getAsString());
                        }
                    }
                }

                if (toRead.equals(OLD_CONFIG_PATH)) {
                    saveJson();
                }
            } else {
                if (!ModConfig.INSTANCE.itemModelOverrides.isEmpty()) {
                    overrides.putAll(ModConfig.INSTANCE.itemModelOverrides);
                    saveJson();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load custom item models", e);
        }
    }

    private static JsonObject buildJsonSnapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("description", "Wish Mod - Custom Item Models Configuration");
        JsonObject modelsObj = new JsonObject();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            modelsObj.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("models", modelsObj);

        JsonObject glowObj = new JsonObject();
        for (Map.Entry<String, Boolean> entry : glowOverrides.entrySet()) {
            glowObj.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("glow", glowObj);

        JsonObject namesObj = new JsonObject();
        for (Map.Entry<String, String> entry : nameOverrides.entrySet()) {
            namesObj.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("names", namesObj);
        return root;
    }

    public static void saveJson() {
        final String jsonSnapshot;
        synchronized (CustomModelManager.class) {
            jsonSnapshot = GSON.toJson(buildJsonSnapshot());
        }
        synchronized (SAVE_LOCK) {
            if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
                pendingSaveTask.cancel(false);
            }
            try {
                WishPaths.writeStringAtomically(CONFIG_PATH, jsonSnapshot);
            } catch (Exception e) {
                LOGGER.error("Failed to save custom item models", e);
            }
        }
    }

    public static void saveJsonAsync() {
        final String jsonSnapshot;
        synchronized (CustomModelManager.class) {
            jsonSnapshot = GSON.toJson(buildJsonSnapshot());
        }
        synchronized (SAVE_LOCK) {
            if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
                pendingSaveTask.cancel(false);
            }
            pendingSaveTask = SAVE_EXECUTOR.schedule(() -> {
                synchronized (SAVE_LOCK) {
                    try {
                        WishPaths.writeStringAtomically(CONFIG_PATH, jsonSnapshot);
                    } catch (Exception e) {
                        LOGGER.error("Failed to save custom item models asynchronously", e);
                    }
                }
            }, 50, TimeUnit.MILLISECONDS);
        }
    }

    public static SkyblockData extractSkyblockData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        SkyblockData cached;
        synchronized (sbDataCache) {
            cached = sbDataCache.get(stack);
        }
        if (cached != null) return cached;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            SkyblockData empty = new SkyblockData("", "");
            synchronized (sbDataCache) {
                sbDataCache.put(stack, empty);
            }
            return empty;
        }

        try {
            CompoundTag tag = customData.copyTag();
            String id = null;
            String uuid = null;

            if (tag.contains("ExtraAttributes")) {
                CompoundTag ea = tag.getCompoundOrEmpty("ExtraAttributes");
                id = ea.getStringOr("id", null);
                uuid = ea.getStringOr("uuid", null);
            }

            if (id == null || id.isEmpty()) {
                id = tag.getStringOr("id", null);
            }
            if (uuid == null || uuid.isEmpty()) {
                uuid = tag.getStringOr("uuid", null);
            }

            if (id == null || id.isEmpty() || uuid == null || uuid.isEmpty()) {
                for (String key : tag.keySet()) {
                    var sub = tag.get(key);
                    if (sub instanceof CompoundTag subComp) {
                        if (id == null || id.isEmpty()) {
                            id = subComp.getStringOr("id", null);
                        }
                        if (uuid == null || uuid.isEmpty()) {
                            uuid = subComp.getStringOr("uuid", null);
                        }
                    }
                }
            }

            if ((id != null && !id.isEmpty()) || (uuid != null && !uuid.isEmpty())) {
                SkyblockData result = new SkyblockData(id, uuid);
                synchronized (sbDataCache) {
                    sbDataCache.put(stack, result);
                }
                return result;
            }
        } catch (Exception ignored) {}

        SkyblockData empty = new SkyblockData("", "");
        synchronized (sbDataCache) {
            sbDataCache.put(stack, empty);
        }
        return empty;
    }

    public static String getSkyblockId(ItemStack stack) {
        SkyblockData data = extractSkyblockData(stack);
        return (data != null && !data.id.isEmpty()) ? data.id : null;
    }

    public static String getItemTargetKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        SkyblockData data = extractSkyblockData(stack);
        if (data != null && !data.uuid.isEmpty()) {
            return data.uuid;
        }
        if (data != null && !data.id.isEmpty()) {
            return data.id;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : "";
    }

    public static String getItemDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "Air";
        SkyblockData data = extractSkyblockData(stack);
        if (data != null && !data.id.isEmpty()) {
            SkyblockItem sb = SKYBLOCK_MAP.get(data.id.toUpperCase(Locale.ROOT));
            if (sb != null) return sb.colorPrefix + sb.name;
            return "§e" + data.id;
        }
        return getOriginalHoverName(stack).getString();
    }

    public static Component getOriginalHoverName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Component.empty();
        }
        boolean prev = bypassReplacement;
        bypassReplacement = true;
        try {
            return stack.getHoverName();
        } finally {
            bypassReplacement = prev;
        }
    }

    public static void requestToolHighlightRefresh() {
        toolHighlightRefreshPending = true;
    }

    public static boolean consumeToolHighlightRefresh() {
        if (!toolHighlightRefreshPending) {
            return false;
        }
        toolHighlightRefreshPending = false;
        return true;
    }

    public static ItemStack createItemStackForModel(String modelId, ItemStack fallbackStack) {
        if (modelId == null || modelId.isEmpty() || "default".equalsIgnoreCase(modelId)) return null;

        Item cachedItem = modelItemCache.get(modelId);
        if (cachedItem != null) {
            return new ItemStack(cachedItem);
        }

        SkyblockItem sb = SKYBLOCK_MAP.get(modelId.toUpperCase(Locale.ROOT));
        if (sb != null) {
            modelItemCache.put(modelId, sb.baseItem);
            return new ItemStack(sb.baseItem);
        }

        Identifier id = Identifier.tryParse(modelId.contains(":") ? modelId : "minecraft:" + modelId);
        if (id != null) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != null && item != Items.AIR) {
                modelItemCache.put(modelId, item);
                return new ItemStack(item);
            }

            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.getModelManager() != null) {
                    var model = mc.getModelManager().getItemModel(id);
                    if (model == null || model instanceof net.minecraft.client.renderer.item.MissingItemModel) {
                        return null;
                    }
                }
            } catch (Exception ignored) {}

            Item baseItem = (fallbackStack != null && !fallbackStack.isEmpty()) ? fallbackStack.getItem() : Items.DIAMOND_SWORD;
            ItemStack packStack = new ItemStack(baseItem);
            packStack.set(DataComponents.ITEM_MODEL, id);
            return packStack;
        }
        return null;
    }

    public static ItemStack getReplacement(ItemStack stack) {
        if (bypassReplacement) return stack;
        if (!ModConfig.INSTANCE.enableCustomItemModels) return stack;
        if (stack == null || stack.isEmpty()) return stack;
        if (overrides.isEmpty() && glowOverrides.isEmpty()) return stack;

        ItemStack cached;
        synchronized (replacementCache) {
            cached = replacementCache.get(stack);
        }
        if (cached != null) {
            if (cached == stack || cached.getCount() == stack.getCount()) {
                return cached;
            }
            ItemStack updated = cached.copy();
            updated.setCount(stack.getCount());
            synchronized (replacementCache) {
                replacementCache.put(stack, updated);
            }
            return updated;
        }

        try {
            String repKey = null;
            String matchedTargetKey = null;
            SkyblockData sbData = extractSkyblockData(stack);

            if (sbData != null && !sbData.uuid.isEmpty()) {
                repKey = getOverride(sbData.uuid);
                if (repKey != null) matchedTargetKey = sbData.uuid;
                if (repKey == null) {
                    repKey = getOverride("uuid:" + sbData.uuid);
                    if (repKey != null) matchedTargetKey = "uuid:" + sbData.uuid;
                }
            }

            if (repKey == null && sbData != null && !sbData.id.isEmpty()) {
                repKey = getOverride(sbData.id);
                if (repKey != null) matchedTargetKey = sbData.id;
            }

            if (repKey == null) {
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null) {
                    repKey = getOverride(id.toString());
                    if (repKey != null) matchedTargetKey = id.toString();
                    if (repKey == null) {
                        repKey = getOverride(id.getPath());
                        if (repKey != null) matchedTargetKey = id.getPath();
                    }
                }
            }

            if (matchedTargetKey == null) {
                if (sbData != null && !sbData.uuid.isEmpty() && getGlowOverride(sbData.uuid) != null) {
                    matchedTargetKey = sbData.uuid;
                } else if (sbData != null && !sbData.id.isEmpty() && getGlowOverride(sbData.id) != null) {
                    matchedTargetKey = sbData.id;
                } else {
                    Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && (getGlowOverride(id.toString()) != null || getGlowOverride(id.getPath()) != null)) {
                        matchedTargetKey = id.toString();
                    }
                }
            }

            ItemStack result = null;
            if (repKey != null && !repKey.isEmpty() && !"default".equalsIgnoreCase(repKey)) {
                ItemStack rep = createItemStackForModel(repKey, stack);
                if (rep != null && !rep.isEmpty()) {
                    result = new ItemStack(rep.getItem(), stack.getCount());
                    if (rep.has(DataComponents.ITEM_MODEL)) {
                        result.set(DataComponents.ITEM_MODEL, rep.get(DataComponents.ITEM_MODEL));
                    }
                }
            }

            if (result == null && matchedTargetKey != null && getGlowOverride(matchedTargetKey) != null) {
                result = stack.copy();
            }

            if (result != null) {
                Boolean glow = (matchedTargetKey != null) ? getGlowOverride(matchedTargetKey) : null;
                if (glow != null) {
                    result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glow);
                }
                synchronized (replacementCache) {
                    replacementCache.put(stack, result);
                }
                return result;
            }
            if (sbData != null && (!sbData.id.isEmpty() || !sbData.uuid.isEmpty())) {
                synchronized (replacementCache) {
                    replacementCache.put(stack, stack);
                }
            }
        } catch (Exception ignored) {}

        return stack;
    }

    private static <T> T getCaseInsensitive(Map<String, T> map, String key) {
        if (key == null) return null;
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return null;
        T val = map.get(trimmed);
        if (val == null) val = map.get(trimmed.toUpperCase(Locale.ROOT));
        if (val == null) val = map.get(trimmed.toLowerCase(Locale.ROOT));
        return val;
    }

    private static void removeCaseInsensitive(Map<String, ?> map, String key) {
        if (key == null) return;
        String trimmed = key.trim();
        if (trimmed.isEmpty()) return;
        map.remove(trimmed);
        map.remove(trimmed.toUpperCase(Locale.ROOT));
        map.remove(trimmed.toLowerCase(Locale.ROOT));
    }

    private static void clearCaches() {
        synchronized (replacementCache) {
            replacementCache.clear();
        }
        synchronized (sbDataCache) {
            sbDataCache.clear();
        }
        synchronized (compiledNameCache) {
            compiledNameCache.clear();
        }
    }

    public static synchronized void setOverride(String targetId, String modelId) {
        if (targetId == null || targetId.isEmpty()) return;
        String trimmedTarget = targetId.trim();

        if (modelId == null || modelId.isEmpty() || "default".equalsIgnoreCase(modelId)) {
            removeCaseInsensitive(overrides, trimmedTarget);
        } else {
            overrides.put(trimmedTarget, modelId.trim());
        }
        clearCaches();
        saveJsonAsync();
    }

    public static synchronized String getOverride(String targetId) {
        return getCaseInsensitive(overrides, targetId);
    }

    public static synchronized void removeOverride(String targetId) {
        if (targetId == null) return;
        removeCaseInsensitive(overrides, targetId);
        removeCaseInsensitive(glowOverrides, targetId);
        removeCaseInsensitive(nameOverrides, targetId);
        clearCaches();
        requestToolHighlightRefresh();
        saveJsonAsync();
    }

    public static synchronized Boolean getGlowOverride(String targetKey) {
        return getCaseInsensitive(glowOverrides, targetKey);
    }

    public static synchronized void setGlowOverride(String targetKey, Boolean glow) {
        if (targetKey == null || targetKey.isEmpty()) return;
        String trimmedKey = targetKey.trim();
        if (glow == null) {
            removeCaseInsensitive(glowOverrides, trimmedKey);
        } else {
            glowOverrides.put(trimmedKey, glow);
        }
        clearCaches();
        saveJsonAsync();
    }

    public static String getNameOverride(String targetKey) {
        return getCaseInsensitive(nameOverrides, targetKey);
    }

    public static synchronized void setNameOverride(String targetKey, String formattedName) {
        if (targetKey == null || targetKey.isEmpty()) return;
        String trimmedKey = targetKey.trim();
        String sanitized = ItemNameFormatter.sanitizeInput(formattedName);
        if (sanitized.isEmpty()) {
            removeCaseInsensitive(nameOverrides, trimmedKey);
        } else {
            nameOverrides.put(trimmedKey, sanitized);
        }
        clearCaches();
        requestToolHighlightRefresh();
        saveJsonAsync();
    }

    public static synchronized boolean hasAnyOverride(String targetKey) {
        if (targetKey == null || targetKey.isEmpty()) return false;
        String model = getOverride(targetKey);
        boolean hasModel = model != null && !model.isEmpty() && !"default".equalsIgnoreCase(model);
        boolean hasGlow = getGlowOverride(targetKey) != null;
        String customName = getNameOverride(targetKey);
        boolean hasName = customName != null && !customName.isEmpty();
        return hasModel || hasGlow || hasName;
    }

    public static Component getCompiledNameComponent(String rawFormattedName) {
        String sanitized = ItemNameFormatter.sanitizeInput(rawFormattedName);
        if (!ItemNameFormatter.hasVisibleText(sanitized)) {
            return null;
        }
        synchronized (compiledNameCache) {
            Component cached = compiledNameCache.get(sanitized);
            if (cached != null) {
                return cached;
            }
            Component built = ItemNameFormatter.toComponent(sanitized);
            compiledNameCache.put(sanitized, built);
            return built;
        }
    }

    public static Component getCustomNameComponent(ItemStack stack) {
        if (!ModConfig.INSTANCE.enableCustomItemModels || stack == null || stack.isEmpty() || nameOverrides.isEmpty()) {
            return null;
        }
        String rawName = resolveRawNameForStack(stack);
        if (rawName == null || rawName.isEmpty()) {
            return null;
        }
        return getCompiledNameComponent(rawName);
    }

    private static String resolveRawNameForStack(ItemStack stack) {
        SkyblockData sbData = extractSkyblockData(stack);
        if (sbData != null && !sbData.uuid.isEmpty()) {
            String val = getCaseInsensitive(nameOverrides, sbData.uuid);
            if (val != null) return val;
            val = getCaseInsensitive(nameOverrides, "uuid:" + sbData.uuid);
            if (val != null) return val;
        }
        if (sbData != null && !sbData.id.isEmpty()) {
            String val = getCaseInsensitive(nameOverrides, sbData.id);
            if (val != null) return val;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String val = getCaseInsensitive(nameOverrides, id.toString());
            if (val != null) return val;
            return getCaseInsensitive(nameOverrides, id.getPath());
        }
        return null;
    }
}
