package com.s018d.enslavement;

import com.s018d.ModItems;
import com.s018d.util.CraftBook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Drives any enslaved creature. It always owns the MOVE flag, so vanilla
 * wandering cannot fight the master's order. Creatures use tools from their
 * bag, gather drops into the bag and sort or fill nearby containers.
 */
public class EnslavedMobGoal extends Goal {
    private final Mob mob;
    private BlockPos workTarget;
    private BlockPos containerTarget;
    private ItemEntity itemTarget;
    private int cooldown;
    private int scanCooldown;
    private int patrolCooldown;
    private int pickupCooldown;
    private int equipCooldown;
    private int guardCooldown;
    private int craftAskCooldown;
    private final ButlerJob butler = new ButlerJob();
    private final CraftJob craft = new CraftJob();
    private final FetchJob fetch = new FetchJob();
    private final Set<BlockPos> sortedChests = new HashSet<>();

    public EnslavedMobGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return EnslavementManager.isEnslaved(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.scanCooldown = 0;
        this.patrolCooldown = 0;
        this.pickupCooldown = 0;
        this.equipCooldown = 0;
        this.guardCooldown = 0;
        this.craftAskCooldown = 0;
        this.workTarget = null;
        this.containerTarget = null;
        this.itemTarget = null;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        workTarget = null;
        containerTarget = null;
        itemTarget = null;
    }

    @Override
    public void tick() {
        if (mob.level().isClientSide()) {
            return;
        }
        ServantMind.tick(mob);
        if (SunSafety.tick(mob)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (scanCooldown > 0) {
            scanCooldown--;
        }
        if (guardCooldown > 0) {
            guardCooldown--;
        } else {
            guardCooldown = 10;
            FriendlyFireEvents.guardTarget(mob);
        }
        Player owner = owner();
        EnslavedCommand current = EnslavementManager.command(mob);
        if (limitedJob(current) && EnslavementManager.workLimitReached(mob)) {
            if (EnslavementManager.advanceQueue(mob)) {
                return;
            }
            if (EnslavementManager.autoDepositEnabled(mob)
                && EnslavementManager.depositTarget(mob) != null) {
                EnslavementManager.setCommand(mob, EnslavedCommand.DEPOSIT);
                return;
            }
            hold();
            return;
        }
        if (equipCooldown > 0) {
            equipCooldown--;
        } else if (needsTool(current) && !EnslavementManager.hasToolFor(mob, current)) {
            EnslavementManager.equipBestTool(mob, current);
            equipCooldown = 40;
        }
        if (needsTool(current) && !EnslavementManager.hasToolFor(mob, current)) {
            if (craftAskCooldown > 0) {
                craftAskCooldown--;
            } else {
                askCraftsman(current);
            }
        }
        if (pickupCooldown > 0) {
            pickupCooldown--;
        } else if (autoPickupAllowed(current)) {
            pickupCooldown = 10;
            autoPickup();
        }
        switch (current) {
            case FOLLOW -> follow(owner, false);
            case COME -> follow(owner, true);
            case STAY, STOP -> hold();
            case PROTECT -> protect(owner);
            case ATTACK -> attack(owner);
            case GUARD -> guard(owner);
            case GUARD_RANGED -> guardRanged(owner);
            case GUARD_POST -> {
                EnslavementManager.setGuardPost(mob, mob.blockPosition());
                EnslavementManager.setCommand(mob, EnslavedCommand.GUARD);
            }
            case PATROL -> patrol();
            case GATHER_WOOD -> gatherWood();
            case MINE -> mine();
            case DIG -> dig();
            case HARVEST -> harvest();
            case PLANT -> plant();
            case TILL -> till();
            case FERTILIZE -> fertilize();
            case RETURN_HOME -> goHome();
            case TORCH -> torch();
            case FISH -> fish();
            case SHEAR -> shear();
            case GROW -> grow();
            case COLLECT -> collect(owner);
            case BREED -> breed(owner);
            case SORT_CHESTS -> sortChests();
            case DEPOSIT -> deposit();
            case HUNT -> hunt(owner);
            case SHOOT -> shoot(owner);
            case EXPLODE -> explode(owner);
            case HEAL -> heal(owner);
            case DANCE -> dance();
            case SIT -> sit();
            case BUTLER -> butler.tick(mob, owner, (ServerLevel) mob.level());
            case CRAFT -> craft.tick(mob, owner, (ServerLevel) mob.level());
            case FETCH -> fetch.tick(mob, owner, (ServerLevel) mob.level());
            default -> hold();
        }
    }

    private Player owner() {
        UUID id = EnslavementManager.ownerId(mob);
        if (id == null) {
            return null;
        }
        Player local = mob.level().getPlayerByUUID(id);
        if (local != null) {
            return local;
        }
        // The master may be in another dimension; pets still come along.
        return mob.getServer() == null ? null : mob.getServer().getPlayerList().getPlayer(id);
    }

    private void follow(Player owner, boolean come) {
        if (owner == null || !owner.isAlive()) {
            hold();
            return;
        }
        if (owner.level() != mob.level()) {
            if (owner.level() instanceof ServerLevel target) {
                teleportToLevel(target, owner);
            }
            return;
        }
        double distance = mob.distanceToSqr(owner);
        if (come && distance > 24.0 * 24.0) {
            teleportNear(owner);
            return;
        }
        if (distance <= (come ? 3.0 : 4.0) * (come ? 3.0 : 4.0)) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(owner, 30.0f, 30.0f);
            return;
        }
        if (distance > 48.0 * 48.0) {
            teleportNear(owner);
            return;
        }
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(owner, 1.15);
        }
    }

    private void teleportNear(Player owner) {
        double x = owner.getX() + (mob.getRandom().nextDouble() - 0.5) * 3.0;
        double z = owner.getZ() + (mob.getRandom().nextDouble() - 0.5) * 3.0;
        mob.teleportTo(x, owner.getY(), z);
        mob.getNavigation().stop();
    }

    private void teleportToLevel(ServerLevel target, Player owner) {
        double x = owner.getX() + (mob.getRandom().nextDouble() - 0.5) * 3.0;
        double z = owner.getZ() + (mob.getRandom().nextDouble() - 0.5) * 3.0;
        mob.teleportTo(target, x, owner.getY(), z, Set.of(), mob.getYRot(), mob.getXRot());
        mob.getNavigation().stop();
    }

    private void hold() {
        mob.getNavigation().stop();
        mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
        mob.setTarget(null);
    }

    private void sit() {
        hold();
        Player owner = owner();
        if (owner != null) {
            mob.getLookControl().setLookAt(owner, 30.0f, 30.0f);
        }
    }

    private void goHome() {
        BlockPos home = EnslavementManager.home(mob);
        if (mob.blockPosition().closerThan(home, 3.0)) {
            hold();
            return;
        }
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.1);
        }
    }

    private void attack(Player owner) {
        LivingEntity target = ownerAttacker(owner);
        if (target == null) {
            target = mob.getTarget();
            if (!isValidTarget(target, owner)) {
                if (scanCooldown <= 0) {
                    scanCooldown = 20;
                    target = findHostile(owner, 24.0);
                    mob.setTarget(target);
                } else {
                    target = null;
                }
            }
        } else {
            mob.setTarget(target);
        }
        fight(target);
    }

    private void protect(Player owner) {
        if (owner == null || !owner.isAlive()) {
            hold();
            return;
        }
        LivingEntity target = ownerAttacker(owner);
        if (target == null) {
            target = mob.getTarget();
            if (!isValidTarget(target, owner)) {
                target = null;
                if (scanCooldown <= 0) {
                    scanCooldown = 15;
                    target = findHostileAt(owner.blockPosition(), 16.0, owner);
                    mob.setTarget(target);
                }
            }
        } else {
            mob.setTarget(target);
        }
        if (target != null && target.isAlive()) {
            fight(target);
            return;
        }
        double distance = mob.distanceToSqr(owner);
        if (distance > 7.0 * 7.0) {
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(owner, 1.2);
            }
        } else {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(owner, 30.0f, 30.0f);
        }
    }

    private void guard(Player owner) {
        BlockPos post = EnslavementManager.guardPost(mob);
        LivingEntity target = ownerAttacker(owner);
        if (target == null) {
            target = mob.getTarget();
            if (!isValidTarget(target, owner)) {
                target = null;
                if (scanCooldown <= 0) {
                    scanCooldown = 20;
                    target = findHostileAt(post, 20.0, owner);
                    mob.setTarget(target);
                }
            }
        }
        if (target != null && target.isAlive()) {
            fight(target);
            return;
        }
        returnToPost(post, 4.0);
    }

    /** Guard a fixed post with a bow/crossbow and never chase far from it. */
    private void guardRanged(Player owner) {
        BlockPos post = EnslavementManager.guardPost(mob);
        LivingEntity target = ownerAttacker(owner);
        if (target == null) {
            target = mob.getTarget();
            if (!isValidTarget(target, owner)) {
                target = null;
                if (scanCooldown <= 0) {
                    scanCooldown = 15;
                    target = findHostileAt(post, 28.0, owner);
                    mob.setTarget(target);
                }
            }
        }
        if (target != null && target.isAlive()) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (mob instanceof RangedAttackMob ranged) {
                if (cooldown <= 0 && mob.hasLineOfSight(target)) {
                    ranged.performRangedAttack(target, 1.0f);
                    cooldown = EnslavementManager.hasToolFor(mob, EnslavedCommand.GUARD_RANGED) ? 18 : 28;
                    if (!target.isAlive()) {
                        EnslavementManager.addKills(mob, 1);
                    }
                }
            } else if (mob.distanceToSqr(target) < 9.0) {
                fight(target);
            }
            return;
        }
        returnToPost(post, 3.0);
    }

    private void returnToPost(BlockPos post, double distance) {
        if (!mob.blockPosition().closerThan(post, distance) && mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 1.05);
        } else if (mob.blockPosition().closerThan(post, distance)) {
            mob.getNavigation().stop();
        }
    }

    private void fight(LivingEntity target) {
        if (target == null) {
            return;
        }
        if (target instanceof Enemy && mob.getHealth() < mob.getMaxHealth() * 0.3f) {
            retreat(target);
            return;
        }
        if (mob.blockPosition().closerThan(target.blockPosition(), 2.5)) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (cooldown <= 0) {
                cooldown = 20;
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(target);
                damageTool(1);
                if (!target.isAlive()) {
                    EnslavementManager.addKills(mob, 1);
                }
            }
        } else if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(target, 1.2);
        }
    }

    /** Low-health servants step back and use a bandage from their own bag. */
    private void retreat(LivingEntity threat) {
        if (mob.tickCount % 40 == 0) {
            ItemStack bandage = new ItemStack(ModItems.BANDAGE.get());
            if (EnslavementManager.stashRemoveOne(mob, bandage)) {
                mob.heal(4.0f);
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8f, 1.3f);
            }
        }
        if (mob.getNavigation().isDone()) {
            var away = mob.position().subtract(threat.position());
            if (away.lengthSqr() < 0.01) {
                away = new net.minecraft.world.phys.Vec3(1.0, 0.0, 0.0);
            }
            away = away.normalize().scale(6.0);
            BlockPos retreat = BlockPos.containing(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z);
            mob.getNavigation().moveTo(retreat.getX() + 0.5, retreat.getY(), retreat.getZ() + 0.5, 1.3);
        }
        mob.getLookControl().setLookAt(threat, 30.0f, 30.0f);
    }

    private LivingEntity ownerAttacker(Player owner) {
        if (owner == null) {
            return null;
        }
        LivingEntity attacker = owner.getLastHurtByMob();
        return isValidTarget(attacker, owner) ? attacker : null;
    }

    private boolean isValidTarget(LivingEntity target, Player owner) {
        if (target == null || !target.isAlive() || target == mob || target == owner) {
            return false;
        }
        if (ServantTeam.isFriendly(mob, target)) {
            return false;
        }
        if (target instanceof Player) {
            return false;
        }
        return !(target instanceof Mob other) || !EnslavementManager.isEnslaved(other);
    }

    private LivingEntity findHostile(Player owner, double radius) {
        BlockPos center = owner != null ? owner.blockPosition() : mob.blockPosition();
        return findHostileAt(center, radius, owner);
    }

    private LivingEntity findHostileAt(BlockPos center, double radius, Player owner) {
        AABB box = new AABB(center.getX(), center.getY(), center.getZ(),
                center.getX() + 1, center.getY() + 1, center.getZ() + 1).inflate(radius);
        List<LivingEntity> enemies = mob.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity instanceof Enemy && isValidTarget(entity, owner));
        return nearest(enemies);
    }

    // ------------------------------------------------------------------
    // Work orders
    // ------------------------------------------------------------------

    /** A servant without the right tool asks a nearby craftsman for one. */
    private void askCraftsman(EnslavedCommand job) {
        String recipe = toolRecipe(job);
        if (recipe == null) {
            return;
        }
        Mob craftsman = findCraftsman();
        if (craftsman == null) {
            craftAskCooldown = 400;
            return;
        }
        if (!EnslavementManager.craftRequests(craftsman).contains(recipe)) {
            EnslavementManager.addCraftRequest(craftsman, recipe);
            EnslavementManager.setCommand(craftsman, EnslavedCommand.CRAFT);
            CraftBook.Entry entry = CraftBook.get(recipe);
            Player owner = owner();
            if (owner != null && entry != null) {
                owner.displayClientMessage(Component.translatable("gui.s018d.craft_requested",
                    mob.getName(), craftsman.getName(), entry.result().getHoverName()), true);
            }
        }
        craftAskCooldown = 600;
    }

    private Mob findCraftsman() {
        var ownerId = EnslavementManager.ownerId(mob);
        return mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32.0), candidate ->
                candidate != mob
                    && candidate.isAlive()
                    && EnslavementManager.isEnslaved(candidate)
                    && EnslavementManager.command(candidate) == EnslavedCommand.CRAFT
                    && java.util.Objects.equals(ownerId, EnslavementManager.ownerId(candidate)))
            .stream()
            .min(Comparator.comparingDouble(mob::distanceToSqr))
            .orElse(null);
    }

    private static String toolRecipe(EnslavedCommand job) {
        return switch (job) {
            case GATHER_WOOD -> "wooden_axe";
            case MINE -> "stone_pickaxe";
            case DIG -> "stone_shovel";
            case HARVEST, PLANT, TILL -> "wooden_hoe";
            case SHEAR -> "shears";
            case FISH -> "fishing_rod";
            case HUNT, ATTACK, PROTECT, GUARD -> "wooden_sword";
            case GUARD_RANGED, SHOOT -> "bow";
            default -> null;
        };
    }

    /** Orders that use a tool the servant may pull out of its bag at any time. */
    private static boolean needsTool(EnslavedCommand job) {
        return switch (job) {
            case GATHER_WOOD, MINE, DIG, HARVEST, PLANT, TILL, SHEAR, FISH,
                 HUNT, ATTACK, PROTECT, GUARD, GUARD_RANGED, SHOOT -> true;
            default -> false;
        };
    }

    /** Jobs whose gathered-item counter can reach the configured limit. */
    private static boolean limitedJob(EnslavedCommand job) {
        return switch (job) {
            case GATHER_WOOD, MINE, DIG, HARVEST, PLANT, TILL, FERTILIZE, FISH,
                 SHEAR, GROW, COLLECT, BREED, TORCH -> true;
            default -> false;
        };
    }

    /** Loose orders where walking to a dropped item does not disturb the job. */
    private boolean autoPickupAllowed(EnslavedCommand job) {
        if (!EnslavementManager.autoPickupEnabled(mob)) {
            return false;
        }
        return switch (job) {
            case FOLLOW, COME, STAY, STOP, PATROL, RETURN_HOME, SIT, DANCE -> true;
            default -> false;
        };
    }

    /** Picks up items thrown to the servant, without touching work or combat orders. */
    private void autoPickup() {
        if (!(mob.level() instanceof ServerLevel server)) {
            return;
        }
        if (itemTarget != null && (!itemTarget.isAlive() || itemTarget.getItem().isEmpty())) {
            itemTarget = null;
        }
        if (itemTarget == null) {
            itemTarget = nearest(server.getEntitiesOfClass(ItemEntity.class,
                mob.getBoundingBox().inflate(6.0), ItemEntity::isAlive));
            if (itemTarget == null) {
                return;
            }
        }
        double distance = mob.distanceToSqr(itemTarget);
        if (distance > 6.0 * 6.0) {
            itemTarget = null;
            return;
        }
        EnslavedCommand command = EnslavementManager.command(mob);
        boolean stay = command == EnslavedCommand.STAY || command == EnslavedCommand.STOP
            || command == EnslavedCommand.SIT;
        if (distance > 4.0) {
            if (stay) {
                itemTarget = null;
                return;
            }
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(itemTarget, 1.2);
            }
            return;
        }
        ItemStack stack = itemTarget.getItem();
        if (EnslavementManager.stashAdd(mob, stack)) {
            EnslavementManager.addGatheredItems(mob, stack.getCount());
            itemTarget.discard();
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.0,
                mob.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
            itemTarget = null;
        }
    }

    private void gatherWood() {
        if (!(mob.level() instanceof ServerLevel server) || !canWork()
                || EnslavementManager.workLimitReached(mob)) {
            hold();
            return;
        }
        if (workTarget == null || !server.getBlockState(workTarget).is(BlockTags.LOGS)) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findBlock(server, 20.0, state -> state.is(BlockTags.LOGS));
        }
        workBlock(server, EnslavedCommand.GATHER_WOOD);
    }

    private void mine() {
        if (!(mob.level() instanceof ServerLevel server) || !canWork()
                || EnslavementManager.workLimitReached(mob)) {
            hold();
            return;
        }
        if (workTarget == null || !isMineTarget(server.getBlockState(workTarget))) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findBlock(server, 20.0, EnslavedMobGoal::isMineTarget);
        }
        workBlock(server, EnslavedCommand.MINE);
    }

    private void dig() {
        if (!(mob.level() instanceof ServerLevel server) || !canWork()
                || EnslavementManager.workLimitReached(mob)) {
            hold();
            return;
        }
        if (workTarget == null || !isDigTarget(server.getBlockState(workTarget))) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findBlock(server, 16.0, EnslavedMobGoal::isDigTarget);
        }
        workBlock(server, EnslavedCommand.DIG);
    }

    private void harvest() {
        if (!(mob.level() instanceof ServerLevel server) || !canWork()
                || EnslavementManager.workLimitReached(mob)) {
            hold();
            return;
        }
        if (workTarget == null || !isRipeCrop(server.getBlockState(workTarget))) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findBlock(server, 14.0, EnslavedMobGoal::isRipeCrop);
        }
        if (workTarget == null) {
            return;
        }
        if (!mob.blockPosition().closerThan(workTarget, 2.2)) {
            if (!moveTo(workTarget)) {
                workTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        BlockState state = server.getBlockState(workTarget);
        if (state.getBlock() instanceof CropBlock) {
            Block cropBlock = state.getBlock();
            storeDrops(server, workTarget, state);
            server.setBlockAndUpdate(workTarget, Blocks.AIR.defaultBlockState());
            Item seed = seedFor(cropBlock);
            if (seed != null && EnslavementManager.stashRemoveOne(mob, seed)) {
                server.setBlockAndUpdate(workTarget, cropBlock.defaultBlockState());
            }
            damageTool(1);
            mob.swing(InteractionHand.MAIN_HAND);
            EnslavementManager.addWorkedBlocks(mob, 1);
            cooldown = workCooldown(EnslavedCommand.HARVEST);
        }
        workTarget = null;
    }

    private void plant() {
        if (!(mob.level() instanceof ServerLevel server) || !canWork()) {
            hold();
            return;
        }
        if (workTarget == null || !server.getBlockState(workTarget.below()).is(Blocks.FARMLAND)
                || !server.getBlockState(workTarget).isAir()) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findPlantSpot(server, 14.0);
        }
        if (workTarget == null) {
            hold();
            return;
        }
        if (!mob.blockPosition().closerThan(workTarget, 2.2)) {
            if (!moveTo(workTarget)) {
                workTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        Block crop = takeSeedCrop();
        if (crop == null) {
            // No seeds in the bag: wait for the master to hand some over.
            cooldown = 40;
            return;
        }
        server.setBlockAndUpdate(workTarget, crop.defaultBlockState());
        mob.swing(InteractionHand.MAIN_HAND);
        cooldown = workCooldown(EnslavedCommand.PLANT);
        workTarget = null;
    }

    /** Turns grass and dirt into farmland with the hoe in hand. */
    private void till() {
        if (!(mob.level() instanceof ServerLevel server) || !canWork()) {
            hold();
            return;
        }
        if (!EnslavementManager.hasToolFor(mob, EnslavedCommand.TILL)) {
            cooldown = 40;
            hold();
            return;
        }
        if (workTarget == null || !isTillTarget(server.getBlockState(workTarget))) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findBlock(server, 14.0, EnslavedMobGoal::isTillTarget);
        }
        if (workTarget == null) {
            hold();
            return;
        }
        if (!mob.blockPosition().closerThan(workTarget, 2.2)) {
            if (!moveTo(workTarget)) {
                workTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        BlockState state = server.getBlockState(workTarget);
        if (!server.getBlockState(workTarget.above()).isAir()) {
            workTarget = null;
            return;
        }
        if (state.is(Blocks.COARSE_DIRT)) {
            server.setBlockAndUpdate(workTarget, Blocks.DIRT.defaultBlockState());
        } else {
            server.setBlockAndUpdate(workTarget, Blocks.FARMLAND.defaultBlockState());
        }
        server.playSound(null, workTarget, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, workTarget.getX() + 0.5,
            workTarget.getY() + 1.0, workTarget.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
        damageTool(1);
        mob.swing(InteractionHand.MAIN_HAND);
        EnslavementManager.addWorkedBlocks(mob, 1);
        cooldown = workCooldown(EnslavedCommand.TILL);
        workTarget = null;
    }

    private static boolean isTillTarget(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
            || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
    }

    /** Uses bone meal from the bag on crops that are still growing. */
    private void fertilize() {
        if (!(mob.level() instanceof ServerLevel server) || !canWork()) {
            hold();
            return;
        }
        if (workTarget == null || !isFertilizable(server, workTarget)) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findBlock(server, 14.0,
                state -> state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state));
        }
        if (workTarget == null) {
            cooldown = 60;
            hold();
            return;
        }
        if (!mob.blockPosition().closerThan(workTarget, 2.2)) {
            if (!moveTo(workTarget)) {
                workTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        if (!EnslavementManager.stashRemoveOne(mob, Items.BONE_MEAL)) {
            // Out of bone meal: wait for the master to restock the bag.
            cooldown = 60;
            return;
        }
        BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), server, workTarget, null);
        server.playSound(null, workTarget, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, workTarget.getX() + 0.5,
            workTarget.getY() + 1.0, workTarget.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
        mob.swing(InteractionHand.MAIN_HAND);
        cooldown = 30;
        workTarget = null;
    }

    private static boolean isFertilizable(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)
            && crop.isValidBonemealTarget(server, pos, state);
    }

    private void fish() {
        if (!(mob.level() instanceof ServerLevel server) || EnslavementManager.workLimitReached(mob)) {
            return;
        }
        if (workTarget == null || !server.getBlockState(workTarget).is(Blocks.WATER)) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            workTarget = findBlock(server, 16.0, state -> state.is(Blocks.WATER));
        }
        if (workTarget == null) {
            hold();
            return;
        }
        BlockPos stand = server.getBlockState(workTarget.above()).isAir() ? workTarget.above() : workTarget;
        if (!mob.blockPosition().closerThan(stand, 3.5)) {
            if (!moveTo(stand)) {
                workTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(workTarget.getX() + 0.5, workTarget.getY() + 0.5,
            workTarget.getZ() + 0.5, 30.0f, 30.0f);
        if (cooldown > 0) {
            return;
        }
        boolean rod = EnslavementManager.hasToolFor(mob, EnslavedCommand.FISH);
        cooldown = rod ? 40 + mob.getRandom().nextInt(40) : 100 + mob.getRandom().nextInt(60);
        server.sendParticles(ParticleTypes.SPLASH, workTarget.getX() + 0.5,
            workTarget.getY() + 1.0, workTarget.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.05);
        ItemStack caught = new ItemStack(mob.getRandom().nextInt(5) == 0 ? Items.SALMON : Items.COD);
        if (!EnslavementManager.stashAdd(mob, caught)) {
            ItemEntity drop = new ItemEntity(server, mob.getX(), mob.getY() + 0.4, mob.getZ(), caught);
            server.addFreshEntity(drop);
        } else {
            EnslavementManager.addGatheredItems(mob, 1);
        }
    }

    /** Shears nearby sheep with the shears in hand; wool goes to the bag. */
    private void shear() {
        if (!(mob.level() instanceof ServerLevel server) || cooldown > 0
                || EnslavementManager.workLimitReached(mob)) {
            return;
        }
        if (!EnslavementManager.hasToolFor(mob, EnslavedCommand.SHEAR)) {
            cooldown = 40;
            return;
        }
        AABB box = mob.getBoundingBox().inflate(16.0);
        Sheep target = nearest(server.getEntitiesOfClass(Sheep.class, box, Sheep::readyForShearing));
        if (target == null) {
            hold();
            cooldown = 40;
            return;
        }
        if (!mob.blockPosition().closerThan(target.blockPosition(), 2.0)) {
            moveTo(target.blockPosition());
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        int woolCount = 1 + mob.getRandom().nextInt(3);
        ItemStack wool = new ItemStack(woolItem(target), woolCount);
        if (!EnslavementManager.stashAdd(mob, wool)) {
            target.spawnAtLocation(wool.copy());
        } else {
            EnslavementManager.addGatheredItems(mob, woolCount);
        }
        target.setSheared(true);
        server.playSound(null, target, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0f, 1.0f);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 0.8,
            target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        damageTool(1);
        mob.swing(InteractionHand.MAIN_HAND);
        cooldown = 30;
    }

    private static Item woolItem(Sheep sheep) {
        return BuiltInRegistries.ITEM.get(
            ResourceLocation.withDefaultNamespace(sheep.getColor().getSerializedName() + "_wool"));
    }

    /** Feeds baby animals so they grow up faster. */
    private void grow() {
        if (!(mob.level() instanceof ServerLevel server) || cooldown > 0) {
            return;
        }
        AABB box = mob.getBoundingBox().inflate(16.0);
        Animal target = nearest(server.getEntitiesOfClass(Animal.class, box,
            animal -> animal.isAlive() && animal.isBaby()));
        if (target == null) {
            hold();
            cooldown = 40;
            return;
        }
        if (!mob.blockPosition().closerThan(target.blockPosition(), 2.0)) {
            moveTo(target.blockPosition());
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (!consumeFood(target, 1)) {
            cooldown = 40;
            return;
        }
        target.ageUp(60);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 0.8,
            target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        cooldown = 40;
    }

    private void collect(Player owner) {
        if (!(mob.level() instanceof ServerLevel server) || EnslavementManager.workLimitReached(mob)) {
            return;
        }
        if (itemTarget == null || !itemTarget.isAlive() || itemTarget.getItem().isEmpty()) {
            itemTarget = null;
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 10;
            AABB box = mob.getBoundingBox().inflate(16.0);
            itemTarget = nearest(server.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive));
        }
        if (itemTarget == null) {
            if (owner != null) {
                follow(owner, false);
            } else {
                hold();
            }
            return;
        }
        if (!mob.blockPosition().closerThan(itemTarget.blockPosition(), 2.0)) {
            if (!moveTo(itemTarget.blockPosition())) {
                itemTarget = null;
                scanCooldown = Math.max(scanCooldown, 20);
            }
            return;
        }
        ItemStack stack = itemTarget.getItem();
        if (EnslavementManager.stashAdd(mob, stack)) {
            EnslavementManager.addGatheredItems(mob, stack.getCount());
            itemTarget.discard();
        } else if (owner != null && owner.isAlive() && owner.distanceToSqr(mob) < 32.0 * 32.0
                && owner.getInventory().add(stack.copy())) {
            itemTarget.discard();
        } else {
            itemTarget.setPos(mob.getX(), mob.getY(), mob.getZ());
        }
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.0,
            mob.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
        itemTarget = null;
        cooldown = 5;
    }

    private void torch() {
        if (!(mob.level() instanceof ServerLevel server) || cooldown > 0
                || !server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        BlockPos home = EnslavementManager.home(mob);
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos pos = home.offset(mob.getRandom().nextInt(9) - 4,
                mob.getRandom().nextInt(5) - 2, mob.getRandom().nextInt(9) - 4);
            if (!server.isLoaded(pos) || !server.getBlockState(pos).isAir()) {
                continue;
            }
            BlockPos below = pos.below();
            if (!server.getBlockState(below).isFaceSturdy(server, below, Direction.UP)) {
                continue;
            }
            if (server.getMaxLocalRawBrightness(pos) > 7) {
                continue;
            }
            if (!consumeTorch()) {
                // Out of torches: wait until the master restocks the bag.
                cooldown = 40;
                return;
            }
            server.setBlockAndUpdate(pos, Blocks.TORCH.defaultBlockState());
            server.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.6,
                pos.getZ() + 0.5, 5, 0.1, 0.1, 0.1, 0.01);
            cooldown = 40;
            return;
        }
        cooldown = 20;
    }

    private void breed(Player owner) {
        if (owner == null || !(mob.level() instanceof ServerLevel server) || cooldown > 0) {
            return;
        }
        if (owner.distanceToSqr(mob) > 24.0 * 24.0) {
            follow(owner, false);
            return;
        }
        AABB box = mob.getBoundingBox().inflate(12.0);
        List<Animal> animals = server.getEntitiesOfClass(Animal.class, box,
            animal -> !animal.isBaby() && animal.canFallInLove() && !animal.isInLove());
        for (int i = 0; i < animals.size(); i++) {
            for (int j = i + 1; j < animals.size(); j++) {
                Animal first = animals.get(i);
                Animal second = animals.get(j);
                if (first.getClass() != second.getClass()) {
                    continue;
                }
                if (!consumeFood(first, 2)) {
                    cooldown = 60;
                    return;
                }
                first.setInLove(owner);
                second.setInLove(owner);
                server.sendParticles(ParticleTypes.HEART, first.getX(), first.getY() + 1.0,
                    first.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                cooldown = 200;
                return;
            }
        }
        cooldown = 60;
    }

    /** Feeds a pair from the bag: two pieces of the right food. */
    private boolean consumeFood(Animal animal, int count) {
        Item[] foods = foodsFor(animal);
        if (foods == null) {
            return false;
        }
        for (Item food : foods) {
            if (EnslavementManager.stashRemove(mob, food, count)) {
                return true;
            }
        }
        return false;
    }

    private static Item[] foodsFor(Animal animal) {
        if (animal instanceof Cow || animal instanceof Sheep) {
            return new Item[]{Items.WHEAT};
        }
        if (animal instanceof Pig) {
            return new Item[]{Items.CARROT, Items.POTATO, Items.BEETROOT};
        }
        if (animal instanceof Chicken) {
            return new Item[]{Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS,
                Items.MELON_SEEDS, Items.PUMPKIN_SEEDS};
        }
        if (animal instanceof Rabbit) {
            return new Item[]{Items.CARROT, Items.DANDELION};
        }
        return null;
    }

    private void sortChests() {
        if (!(mob.level() instanceof ServerLevel server)) {
            return;
        }
        Container container = containerAt(server, containerTarget);
        if (container == null) {
            containerTarget = null;
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 40;
            containerTarget = nextUnsortedChest(server, EnslavementManager.home(mob));
            return;
        }
        if (!mob.blockPosition().closerThan(containerTarget, 3.0)) {
            if (!moveTo(containerTarget)) {
                containerTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(containerTarget.getX() + 0.5, containerTarget.getY() + 0.5,
            containerTarget.getZ() + 0.5, 30.0f, 30.0f);
        if (cooldown > 0) {
            return;
        }
        sortContainer(container);
        sortedChests.add(containerTarget);
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, containerTarget.getX() + 0.5,
            containerTarget.getY() + 1.0, containerTarget.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
        containerTarget = null;
        cooldown = 40;
    }

    private BlockPos nextUnsortedChest(ServerLevel server, BlockPos home) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(home.offset(-16, -4, -16), home.offset(16, 4, 16))) {
            if (sortedChests.contains(pos)) {
                continue;
            }
            if (server.getBlockEntity(pos) instanceof Container) {
                candidates.add(pos.immutable());
            }
        }
        if (candidates.isEmpty()) {
            sortedChests.clear();
            return null;
        }
        return candidates.stream()
            .min(Comparator.comparingDouble(pos -> pos.distSqr(mob.blockPosition())))
            .orElse(null);
    }

    private void deposit() {
        if (!(mob.level() instanceof ServerLevel server)) {
            return;
        }
        if (EnslavementManager.stashCount(mob) == 0) {
            hold();
            return;
        }
        Container container = containerAt(server, containerTarget);
        if (container == null) {
            containerTarget = null;
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 40;
            BlockPos wanted = EnslavementManager.depositTarget(mob);
            containerTarget = wanted != null && containerAt(server, wanted) != null
                ? wanted
                : EnslavementManager.findContainerPos(server, EnslavementManager.home(mob), 16.0);
            return;
        }
        if (!mob.blockPosition().closerThan(containerTarget, 3.0)) {
            if (!moveTo(containerTarget)) {
                containerTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(containerTarget.getX() + 0.5, containerTarget.getY() + 0.5,
            containerTarget.getZ() + 0.5, 30.0f, 30.0f);
        if (cooldown > 0) {
            return;
        }
        List<ItemStack> items = EnslavementManager.stashTakeAll(mob);
        boolean stored = false;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            insertIntoContainer(container, stack);
            if (stack.isEmpty()) {
                stored = true;
            } else if (!EnslavementManager.stashAdd(mob, stack)) {
                mob.spawnAtLocation(stack);
            }
        }
        server.sendParticles(stored ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE,
            containerTarget.getX() + 0.5, containerTarget.getY() + 1.0, containerTarget.getZ() + 0.5,
            8, 0.3, 0.3, 0.3, 0.02);
        cooldown = 60;
    }

    private void hunt(Player owner) {
        LivingEntity target = mob.getTarget();
        if (!isHuntTarget(target)) {
            if (scanCooldown > 0) {
                return;
            }
            scanCooldown = 20;
            AABB box = mob.getBoundingBox().inflate(24.0);
            target = nearest(mob.level().getEntitiesOfClass(Animal.class, box, this::isHuntTarget));
            mob.setTarget(target);
        }
        if (target != null) {
            fight(target);
        } else if (owner != null) {
            follow(owner, false);
        } else {
            hold();
        }
    }

    private boolean isHuntTarget(LivingEntity entity) {
        if (!(entity instanceof Animal animal) || !entity.isAlive() || animal.isBaby()) {
            return false;
        }
        return animal instanceof Cow || animal instanceof Pig || animal instanceof Sheep
            || animal instanceof Chicken || animal instanceof Rabbit;
    }

    private void shoot(Player owner) {
        LivingEntity target = findTarget(owner, 24.0);
        if (target == null) {
            hold();
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (mob instanceof RangedAttackMob ranged) {
            if (cooldown <= 0) {
                ranged.performRangedAttack(target, 1.0f);
                if (!target.isAlive()) {
                    EnslavementManager.addKills(mob, 1);
                }
                cooldown = EnslavementManager.hasToolFor(mob, EnslavedCommand.SHOOT) ? 20 : 30;
            }
        } else {
            fight(target);
        }
    }

    private void explode(Player owner) {
        if (!(mob instanceof Creeper creeper)) {
            attack(owner);
            return;
        }
        LivingEntity target = findTarget(owner, 12.0);
        if (target == null) {
            hold();
            return;
        }
        boolean grief = mob.level() instanceof ServerLevel server
            && server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (mob.distanceToSqr(target) <= 4.0 * 4.0) {
            if (!grief) {
                attack(owner);
                return;
            }
            if (mob.level() instanceof ServerLevel server) {
                server.explode(creeper, mob.getX(), mob.getY(), mob.getZ(), 3.0f,
                    Level.ExplosionInteraction.MOB);
            }
            creeper.discard();
        } else {
            moveTo(target.blockPosition());
        }
    }

    private void heal(Player owner) {
        if (!(mob.level() instanceof ServerLevel server) || cooldown > 0) {
            return;
        }
        boolean healed = false;
        if (owner != null && owner.isAlive() && owner.distanceToSqr(mob) < 16.0 * 16.0
            && owner.getHealth() < owner.getMaxHealth()) {
            owner.heal(4.0f);
            server.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.5,
                owner.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
            healed = true;
        }
        AABB box = mob.getBoundingBox().inflate(16.0);
        for (Mob ally : server.getEntitiesOfClass(Mob.class, box,
                other -> other != mob && EnslavementManager.isEnslaved(other)
                    && EnslavementManager.isOwner(other, owner))) {
            if (ally.getHealth() < ally.getMaxHealth()) {
                ally.heal(3.0f);
                healed = true;
            }
        }
        cooldown = healed ? 60 : 20;
    }

    private void dance() {
        hold();
        mob.setYRot(mob.getYRot() + 12.0f);
        mob.yBodyRot = mob.getYRot();
        mob.yHeadRot = mob.getYRot();
        if (cooldown <= 0) {
            mob.setDeltaMovement(0.0, 0.35, 0.0);
            cooldown = 25;
        }
        if (mob.level() instanceof ServerLevel server && mob.getRandom().nextInt(12) == 0) {
            server.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + 1.6, mob.getZ(),
                3, 0.4, 0.2, 0.4, 0.1);
        }
    }

    private LivingEntity findTarget(Player owner, double radius) {
        LivingEntity attacker = ownerAttacker(owner);
        return attacker != null ? attacker : findHostile(owner, radius);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean canWork() {
        return mob.level() instanceof ServerLevel server
            && server.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private void workBlock(ServerLevel server, EnslavedCommand job) {
        if (workTarget == null) {
            return;
        }
        if (!mob.blockPosition().closerThan(workTarget, 2.2)) {
            if (!moveTo(workTarget)) {
                // Unreachable: forget it and look somewhere else.
                workTarget = null;
                scanCooldown = Math.max(scanCooldown, 40);
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        BlockState state = server.getBlockState(workTarget);
        storeDrops(server, workTarget, state);
        server.destroyBlock(workTarget, false, mob);
        damageTool(1);
        mob.swing(InteractionHand.MAIN_HAND);
        EnslavementManager.addWorkedBlocks(mob, 1);
        cooldown = workCooldown(job);
        workTarget = null;
    }

    private void storeDrops(ServerLevel server, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, server, pos, server.getBlockEntity(pos),
            mob, mob.getMainHandItem());
        int gathered = 0;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            if (!EnslavementManager.stashAdd(mob, drop)) {
                mob.spawnAtLocation(drop);
            } else {
                gathered += drop.getCount();
            }
        }
        if (gathered > 0) {
            EnslavementManager.addGatheredItems(mob, gathered);
        }
    }

    private int workCooldown(EnslavedCommand job) {
        return EnslavementManager.hasToolFor(mob, job) ? 8 : 20;
    }

    /** Tool wear. Broken tools simply vanish, exactly like player tools. */
    private void damageTool(int amount) {
        ItemStack hand = mob.getMainHandItem();
        if (!hand.isEmpty() && hand.isDamageableItem()) {
            hand.hurtAndBreak(amount, mob, EquipmentSlot.MAINHAND);
        }
    }

    private boolean consumeTorch() {
        return EnslavementManager.stashRemoveOne(mob, Items.TORCH)
            || EnslavementManager.stashRemoveOne(mob, Items.SOUL_TORCH);
    }

    private BlockPos findPlantSpot(ServerLevel server, double radius) {
        BlockPos farmland = findBlock(server, radius, state -> state.is(Blocks.FARMLAND));
        if (farmland == null) {
            return null;
        }
        BlockPos above = farmland.above();
        return server.getBlockState(above).isAir() ? above : null;
    }

    private Block takeSeedCrop() {
        if (EnslavementManager.stashRemoveOne(mob, Items.WHEAT_SEEDS)) {
            return Blocks.WHEAT;
        }
        if (EnslavementManager.stashRemoveOne(mob, Items.BEETROOT_SEEDS)) {
            return Blocks.BEETROOTS;
        }
        if (EnslavementManager.stashRemoveOne(mob, Items.CARROT)) {
            return Blocks.CARROTS;
        }
        if (EnslavementManager.stashRemoveOne(mob, Items.POTATO)) {
            return Blocks.POTATOES;
        }
        return null;
    }

    private static Item seedFor(Block crop) {
        if (crop == Blocks.WHEAT) {
            return Items.WHEAT_SEEDS;
        }
        if (crop == Blocks.BEETROOTS) {
            return Items.BEETROOT_SEEDS;
        }
        if (crop == Blocks.CARROTS) {
            return Items.CARROT;
        }
        if (crop == Blocks.POTATOES) {
            return Items.POTATO;
        }
        return null;
    }

    private Container containerAt(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }

    private void sortContainer(Container container) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack base = stacks.get(i);
            if (base.isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < stacks.size(); j++) {
                ItemStack other = stacks.get(j);
                if (other.isEmpty() || !ItemStack.isSameItemSameComponents(base, other)) {
                    continue;
                }
                int space = base.getMaxStackSize() - base.getCount();
                int move = Math.min(space, other.getCount());
                base.grow(move);
                other.shrink(move);
            }
        }
        stacks.removeIf(ItemStack::isEmpty);
        stacks.sort(Comparator.comparing(EnslavementManager::itemId));
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
        }
        container.setChanged();
    }

    private void insertIntoContainer(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int space = Math.min(container.getMaxStackSize(), existing.getMaxStackSize()) - existing.getCount();
                int move = Math.min(space, stack.getCount());
                existing.grow(move);
                stack.shrink(move);
                container.setItem(i, existing);
            }
        }
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            if (container.getItem(i).isEmpty()) {
                int move = Math.min(Math.min(container.getMaxStackSize(), stack.getMaxStackSize()),
                    stack.getCount());
                ItemStack copy = stack.copy();
                copy.setCount(move);
                container.setItem(i, copy);
                stack.shrink(move);
            }
        }
        container.setChanged();
    }

    /** Returns false when no route to the position exists. */
    private boolean moveTo(BlockPos pos) {
        if (mob.getNavigation().isDone()) {
            return mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.1);
        }
        return true;
    }

    private BlockPos findBlock(ServerLevel level, double radius,
                               java.util.function.Predicate<BlockState> match) {
        BlockPos home = EnslavementManager.home(mob);
        int r = (int) radius;
        int span = r * 2 + 1;
        for (int attempt = 0; attempt < 256; attempt++) {
            BlockPos pos = home.offset(mob.getRandom().nextInt(span) - r,
                mob.getRandom().nextInt(13) - 6, mob.getRandom().nextInt(span) - r);
            if (level.isLoaded(pos) && match.test(level.getBlockState(pos))) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean isMineTarget(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.NETHERRACK) || state.is(Blocks.COBBLESTONE)
                || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES);
    }

    private static boolean isDigTarget(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MUD);
    }

    private static boolean isRipeCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    private <T extends net.minecraft.world.entity.Entity> T nearest(List<T> entities) {
        T best = null;
        double bestDistance = Double.MAX_VALUE;
        for (T entity : entities) {
            double distance = entity.distanceToSqr(mob);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private void patrol() {
        if (patrolCooldown > 0) {
            patrolCooldown--;
            return;
        }
        if (!mob.getNavigation().isDone()) {
            return;
        }
        BlockPos home = EnslavementManager.home(mob);
        BlockPos target = home.offset(mob.getRandom().nextInt(13) - 6, mob.getRandom().nextInt(3) - 1,
            mob.getRandom().nextInt(13) - 6);
        mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        patrolCooldown = 60;
    }
}


