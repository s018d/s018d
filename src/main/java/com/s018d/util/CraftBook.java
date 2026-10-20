package com.s018d.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed recipe book for the crafter worker. Materials are pulled from
 * nearby chests and results go to filtered chests. JEI-driven free
 * recipe choice is a later step.
 */
public class CraftBook {
    public record Ingredient(TagKey<Item> tag, Item item, int count) {
        public static Ingredient of(TagKey<Item> tag, int count) {
            return new Ingredient(tag, null, count);
        }

        public static Ingredient of(Item item, int count) {
            return new Ingredient(null, item, count);
        }

        boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (item != null) {
                return stack.is(item);
            }
            return tag != null && stack.is(tag);
        }

        public boolean test(ItemStack stack) {
            return matches(stack);
        }

        public Item representativeItem() {
            if (item != null) {
                return item;
            }
            if (tag == null) {
                return Items.AIR;
            }
            return BuiltInRegistries.ITEM.getTag(tag)
                .flatMap(holders -> holders.stream().findFirst())
                .map(holder -> holder.value())
                .orElse(Items.AIR);
        }
    }

    public record Entry(String id, ItemStack result, List<Ingredient> ingredients) {
    }

    private static final Map<String, Entry> RECIPES = new LinkedHashMap<>();

    static {
        register("stick", new ItemStack(Items.STICK, 4), List.of(Ingredient.of(ItemTags.PLANKS, 2)));
        register("torch", new ItemStack(Items.TORCH, 4),
            List.of(Ingredient.of(ItemTags.COALS, 1), Ingredient.of(Items.STICK, 1)));
        register("oak_planks", new ItemStack(Items.OAK_PLANKS, 4), List.of(Ingredient.of(ItemTags.LOGS, 1)));
        register("bread", new ItemStack(Items.BREAD, 1),
            List.of(Ingredient.of(Items.WHEAT, 3)));
        register("crafting_table", new ItemStack(Items.CRAFTING_TABLE, 1),
            List.of(Ingredient.of(ItemTags.PLANKS, 4)));
        register("chest", new ItemStack(Items.CHEST, 1), List.of(Ingredient.of(ItemTags.PLANKS, 8)));
        register("furnace", new ItemStack(Items.FURNACE, 1),
            List.of(Ingredient.of(Items.COBBLESTONE, 8)));
        register("bucket", new ItemStack(Items.BUCKET, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 3)));
        register("iron_pickaxe", new ItemStack(Items.IRON_PICKAXE, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 3), Ingredient.of(Items.STICK, 2)));
        register("iron_axe", new ItemStack(Items.IRON_AXE, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 3), Ingredient.of(Items.STICK, 2)));
        register("iron_sword", new ItemStack(Items.IRON_SWORD, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 2), Ingredient.of(Items.STICK, 1)));
        register("shears", new ItemStack(Items.SHEARS, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 2)));
        register("iron_hoe", new ItemStack(Items.IRON_HOE, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 2), Ingredient.of(Items.STICK, 2)));
        register("iron_shovel", new ItemStack(Items.IRON_SHOVEL, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 1), Ingredient.of(Items.STICK, 2)));
        register("shield", new ItemStack(Items.SHIELD, 1),
            List.of(Ingredient.of(ItemTags.PLANKS, 6), Ingredient.of(Items.IRON_INGOT, 1)));
        register("iron_helmet", new ItemStack(Items.IRON_HELMET, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 5)));
        register("iron_chestplate", new ItemStack(Items.IRON_CHESTPLATE, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 8)));
        register("iron_leggings", new ItemStack(Items.IRON_LEGGINGS, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 7)));
        register("iron_boots", new ItemStack(Items.IRON_BOOTS, 1),
            List.of(Ingredient.of(Items.IRON_INGOT, 4)));
        register("wooden_pickaxe", new ItemStack(Items.WOODEN_PICKAXE, 1),
            List.of(Ingredient.of(ItemTags.PLANKS, 3), Ingredient.of(Items.STICK, 2)));
        register("wooden_axe", new ItemStack(Items.WOODEN_AXE, 1),
            List.of(Ingredient.of(ItemTags.PLANKS, 3), Ingredient.of(Items.STICK, 2)));
        register("wooden_shovel", new ItemStack(Items.WOODEN_SHOVEL, 1),
            List.of(Ingredient.of(ItemTags.PLANKS, 1), Ingredient.of(Items.STICK, 2)));
        register("wooden_hoe", new ItemStack(Items.WOODEN_HOE, 1),
            List.of(Ingredient.of(ItemTags.PLANKS, 2), Ingredient.of(Items.STICK, 2)));
        register("wooden_sword", new ItemStack(Items.WOODEN_SWORD, 1),
            List.of(Ingredient.of(ItemTags.PLANKS, 2), Ingredient.of(Items.STICK, 1)));
        register("stone_pickaxe", new ItemStack(Items.STONE_PICKAXE, 1),
            List.of(Ingredient.of(Items.COBBLESTONE, 3), Ingredient.of(Items.STICK, 2)));
        register("stone_axe", new ItemStack(Items.STONE_AXE, 1),
            List.of(Ingredient.of(Items.COBBLESTONE, 3), Ingredient.of(Items.STICK, 2)));
        register("stone_shovel", new ItemStack(Items.STONE_SHOVEL, 1),
            List.of(Ingredient.of(Items.COBBLESTONE, 1), Ingredient.of(Items.STICK, 2)));
        register("stone_hoe", new ItemStack(Items.STONE_HOE, 1),
            List.of(Ingredient.of(Items.COBBLESTONE, 2), Ingredient.of(Items.STICK, 2)));
        register("stone_sword", new ItemStack(Items.STONE_SWORD, 1),
            List.of(Ingredient.of(Items.COBBLESTONE, 2), Ingredient.of(Items.STICK, 1)));
        register("fishing_rod", new ItemStack(Items.FISHING_ROD, 1),
            List.of(Ingredient.of(Items.STICK, 3), Ingredient.of(Items.STRING, 2)));
        register("bow", new ItemStack(Items.BOW, 1),
            List.of(Ingredient.of(Items.STICK, 3), Ingredient.of(Items.STRING, 3)));
        register("arrow", new ItemStack(Items.ARROW, 4),
            List.of(Ingredient.of(Items.FLINT, 1), Ingredient.of(Items.STICK, 1),
                Ingredient.of(Items.FEATHER, 1)));
    }

    private static void register(String id, ItemStack result, List<Ingredient> ingredients) {
        RECIPES.put(id, new Entry(id, result, ingredients));
    }

    public static List<Entry> all() {
        return new ArrayList<>(RECIPES.values());
    }

    public static Entry get(String id) {
        return RECIPES.get(id);
    }

    /** Tries to craft once. Returns true when a result was produced. */
    public static boolean craftOnce(ServerLevel level, BlockPos center, String recipeId) {
        Entry entry = RECIPES.get(recipeId);
        if (entry == null) {
            return false;
        }
        if (!takeIngredients(level, center, entry.ingredients())) {
            return false;
        }
        ChestFilterManager.depositNear(level, center, List.of(entry.result().copy()), 12);
        return true;
    }

    /**
     * Crafts any shapeless recipe known to the game by result item id.
     * Order-free recipes match directly against chest contents.
     */
    public static boolean craftShapeless(ServerLevel level, BlockPos center, String resultId) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ShapelessRecipe recipe)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (key == null || !key.toString().equals(resultId)) {
                continue;
            }
            List<net.minecraft.world.item.crafting.Ingredient> needed = new ArrayList<>(recipe.getIngredients());
            if (!takeVanilla(level, center, needed)) {
                return false;
            }
            ChestFilterManager.depositNear(level, center, List.of(result.copy()), 12);
            return true;
        }
        return false;
    }

    private static boolean takeVanilla(ServerLevel level, BlockPos center,
        List<net.minecraft.world.item.crafting.Ingredient> needed) {
        for (net.minecraft.world.item.crafting.Ingredient ingredient : needed) {
            if (countVanilla(level, center, ingredient) < 1) {
                return false;
            }
        }
        for (net.minecraft.world.item.crafting.Ingredient ingredient : needed) {
            boolean taken = false;
            for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
                BlockEntity blockEntity = level.getBlockEntity(candidate.immutable());
                if (!(blockEntity instanceof Container container)) {
                    continue;
                }
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack slot = container.getItem(i);
                    if (!slot.isEmpty() && ingredient.test(slot)) {
                        slot.shrink(1);
                        container.setChanged();
                        taken = true;
                        break;
                    }
                }
                if (taken) {
                    break;
                }
            }
            if (!taken) {
                return false;
            }
        }
        return true;
    }

    private static int countVanilla(ServerLevel level, BlockPos center,
        net.minecraft.world.item.crafting.Ingredient ingredient) {
        int total = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
            BlockEntity blockEntity = level.getBlockEntity(candidate.immutable());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && ingredient.test(slot)) {
                    total += slot.getCount();
                }
            }
        }
        return total;
    }

    private static boolean takeIngredients(ServerLevel level, BlockPos center, List<Ingredient> ingredients) {
        // First pass: verify everything exists.
        for (Ingredient ingredient : ingredients) {
            if (countMatching(level, center, ingredient) < ingredient.count()) {
                return false;
            }
        }
        // Second pass: consume.
        for (Ingredient ingredient : ingredients) {
            int left = ingredient.count();
            outer:
            for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
                BlockPos pos = candidate.immutable();
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (!(blockEntity instanceof Container container)) {
                    continue;
                }
                for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
                    ItemStack slot = container.getItem(i);
                    if (ingredient.matches(slot)) {
                        int take = Math.min(slot.getCount(), left);
                        slot.shrink(take);
                        left -= take;
                        container.setChanged();
                        if (left <= 0) {
                            break outer;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static int countMatching(ServerLevel level, BlockPos center, Ingredient ingredient) {
        int total = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
            BlockEntity blockEntity = level.getBlockEntity(candidate.immutable());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (ingredient.matches(slot)) {
                    total += slot.getCount();
                }
            }
        }
        return total;
    }

    /** Maps a harvested log block to its matching planks for the planks recipe. */
    public static Block planksFor(Block log) {
        if (log == Blocks.SPRUCE_LOG) {
            return Blocks.SPRUCE_PLANKS;
        }
        if (log == Blocks.BIRCH_LOG) {
            return Blocks.BIRCH_PLANKS;
        }
        return Blocks.OAK_PLANKS;
    }
}
