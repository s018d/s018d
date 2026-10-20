package com.s018d;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Constants.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> S018D_TAB = CREATIVE_TABS.register("s018d",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.s018d"))
            .icon(() -> new ItemStack(Items.COMMAND_BLOCK))
            .displayItems((params, output) -> {
                output.accept(ModItems.SERVANT_CONTROLLER.get());
                output.accept(ModItems.SERVANT_SPAWN_EGG.get());
                output.accept(ModItems.HYBRID_SERVANT_SPAWN_EGG.get());
                output.accept(ModItems.CHEST_FILTER.get());
                output.accept(ModItems.BANDAGE.get());
                output.accept(ModItems.ENGAGEMENT_RING.get());
                output.accept(ModItems.WEDDING_RING.get());
                output.accept(ModItems.BONDING_RING.get());
            })
            .build());
}
