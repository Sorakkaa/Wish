package com.wish.client.gui;

import com.wish.client.mixin.ModelManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WishModelCatalog {

    public static final class ModelOption {
        public final String id;
        public final String name;
        public final ItemStack stack;
        public final boolean isTexturePack;

        public ModelOption(String id, String name, ItemStack stack, boolean isTexturePack) {
            this.id = id;
            this.name = name;
            this.stack = stack;
            this.isTexturePack = isTexturePack;
        }

        public ModelOption(String id, String name, ItemStack stack) {
            this(id, name, stack, false);
        }
    }

    private static final List<ModelOption> ALL_OPTIONS = new ArrayList<>();
    private static final List<ModelOption> PACK_OPTIONS = new ArrayList<>();
    private static boolean packModelsLoaded = false;
    private static final ModelOption CUSTOM_INPUT_OPTION = new ModelOption(
            "wish:custom_pack_input",
            "\u00A7d+ Enter Pack Model ID",
            new ItemStack(Items.NAME_TAG),
            true
    );

    static {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }

            String path = id.getPath().toLowerCase(Locale.ROOT);
            ItemStack stack = new ItemStack(item);
            String name = com.wish.client.features.CustomModelManager.getOriginalHoverName(stack).getString();
            if (name == null || name.isEmpty()) {
                name = path;
            }

            ALL_OPTIONS.add(new ModelOption(id.toString(), name, stack));
        }
    }

    private WishModelCatalog() {
    }

    public static void ensurePackModelsLoaded() {
        if (!packModelsLoaded) {
            loadTexturePackModels();
        }
    }

    public static synchronized void loadTexturePackModels() {
        PACK_OPTIONS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getModelManager() == null) {
            return;
        }

        Set<String> seenIds = new HashSet<>();
        Set<String> vanillaKeys = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier vid = BuiltInRegistries.ITEM.getKey(item);
            if (vid != null) {
                vanillaKeys.add(vid.toString());
                vanillaKeys.add(vid.getPath());
            }
        }

        try {
            var bakedMap = ((ModelManagerAccessor) mc.getModelManager()).getBakedItemStackModels();
            if (bakedMap != null) {
                for (Map.Entry<Identifier, ItemModel> entry : bakedMap.entrySet()) {
                    Identifier resId = entry.getKey();
                    ItemModel model = entry.getValue();
                    if (model == null || model instanceof MissingItemModel) {
                        continue;
                    }

                    String ns = resId.getNamespace();
                    String path = resId.getPath();
                    if (path.contains("_pulling") || path.endsWith("_cast") || path.contains("_firework")
                            || path.contains("_arrow") || path.contains("_charged") || path.contains("_standby")) {
                        continue;
                    }
                    if ("minecraft".equals(ns) && vanillaKeys.contains(path)) {
                        continue;
                    }

                    String fullModelId = resId.toString();
                    if (!seenIds.add(fullModelId)) {
                        continue;
                    }

                    String rawName = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
                    String displayName = rawName.isEmpty()
                            ? fullModelId
                            : Character.toUpperCase(rawName.charAt(0)) + rawName.substring(1);

                    ItemStack iconStack = new ItemStack(Items.DIAMOND_SWORD);
                    iconStack.set(DataComponents.ITEM_MODEL, resId);

                    PACK_OPTIONS.add(new ModelOption(fullModelId, displayName + " \u00A77(" + ns + ")", iconStack, true));
                }
            }
        } catch (Exception ignored) {
        }

        PACK_OPTIONS.sort(Comparator.comparing(a -> a.name));
        packModelsLoaded = true;
    }

    public static void populateFilteredOptions(String searchQuery, ItemStack heldStack, List<ModelOption> out) {
        out.clear();
        String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);

        if (q.isEmpty()) {
            out.addAll(ALL_OPTIONS);
            out.addAll(PACK_OPTIONS);
            out.add(CUSTOM_INPUT_OPTION);
            return;
        }

        if (q.contains(":") || q.contains("/")) {
            Identifier typedId = Identifier.tryParse(q.contains(":") ? q : "minecraft:" + q);
            if (typedId != null) {
                var mm = Minecraft.getInstance().getModelManager();
                if (mm != null) {
                    var model = mm.getItemModel(typedId);
                    if (model != null && !(model instanceof MissingItemModel)) {
                        ItemStack previewStack = new ItemStack(heldStack.getItem());
                        previewStack.set(DataComponents.ITEM_MODEL, typedId);
                        out.add(new ModelOption(
                                typedId.toString(),
                                "\u00A7d[Custom Pack ID] \u00A7f" + typedId,
                                previewStack,
                                true
                        ));
                    }
                }
            }
        }

        for (ModelOption opt : ALL_OPTIONS) {
            if (opt.name.toLowerCase(Locale.ROOT).contains(q) || opt.id.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(opt);
            }
        }
        for (ModelOption opt : PACK_OPTIONS) {
            if (opt.name.toLowerCase(Locale.ROOT).contains(q) || opt.id.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(opt);
            }
        }
        if ("texture pack model enter id".contains(q)) {
            out.add(CUSTOM_INPUT_OPTION);
        }
    }
}
