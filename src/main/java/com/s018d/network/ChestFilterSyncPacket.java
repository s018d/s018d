package com.s018d.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChestFilterSyncPacket(BlockPos pos, int filterType, String itemId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChestFilterSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "chest_filter_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChestFilterSyncPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ChestFilterSyncPacket::pos,
        ByteBufCodecs.INT, ChestFilterSyncPacket::filterType,
        ByteBufCodecs.STRING_UTF8, ChestFilterSyncPacket::itemId,
        ChestFilterSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChestFilterSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.s018d.util.ChestFilterManager.setClientFilter(
            packet.pos(), packet.filterType(), packet.itemId()));
    }
}
