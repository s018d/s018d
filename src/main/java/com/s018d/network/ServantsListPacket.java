package com.s018d.network;

import com.s018d.util.ClientScreens;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Sorted list of every creature the player owns, used by the servant list screen. */
public record ServantsListPacket(List<Entry> servants) implements CustomPacketPayload {
    public record Entry(int id, String name, String type, String job, float health, float maxHealth,
                        int affection, String mood, String dimension, int x, int y, int z) {
    }

    public static final CustomPacketPayload.Type<ServantsListPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "servants_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServantsListPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeInt(packet.servants().size());
            for (Entry entry : packet.servants()) {
                buf.writeInt(entry.id());
                buf.writeUtf(entry.name());
                buf.writeUtf(entry.type());
                buf.writeUtf(entry.job());
                buf.writeFloat(entry.health());
                buf.writeFloat(entry.maxHealth());
                buf.writeInt(entry.affection());
                buf.writeUtf(entry.mood());
                buf.writeUtf(entry.dimension());
                buf.writeInt(entry.x());
                buf.writeInt(entry.y());
                buf.writeInt(entry.z());
            }
        },
        buf -> {
            int size = buf.readInt();
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                entries.add(new Entry(buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readUtf(), buf.readUtf(),
                    buf.readInt(), buf.readInt(), buf.readInt()));
            }
            return new ServantsListPacket(List.copyOf(entries));
        });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServantsListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientScreens.openServantList(packet.servants()));
    }
}
