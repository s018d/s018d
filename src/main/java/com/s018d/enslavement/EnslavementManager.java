package com.s018d.enslavement;

import com.s018d.entity.ServantEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores the master/servant relationship on any {@link Mob} using the
 * entity's persistent data, so it survives reloads and world restarts.
 * Also owns the small servant bag used for tools and gathered items.
 */
public final class EnslavementManager {
    private static final String OWNER = "s018d_enslaved_owner";
    private static final String COMMAND = "s018d_enslaved_command";
    private static final String HOME = "s018d_enslaved_home";
    private static final String STASH = "s018d_servant_stash";
    private static final String STASH_REV = "s018d_servant_stash_rev";
    private static final String STAT_BLOCKS = "s018d_stat_blocks";
    private static final String STAT_ITEMS = "s018d_stat_items";
    private static final String STAT_KILLS = "s018d_stat_kills";
    private static final String WORK_LIMIT = "s018d_work_limit";
    private static final String WORK_DONE = "s018d_work_done";
    private static final String DEPOSIT_POS = "s018d_deposit_pos";
    private static final String DEPOSIT_DIM = "s018d_deposit_dim";

    public static final int STASH_SIZE = 9;

    private EnslavementManager() {
    }

    public static boolean isEnslaved(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        return tag.hasUUID(OWNER) && tag.contains(COMMAND);
    }

    public static UUID ownerId(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        return tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
    }

    public static boolean isOwner(Mob mob, Player player) {
        return player != null && player.getUUID().equals(ownerId(mob));
    }

    public static EnslavedCommand command(Mob mob) {
        return EnslavedCommand.byName(mob.getPersistentData().getString(COMMAND));
    }

    public static BlockPos home(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        if (!tag.contains(HOME)) {
            return mob.blockPosition();
        }
        return BlockPos.of(tag.getLong(HOME));
    }

    public static void setHome(Mob mob, BlockPos pos) {
        mob.getPersistentData().putLong(HOME, pos.asLong());
    }

    // ------------------------------------------------------------------
    // Lifetime work statistics (shown in the order menu)
    // ------------------------------------------------------------------

    public static int statBlocks(Mob mob) {
        return mob.getPersistentData().getInt(STAT_BLOCKS);
    }

    public static int statItems(Mob mob) {
        return mob.getPersistentData().getInt(STAT_ITEMS);
    }

    public static int statKills(Mob mob) {
        return mob.getPersistentData().getInt(STAT_KILLS);
    }

    public static void addWorkedBlocks(Mob mob, int amount) {
        CompoundTag tag = mob.getPersistentData();
        tag.putInt(STAT_BLOCKS, tag.getInt(STAT_BLOCKS) + amount);
    }

    public static void addGatheredItems(Mob mob, int amount) {
        CompoundTag tag = mob.getPersistentData();
        tag.putInt(STAT_ITEMS, tag.getInt(STAT_ITEMS) + amount);
        tag.putInt(WORK_DONE, tag.getInt(WORK_DONE) + amount);
    }

    public static int workLimit(Mob mob) {
        return mob.getPersistentData().getInt(WORK_LIMIT);
    }

    public static void setWorkLimit(Mob mob, int limit) {
        mob.getPersistentData().putInt(WORK_LIMIT, Math.max(limit, 0));
    }

    public static int workDone(Mob mob) {
        return mob.getPersistentData().getInt(WORK_DONE);
    }

    public static void resetWorkDone(Mob mob) {
        mob.getPersistentData().putInt(WORK_DONE, 0);
    }

    /** True when the servant already gathered its allowed amount of items. */
    public static boolean workLimitReached(Mob mob) {
        int limit = workLimit(mob);
        return limit > 0 && mob.getPersistentData().getInt(WORK_DONE) >= limit;
    }

    private static final String AUTO_PICKUP = "s018d_auto_pickup";
    private static final String AUTO_DEPOSIT = "s018d_auto_deposit";
    private static final String COMMAND_QUEUE = "s018d_command_queue";
    private static final String GUARD_POS = "s018d_guard_pos";
    private static final String CRAFT_QUEUE = "s018d_craft_queue";
    private static final String FETCH_ITEM = "s018d_fetch_item";
    private static final String FETCH_FOR = "s018d_fetch_for";
    private static final String FETCH_FOR_ID = "s018d_fetch_for_id";
    private static final String FETCH_COUNT = "s018d_fetch_count";
    private static final String FETCH_PREV = "s018d_fetch_prev";

    public static boolean autoPickupEnabled(Mob mob) {
        var tag = mob.getPersistentData();
        return !tag.contains(AUTO_PICKUP) || tag.getBoolean(AUTO_PICKUP);
    }

    public static boolean toggleAutoPickup(Mob mob) {
        boolean next = !autoPickupEnabled(mob);
        mob.getPersistentData().putBoolean(AUTO_PICKUP, next);
        return next;
    }

    /** When the work limit is reached the servant can walk to its chest first. */
    public static boolean autoDepositEnabled(Mob mob) {
        var tag = mob.getPersistentData();
        return !tag.contains(AUTO_DEPOSIT) || tag.getBoolean(AUTO_DEPOSIT);
    }

    public static boolean toggleAutoDeposit(Mob mob) {
        boolean next = !autoDepositEnabled(mob);
        mob.getPersistentData().putBoolean(AUTO_DEPOSIT, next);
        return next;
    }

    public static BlockPos guardPost(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        return tag.contains(GUARD_POS) ? BlockPos.of(tag.getLong(GUARD_POS)) : home(mob);
    }

    public static void setGuardPost(Mob mob, BlockPos pos) {
        mob.getPersistentData().putLong(GUARD_POS, pos.asLong());
    }

    public static void clearGuardPost(Mob mob) {
        mob.getPersistentData().remove(GUARD_POS);
    }

    public static List<EnslavedCommand> commandQueue(Mob mob) {
        String raw = mob.getPersistentData().getString(COMMAND_QUEUE);
        List<EnslavedCommand> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                result.add(EnslavedCommand.byName(part.trim()));
            }
        }
        return result;
    }

    private static void saveQueue(Mob mob, List<EnslavedCommand> queue) {
        List<String> names = new ArrayList<>();
        for (EnslavedCommand command : queue) {
            names.add(command.name());
        }
        mob.getPersistentData().putString(COMMAND_QUEUE, String.join(",", names));
    }

    public static void queueCommand(Mob mob, EnslavedCommand command) {
        List<EnslavedCommand> queue = commandQueue(mob);
        if (queue.size() >= 32) {
            queue.remove(0);
        }
        queue.add(command);
        saveQueue(mob, queue);
    }

    public static int queueSize(Mob mob) {
        return commandQueue(mob).size();
    }

    public static void clearQueue(Mob mob) {
        mob.getPersistentData().remove(COMMAND_QUEUE);
    }

    /** Starts the next queued job. Returns false when the queue is empty. */
    public static boolean advanceQueue(Mob mob) {
        List<EnslavedCommand> queue = commandQueue(mob);
        while (!queue.isEmpty()) {
            EnslavedCommand next = queue.remove(0);
            saveQueue(mob, queue);
            if (next != EnslavedCommand.RELEASE && next != EnslavedCommand.GUARD_POST) {
                setCommand(mob, next);
                return true;
            }
        }
        clearQueue(mob);
        return false;
    }

    // ------------------------------------------------------------------
    // Craftsman requests and courier deliveries
    // ------------------------------------------------------------------

    public static List<String> craftRequests(Mob mob) {
        String raw = mob.getPersistentData().getString(CRAFT_QUEUE);
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }

    private static void saveCraftRequests(Mob mob, List<String> requests) {
        mob.getPersistentData().putString(CRAFT_QUEUE, String.join(",", requests));
    }

    public static void addCraftRequest(Mob mob, String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        List<String> requests = craftRequests(mob);
        if (requests.size() >= 32) {
            requests.remove(0);
        }
        requests.add(recipeId.trim());
        saveCraftRequests(mob, requests);
    }

    public static String peekCraftRequest(Mob mob) {
        List<String> requests = craftRequests(mob);
        return requests.isEmpty() ? null : requests.get(0);
    }

    public static void popCraftRequest(Mob mob) {
        List<String> requests = craftRequests(mob);
        if (!requests.isEmpty()) {
            requests.remove(0);
        }
        saveCraftRequests(mob, requests);
    }

    public static void clearCraftRequests(Mob mob) {
        mob.getPersistentData().remove(CRAFT_QUEUE);
    }

    public static void requestFetch(Mob courier, String itemId, int count, Mob requester) {
        if (courier == null || requester == null || itemId == null || itemId.isBlank()) {
            return;
        }
        CompoundTag tag = courier.getPersistentData();
        tag.putString(FETCH_ITEM, itemId);
        tag.putUUID(FETCH_FOR, requester.getUUID());
        tag.putInt(FETCH_FOR_ID, requester.getId());
        tag.putInt(FETCH_COUNT, Math.max(1, count));
        tag.putString(FETCH_PREV, command(courier).name());
        setCommand(courier, EnslavedCommand.FETCH);
    }

    public static String fetchItem(Mob mob) {
        String item = mob.getPersistentData().getString(FETCH_ITEM);
        return item == null || item.isBlank() ? null : item;
    }

    public static int fetchCount(Mob mob) {
        return Math.max(1, mob.getPersistentData().getInt(FETCH_COUNT));
    }

    public static UUID fetchRequester(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        return tag.hasUUID(FETCH_FOR) ? tag.getUUID(FETCH_FOR) : null;
    }

    public static int fetchRequesterId(Mob mob) {
        return mob.getPersistentData().getInt(FETCH_FOR_ID);
    }

    public static EnslavedCommand fetchPrevious(Mob mob) {
        String raw = mob.getPersistentData().getString(FETCH_PREV);
        return raw == null || raw.isBlank() ? null : EnslavedCommand.byName(raw);
    }

    public static boolean isFetching(Mob mob) {
        return fetchItem(mob) != null;
    }

    public static void clearFetch(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        tag.remove(FETCH_ITEM);
        tag.remove(FETCH_FOR);
        tag.remove(FETCH_FOR_ID);
        tag.remove(FETCH_COUNT);
        tag.remove(FETCH_PREV);
    }

    public static void setDepositTarget(Mob mob, BlockPos pos) {
        CompoundTag tag = mob.getPersistentData();
        tag.putLong(DEPOSIT_POS, pos.asLong());
        tag.putString(DEPOSIT_DIM, mob.level().dimension().location().toString());
    }

    /** Exact chest chosen by the master, or null for "nearest container". */
    public static BlockPos depositTarget(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        if (!tag.contains(DEPOSIT_POS)) {
            return null;
        }
        if (!tag.getString(DEPOSIT_DIM).equals(mob.level().dimension().location().toString())) {
            return null;
        }
        return BlockPos.of(tag.getLong(DEPOSIT_POS));
    }

    public static void clearDepositTarget(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        tag.remove(DEPOSIT_POS);
        tag.remove(DEPOSIT_DIM);
    }

    public static void addKills(Mob mob, int amount) {
        CompoundTag tag = mob.getPersistentData();
        tag.putInt(STAT_KILLS, tag.getInt(STAT_KILLS) + amount);
    }

    /** Claims a free creature. Returns false when it already belongs to someone. */
    public static boolean enslave(Mob mob, Player player) {
        if (isEnslaved(mob)) {
            return false;
        }
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(OWNER, player.getUUID());
        tag.putString(COMMAND, EnslavedCommand.FOLLOW.name());
        tag.putLong(HOME, mob.blockPosition().asLong());
        tag.putInt(STAT_BLOCKS, 0);
        tag.putInt(STAT_ITEMS, 0);
        tag.putInt(STAT_KILLS, 0);
        tag.putInt(WORK_LIMIT, 0);
        tag.putInt(WORK_DONE, 0);
        tag.remove(DEPOSIT_POS);
        tag.remove(DEPOSIT_DIM);
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.setPersistenceRequired();
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        ServantMind.init(mob);
        ServantMind.applySpeed(mob);
        ensureGoal(mob);
        highlight(mob);
        return true;
    }

    /** Migrates a legacy servant that stored its owner only on the entity. */
    public static void adopt(Mob mob, UUID owner) {
        if (owner == null || isEnslaved(mob)) {
            return;
        }
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(OWNER, owner);
        tag.putString(COMMAND, EnslavedCommand.FOLLOW.name());
        if (!tag.contains(HOME)) {
            tag.putLong(HOME, mob.blockPosition().asLong());
        }
        tag.putInt(STAT_BLOCKS, 0);
        tag.putInt(STAT_ITEMS, 0);
        tag.putInt(STAT_KILLS, 0);
        tag.putInt(WORK_LIMIT, 0);
        tag.putInt(WORK_DONE, 0);
        mob.setPersistenceRequired();
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        ServantMind.init(mob);
        ServantMind.applySpeed(mob);
        ensureGoal(mob);
    }

    public static void highlight(Mob mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false, false));
        if (mob.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.0, mob.getZ(),
                12, 0.4, 0.4, 0.4, 0.02);
        }
    }

    public static void release(Mob mob) {
        if (mob instanceof com.s018d.entity.ServantEntity servant) {
            servant.clearLegacyOwner();
            servant.clearTasks();
        }
        CompoundTag tag = mob.getPersistentData();
        tag.remove(OWNER);
        tag.remove(COMMAND);
        tag.remove(HOME);
        removeGoal(mob);
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setDropChance(EquipmentSlot.MAINHAND, 0.085f);
        dropServantItems(mob);
        highlight(mob);
    }

    /** Gives the held tool and the whole bag back to the world as drops. */
    public static void dropServantItems(Mob mob) {
        if (mob.level().isClientSide()) {
            return;
        }
        ItemStack hand = mob.getMainHandItem();
        if (!hand.isEmpty()) {
            mob.spawnAtLocation(hand.copy());
            mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        for (ItemStack stack : stashTakeAll(mob)) {
            mob.spawnAtLocation(stack);
        }
        if (mob instanceof ServantEntity servant) {
            for (int i = 0; i < servant.getInventory().getContainerSize(); i++) {
                ItemStack stack = servant.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    mob.spawnAtLocation(stack.copy());
                }
            }
            servant.getInventory().clearContent();
        }
    }

    public static void setCommand(Mob mob, EnslavedCommand command) {
        if (!isEnslaved(mob)) {
            return;
        }
        if (command == EnslavedCommand.RELEASE) {
            release(mob);
            return;
        }
        mob.getPersistentData().putString(COMMAND, command.name());
        resetWorkDone(mob);
        ServantMind.onForcedWork(mob);
        ensureGoal(mob);
        equipBestTool(mob, command);
        if (command == EnslavedCommand.STAY || command == EnslavedCommand.STOP) {
            mob.getNavigation().stop();
        }
    }

    /** Adds our goal if it is missing. Safe to call on every entity join. */
    public static void ensureGoal(Mob mob) {
        if (!isEnslaved(mob)) {
            return;
        }
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof EnslavedMobGoal) {
                return;
            }
        }
        mob.goalSelector.addGoal(0, new EnslavedMobGoal(mob));
    }

    private static void removeGoal(Mob mob) {
        EnslavedMobGoal found = null;
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof EnslavedMobGoal goal) {
                found = goal;
                break;
            }
        }
        if (found != null) {
            mob.goalSelector.removeGoal(found);
        }
    }

    // ------------------------------------------------------------------
    // Servant bag
    // ------------------------------------------------------------------

    public static List<ItemStack> stash(Mob mob) {
        List<ItemStack> items = new ArrayList<>();
        ListTag list = mob.getPersistentData().getList(STASH, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(mob.level().registryAccess(), list.getCompound(i));
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items;
    }

    private static void saveStash(Mob mob, List<ItemStack> items) {
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                list.add(stack.save(mob.level().registryAccess()));
            }
        }
        mob.getPersistentData().put(STASH, list);
        bumpStashRevision(mob);
    }

    /** Changes whenever the bag content changes, so open menus can refresh. */
    public static int stashRevision(Mob mob) {
        return mob.getPersistentData().getInt(STASH_REV);
    }

    private static void bumpStashRevision(Mob mob) {
        CompoundTag tag = mob.getPersistentData();
        tag.putInt(STASH_REV, tag.getInt(STASH_REV) + 1);
    }

    /** Puts one stack into the servant bag. Returns false when the bag is full. */
    public static boolean stashAdd(Mob mob, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        List<ItemStack> items = stash(mob);
        ItemStack remaining = stack.copy();
        mergeInto(items, remaining);
        if (!remaining.isEmpty()) {
            if (items.size() >= STASH_SIZE) {
                return false;
            }
            items.add(remaining.copy());
        }
        saveStash(mob, items);
        return true;
    }

    public static List<ItemStack> stashTakeAll(Mob mob) {
        List<ItemStack> items = stash(mob);
        mob.getPersistentData().remove(STASH);
        bumpStashRevision(mob);
        return items;
    }

    /** Fixed-size view of the bag used by the container menu (empty slots included). */
    public static List<ItemStack> stashSlots(Mob mob) {
        ListTag list = mob.getPersistentData().getList(STASH, Tag.TAG_COMPOUND);
        List<ItemStack> slots = new ArrayList<>(STASH_SIZE);
        for (int i = 0; i < STASH_SIZE; i++) {
            ItemStack stack = i < list.size()
                ? ItemStack.parseOptional(mob.level().registryAccess(), list.getCompound(i))
                : ItemStack.EMPTY;
            slots.add(stack);
        }
        return slots;
    }

    /** Persists a whole bag list slot by slot, e.g. after the player moved items. */
    public static void writeStash(Mob mob, List<ItemStack> items) {
        ListTag list = new ListTag();
        for (int i = 0; i < STASH_SIZE; i++) {
            ItemStack stack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            list.add(stack.saveOptional(mob.level().registryAccess()));
        }
        mob.getPersistentData().put(STASH, list);
        bumpStashRevision(mob);
    }

    /** Nearest container block entity around the given point, or null. */
    public static BlockPos findContainerPos(ServerLevel level, BlockPos center, double radius) {
        int r = Mth.ceil(radius / 16.0) + 1;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof Container) || !entry.getKey().closerThan(center, radius)) {
                        continue;
                    }
                    double distance = entry.getKey().distSqr(center);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = entry.getKey();
                    }
                }
            }
        }
        return best;
    }

    /** Removes a number of a specific item from the bag. False when too few. */
    public static boolean stashRemove(Mob mob, Item item, int count) {
        List<ItemStack> items = stash(mob);
        int have = 0;
        for (ItemStack stack : items) {
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        if (have < count) {
            return false;
        }
        int left = count;
        for (ItemStack stack : items) {
            if (left <= 0) {
                break;
            }
            if (!stack.is(item)) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        items.removeIf(ItemStack::isEmpty);
        saveStash(mob, items);
        return true;
    }

    public static boolean stashRemoveOne(Mob mob, Item item) {
        return stashRemove(mob, item, 1);
    }

    /** Removes one exact stack (including components) from the bag. */
    public static boolean stashRemoveOne(Mob mob, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        List<ItemStack> items = stash(mob);
        for (ItemStack stack : items) {
            if (ItemStack.isSameItemSameComponents(stack, wanted)) {
                stack.shrink(1);
                items.removeIf(ItemStack::isEmpty);
                saveStash(mob, items);
                return true;
            }
        }
        return false;
    }

    public static ItemStack takeHand(Mob mob) {
        ItemStack hand = mob.getMainHandItem();
        mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return hand;
    }

    public static int stashCount(Mob mob) {
        int count = 0;
        for (ItemStack stack : stash(mob)) {
            count += stack.getCount();
        }
        return count;
    }

    public static String toolName(Mob mob) {
        ItemStack hand = mob.getMainHandItem();
        return hand.isEmpty() ? "" : hand.getHoverName().getString();
    }

    /** Moves the best tool for the new order from the bag into the creature's hand. */
    public static void equipBestTool(Mob mob, EnslavedCommand job) {
        List<ItemStack> items = stash(mob);
        int index = -1;
        for (int i = 0; i < items.size(); i++) {
            if (matchesTool(items.get(i), job)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        ItemStack tool = items.remove(index);
        ItemStack previous = mob.getMainHandItem();
        mob.setItemInHand(InteractionHand.MAIN_HAND, tool);
        if (!previous.isEmpty()) {
            mergeInto(items, previous);
            if (!previous.isEmpty()) {
                if (items.size() < STASH_SIZE) {
                    items.add(previous.copy());
                } else {
                    mob.spawnAtLocation(previous);
                }
            }
        }
        saveStash(mob, items);
    }

    public static boolean hasToolFor(Mob mob, EnslavedCommand job) {
        return matchesTool(mob.getMainHandItem(), job);
    }

    private static void mergeInto(List<ItemStack> items, ItemStack remaining) {
        for (ItemStack existing : items) {
            if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int move = Math.min(space, remaining.getCount());
                existing.grow(move);
                remaining.shrink(move);
                if (remaining.isEmpty()) {
                    return;
                }
            }
        }
    }

    private static boolean matchesTool(ItemStack stack, EnslavedCommand job) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (job) {
            case GATHER_WOOD -> stack.getItem() instanceof AxeItem;
            case MINE -> stack.getItem() instanceof PickaxeItem;
            case DIG -> stack.getItem() instanceof ShovelItem;
            case HARVEST, PLANT, TILL -> stack.getItem() instanceof HoeItem;
            case SHEAR -> stack.getItem() instanceof ShearsItem;
            case HUNT, ATTACK, PROTECT, GUARD -> stack.getItem() instanceof SwordItem;
            case GUARD_RANGED, SHOOT -> stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
            case FISH -> stack.getItem() instanceof FishingRodItem;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // Mass orders
    // ------------------------------------------------------------------

    /** Claims every free creature around the player. */
    public static int massEnslave(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        int count = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                mob -> !isEnslaved(mob)
                    && !(mob instanceof ServantEntity))) {
            if (enslave(mob, player)) {
                count++;
            }
        }
        return count;
    }

    /** Applies an order to every creature the player owns around them. */
    public static int applyAll(ServerPlayer player, EnslavedCommand command) {
        AABB box = player.getBoundingBox().inflate(256.0);
        int count = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                mob -> isEnslaved(mob) && isOwner(mob, player))) {
            if (mob instanceof ServantEntity) {
                continue;
            }
            setCommand(mob, command);
            count++;
        }
        return count;
    }

    public static int setHomeAll(ServerPlayer player, BlockPos pos) {
        AABB box = player.getBoundingBox().inflate(256.0);
        int count = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                mob -> isEnslaved(mob) && isOwner(mob, player))) {
            if (mob instanceof ServantEntity) {
                continue;
            }
            setHome(mob, pos);
            highlight(mob);
            count++;
        }
        return count;
    }

    /** Makes every owned creature glow so the master can see them. */
    public static int highlightAll(ServerPlayer player, int ticks) {
        AABB box = player.getBoundingBox().inflate(256.0);
        int count = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                mob -> isEnslaved(mob) && isOwner(mob, player))) {
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, false));
            count++;
        }
        return count;
    }

    public static void sendOpen(ServerPlayer player, Mob mob) {
        highlight(mob);
        com.s018d.network.ModPackets.sendToPlayer(player,
                new com.s018d.network.EnslavedDataPacket(mob, true));
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
