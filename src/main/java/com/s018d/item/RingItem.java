package com.s018d.item;

import com.s018d.enslavement.EnslavementManager;
import com.s018d.enslavement.MarriageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Selection-based marriage flow:
 * engagement ring on the first servant, wedding ring on the second, then a
 * bonding ring on either spouse to welcome a child.
 */
public class RingItem extends Item {
    public enum Type {
        ENGAGEMENT,
        WEDDING,
        BONDING
    }

    private final Type type;

    public RingItem(Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Mob mob) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!EnslavementManager.isOwner(mob, player)) {
            return InteractionResult.PASS;
        }
        boolean success = switch (type) {
            case ENGAGEMENT -> MarriageManager.select(serverPlayer, mob);
            case WEDDING -> MarriageManager.tryMarry(serverPlayer, mob);
            case BONDING -> MarriageManager.tryBond(serverPlayer, mob);
        };
        return success
            ? InteractionResult.sidedSuccess(player.level().isClientSide())
            : InteractionResult.FAIL;
    }
}
