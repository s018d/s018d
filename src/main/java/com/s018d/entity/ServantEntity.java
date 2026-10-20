package com.s018d.entity;

import com.s018d.enslavement.EnslavementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Super servant: opens a menu where the owner loads items and queues
 * ordered tasks. Executes them one by one, smartly skipping failures.
 */
public class ServantEntity extends PathfinderMob {
    public enum TaskType {
        FOLLOW, STAY, GOTO, BREAK, FETCH, DELIVER, ATTACK, HARVEST, PLANT
    }

    public enum TaskStatus {
        PENDING, ACTIVE, DONE, FAILED
    }

    public static class Task {
        public TaskType type;
        public Map<String, String> params = new HashMap<>();
        public TaskStatus status = TaskStatus.PENDING;
        public int progress = 0;

        public String param(String key, String def) {
            return params.getOrDefault(key, def);
        }

        public int paramInt(String key, int def) {
            try {
                return Integer.parseInt(params.getOrDefault(key, String.valueOf(def)));
            } catch (NumberFormatException e) {
                return def;
            }
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("T", type.ordinal());
            StringBuilder data = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (data.length() > 0) {
                    data.append(';');
                }
                data.append(entry.getKey()).append('=').append(entry.getValue());
            }
            tag.putString("D", data.toString());
            tag.putInt("S", status.ordinal());
            tag.putInt("P", progress);
            return tag;
        }

        public static Task fromTag(CompoundTag tag) {
            Task task = new Task();
            TaskType[] types = TaskType.values();
            int ordinal = tag.getInt("T");
            task.type = ordinal >= 0 && ordinal < types.length ? types[ordinal] : TaskType.STAY;
            for (String pair : tag.getString("D").split(";")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    task.params.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            TaskStatus[] statuses = TaskStatus.values();
            int statusOrdinal = tag.getInt("S");
            task.status = statusOrdinal >= 0 && statusOrdinal < statuses.length ? statuses[statusOrdinal] : TaskStatus.PENDING;
            if (task.status == TaskStatus.ACTIVE) {
                task.status = TaskStatus.PENDING;
            }
            task.progress = tag.getInt("P");
            return task;
        }
    }

    private static final int TASK_TIMEOUT = 2400;
    private static final int INV_SIZE = 27;
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
        SynchedEntityData.defineId(ServantEntity.class, EntityDataSerializers.INT);

    private final SimpleContainer inventory = new SimpleContainer(INV_SIZE);
    private final List<Task> tasks = new ArrayList<>();
    private int taskTimeout = 0;
    private int stuckTicks = 0;
    private UUID legacyOwnerId;

    public ServantEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData);
        this.entityData.set(DATA_VARIANT, this.random.nextInt(8));
        return result;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ServantTaskGoal(this));
        this.goalSelector.addGoal(2, new ServantFollowGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.monster.Monster.class, true));
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public UUID getOwnerId() {
        UUID owner = EnslavementManager.ownerId(this);
        return owner != null ? owner : legacyOwnerId;
    }

    public void clearLegacyOwner() {
        legacyOwnerId = null;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public boolean hasQueuedTask() {
        for (Task task : tasks) {
            if (task.status == TaskStatus.PENDING || task.status == TaskStatus.ACTIVE) {
                return true;
            }
        }
        return false;
    }

    public void addTask(TaskType type, Map<String, String> params) {
        Task task = new Task();
        task.type = type;
        if (params != null) {
            task.params.putAll(params);
        }
        tasks.add(task);
    }

    public void clearTasks() {
        tasks.clear();
    }

    public void removeTask(int index) {
        if (index >= 0 && index < tasks.size()) {
            tasks.remove(index);
        }
    }

    public void startTimeout() {
        taskTimeout = TASK_TIMEOUT;
        stuckTicks = 0;
    }

    public boolean tickTimeout() {
        taskTimeout--;
        return taskTimeout <= 0;
    }

    private void notifyOwner(String messageKey) {
        if (this.level() instanceof ServerLevel server && getOwnerId() != null) {
            ServerPlayer owner = server.getServer().getPlayerList().getPlayer(getOwnerId());
            if (owner != null) {
                owner.displayClientMessage(net.minecraft.network.chat.Component.translatable(messageKey), false);
            }
        }
    }

    public void failActive(String messageKey) {
        for (Task task : tasks) {
            if (task.status == TaskStatus.ACTIVE) {
                task.status = TaskStatus.FAILED;
            }
        }
        notifyOwner(messageKey);
    }

    public void completeActive() {
        tasks.removeIf(task -> task.status == TaskStatus.ACTIVE || task.status == TaskStatus.FAILED);
        tasks.removeIf(task -> task.status == TaskStatus.DONE);
    }

    public Task activeTask() {
        for (Task task : tasks) {
            if (task.status == TaskStatus.ACTIVE) {
                return task;
            }
        }
        return null;
    }

    public void activateNext() {
        for (Task task : tasks) {
            if (task.status == TaskStatus.PENDING) {
                task.status = TaskStatus.ACTIVE;
                task.progress = 0;
                startTimeout();
                return;
            }
        }
    }

    public boolean moveToward(BlockPos pos, double speed) {
        if (this.blockPosition().closerThan(pos, 2.0)) {
            stuckTicks = 0;
            return true;
        }
        if (this.getNavigation().isDone()) {
            if (++stuckTicks > 100) {
                stuckTicks = 0;
                return true;
            }
            this.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), speed);
        } else {
            stuckTicks = 0;
        }
        return false;
    }

    public Player ownerPlayer() {
        if (getOwnerId() == null) {
            return null;
        }
        return this.level().getPlayerByUUID(getOwnerId());
    }

    /** Moves matching stacks from nearby chests into the servant inventory. */
    public int pullFromChests(ServerLevel level, java.util.function.Predicate<ItemStack> match, int max) {
        int moved = 0;
        BlockPos center = this.blockPosition();
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 4, 16))) {
            BlockEntity blockEntity = level.getBlockEntity(candidate.immutable());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize() && moved < max; i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && match.test(slot)) {
                    ItemStack copy = slot.copy();
                    copy.setCount(1);
                    ItemStack leftover = inventory.addItem(copy);
                    if (leftover.isEmpty()) {
                        slot.shrink(1);
                        container.setChanged();
                        moved++;
                    }
                }
            }
            if (moved >= max) {
                break;
            }
        }
        return moved;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", getVariant());
        UUID owner = getOwnerId();
        if (owner != null) {
            tag.putUUID("OwnerUUID", owner);
        }
        ListTag inv = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag item = (CompoundTag) stack.save(this.level().registryAccess());
                item.putByte("Slot", (byte) i);
                inv.add(item);
            }
        }
        tag.put("ServantInv", inv);
        ListTag taskList = new ListTag();
        for (Task task : tasks) {
            taskList.add(task.toTag());
        }
        tag.put("ServantTasks", taskList);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Variant")) {
            this.entityData.set(DATA_VARIANT, Math.floorMod(tag.getInt("Variant"), 8));
        }
        if (tag.hasUUID("OwnerUUID")) {
            legacyOwnerId = tag.getUUID("OwnerUUID");
            if (!this.level().isClientSide() && EnslavementManager.ownerId(this) == null) {
                EnslavementManager.adopt(this, legacyOwnerId);
            }
        }
        inventory.clearContent();
        for (Tag element : tag.getList("ServantInv", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            int slot = item.getByte("Slot");
            ItemStack parsed = ItemStack.parseOptional(this.level().registryAccess(), item);
            if (!parsed.isEmpty() && slot >= 0 && slot < inventory.getContainerSize()) {
                inventory.setItem(slot, parsed);
            }
        }
        tasks.clear();
        for (Tag element : tag.getList("ServantTasks", Tag.TAG_COMPOUND)) {
            tasks.add(Task.fromTag((CompoundTag) element));
        }
    }
}
