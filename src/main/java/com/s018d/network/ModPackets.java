package com.s018d.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPackets {
    public static final String PROTOCOL_VERSION = "2";

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(SetChestFilterPacket.TYPE, SetChestFilterPacket.STREAM_CODEC, SetChestFilterPacket::handle);
        registrar.playToServer(ServantActionPacket.TYPE, ServantActionPacket.STREAM_CODEC, ServantActionPacket::handle);
        registrar.playToServer(EnslaveActionPacket.TYPE, EnslaveActionPacket.STREAM_CODEC, EnslaveActionPacket::handle);
        registrar.playToServer(RequestServantsPacket.TYPE, RequestServantsPacket.STREAM_CODEC, RequestServantsPacket::handle);
        registrar.playToClient(ServantsListPacket.TYPE, ServantsListPacket.STREAM_CODEC, ServantsListPacket::handle);
        registrar.playToClient(ChestFilterSyncPacket.TYPE, ChestFilterSyncPacket.STREAM_CODEC, ChestFilterSyncPacket::handle);
        registrar.playToClient(ServantDataPacket.TYPE, ServantDataPacket.STREAM_CODEC, ServantDataPacket::handle);
        registrar.playToClient(EnslavedDataPacket.TYPE, EnslavedDataPacket.STREAM_CODEC, EnslavedDataPacket::handle);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToAll(CustomPacketPayload packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }
}
