package com.s018d.network;

import com.s018d.entity.ServantEntity;
import com.s018d.util.ClientScreens;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Snapshot of a servant for its menu screen. */
public record ServantDataPacket(int servantId, List<String> tasks, List<String> items, boolean openGui) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServantDataPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "servant_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServantDataPacket> STREAM_CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeInt(packet.servantId());
            buf.writeInt(packet.tasks().size());
            for (String task : packet.tasks()) {
                buf.writeUtf(task);
            }
            buf.writeInt(packet.items().size());
            for (String item : packet.items()) {
                buf.writeUtf(item);
            }
            buf.writeBoolean(packet.openGui());
        },
        buf -> {
            int id = buf.readInt();
            int taskCount = buf.readInt();
            List<String> tasks = new ArrayList<>();
            for (int i = 0; i < taskCount; i++) {
                tasks.add(buf.readUtf());
            }
            int itemCount = buf.readInt();
            List<String> items = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) {
                items.add(buf.readUtf());
            }
            return new ServantDataPacket(id, List.copyOf(tasks), List.copyOf(items), buf.readBoolean());
        });

    public ServantDataPacket(ServantEntity servant, boolean openGui) {
        this(servant.getId(), describeTasks(servant), describeInventory(servant), openGui);
    }

    private static List<String> describeTasks(ServantEntity servant) {
        List<String> lines = new ArrayList<>();
        for (ServantEntity.Task task : servant.getTasks()) {
            lines.add(task.type.name() + "|" + task.status.name());
        }
        return lines;
    }

    private static List<String> describeInventory(ServantEntity servant) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < servant.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack slot = servant.getInventory().getItem(i);
            if (!slot.isEmpty()) {
                lines.add(slot.getHoverName().getString() + " x" + slot.getCount());
            }
        }
        return lines;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServantDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Entity entity = ClientScreens.clientEntity(packet.servantId());
            if (entity instanceof ServantEntity && packet.openGui()) {
                ClientScreens.openServant(packet.servantId(), packet.tasks(), packet.items());
            }
        });
    }
}
