package com.s018d.enslavement;

import com.s018d.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Literal butler mode: keeps the house, keeps the pantry full, cooks nothing
 * magically and heals the owner only with real carried items.
 */
public final class ButlerJob {
    private static final int HOME_RADIUS = 16;

    private int actionCooldown;
    private int scanCooldown;
    private int containerCooldown;
    private List<BlockPos> containers = List.of();
    private ItemEntity itemTarget;
    private Animal huntTarget;
    private BlockPos patrolTarget;

    public void tick(Mob mob, Player owner, ServerLevel level) {
        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
        }
        if (containerCooldown > 0) {
            containerCooldown--;
        }
        if (owner == null || !owner.isAlive()) {
            hold(mob);
            return;
        }
        BlockPos home = EnslavementManager.home(mob);
        if (!mob.blockPosition().closerThan(home, 28.0)) {
            goHome(mob, home);
            return;
        }
        if (owner.level() == mob.level() && mob.distanceToSqr(owner) > 56.0 * 56.0) {
            ServantMind.bark(mob, "gui.s018d.bark_come_home");
            hold(mob);
            return;
        }

        if (owner.getHealth() < owner.getMaxHealth() * 0.6f && actionCooldown <= 0) {
            if (healOwner(mob, owner, level, home)) {
                return;
            }
        }
        if (owner.getFoodData().getFoodLevel() < 14 && actionCooldown <= 0) {
            if (feedOwner(mob, owner, level, home)) {
                return;
            }
        }
        if (homeFoodStock(mob, level, home) < 8) {
            if (hunt(mob, owner, level, home)) {
                return;
            }
        }
        if (tidy(mob, level, home)) {
            return;
        }
        patrol(mob, home);
    }

    // ------------------------------------------------------------------
    // Feeding and healing
    // ------------------------------------------------------------------

    private boolean feedOwner(Mob mob, Player owner, ServerLevel level, BlockPos home) {
        FoodChoice food = findFood(mob, owner, level, home);
        if (food == null) {
            ServantMind.bark(mob, "gui.s018d.bark_no_food");
            actionCooldown = 120;
            return false;
        }
        if (!moveTo(mob, owner.blockPosition(), 3.0)) {
            return true;
        }
        if (mob.distanceToSqr(owner) > 9.0) {
            return true;
        }
        ItemStack one = food.stack().copyWithCount(1);
        if (!takeOne(mob, food, level)) {
            return false;
        }
        owner.eat(level, one);
        ServantMind.onFed(mob);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.2, owner.getZ(),
            4, 0.25, 0.25, 0.25, 0.01);
        actionCooldown = 60;
        return true;
    }

    private boolean healOwner(Mob mob, Player owner, ServerLevel level, BlockPos home) {
        if (owner.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) {
            return false;
        }
        FoodChoice medicine = findMedicine(mob, level, home);
        if (medicine == null) {
            ServantMind.bark(mob, "gui.s018d.bark_no_medicine");
            actionCooldown = 140;
            return false;
        }
        if (!moveTo(mob, owner.blockPosition(), 3.0)) {
            return true;
        }
        if (mob.distanceToSqr(owner) > 9.0) {
            return true;
        }
        if (!takeOne(mob, medicine, level)) {
            return false;
        }
        if (medicine.stack().is(ModItems.BANDAGE.get())) {
            owner.heal(4.0f);
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8f, 1.25f);
        } else {
            owner.eat(level, medicine.stack().copyWithCount(1));
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, owner.getX(), owner.getY() + 1.0,
            owner.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
        ServantMind.praise(mob, 2);
        actionCooldown = 100;
        return true;
    }

    private boolean takeOne(Mob mob, FoodChoice choice, ServerLevel level) {
        if (choice.container() != null) {
            Container container = containerAt(level, choice.container());
            if (container == null) {
                return false;
            }
            ItemStack removed = container.removeItem(choice.slot(), 1);
            container.setChanged();
            return !removed.isEmpty();
        }
        return EnslavementManager.stashRemoveOne(mob, choice.stack());
    }

    private FoodChoice findFood(Mob mob, Player owner, ServerLevel level, BlockPos home) {
        FoodChoice best = null;
        int bestNutrition = -1;
        for (ItemStack stack : EnslavementManager.stash(mob)) {
            FoodProperties properties = stack.getFoodProperties(owner);
            if (properties != null && properties.nutrition() > bestNutrition) {
                bestNutrition = properties.nutrition();
                best = new FoodChoice(stack, null, -1);
            }
        }
        if (best != null) {
            return best;
        }
        for (BlockPos pos : containers(level, home)) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                FoodProperties properties = stack.isEmpty() ? null : stack.getFoodProperties(owner);
                if (properties != null && properties.nutrition() > bestNutrition) {
                    bestNutrition = properties.nutrition();
                    best = new FoodChoice(stack.copy(), pos, i);
                }
            }
        }
        return best;
    }

    private FoodChoice findMedicine(Mob mob, ServerLevel level, BlockPos home) {
        for (ItemStack stack : EnslavementManager.stash(mob)) {
            if (isMedicine(stack)) {
                return new FoodChoice(stack, null, -1);
            }
        }
        for (BlockPos pos : containers(level, home)) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (isMedicine(stack)) {
                    return new FoodChoice(stack.copy(), pos, i);
                }
            }
        }
        return null;
    }

    private boolean isMedicine(ItemStack stack) {
        return stack.is(ModItems.BANDAGE.get()) || stack.is(Items.GOLDEN_APPLE)
            || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_CARROT);
    }

    // ------------------------------------------------------------------
    // Hunting and tidying
    // ------------------------------------------------------------------

    private boolean hunt(Mob mob, Player owner, ServerLevel level, BlockPos home) {
        if (huntTarget != null && (!huntTarget.isAlive() || huntTarget.isBaby())) {
            huntTarget = null;
        }
        if (huntTarget == null) {
            if (scanCooldown > 0) {
                return false;
            }
            scanCooldown = 40;
            huntTarget = level.getEntitiesOfClass(Animal.class,
                    new net.minecraft.world.phys.AABB(home).inflate(48.0),
                    animal -> animal.isAlive() && !animal.isBaby()
                        && !(animal instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame())
                        && !EnslavementManager.isEnslaved(animal))
                .stream()
                .min(java.util.Comparator.comparingDouble(animal -> animal.distanceToSqr(
                    home.getX(), home.getY(), home.getZ())))
                .orElse(null);
            if (huntTarget == null) {
                ServantMind.bark(mob, "gui.s018d.bark_no_food");
                actionCooldown = 160;
                return false;
            }
        }
        if (!moveTo(mob, huntTarget.blockPosition(), 2.5)) {
            return true;
        }
        if (mob.distanceToSqr(huntTarget) <= 6.25) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(huntTarget, 30.0f, 30.0f);
            if (actionCooldown <= 0) {
                actionCooldown = 20;
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                mob.doHurtTarget(huntTarget);
            }
        }
        return true;
    }

    private boolean tidy(Mob mob, ServerLevel level, BlockPos home) {
        if (itemTarget != null && (!itemTarget.isAlive() || itemTarget.getItem().isEmpty())) {
            itemTarget = null;
        }
        if (itemTarget == null) {
            if (scanCooldown > 0) {
                return false;
            }
            scanCooldown = 40;
            itemTarget = level.getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(home).inflate(HOME_RADIUS),
                    ItemEntity::isAlive)
                .stream()
                .min(java.util.Comparator.comparingDouble(item -> item.distanceToSqr(
                    mob.getX(), mob.getY(), mob.getZ())))
                .orElse(null);
            if (itemTarget == null) {
                return false;
            }
        }
        if (!moveTo(mob, itemTarget.blockPosition(), 1.8)) {
            return true;
        }
        if (mob.distanceToSqr(itemTarget) <= 4.0) {
            ItemStack stack = itemTarget.getItem();
            if (EnslavementManager.stashAdd(mob, stack)) {
                itemTarget.discard();
                ServantMind.onFed(mob);
            } else {
                depositOne(mob, level, stack, home);
                itemTarget.discard();
            }
            itemTarget = null;
            actionCooldown = 10;
        }
        return true;
    }

    private void depositOne(Mob mob, ServerLevel level, ItemStack stack, BlockPos home) {
        for (BlockPos pos : containers(level, home)) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty()) {
                    container.setItem(i, stack.copy());
                    container.setChanged();
                    return;
                }
            }
        }
        mob.spawnAtLocation(stack.copy());
    }

    // ------------------------------------------------------------------
    // Home, pantry and movement
    // ------------------------------------------------------------------

    private int homeFoodStock(Mob mob, ServerLevel level, BlockPos home) {
        int count = 0;
        for (ItemStack stack : EnslavementManager.stash(mob)) {
            if (stack.getFoodProperties(mob) != null) {
                count += stack.getCount();
            }
        }
        for (BlockPos pos : containers(level, home)) {
            Container container = containerAt(level, pos);
            if (container == null) {
                continue;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && stack.getFoodProperties(mob) != null) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    private void patrol(Mob mob, BlockPos home) {
        if (patrolTarget != null && mob.blockPosition().closerThan(patrolTarget, 2.0)) {
            patrolTarget = null;
        }
        if (patrolTarget == null) {
            if (actionCooldown > 0) {
                return;
            }
            actionCooldown = 60;
            patrolTarget = home.offset(mob.getRandom().nextInt(13) - 6, 0,
                mob.getRandom().nextInt(13) - 6);
        }
        moveTo(mob, patrolTarget, 1.2);
    }

    private void goHome(Mob mob, BlockPos home) {
        moveTo(mob, home, 3.0);
    }

    private boolean moveTo(Mob mob, BlockPos pos, double closeEnough) {
        if (mob.blockPosition().closerThan(pos, closeEnough)) {
            return true;
        }
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.05);
        }
        return false;
    }

    private void hold(Mob mob) {
        mob.getNavigation().stop();
        mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
    }

    private List<BlockPos> containers(ServerLevel level, BlockPos home) {
        if (containerCooldown > 0) {
            return containers;
        }
        containerCooldown = 200;
        BlockPos center = home;
        List<BlockPos> found = new ArrayList<>();
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (var entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof Container) {
                        found.add(entry.getKey().immutable());
                    }
                }
            }
        }
        containers = List.copyOf(found);
        return containers;
    }

    private Container containerAt(ServerLevel level, BlockPos pos) {
        if (pos == null) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container container ? container : null;
    }

    private record FoodChoice(ItemStack stack, BlockPos container, int slot) {
    }
}
