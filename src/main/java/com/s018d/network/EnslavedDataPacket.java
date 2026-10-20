package com.s018d.network;

import com.s018d.enslavement.EnslavementManager;
import com.s018d.util.ClientScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Snapshot that opens the order menu of one enslaved creature. */
public record EnslavedDataPacket(int entityId, String name, String command, String tool, int stashCount,
                                 float health, float maxHealth, int blocks, int items, int kills,
                                 int done, int limit, String chest, boolean openGui) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnslavedDataPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "enslaved_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnslavedDataPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeInt(packet.entityId());
            buf.writeUtf(packet.name());
            buf.writeUtf(packet.command());
            buf.writeUtf(packet.tool());
            buf.writeInt(packet.stashCount());
            buf.writeFloat(packet.health());
            buf.writeFloat(packet.maxHealth());
            buf.writeInt(packet.blocks());
            buf.writeInt(packet.items());
            buf.writeInt(packet.kills());
            buf.writeInt(packet.done());
            buf.writeInt(packet.limit());
            buf.writeUtf(packet.chest());
            buf.writeBoolean(packet.openGui());
        },
        buf -> new EnslavedDataPacket(
            buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt(),
            buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readInt(),
            buf.readInt(), buf.readInt(), buf.readUtf(), buf.readBoolean()));

    public EnslavedDataPacket(Mob mob, boolean openGui) {
        this(mob.getId(), mob.getName().getString(), EnslavementManager.command(mob).name(),
            EnslavementManager.toolName(mob), EnslavementManager.stashCount(mob),
            mob.getHealth(), mob.getMaxHealth(), EnslavementManager.statBlocks(mob),
            EnslavementManager.statItems(mob), EnslavementManager.statKills(mob),
            EnslavementManager.workDone(mob), EnslavementManager.workLimit(mob), chestInfo(mob), openGui);
    }

    private static String chestInfo(Mob mob) {
        BlockPos pos = EnslavementManager.depositTarget(mob);
        return pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EnslavedDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.openGui()) {
                ClientScreens.openEnslaved(packet.entityId(), packet.name(), packet.command(),
                    packet.tool(), packet.stashCount(), packet.health(), packet.maxHealth(),
                    packet.blocks(), packet.items(), packet.kills(), packet.done(), packet.limit(), packet.chest());
            } else {
                ClientScreens.refreshEnslaved(packet.entityId(), packet.name(), packet.command(),
                    packet.tool(), packet.stashCount(), packet.health(), packet.maxHealth(),
                    packet.blocks(), packet.items(), packet.kills(), packet.done(), packet.limit(), packet.chest());
            }
        });
    }
}
