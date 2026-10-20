package com.s018d.item;

import com.s018d.enslavement.EnslavementManager;
import com.s018d.entity.ServantEntity;
import com.s018d.network.ModPackets;
import com.s018d.network.RequestServantsPacket;
import com.s018d.util.ClientScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Claims creatures and opens the servant menus. */
public class ServantControllerItem extends Item {
    public ServantControllerItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
                                                  InteractionHand hand) {
        if (!player.level().isClientSide()) {
            if (player.isShiftKeyDown() && entity instanceof Mob mob
                && EnslavementManager.isEnslaved(mob) && EnslavementManager.isOwner(mob, player)) {
                com.s018d.enslavement.ServantBagMenu.open(
                    (net.minecraft.server.level.ServerPlayer) player, mob);
                return InteractionResult.SUCCESS;
            }
            if (entity instanceof ServantEntity servant) {
                if (!EnslavementManager.isEnslaved(servant)) {
                    if (EnslavementManager.enslave(servant, player)) {
                        servant.setCustomName(Component.translatable("entity.s018d.servant"));
                        servant.setCustomNameVisible(true);
                        player.displayClientMessage(Component.translatable("gui.s018d.servant_claimed"), false);
                    }
                } else if (!EnslavementManager.isOwner(servant, player)) {
                    player.displayClientMessage(Component.translatable("gui.s018d.servant_denied"), false);
                    return InteractionResult.SUCCESS;
                }
                ModPackets.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                    new com.s018d.network.ServantDataPacket(servant, true));
                return InteractionResult.SUCCESS;
            }
            if (entity instanceof Mob mob) {
                if (!EnslavementManager.isEnslaved(mob)) {
                    if (EnslavementManager.enslave(mob, player)) {
                        player.displayClientMessage(Component.translatable(
                            "gui.s018d.enslaved_claimed", mob.getName()), false);
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(),
                            SoundSource.MASTER, 0.7f, 1.6f);
                    }
                } else if (!EnslavementManager.isOwner(mob, player)) {
                    player.displayClientMessage(Component.translatable("gui.s018d.enslaved_denied"), false);
                    return InteractionResult.SUCCESS;
                }
                ModPackets.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                    new com.s018d.network.EnslavedDataPacket(mob, true));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if (entity instanceof ServantEntity || entity instanceof Mob) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                ClientScreens.openEnslaved(-1, "", "FOLLOW", "", -1, 0.0f, 0.0f, 0, 0, 0, 0, 0, "");
            } else {
                ModPackets.sendToServer(new RequestServantsPacket());
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.s018d.servant_controller"));
        tooltip.add(Component.translatable("tooltip.s018d.servant_controller_keys")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
