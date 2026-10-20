package com.s018d.enslavement;

import com.s018d.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Chest-like menu for the nine-slot servant bag, opened on both sides. */
public class ServantBagMenu extends AbstractContainerMenu {
    public static final int BAG_SLOTS = EnslavementManager.STASH_SIZE;

    private final Container bag;
    private final int entityId;

    public ServantBagMenu(int containerId, Inventory playerInventory, int entityId) {
        this(containerId, playerInventory, entityId,
            playerInventory.player.level().getEntity(entityId) instanceof Mob mob
                ? new ServantBagContainer(mob)
                : new SimpleContainer(BAG_SLOTS));
    }

    private ServantBagMenu(int containerId, Inventory playerInventory, int entityId, Container bag) {
        super(ModMenus.SERVANT_BAG.get(), containerId);
        checkContainerSize(bag, BAG_SLOTS);
        this.bag = bag;
        this.entityId = entityId;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(bag, col + row * 3, 62 + col * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 85 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 143));
        }
    }

    public int entityId() {
        return entityId;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.bag.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < BAG_SLOTS) {
                if (!this.moveItemStackTo(stack, BAG_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, BAG_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    /** Server-side helper: opens the bag of an owned servant for the player. */
    public static void open(ServerPlayer player, Mob mob) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) ->
                    new ServantBagMenu(containerId, inventory, mob.getId()),
                mob.getDisplayName()),
            buffer -> buffer.writeInt(mob.getId()));
    }
}
