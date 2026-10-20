package com.s018d.enslavement;

import com.s018d.S018dMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/** Re-attaches the control goal after world reloads and kills. */
@EventBusSubscriber(modid = S018dMod.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EnslavementEvents {
    private EnslavementEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Mob mob) {
            EnslavementManager.ensureGoal(mob);
        }
    }

    /** Sneak + right click with an empty hand opens the servant bag. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Mob mob)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return;
        }
        if (!EnslavementManager.isEnslaved(mob) || !EnslavementManager.isOwner(mob, player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ServantBagMenu.open(player, mob);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) {
            UUID owner = EnslavementManager.ownerId(mob);
            if (EnslavementManager.isEnslaved(mob)) {
                EnslavementManager.dropServantItems(mob);
                if (owner != null && mob.getServer() != null) {
                    ServerPlayer player = mob.getServer().getPlayerList().getPlayer(owner);
                    if (player != null) {
                        player.displayClientMessage(Component.translatable(
                            "gui.s018d.ens_servant_died", mob.getName()), false);
                    }
                }
            }
            mob.getPersistentData().remove("s018d_enslaved_owner");
            mob.getPersistentData().remove("s018d_enslaved_command");
            mob.getPersistentData().remove("s018d_enslaved_home");
        }
    }
}
