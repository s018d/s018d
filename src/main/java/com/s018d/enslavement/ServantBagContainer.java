package com.s018d.enslavement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Nine-slot view of a servant bag. The cached slots are refreshed whenever the
 * mob bag changes elsewhere (gathering, pickup), so nothing is overwritten,
 * and every menu change is written straight back to the mob.
 */
public class ServantBagContainer implements Container {
    private final Mob mob;
    private List<ItemStack> items;
    private int revision;

    public ServantBagContainer(Mob mob) {
        this.mob = mob;
        this.items = EnslavementManager.stashSlots(mob);
        this.revision = EnslavementManager.stashRevision(mob);
    }

    private void sync() {
        int current = EnslavementManager.stashRevision(mob);
        if (current != this.revision) {
            this.revision = current;
            this.items = EnslavementManager.stashSlots(mob);
        }
    }

    private void persist() {
        if (!(this.mob.level() instanceof ServerLevel)) {
            return;
        }
        EnslavementManager.writeStash(mob, this.items);
        this.revision = EnslavementManager.stashRevision(mob);
    }

    @Override
    public int getContainerSize() {
        return EnslavementManager.STASH_SIZE;
    }

    @Override
    public boolean isEmpty() {
        this.sync();
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        this.sync();
        return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        this.sync();
        if (slot < 0 || slot >= this.items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = stack.split(amount);
        if (split.isEmpty()) {
            return ItemStack.EMPTY;
        }
        this.persist();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        this.sync();
        if (slot < 0 || slot >= this.items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        this.items.set(slot, ItemStack.EMPTY);
        this.persist();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.sync();
        if (slot < 0 || slot >= this.items.size()) {
            return;
        }
        this.items.set(slot, stack);
        this.persist();
    }

    @Override
    public void setChanged() {
        // Vanilla mutates the stack returned by getItem() and then calls this.
        this.persist();
    }

    @Override
    public boolean stillValid(Player player) {
        return mob.isAlive()
            && mob.level() == player.level()
            && player.distanceToSqr(mob) <= 64.0 * 64.0
            && EnslavementManager.isOwner(mob, player);
    }

    @Override
    public void clearContent() {
        this.sync();
        for (int i = 0; i < this.items.size(); i++) {
            this.items.set(i, ItemStack.EMPTY);
        }
        this.persist();
    }
}
