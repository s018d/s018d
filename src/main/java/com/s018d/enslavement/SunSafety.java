package com.s018d.enslavement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Undead servants value their skin. During the day they look for a roof,
 * a tree or the shaded side of the house instead of standing in direct sun.
 */
public final class SunSafety {
    private static final int SEARCH_RADIUS = 14;

    private SunSafety() {
    }

    /** Returns true when the servant is busy walking to shade this tick. */
    public static boolean tick(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !sunSensitive(mob)) {
            return false;
        }
        BlockPos here = mob.blockPosition();
        boolean exposed = level.isDay() && level.canSeeSky(here)
            && !mob.isInWaterRainOrBubble();
        if (!exposed) {
            if (mob.isOnFire()) {
                mob.clearFire();
            }
            return false;
        }

        BlockPos shade = findShade(level, here);
        if (shade != null) {
            if (mob.blockPosition().closerThan(shade, 1.6)) {
                mob.getNavigation().stop();
                mob.clearFire();
                return false;
            }
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(shade.getX() + 0.5, shade.getY(), shade.getZ() + 0.5, 1.15);
            }
            return true;
        }

        BlockPos home = EnslavementManager.home(mob);
        if (!mob.blockPosition().closerThan(home, 3.0)) {
            if (mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.15);
            }
            return true;
        }
        // No shade exists. The damage guard below keeps the servant alive;
        // it still stays home and waits for the player to build a roof.
        mob.clearFire();
        return false;
    }

    public static boolean sunSensitive(Mob mob) {
        return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof Phantom;
    }

    private static BlockPos findShade(ServerLevel level, BlockPos center) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-SEARCH_RADIUS, -4, -SEARCH_RADIUS),
            center.offset(SEARCH_RADIUS, 4, SEARCH_RADIUS))) {
            if (level.canSeeSky(pos) || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
                continue;
            }
            if (level.getFluidState(pos).isEmpty() == false) {
                continue;
            }
            if (!level.getBlockState(pos.below()).blocksMotion()) {
                continue;
            }
            double distance = pos.distSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }
}
