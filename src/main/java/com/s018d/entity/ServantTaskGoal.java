package com.s018d.entity;

import com.s018d.util.ChestFilterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;

/** Executes the servant's ordered task queue, one task at a time. */
public class ServantTaskGoal extends Goal {
    private final ServantEntity servant;
    private int attackCooldown = 0;

    public ServantTaskGoal(ServantEntity servant) {
        this.servant = servant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return servant.getOwnerId() != null && servant.hasQueuedTask();
    }

    @Override
    public void tick() {
        if (servant.level().isClientSide() || !(servant.level() instanceof ServerLevel server)) {
            return;
        }
        ServantEntity.Task task = servant.activeTask();
        if (task == null) {
            servant.activateNext();
            task = servant.activeTask();
            if (task == null) {
                return;
            }
        }
        if (servant.tickTimeout()) {
            servant.failActive("gui.s018d.task_failed");
            return;
        }
        switch (task.type) {
            case FOLLOW -> tickFollow(task);
            case STAY -> tickStay(task);
            case GOTO -> tickGoto(task);
            case BREAK -> tickBreak(task, server);
            case FETCH -> tickFetch(task, server);
            case DELIVER -> tickDeliver(task, server);
            case ATTACK -> tickAttack(task, server);
            case HARVEST -> tickHarvest(task, server);
            case PLANT -> tickPlant(task, server);
        }
    }

    private void tickFollow(ServantEntity.Task task) {
        Player owner = servant.ownerPlayer();
        if (owner == null) {
            servant.failActive("gui.s018d.task_failed");
            return;
        }
        if (servant.blockPosition().closerThan(owner.blockPosition(), 3.0)) {
            task.status = ServantEntity.TaskStatus.DONE;
            servant.completeActive();
            return;
        }
        servant.getNavigation().moveTo(owner, 1.1);
    }

    private void tickStay(ServantEntity.Task task) {
        servant.getNavigation().stop();
        task.progress++;
        if (task.progress >= task.paramInt("ticks", 200)) {
            task.status = ServantEntity.TaskStatus.DONE;
            servant.completeActive();
        }
    }

    private void tickGoto(ServantEntity.Task task) {
        BlockPos pos = new BlockPos(
            task.paramInt("x", servant.blockPosition().getX()),
            task.paramInt("y", servant.blockPosition().getY()),
            task.paramInt("z", servant.blockPosition().getZ()));
        if (servant.moveToward(pos, 1.1)) {
            task.status = ServantEntity.TaskStatus.DONE;
            servant.completeActive();
        }
    }

    private Block resolveBlock(String id) {
        if (id == null || id.isEmpty() || !id.contains(":")) {
            return null;
        }
        try {
            return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        } catch (Exception e) {
            return null;
        }
    }

    private void tickBreak(ServantEntity.Task task, ServerLevel server) {
        Block wanted = resolveBlock(task.param("block", ""));
        int count = task.paramInt("count", 16);
        BlockPos center = servant.blockPosition();
        BlockPos found = null;
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-16, -6, -16), center.offset(16, 6, 16))) {
            BlockPos pos = candidate.immutable();
            BlockState state = server.getBlockState(pos);
            if (wanted == null || !state.is(wanted)) {
                continue;
            }
            found = pos;
            break;
        }
        if (found == null) {
            task.progress++;
            if (task.progress > 5) {
                task.status = ServantEntity.TaskStatus.DONE;
                servant.completeActive();
            }
            return;
        }
        task.progress = 0;
        if (!servant.blockPosition().closerThan(found, 2.0)) {
            servant.moveToward(found, 1.1);
            return;
        }
        BlockState state = server.getBlockState(found);
        List<ItemStack> drops = Block.getDrops(state, server, found, server.getBlockEntity(found));
        server.destroyBlock(found, false);
        servant.swing(InteractionHand.MAIN_HAND);
        for (ItemStack drop : drops) {
            ItemStack leftover = servant.getInventory().addItem(drop.copy());
            if (!leftover.isEmpty()) {
                Block.popResource(server, found, leftover);
            }
        }
        task.param("done", String.valueOf(task.paramInt("done", 0) + 1));
        if (task.paramInt("done", 0) >= count) {
            task.status = ServantEntity.TaskStatus.DONE;
            servant.completeActive();
        }
    }

    private void tickFetch(ServantEntity.Task task, ServerLevel server) {
        String itemId = task.param("item", "");
        int count = task.paramInt("count", 16);
        int have = 0;
        for (int i = 0; i < servant.getInventory().getContainerSize(); i++) {
            ItemStack slot = servant.getInventory().getItem(i);
            if (!slot.isEmpty() && idOf(slot).equals(itemId)) {
                have += slot.getCount();
            }
        }
        if (have >= count) {
            task.status = ServantEntity.TaskStatus.DONE;
            servant.completeActive();
            return;
        }
        int moved = servant.pullFromChests(server, stack -> idOf(stack).equals(itemId), count - have);
        if (moved <= 0) {
            task.progress++;
            if (task.progress > 5) {
                task.status = ServantEntity.TaskStatus.DONE;
                servant.completeActive();
            }
        } else {
            task.progress = 0;
            servant.moveToward(servant.blockPosition(), 1.0);
        }
    }

    private static String idOf(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private void tickDeliver(ServantEntity.Task task, ServerLevel server) {
        BlockPos center = servant.blockPosition();
        boolean movedAnything = false;
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 4, 16))) {
            BlockPos pos = candidate.immutable();
            BlockEntity blockEntity = server.getBlockEntity(pos);
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            for (int i = 0; i < servant.getInventory().getContainerSize(); i++) {
                ItemStack slot = servant.getInventory().getItem(i);
                if (slot.isEmpty() || !ChestFilterManager.canInsert(pos, slot, server)) {
                    continue;
                }
                ItemStack copy = slot.copy();
                if (ChestFilterManager.insertInto(container, copy)) {
                    servant.getInventory().setItem(i, copy);
                    movedAnything = true;
                }
            }
        }
        boolean empty = true;
        for (int i = 0; i < servant.getInventory().getContainerSize(); i++) {
            if (!servant.getInventory().getItem(i).isEmpty()) {
                empty = false;
                break;
            }
        }
        if (empty || !movedAnything) {
            task.status = ServantEntity.TaskStatus.DONE;
            servant.completeActive();
        }
    }

    private void tickAttack(ServantEntity.Task task, ServerLevel server) {
        AABB box = servant.getBoundingBox().inflate(32.0);
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, box,
            e -> e.isAlive() && !(e instanceof Player) && !(e instanceof ServantEntity));
        if (targets.isEmpty()) {
            task.progress++;
            if (task.progress > 3) {
                task.status = ServantEntity.TaskStatus.DONE;
                servant.completeActive();
            }
            return;
        }
        task.progress = 0;
        LivingEntity target = targets.get(0);
        if (servant.blockPosition().closerThan(target.blockPosition(), 2.5)) {
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }
            attackCooldown = 20;
            servant.swing(InteractionHand.MAIN_HAND);
            target.hurt(server.damageSources().mobAttack(servant), 6.0f);
        } else {
            servant.getNavigation().moveTo(target, 1.2);
        }
    }

    private void tickHarvest(ServantEntity.Task task, ServerLevel server) {
        int count = task.paramInt("count", 16);
        BlockPos center = servant.blockPosition();
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-12, -2, -12), center.offset(12, 2, 12))) {
            BlockPos pos = candidate.immutable();
            BlockState state = server.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
                continue;
            }
            if (!servant.blockPosition().closerThan(pos, 2.0)) {
                servant.moveToward(pos, 1.0);
                return;
            }
            List<ItemStack> drops = Block.getDrops(state, server, pos, server.getBlockEntity(pos));
            server.destroyBlock(pos, false);
            servant.swing(InteractionHand.MAIN_HAND);
            for (ItemStack drop : drops) {
                ItemStack leftover = servant.getInventory().addItem(drop.copy());
                if (!leftover.isEmpty()) {
                    Block.popResource(server, pos, leftover);
                }
            }
            task.param("done", String.valueOf(task.paramInt("done", 0) + 1));
            if (task.paramInt("done", 0) >= count) {
                task.status = ServantEntity.TaskStatus.DONE;
                servant.completeActive();
            }
            return;
        }
        task.progress++;
        if (task.progress > 5) {
            task.status = ServantEntity.TaskStatus.DONE;
            servant.completeActive();
        }
    }

    private void tickPlant(ServantEntity.Task task, ServerLevel server) {
        int count = task.paramInt("count", 16);
        BlockPos center = servant.blockPosition();
        for (BlockPos candidate : BlockPos.betweenClosed(center.offset(-12, -2, -12), center.offset(12, 2, 12))) {
            BlockPos pos = candidate.immutable();
            if (!server.getBlockState(pos).is(Blocks.FARMLAND) || !server.getBlockState(pos.above()).isAir()) {
                continue;
            }
            ItemStack seeds = takeSeeds();
            if (seeds.isEmpty()) {
                task.status = ServantEntity.TaskStatus.DONE;
                servant.completeActive();
                return;
            }
            if (!servant.blockPosition().closerThan(pos, 2.5)) {
                servant.moveToward(pos, 1.0);
                returnSeeds(seeds);
                return;
            }
            server.setBlockAndUpdate(pos.above(), cropFor(seeds));
            task.param("done", String.valueOf(task.paramInt("done", 0) + 1));
            if (task.paramInt("done", 0) >= count) {
                task.status = ServantEntity.TaskStatus.DONE;
                servant.completeActive();
            }
            return;
        }
        task.status = ServantEntity.TaskStatus.DONE;
        servant.completeActive();
    }

    private ItemStack takeSeeds() {
        for (int i = 0; i < servant.getInventory().getContainerSize(); i++) {
            ItemStack slot = servant.getInventory().getItem(i);
            if (!slot.is(Items.WHEAT_SEEDS) && !slot.is(Items.CARROT)
                && !slot.is(Items.POTATO) && !slot.is(Items.BEETROOT_SEEDS)) {
                continue;
            }
            ItemStack one = slot.copy();
            one.setCount(1);
            slot.shrink(1);
            return one;
        }
        return ItemStack.EMPTY;
    }

    private void returnSeeds(ItemStack seeds) {
        ItemStack leftover = servant.getInventory().addItem(seeds);
        if (!leftover.isEmpty() && servant.level() instanceof ServerLevel server) {
            Block.popResource(server, servant.blockPosition(), leftover);
        }
    }

    private BlockState cropFor(ItemStack seeds) {
        if (seeds.is(Items.CARROT)) {
            return Blocks.CARROTS.defaultBlockState();
        }
        if (seeds.is(Items.POTATO)) {
            return Blocks.POTATOES.defaultBlockState();
        }
        if (seeds.is(Items.BEETROOT_SEEDS)) {
            return Blocks.BEETROOTS.defaultBlockState();
        }
        return Blocks.WHEAT.defaultBlockState();
    }
}
