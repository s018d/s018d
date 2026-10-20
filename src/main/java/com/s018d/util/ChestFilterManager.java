package com.s018d.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChestFilterManager {
    public static final int FILTER_ALL = 0;
    public static final int FILTER_SPECIFIC = 1;
    public static final int FILTER_OUTPUT = 2;
    public static final int FILTER_BLACKLIST = 3;

    private static final String TAG_ROOT = "s018d_filter";
    private static final String TAG_TYPE = "type";
    private static final String TAG_ITEMS = "items";

    private static final Map<BlockPos, FilterData> serverFilters = new HashMap<>();
    private static final Map<BlockPos, FilterData> clientFilters = new HashMap<>();

    public static class FilterData {
        public int filterType = FILTER_ALL;
        public List<String> allowedIds = new ArrayList<>();
        public List<String> blockedIds = new ArrayList<>();

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_TYPE, filterType);
            ListTag allowed = new ListTag();
            for (String id : allowedIds) {
                allowed.add(StringTag.valueOf(id));
            }
            tag.put("allowed", allowed);
            ListTag blocked = new ListTag();
            for (String id : blockedIds) {
                blocked.add(StringTag.valueOf(id));
            }
            tag.put("blocked", blocked);
            return tag;
        }

        public static FilterData fromTag(CompoundTag tag) {
            FilterData data = new FilterData();
            data.filterType = tag.getInt(TAG_TYPE);
            for (Tag element : tag.getList("allowed", Tag.TAG_STRING)) {
                data.allowedIds.add(element.getAsString());
            }
            for (Tag element : tag.getList("blocked", Tag.TAG_STRING)) {
                data.blockedIds.add(element.getAsString());
            }
            return data;
        }
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public static void setFilter(BlockPos pos, int filterType, String itemId, Level level) {
        FilterData data = readStored(pos, level);
        data.filterType = filterType;
        if (itemId != null && !itemId.isEmpty() && !itemId.equals("minecraft:air")) {
            if (filterType == FILTER_BLACKLIST) {
                data.blockedIds.clear();
                data.blockedIds.add(itemId);
            } else if (filterType == FILTER_SPECIFIC) {
                data.allowedIds.clear();
                data.allowedIds.add(itemId);
            }
        }
        serverFilters.put(pos.immutable(), data);
        writeStored(pos, level, data);
    }

    public static FilterData getFilter(BlockPos pos) {
        return serverFilters.getOrDefault(pos.immutable(), new FilterData());
    }

    private static FilterData readStored(BlockPos pos, Level level) {
        if (level != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null && blockEntity.getPersistentData().contains(TAG_ROOT)) {
                return FilterData.fromTag(blockEntity.getPersistentData().getCompound(TAG_ROOT));
            }
        }
        return serverFilters.getOrDefault(pos.immutable(), new FilterData());
    }

    private static void writeStored(BlockPos pos, Level level, FilterData data) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.getPersistentData().put(TAG_ROOT, data.toTag());
            blockEntity.setChanged();
        }
    }

    public static boolean canInsert(BlockPos pos, ItemStack stack, Level level) {
        FilterData filter = readStored(pos, level);
        String id = itemId(stack);

        switch (filter.filterType) {
            case FILTER_ALL -> {
                return true;
            }
            case FILTER_SPECIFIC -> {
                return filter.allowedIds.contains(id);
            }
            case FILTER_OUTPUT -> {
                return true;
            }
            case FILTER_BLACKLIST -> {
                return !filter.blockedIds.contains(id);
            }
            default -> {
                return true;
            }
        }
    }

    /** Backwards-compatible check without world access (memory only). */
    public static boolean canInsert(BlockPos pos, ItemStack stack) {
        return canInsert(pos, stack, null);
    }

    public static void setClientFilter(BlockPos pos, int filterType, String itemId) {
        FilterData data = clientFilters.getOrDefault(pos.immutable(), new FilterData());
        data.filterType = filterType;
        if (itemId != null && !itemId.isEmpty()) {
            if (filterType == FILTER_BLACKLIST) {
                data.blockedIds.clear();
                data.blockedIds.add(itemId);
            } else if (filterType == FILTER_SPECIFIC) {
                data.allowedIds.clear();
                data.allowedIds.add(itemId);
            }
        }
        clientFilters.put(pos.immutable(), data);
    }

    public static FilterData getClientFilter(BlockPos pos) {
        return clientFilters.getOrDefault(pos.immutable(), new FilterData());
    }

    public static void clearFilter(BlockPos pos) {
        serverFilters.remove(pos.immutable());
        clientFilters.remove(pos.immutable());
    }

    /** Finds the closest container within radius whose filter accepts the stack. */
    public static BlockPos findChestFor(ServerLevel level, BlockPos center, ItemStack stack, int radius) {
        return findChestFor(level, center, null, stack, radius, false);
    }

    /** Finds a strictly-filtered chest (specific/output), excluding the source. */
    public static BlockPos findFilteredChest(ServerLevel level, BlockPos source, ItemStack stack, int radius) {
        return findChestFor(level, source, source, stack, radius, true);
    }

    private static BlockPos findChestFor(ServerLevel level, BlockPos center, BlockPos exclude, ItemStack stack, int radius, boolean strict) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-radius, -radius / 3, -radius),
                center.offset(radius, radius / 3, radius))) {
            BlockPos pos = candidate.immutable();
            if (exclude != null && pos.equals(exclude)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof Container)) {
                continue;
            }
            if (strict) {
                int type = readStored(pos, level).filterType;
                if (type != FILTER_SPECIFIC && type != FILTER_OUTPUT) {
                    continue;
                }
            }
            if (canInsert(pos, stack, level)) {
                return pos;
            }
        }
        return null;
    }

    public static boolean insertInto(Container container, ItemStack stack) {
        boolean moved = false;
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                container.setItem(i, stack.copy());
                stack.setCount(0);
                moved = true;
            } else if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(move);
                stack.shrink(move);
                moved = true;
            }
        }
        if (moved) {
            container.setChanged();
        }
        return moved;
    }

    /**
     * Stores harvested drops directly into nearby filtered chests.
     * Anything that fits nowhere drops on the ground as usual.
     */
    public static void depositNear(ServerLevel level, BlockPos center, List<ItemStack> drops, int radius) {
        depositNear(level, center, drops, radius, BlockPos.ZERO);
    }

    /**
     * Same, but tries the worker's assigned output chest first so the
     * player decides exactly where things go.
     */
    public static void depositNear(ServerLevel level, BlockPos center, List<ItemStack> drops, int radius, BlockPos preferred) {
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!preferred.equals(BlockPos.ZERO)) {
                BlockEntity preferredEntity = level.getBlockEntity(preferred);
                if (preferredEntity instanceof Container preferredContainer
                    && canInsert(preferred, stack, level)) {
                    insertInto(preferredContainer, stack);
                }
            }
            if (stack.isEmpty()) {
                continue;
            }
            BlockPos chest = findChestFor(level, center, stack, radius);
            BlockEntity blockEntity = chest == null ? null : level.getBlockEntity(chest);
            if (blockEntity instanceof Container container) {
                insertInto(container, stack);
            }
            if (!stack.isEmpty()) {
                Block.popResource(level, center, stack);
            }
        }
    }
}
