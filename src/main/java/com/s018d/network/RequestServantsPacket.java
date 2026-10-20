package com.s018d.network;

import com.s018d.enslavement.EnslavedCommand;
import com.s018d.enslavement.EnslavementManager;
import com.s018d.entity.ServantEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the owned-creature list and sends it to the requesting player. */
public record RequestServantsPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestServantsPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "request_servants"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestServantsPacket> STREAM_CODEC =
        StreamCodec.unit(new RequestServantsPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestServantsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.getServer() == null) {
                return;
            }
            List<ServantsListPacket.Entry> entries = new ArrayList<>();
            for (ServerLevel level : player.getServer().getAllLevels()) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.INFINITE,
                        m -> m.isAlive() && EnslavementManager.isOwner(m, player))) {
                    if (mob instanceof ServantEntity) {
                        continue;
                    }
                    String name = mob.hasCustomName() && mob.getCustomName() != null
                        ? mob.getCustomName().getString()
                        : mob.getName().getString();
                    EnslavedCommand job = EnslavementManager.command(mob);
                    entries.add(new ServantsListPacket.Entry(
                        mob.getId(), name, mob.getType().toShortString(), job.name(),
                        mob.getHealth(), mob.getMaxHealth(),
                        com.s018d.enslavement.ServantMind.affection(mob),
                        com.s018d.enslavement.ServantMind.mood(mob).name(),
                        level.dimension().location().toString(),
                        mob.blockPosition().getX(), mob.blockPosition().getY(), mob.blockPosition().getZ()));
                }
            }
            entries.sort(Comparator
                .comparing(ServantsListPacket.Entry::type)
                .thenComparing(ServantsListPacket.Entry::job)
                .thenComparing(ServantsListPacket.Entry::name));
            ModPackets.sendToPlayer(player, new ServantsListPacket(List.copyOf(entries)));
        });
    }
}
