package com.s018d.enslavement;

import com.s018d.util.CraftBook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Objects;

/**
 * Executes the requests queued for a craftsman servant. Ingredients come from
 * the servant bag and from containers around its home. A missing ingredient is
 * handed to a nearby idle servant as a courier job.
 */
public final class CraftJob {
    private int cooldown;

    public void tick(Mob mob, Player owner, ServerLevel level) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        String requestId = EnslavementManager.peekCraftRequest(mob);
        if (requestId == null) {
            mob.getNavigation().stop();
            return;
        }
        CraftBook.Entry entry = CraftBook.get(requestId);
        if (entry == null) {
            EnslavementManager.popCraftRequest(mob);
            cooldown = 10;
            return;
        }
        BlockPos center = EnslavementManager.home(mob);
        if (hasIngredients(mob, level, center, entry)) {
            consumeIngredients(mob, level, center, entry);
            ItemStack result = entry.result().copy();
            if (!EnslavementManager.stashAdd(mob, result)) {
                mob.spawnAtLocation(result);
            }
            EnslavementManager.popCraftRequest(mob);
            cooldown = 30;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.0,
                mob.getZ(), 6, 0.25, 0.25, 0.25, 0.01);
            level.playSound(null, mob.blockPosition(), SoundEvents.VILLAGER_YES,
                SoundSource.NEUTRAL, 0.7f, 1.2f);
            if (owner != null) {
                owner.displayClientMessage(Component.translatable("gui.s018d.craft_done",
                    mob.getName(), result.getHoverName()), true);
            }
            return;
        }

        CraftBook.Ingredient missing = firstMissing(mob, level, center, entry);
        if (missing != null) {
            Item source = sourceItem(mob, level, center, missing);
            if (source != net.minecraft.world.item.Items.AIR) {
                String itemId = BuiltInRegistries.ITEM.getKey(source).toString();
                int missingCount = Math.max(1, missing.count() - countIngredient(mob, level, center, missing));
                Mob courier = findCourier(mob, level);
                if (courier != null) {
                    EnslavementManager.requestFetch(courier, itemId, missingCount, mob);
                    if (owner != null) {
                        owner.displayClientMessage(Component.translatable("gui.s018d.craft_courier",
                            courier.getName(), new ItemStack(source, missingCount).getHoverName()), true);
                    }
                } else if (owner != null) {
                    owner.displayClientMessage(Component.translatable("gui.s018d.craft_missing",
                        new ItemStack(source, missingCount).getHoverName(),
                        entry.result().getHoverName()), true);
                }
            } else if (owner != null) {
                owner.displayClientMessage(Component.translatable("gui.s018d.craft_missing_generic",
                    entry.result().getHoverName()), true);
            }
        }
        cooldown = 200;
    }

    private Item sourceItem(Mob mob, ServerLevel level, BlockPos center, CraftBook.Ingredient ingredient) {
        for (ItemStack stack : EnslavementManager.stash(mob)) {
            if (ingredient.test(stack)) {
                return stack.getItem();
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
            BlockEntity blockEntity = level.getBlockEntity(pos.immutable());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    return stack.getItem();
                }
            }
        }
        return ingredient.representativeItem();
    }
    private boolean hasIngredients(Mob mob, ServerLevel level, BlockPos center, CraftBook.Entry entry) {
        for (CraftBook.Ingredient ingredient : entry.ingredients()) {
            if (countIngredient(mob, level, center, ingredient) < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    private CraftBook.Ingredient firstMissing(Mob mob, ServerLevel level, BlockPos center, CraftBook.Entry entry) {
        for (CraftBook.Ingredient ingredient : entry.ingredients()) {
            if (countIngredient(mob, level, center, ingredient) < ingredient.count()) {
                return ingredient;
            }
        }
        return null;
    }

    private int countIngredient(Mob mob, ServerLevel level, BlockPos center, CraftBook.Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : EnslavementManager.stash(mob)) {
            if (ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
            BlockEntity blockEntity = level.getBlockEntity(pos.immutable());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private void consumeIngredients(Mob mob, ServerLevel level, BlockPos center, CraftBook.Entry entry) {
        List<ItemStack> stash = EnslavementManager.stash(mob);
        for (CraftBook.Ingredient ingredient : entry.ingredients()) {
            int left = ingredient.count();
            for (ItemStack stack : stash) {
                if (left <= 0) {
                    break;
                }
                if (ingredient.test(stack)) {
                    int take = Math.min(stack.getCount(), left);
                    stack.shrink(take);
                    left -= take;
                }
            }
            stash.removeIf(ItemStack::isEmpty);
            if (left > 0) {
                left = takeFromContainers(level, center, ingredient, left);
            }
        }
        EnslavementManager.writeStash(mob, stash);
    }

    private int takeFromContainers(ServerLevel level, BlockPos center,
        CraftBook.Ingredient ingredient, int needed) {
        int left = needed;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -4, -12), center.offset(12, 4, 12))) {
            BlockEntity blockEntity = level.getBlockEntity(pos.immutable());
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            boolean changed = false;
            for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    int take = Math.min(stack.getCount(), left);
                    stack.shrink(take);
                    left -= take;
                    changed = true;
                }
            }
            if (changed) {
                container.setChanged();
            }
            if (left <= 0) {
                break;
            }
        }
        return left;
    }

    private Mob findCourier(Mob craftsman, ServerLevel level) {
        var ownerId = EnslavementManager.ownerId(craftsman);
        return level.getEntitiesOfClass(Mob.class, craftsman.getBoundingBox().inflate(24.0), candidate ->
                candidate != craftsman
                    && candidate.isAlive()
                    && EnslavementManager.isEnslaved(candidate)
                    && !EnslavementManager.isFetching(candidate)
                    && Objects.equals(ownerId, EnslavementManager.ownerId(candidate)))
            .stream()
            .min(java.util.Comparator.comparingDouble(craftsman::distanceToSqr))
            .orElse(null);
    }
}


