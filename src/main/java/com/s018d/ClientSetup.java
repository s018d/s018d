package com.s018d;

import com.s018d.client.HybridServantRenderer;
import com.s018d.client.ServantRenderer;
import com.s018d.gui.EnslavedBagScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only setup for servant rendering and keybinds.
 */
@EventBusSubscriber(modid = S018dMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    public static final KeyMapping SERVANTS_KEY = new KeyMapping(
        "key.s018d.servants", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.s018d");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SERVANTS_KEY);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.SERVANT.get(), ServantRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.HYBRID_SERVANT.get(), HybridServantRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SERVANT_BAG.get(), EnslavedBagScreen::new);
    }
}
