package com.s018d.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Generic servant orders: addtask, clear, withdraw, home, release.
 * data carries "k=v;k=v" parameters.
 */
public record ServantActionPacket(int servantId, String action, String data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServantActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "servant_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServantActionPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, ServantActionPacket::servantId,
        ByteBufCodecs.STRING_UTF8, ServantActionPacket::action,
        ByteBufCodecs.STRING_UTF8, ServantActionPacket::data,
        ServantActionPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServantActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(packet.servantId());
            if (!(entity instanceof com.s018d.entity.ServantEntity servant)) {
                return;
            }
            if (servant.getOwnerId() == null || !servant.getOwnerId().equals(player.getUUID())) {
                return;
            }
            switch (packet.action()) {
                case "addtask" -> {
                    com.s018d.entity.ServantEntity.TaskType[] types =
                        com.s018d.entity.ServantEntity.TaskType.values();
                    try {
                        int ordinal = Integer.parseInt(packet.data().split("\\|", 2)[0]);
                        if (ordinal < 0 || ordinal >= types.length) {
                            return;
                        }
                        java.util.Map<String, String> params = new java.util.HashMap<>();
                        String rest = packet.data().contains("|") ? packet.data().split("\\|", 2)[1] : "";
                        for (String pair : rest.split(";")) {
                            int eq = pair.indexOf('=');
                            if (eq > 0) {
                                params.put(pair.substring(0, eq), pair.substring(eq + 1));
                            }
                        }
                        servant.addTask(types[ordinal], params);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    ModPackets.sendToPlayer(player, new ServantDataPacket(servant, false));
                }
                case "clear" -> {
                    if (packet.data().equals("all")) {
                        servant.clearTasks();
                    } else {
                        try {
                            servant.removeTask(Integer.parseInt(packet.data()));
                        } catch (NumberFormatException e) {
                            servant.clearTasks();
                        }
                    }
                    ModPackets.sendToPlayer(player, new ServantDataPacket(servant, false));
                }
                case "withdraw" -> {
                    for (int i = 0; i < servant.getInventory().getContainerSize(); i++) {
                        net.minecraft.world.item.ItemStack slot = servant.getInventory().getItem(i);
                        if (slot.isEmpty()) {
                            continue;
                        }
                        if (player.getInventory().add(slot.copy())) {
                            servant.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                        }
                    }
                    ModPackets.sendToPlayer(player, new ServantDataPacket(servant, false));
                }
                case "home" -> {
                    com.s018d.enslavement.EnslavementManager.setHome(servant, servant.blockPosition());
                    ModPackets.sendToPlayer(player, new ServantDataPacket(servant, false));
                }
                case "release" -> com.s018d.enslavement.EnslavementManager.release(servant);
                default -> {
                }
            }
        });
    }
}
