package com.s018d;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(S018dMod.MODID)
public class S018dMod {
    public static final String MODID = "s018d";
    private static final Logger LOGGER = LogUtils.getLogger();

    public S018dMod(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::registerPayloads);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.SERVANT_CONTROLLER.get());
            event.accept(ModItems.CHEST_FILTER.get());
            event.accept(ModItems.BANDAGE.get());
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.SERVANT.get(),
            com.s018d.entity.ServantEntity.createAttributes().build());
        event.put(ModEntityTypes.HYBRID_SERVANT.get(),
            com.s018d.entity.HybridServantEntity.createAttributes().build());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        com.s018d.network.ModPackets.register(event.registrar(com.s018d.network.ModPackets.PROTOCOL_VERSION));
    }
}
