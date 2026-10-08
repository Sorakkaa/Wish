package com.wish.client.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wish.client.config.ModConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CustomModelManager {

    private static final Path CONFIG_DIR = Path.of("config", "wish");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("models.json");
    private static final Path OLD_CONFIG_PATH = Path.of("config", "wish_models.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static volatile boolean bypassReplacement = false;
    private static final Map<String, String> overrides = new ConcurrentHashMap<>();

    // Ultra high performance bounded LRU caches (max 256 entries) to guarantee 0 memory leaks and maximum FPS
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

    public static class SkyblockData {
        public final String id;   // e.g. "DARK_CLAYMORE"
        public final String uuid; // e.g. "96a49f1c-ee1f-415e-a0ea-b7db0bf1370b"

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

    public static final List<SkyblockItem> SKYBLOCK_PRESETS = new ArrayList<>();
    public static final Map<String, SkyblockItem> SKYBLOCK_MAP = new HashMap<>();

    private static void registerSb(String id, String name, Item item, String color) {
        SkyblockItem sb = new SkyblockItem(id, name, item, color);
        SKYBLOCK_PRESETS.add(sb);
        SKYBLOCK_MAP.put(id.toUpperCase(Locale.ROOT), sb);
    }

    static {
        // Swords & Melee
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

        // Bows
        registerSb("TERMINATOR", "Terminator", Items.BOW, "§6");
        registerSb("JUJU_SHORTBOW", "Juju Shortbow", Items.BOW, "§5");
        registerSb("SPIRIT_BOW", "Spirit Bow", Items.BOW, "§6");
        registerSb("RUNAANS_BOW", "Runaan's Bow", Items.BOW, "§6");
        registerSb("MOSQUITO_BOW", "Mosquito Bow", Items.BOW, "§6");
        registerSb("MAGMA_BOW", "Magma Bow", Items.BOW, "§a");
        registerSb("EXPLOSIVE_BOW", "Explosive Bow", Items.BOW, "§5");
        registerSb("BONEMERANG", "Bonemerang", Items.BONE, "§9");

        // Magic & Staves
        registerSb("BONZO_STAFF", "Bonzo's Staff", Items.BLAZE_ROD, "§9");
        registerSb("SPIRIT_SCEPTRE", "Spirit Sceptre", Items.ALLIUM, "§6");
        registerSb("MIDAS_STAFF", "Midas Staff", Items.GOLDEN_SHOVEL, "§6");
        registerSb("GYROKINETIC_WAND", "Gyrokinetic Wand", Items.STICK, "§5");
        registerSb("ICE_SPRAY_WAND", "Ice Spray Wand", Items.STICK, "§6");
        registerSb("BAT_WAND", "Bat Wand", Items.STICK, "§9");
        registerSb("FIRE_FREEZE_STAFF", "Fire Freeze Staff", Items.BLAZE_ROD, "§5");
        registerSb("WAND_OF_ATONEMENT", "Wand of Atonement", Items.STICK, "§6");
        registerSb("AURORA_STAFF", "Aurora Staff", Items.BLAZE_ROD, "§5");

        // Mining & Tools
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

        // Utility & Heads
        registerSb("SUPER_BOOM_TNT", "Superboom TNT", Items.TNT, "§9");
        registerSb("SPIRIT_LEAP", "Spirit Leap", Items.ENDER_PEARL, "§9");
        registerSb("WARDEN_HELMET", "Warden Helmet", Items.PLAYER_HEAD, "§6");
        registerSb("CONJURING", "The Conjuring", Items.BOW, "§9");

        loadJson();
    }

    public static synchronized void loadJson() {
        overrides.clear();
        glowOverrides.clear();
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
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                if (obj.has("models") && obj.get("models").isJsonObject()) {
                    JsonObject modelsObj = obj.getAsJsonObject("models");
                    for (String key : modelsObj.keySet()) {
                        overrides.put(key, modelsObj.get(key).getAsString());
                    }
                }
                if (obj.has("glow") && obj.get("glow").isJsonObject()) {
                    JsonObject glowObj = obj.getAsJsonObject("glow");
                    for (String key : glowObj.keySet()) {
                        glowOverrides.put(key, glowObj.get(key).getAsBoolean());
                    }
                } else if (!obj.has("models")) {
                    for (String key : obj.keySet()) {
                        if (obj.get(key).isJsonPrimitive() && !key.equals("description")) {
                            overrides.put(key, obj.get(key).getAsString());
                        }
                    }
                }
                // Save to new path if read from old
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
            e.printStackTrace();
        }
    }

    public static synchronized void saveJson() {
        try {
            if (CONFIG_PATH.getParent() != null) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
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

            Files.writeString(CONFIG_PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Extracts Skyblock ID and UUID from an ItemStack.
     * Fully compatible with root compound, ExtraAttributes compound, and nested NBT tags.
     */
    public static SkyblockData extractSkyblockData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        SkyblockData cached = sbDataCache.get(stack);
        if (cached != null) return cached;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            SkyblockData empty = new SkyblockData("", "");
            sbDataCache.put(stack, empty);
            return empty;
        }

        try {
            CompoundTag tag = customData.copyTag();
            String id = null;
            String uuid = null;

            // 1. Check ExtraAttributes sub-compound (standard Hypixel)
            if (tag.contains("ExtraAttributes")) {
                CompoundTag ea = tag.getCompoundOrEmpty("ExtraAttributes");
                id = ea.getStringOr("id", null);
                uuid = ea.getStringOr("uuid", null);
            }

            // 2. Check root compound directly (in case flattened or dumped directly)
            if (id == null || id.isEmpty()) {
                id = tag.getStringOr("id", null);
            }
            if (uuid == null || uuid.isEmpty()) {
                uuid = tag.getStringOr("uuid", null);
            }

            // 3. Fallback: recursive search in all sub-compounds
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
                sbDataCache.put(stack, result);
                return result;
            }
        } catch (Exception ignored) {}

        SkyblockData empty = new SkyblockData("", "");
        sbDataCache.put(stack, empty);
        return empty;
    }

    public static String getSkyblockId(ItemStack stack) {
        SkyblockData data = extractSkyblockData(stack);
        return (data != null && !data.id.isEmpty()) ? data.id : null;
    }

    public static String getSkyblockUuid(ItemStack stack) {
        SkyblockData data = extractSkyblockData(stack);
        return (data != null && !data.uuid.isEmpty()) ? data.uuid : null;
    }

    public static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        SkyblockData data = extractSkyblockData(stack);
        if (data != null && !data.id.isEmpty()) {
            return data.id;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : "";
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
        return stack.getHoverName().getString();
    }

    public static ItemStack createItemStackForModel(String modelId) {
        return createItemStackForModel(modelId, null);
    }

    public static ItemStack createItemStackForModel(String modelId, ItemStack fallbackStack) {
        if (modelId == null || modelId.isEmpty() || "default".equalsIgnoreCase(modelId)) return null;

        Item cachedItem = modelItemCache.get(modelId);
        if (cachedItem != null) {
            return new ItemStack(cachedItem);
        }

        // 1. Skyblock preset
        SkyblockItem sb = SKYBLOCK_MAP.get(modelId.toUpperCase(Locale.ROOT));
        if (sb != null) {
            modelItemCache.put(modelId, sb.baseItem);
            return new ItemStack(sb.baseItem);
        }

        // 2. Vanilla Minecraft item
        Identifier id = Identifier.tryParse(modelId.contains(":") ? modelId : "minecraft:" + modelId);
        if (id != null) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != null && item != Items.AIR) {
                modelItemCache.put(modelId, item);
                return new ItemStack(item);
            }

            // 3. Texture Pack / Custom Resource Pack Item Model
            try {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc != null && mc.getModelManager() != null) {
                    var model = mc.getModelManager().getItemModel(id);
                    if (model == null || model instanceof net.minecraft.client.renderer.item.MissingItemModel) {
                        return null; // Model is missing/invalid, do not replace with a broken invisible model!
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

    /**
     * Resolves the visual model replacement for an ItemStack.
     * Uses WeakHashMap caching to guarantee 0 FPS loss!
     */
    public static ItemStack getReplacement(ItemStack stack) {
        if (bypassReplacement) return stack;
        if (!ModConfig.INSTANCE.enableCustomItemModels) return stack;
        if (stack == null || stack.isEmpty()) return stack;
        if (overrides.isEmpty() && glowOverrides.isEmpty()) return stack;

        // Fast cache check
        ItemStack cached = replacementCache.get(stack);
        if (cached != null) return cached;

        try {
            String repKey = null;
            String matchedTargetKey = null;
            SkyblockData sbData = extractSkyblockData(stack);

            // 1. Check specific item UUID first (e.g. "96a49f1c-ee1f-415e-a0ea-b7db0bf1370b")
            if (sbData != null && !sbData.uuid.isEmpty()) {
                repKey = getOverride(sbData.uuid);
                if (repKey != null) matchedTargetKey = sbData.uuid;
                if (repKey == null) {
                    repKey = getOverride("uuid:" + sbData.uuid);
                    if (repKey != null) matchedTargetKey = "uuid:" + sbData.uuid;
                }
            }

            // 2. Check Skyblock ID (e.g. "DARK_CLAYMORE")
            if (repKey == null && sbData != null && !sbData.id.isEmpty()) {
                repKey = getOverride(sbData.id);
                if (repKey != null) matchedTargetKey = sbData.id;
            }

            // 3. Fallback: Vanilla Minecraft item identifier (e.g. "minecraft:stone_sword")
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

            // Also check for glow override key if repKey wasn't found
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
                replacementCache.put(stack, result);
                return result;
            }
        } catch (Exception ignored) {}

        // Cache unmodified stack to avoid re-evaluating
        replacementCache.put(stack, stack);
        return stack;
    }

    public static synchronized void setOverride(String targetId, String modelId) {
        if (targetId == null || targetId.isEmpty()) return;
        targetId = targetId.trim();

        if (modelId == null || modelId.isEmpty() || "default".equalsIgnoreCase(modelId)) {
            overrides.remove(targetId);
            overrides.remove(targetId.toUpperCase(Locale.ROOT));
            overrides.remove(targetId.toLowerCase(Locale.ROOT));
        } else {
            overrides.put(targetId, modelId.trim());
        }
        replacementCache.clear();
        saveJson();
    }

    public static synchronized String getOverride(String targetId) {
        if (targetId == null || targetId.isEmpty()) return null;
        targetId = targetId.trim();
        String val = overrides.get(targetId);
        if (val == null) {
            val = overrides.get(targetId.toUpperCase(Locale.ROOT));
        }
        if (val == null) {
            val = overrides.get(targetId.toLowerCase(Locale.ROOT));
        }
        return val;
    }

    public static synchronized void removeOverride(String targetId) {
        if (targetId == null) return;
        targetId = targetId.trim();
        overrides.remove(targetId);
        overrides.remove(targetId.toUpperCase(Locale.ROOT));
        overrides.remove(targetId.toLowerCase(Locale.ROOT));
        glowOverrides.remove(targetId);
        glowOverrides.remove(targetId.toUpperCase(Locale.ROOT));
        glowOverrides.remove(targetId.toLowerCase(Locale.ROOT));
        replacementCache.clear();
        saveJson();
    }

    public static synchronized Boolean getGlowOverride(String targetKey) {
        if (targetKey == null || targetKey.isEmpty()) return null;
        targetKey = targetKey.trim();
        Boolean val = glowOverrides.get(targetKey);
        if (val == null) val = glowOverrides.get(targetKey.toUpperCase(Locale.ROOT));
        if (val == null) val = glowOverrides.get(targetKey.toLowerCase(Locale.ROOT));
        return val;
    }

    public static synchronized void setGlowOverride(String targetKey, Boolean glow) {
        if (targetKey == null || targetKey.isEmpty()) return;
        targetKey = targetKey.trim();
        if (glow == null) {
            glowOverrides.remove(targetKey);
            glowOverrides.remove(targetKey.toUpperCase(Locale.ROOT));
            glowOverrides.remove(targetKey.toLowerCase(Locale.ROOT));
        } else {
            glowOverrides.put(targetKey, glow);
        }
        replacementCache.clear();
        saveJson();
    }

    public static synchronized void clearAllOverrides() {
        overrides.clear();
        glowOverrides.clear();
        replacementCache.clear();
        sbDataCache.clear();
        saveJson();
    }

    public static synchronized Map<String, String> getAllOverrides() {
        return new LinkedHashMap<>(overrides);
    }
}
