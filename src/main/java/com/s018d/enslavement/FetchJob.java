package com.s018d.enslavement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Objects;

/**
 * Courier task used by the craftsman. The courier takes the requested item
 * from storage around its home and carries it to the requesting servant.
 */
public final class FetchJob {
    private int cooldown;
    private int scanCooldown;

    public void tick(Mob mob, Player owner, ServerLevel level) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        String itemId = EnslavementManager.fetchItem(mob);
        if (itemId == null) {
            restore(mob);
            return;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        Item item = key == null ? null : BuiltInRegistries.ITEM.get(key);
        if (item == null) {
            finish(mob, owner, "gui.s018d.fetch_failed");
            return;
        }

        if (!hasItem(mob, item) && scanCooldown <= 0) {
            scanCooldown = 20;
            collectFromStorage(mob, level, item, EnslavementManager.fetchCount(mob));
        }

        if (!hasItem(mob, item)) {
            if (owner != null) {
                owner.displayClientMessage(Component.translatable("gui.s018d.fetch_waiting",
                    mob.getName(), new ItemStack(item).getHoverName()), true);
            }
            cooldown = 200;
            return;
        }

        Mob requester = requester(mob, level);
        if (requester == null) {
            finish(mob, owner, "gui.s018d.fetch_lost");
            return;
        }
        if (mob.distanceToSqr(requester) > 4.0 * 4.0) {
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(requester, 1.2);
            }
            return;
        }

        int amount = Math.min(EnslavementManager.fetchCount(mob), countItem(mob, item));
        if (amount <= 0 || !EnslavementManager.stashRemove(mob, item, amount)) {
            cooldown = 20;
            return;
        }
        ItemStack delivery = new ItemStack(item, amount);
        if (!EnslavementManager.stashAdd(requester, delivery)) {
            requester.spawnAtLocation(delivery);
        }
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("gui.s018d.fetch_done",
                mob.getName(), delivery.getHoverName(), requester.getName()), true);
        }
        finish(mob, owner, null);
    }

    private void restore(Mob mob) {
        EnslavedCommand previous = EnslavementManager.fetchPrevious(mob);
        EnslavementManager.clearFetch(mob);
        EnslavementManager.setCommand(mob, previous == null ? EnslavedCommand.FOLLOW : previous);
    }

    private void finish(Mob mob, Player owner, String messageKey) {
        EnslavedCommand previous = EnslavementManager.fetchPrevious(mob);
        if (messageKey != null && owner != null) {
            owner.displayClientMessage(Component.translatable(messageKey, mob.getName()), true);
        }
        EnslavementManager.clearFetch(mob);
        EnslavementManager.setCommand(mob, previous == null ? EnslavedCommand.FOLLOW : previous);
        cooldown = 20;
    }

    private Mob requester(Mob mob, ServerLevel level) {
        Entity entity = level.getEntity(EnslavementManager.fetchRequesterId(mob));
        if (entity instanceof Mob requester
            && requester.isAlive()
            && Objects.equals(EnslavementManager.ownerId(mob), EnslavementManager.ownerId(requester))) {
            return requester;
        }
        return null;
    }

    private boolean hasItem(Mob mob, Item item) {
        return countItem(mob, item) > 0;
    }

    private int countItem(Mob mob, Item item) {
        int count = 0;
        for (ItemStack stack : EnslavementManager.stash(mob)) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void collectFromStorage(Mob mob, ServerLevel level, Item item, int wanted) {
        BlockPos center = EnslavementManager.home(mob);
        int left = wanted;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 4, 16))) {
            BlockEntity blockEntity = level.getBlockEntity(pos.immutable());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                int take = Math.min(stack.getCount(), left);
                ItemStack picked = stack.copy();
                picked.setCount(take);
                if (!EnslavementManager.stashAdd(mob, picked)) {
                    break;
                }
                stack.shrink(take);
                left -= take;
                changed = true;
            }
            if (changed) {
                container.setChanged();
            }
            if (left <= 0) {
                break;
            }
        }
    }
}
