package com.s018d.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetChestFilterPacket(BlockPos pos, int filterType, String itemId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetChestFilterPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "set_chest_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetChestFilterPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetChestFilterPacket::pos,
        ByteBufCodecs.INT, SetChestFilterPacket::filterType,
        ByteBufCodecs.STRING_UTF8, SetChestFilterPacket::itemId,
        SetChestFilterPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetChestFilterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
            if (blockEntity instanceof Container) {
                com.s018d.util.ChestFilterManager.setFilter(packet.pos(), packet.filterType(), packet.itemId(), player.level());
                ModPackets.sendToAll(new ChestFilterSyncPacket(packet.pos(), packet.filterType(), packet.itemId()));
            }
        });
    }
}
