package com.s018d;

import com.s018d.network.ModPackets;
import com.s018d.network.RequestServantsPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opens the servant list from the keybind. */
@EventBusSubscriber(modid = S018dMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientKeys {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (ClientSetup.SERVANTS_KEY.consumeClick()) {
            ModPackets.sendToServer(new RequestServantsPacket());
        }
    }
}
