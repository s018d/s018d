package com.s018d.enslavement;

import com.s018d.S018dMod;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Server-side protection that works for every enslaved mob, including hostile
 * ones. It is intentionally event based: vanilla target goals may still run,
 * but they can never keep the owner or a teammate as a target, and friendly
 * damage is cancelled before armour or enchantments are processed.
 */
@EventBusSubscriber(modid = S018dMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class FriendlyFireEvents {
    private FriendlyFireEvents() {
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && ServantTeam.isFriendly(event.getEntity(), target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (isFriendlyDamage(event.getEntity(), attacker) || isFriendlyDamage(event.getEntity(), direct)) {
            event.setCanceled(true);
            return;
        }
        // Daylight burning is replaced by the servant walking into shade.
        if (event.getEntity() instanceof Mob mob
            && EnslavementManager.isEnslaved(mob)
            && SunSafety.sunSensitive(mob)
            && source.is(DamageTypes.ON_FIRE)) {
            event.setCanceled(true);
            mob.clearFire();
        }
    }

    private static boolean isFriendlyDamage(LivingEntity victim, Entity attacker) {
        if (attacker == null || attacker == victim) {
            return false;
        }
        return ServantTeam.isFriendly(attacker, victim);
    }

    /** Clears a stale target that vanilla AI may have assigned before we could react. */
    public static void guardTarget(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target != null && ServantTeam.isFriendly(mob, target)) {
            mob.setTarget(null);
        }
    }
}
