package com.s018d.enslavement;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Resolves which player a creature belongs to, so servants, pets and their
 * owner are treated as one team. This is the single source of truth used by
 * the friendly-fire guards.
 */
public final class ServantTeam {
    private ServantTeam() {
    }

    @Nullable
    public static UUID ownerOf(Entity entity) {
        if (entity instanceof Player player) {
            return player.getUUID();
        }
        if (entity instanceof Mob mob && EnslavementManager.isEnslaved(mob)) {
            return EnslavementManager.ownerId(mob);
        }
        if (entity instanceof TamableAnimal tame) {
            return tame.getOwnerUUID();
        }
        return null;
    }

    public static boolean sameTeam(Entity first, Entity second) {
        if (first == null || second == null) {
            return false;
        }
        UUID owner = ownerOf(first);
        return owner != null && owner.equals(ownerOf(second));
    }

    public static boolean isOwnedBy(Entity entity, UUID owner) {
        return owner != null && owner.equals(ownerOf(entity));
    }

    /** Servants never hurt their owner, their owner's servants or the owner's pets. */
    public static boolean isFriendly(Entity attacker, Entity victim) {
        return sameTeam(attacker, victim);
    }
}
