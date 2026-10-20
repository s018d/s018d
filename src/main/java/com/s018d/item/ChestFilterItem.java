package com.s018d.item;

import com.s018d.util.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public class ChestFilterItem extends Item {
    public ChestFilterItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();

        if (level.isClientSide() && player != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity) {
                ClientScreens.openChestFilter(pos);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
