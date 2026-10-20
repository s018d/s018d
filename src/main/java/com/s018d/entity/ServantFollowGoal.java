package com.s018d.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Follows the owner while the servant has no queued task. */
public class ServantFollowGoal extends Goal {
    private final ServantEntity servant;

    public ServantFollowGoal(ServantEntity servant) {
        this.servant = servant;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (servant.getOwnerId() == null || servant.hasQueuedTask()) {
            return false;
        }
        Player owner = servant.ownerPlayer();
        return owner != null && servant.distanceTo(owner) > 4.0;
    }

    @Override
    public boolean canContinueToUse() {
        if (servant.getOwnerId() == null || servant.hasQueuedTask()) {
            return false;
        }
        Player owner = servant.ownerPlayer();
        return owner != null && servant.distanceTo(owner) > 3.0;
    }

    @Override
    public void tick() {
        Player owner = servant.ownerPlayer();
        if (owner != null) {
            servant.getNavigation().moveTo(owner, 1.1);
        }
    }
}
