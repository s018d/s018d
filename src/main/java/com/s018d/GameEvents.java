package com.s018d;

import com.s018d.enslavement.EnslavedCommand;
import com.s018d.enslavement.EnslavementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Group combat: whenever the owner attacks a living target, all of their
 * guards and bodyguards within range join the fight.
 */
@EventBusSubscriber(modid = S018dMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class GameEvents {
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target) || !target.isAlive()) {
            return;
        }
        alertGuardians(player, target);
    }

    @SubscribeEvent
    public static void onPlayerDamaged(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
            || attacker == player || !attacker.isAlive()) {
            return;
        }
        alertGuardians(player, attacker);
    }

    private static void alertGuardians(Player player, LivingEntity target) {
        AABB box = player.getBoundingBox().inflate(64.0);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
                mob -> EnslavementManager.isOwner(mob, player)
                    && isGuardOrder(EnslavementManager.command(mob)))) {
            mob.setTarget(target);
        }
    }

    private static boolean isGuardOrder(EnslavedCommand command) {
        return command == EnslavedCommand.PROTECT
            || command == EnslavedCommand.GUARD
            || command == EnslavedCommand.ATTACK;
    }
}
