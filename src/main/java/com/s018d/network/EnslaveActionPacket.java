package com.s018d.network;

import com.s018d.enslavement.EnslavedCommand;
import com.s018d.enslavement.EnslavementManager;
import com.s018d.enslavement.ServantBagMenu;
import com.s018d.util.CraftBook;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Any order sent from the servant button menu. entityId -1 means
 * "apply to every creature I own nearby".
 */
public record EnslaveActionPacket(int entityId, String command, String data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnslaveActionPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("s018d", "enslave_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnslaveActionPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT, EnslaveActionPacket::entityId,
        ByteBufCodecs.STRING_UTF8, EnslaveActionPacket::command,
        ByteBufCodecs.STRING_UTF8, EnslaveActionPacket::data,
        EnslaveActionPacket::new);

    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EnslaveActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = LAST_ACTION.put(player.getUUID(), now);
            if (last != null && now - last < 100L) {
                return;
            }

            if ("HOME".equals(packet.command())) {
                handleHome(packet, player);
                return;
            }

            if ("EQUIP".equals(packet.command()) || "WITHDRAW".equals(packet.command())) {
                handleBagAction(packet, player);
                return;
            }

            if ("SYNC".equals(packet.command())) {
                // Refresh the menu of one servant from the list screen.
                Mob mob = ownedMob(player, packet.entityId(), false);
                if (mob != null) {
                    EnslavementManager.sendOpen(player, mob);
                }
                return;
            }

            if ("TRACK".equals(packet.command())) {
                int glowing = EnslavementManager.highlightAll(player, 200);
                player.displayClientMessage(
                    Component.translatable("gui.s018d.ens_tracked", glowing), true);
                pling(player);
                return;
            }

            if ("OPENBAG".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    ServantBagMenu.open(player, mob);
                }
                return;
            }

            if ("SETCHEST".equals(packet.command())) {
                handleSetChest(packet, player);
                return;
            }

            if ("MULTI".equals(packet.command())) {
                handleMulti(packet, player);
                return;
            }

            if ("MULTI_HOME".equals(packet.command())) {
                int count = 0;
                for (String raw : packet.data().split(",")) {
                    if (raw.isBlank()) {
                        continue;
                    }
                    try {
                        Entity entity = player.serverLevel().getEntity(Integer.parseInt(raw.trim()));
                        if (entity instanceof Mob mob && EnslavementManager.isOwner(mob, player)
                            && mob.distanceToSqr(player) <= 256.0 * 256.0) {
                            EnslavementManager.setHome(mob, player.blockPosition());
                            EnslavementManager.highlight(mob);
                            count++;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                player.displayClientMessage(Component.translatable("gui.s018d.ens_multi_home", count), true);
                if (count > 0) {
                    pling(player);
                } else {
                    buzz(player);
                }
                return;
            }

            if ("QUEUE_ADD".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    EnslavedCommand queued = EnslavedCommand.byName(packet.data());
                    EnslavementManager.queueCommand(mob, queued);
                    player.displayClientMessage(Component.translatable("gui.s018d.ens_queued",
                        queued.label(), EnslavementManager.queueSize(mob)), true);
                    pling(player);
                }
                return;
            }

            if ("QUEUE_CLEAR".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    EnslavementManager.clearQueue(mob);
                    player.displayClientMessage(Component.translatable("gui.s018d.ens_queue_cleared"), true);
                    buzz(player);
                }
                return;
            }

            if ("QUEUE_NEXT".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    if (EnslavementManager.advanceQueue(mob)) {
                        player.displayClientMessage(Component.translatable("gui.s018d.ens_queue_started",
                            EnslavementManager.command(mob).label()), true);
                        pling(player);
                    } else {
                        player.displayClientMessage(Component.translatable("gui.s018d.ens_queue_empty"), true);
                        buzz(player);
                    }
                    ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
                }
                return;
            }

            if ("GUARD_POST".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    BlockPos post = mob.blockPosition();
                    EnslavementManager.setGuardPost(mob, post);
                    EnslavementManager.clearQueue(mob);
                    EnslavementManager.setCommand(mob, EnslavedCommand.GUARD);
                    player.displayClientMessage(Component.translatable("gui.s018d.ens_guard_post_set",
                        post.getX(), post.getY(), post.getZ()), true);
                    pling(player);
                    ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
                }
                return;
            }

            if ("AUTODEPOSIT".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    boolean enabled = EnslavementManager.toggleAutoDeposit(mob);
                    player.displayClientMessage(Component.translatable(enabled
                        ? "gui.s018d.ens_autodeposit_on" : "gui.s018d.ens_autodeposit_off"), true);
                    pling(player);
                    ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
                }
                return;
            }

            if ("CRAFT_REQ".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                CraftBook.Entry entry = CraftBook.get(packet.data());
                if (mob != null && entry != null) {
                    EnslavementManager.addCraftRequest(mob, entry.id());
                    EnslavementManager.setCommand(mob, EnslavedCommand.CRAFT);
                    player.displayClientMessage(Component.translatable("gui.s018d.craft_queued",
                        entry.result().getHoverName()), true);
                    pling(player);
                    ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
                }
                return;
            }

            if ("CRAFT_CLEAR".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    EnslavementManager.clearCraftRequests(mob);
                    player.displayClientMessage(Component.translatable("gui.s018d.craft_cleared"), true);
                    pling(player);
                }
                return;
            }

            if ("CLEARCHEST".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    EnslavementManager.clearDepositTarget(mob);
                    player.displayClientMessage(Component.translatable("gui.s018d.ens_chest_cleared"), true);
                    pling(player);
                    ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
                }
                return;
            }

            if ("LIMIT".equals(packet.command())) {
                handleLimit(packet, player);
                return;
            }

            if ("AUTOPICKUP".equals(packet.command())) {
                Mob mob = ownedMob(player, packet.entityId());
                if (mob != null) {
                    boolean enabled = EnslavementManager.toggleAutoPickup(mob);
                    player.displayClientMessage(Component.translatable(enabled
                        ? "gui.s018d.ens_autopickup_on" : "gui.s018d.ens_autopickup_off"), true);
                    pling(player);
                    ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
                }
                return;
            }

            EnslavedCommand order = EnslavedCommand.byName(packet.command());

            if (packet.entityId() == -1) {
                int count = EnslavementManager.applyAll(player, order);
                player.displayClientMessage(
                    Component.translatable("gui.s018d.ens_all_done", count), true);
                pling(player);
                return;
            }

            Mob mob = ownedMob(player, packet.entityId());
            if (mob == null) {
                return;
            }
            EnslavementManager.clearQueue(mob);
            EnslavementManager.setCommand(mob, order);
            if (order == EnslavedCommand.RELEASE) {
                player.displayClientMessage(
                    Component.translatable("gui.s018d.enslaved_released"), true);
            } else {
                player.displayClientMessage(Component.translatable("gui.s018d.ens_order_set",
                    mob.getName(), order.label()), true);
                ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
            }
            pling(player);
        });
    }

    private static void pling(Player player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.7f, 1.6f);
    }

    private static void buzz(Player player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.7f, 0.6f);
    }

    private static void handleHome(EnslaveActionPacket packet, ServerPlayer player) {
        if (packet.entityId() == -1) {
            int count = EnslavementManager.setHomeAll(player, player.blockPosition());
            player.displayClientMessage(Component.translatable("gui.s018d.ens_home_all", count), true);
            pling(player);
            return;
        }
        Mob mob = ownedMob(player, packet.entityId());
        if (mob == null) {
            return;
        }
        EnslavementManager.setHome(mob, player.blockPosition());
        EnslavementManager.highlight(mob);
        player.displayClientMessage(Component.translatable("gui.s018d.ens_home_set"), true);
        pling(player);
        ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
    }

    private static final int[] LIMIT_STEPS = {0, 16, 32, 64, 128, 256, 512};

    private static void handleMulti(EnslaveActionPacket packet, ServerPlayer player) {
        String[] parts = packet.data().split("\\|", 2);
        if (parts.length < 2) {
            buzz(player);
            return;
        }
        EnslavedCommand order = EnslavedCommand.byName(parts[0]);
        int count = 0;
        for (String raw : parts[1].split(",")) {
            if (raw.isBlank()) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            Entity entity = player.serverLevel().getEntity(id);
            if (entity instanceof Mob mob && EnslavementManager.isOwner(mob, player)
                && mob.distanceToSqr(player) <= 256.0 * 256.0) {
                EnslavementManager.clearQueue(mob);
                EnslavementManager.setCommand(mob, order);
                EnslavementManager.highlight(mob);
                count++;
            }
        }
        player.displayClientMessage(Component.translatable("gui.s018d.ens_multi_done", count), true);
        if (count > 0) {
            pling(player);
        } else {
            buzz(player);
        }
    }

    private static void handleSetChest(EnslaveActionPacket packet, ServerPlayer player) {
        Mob mob = ownedMob(player, packet.entityId());
        if (mob == null) {
            return;
        }
        BlockPos pos = lookingAtContainer(player);
        if (pos == null) {
            pos = EnslavementManager.findContainerPos(player.serverLevel(),
                player.blockPosition(), 12.0);
        }
        if (pos == null) {
            player.displayClientMessage(Component.translatable("gui.s018d.ens_no_chest"), true);
            buzz(player);
            return;
        }
        EnslavementManager.setDepositTarget(mob, pos);
        player.displayClientMessage(Component.translatable("gui.s018d.ens_chest_set",
            pos.getX(), pos.getY(), pos.getZ()), true);
        pling(player);
        ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
    }

    /** The container under the crosshair wins; otherwise the nearest one is used. */
    private static BlockPos lookingAtContainer(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(16.0));
        BlockHitResult hit = player.level().clip(new ClipContext(start, end,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return player.level().getBlockEntity(pos) instanceof net.minecraft.world.Container
            ? pos.immutable() : null;
    }

    private static void handleLimit(EnslaveActionPacket packet, ServerPlayer player) {
        Mob mob = ownedMob(player, packet.entityId());
        if (mob == null) {
            return;
        }
        int current = EnslavementManager.workLimit(mob);
        int next = LIMIT_STEPS[0];
        for (int i = 0; i < LIMIT_STEPS.length; i++) {
            if (LIMIT_STEPS[i] == current) {
                next = LIMIT_STEPS[(i + 1) % LIMIT_STEPS.length];
                break;
            }
        }
        EnslavementManager.setWorkLimit(mob, next);
        EnslavementManager.resetWorkDone(mob);
        Component amount = next == 0
            ? Component.translatable("gui.s018d.ens_limit_off")
            : Component.literal(String.valueOf(next));
        player.displayClientMessage(
            Component.translatable("gui.s018d.ens_limit_btn", amount), true);
        pling(player);
        ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
    }

    private static void handleBagAction(EnslaveActionPacket packet, ServerPlayer player) {
        Mob mob = ownedMob(player, packet.entityId());
        if (mob == null) {
            return;
        }
        if ("EQUIP".equals(packet.command())) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                player.displayClientMessage(Component.translatable("gui.s018d.ens_hold_item"), true);
                buzz(player);
                return;
            }
            if (!EnslavementManager.stashAdd(mob, held)) {
                player.displayClientMessage(Component.translatable("gui.s018d.ens_stash_full"), true);
                buzz(player);
                return;
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            EnslavementManager.equipBestTool(mob, EnslavementManager.command(mob));
            player.displayClientMessage(
                Component.translatable("gui.s018d.ens_gave", held.getHoverName()), true);
            pling(player);
        } else {
            List<ItemStack> items = EnslavementManager.stashTakeAll(mob);
            ItemStack hand = EnslavementManager.takeHand(mob);
            if (!hand.isEmpty()) {
                items.add(hand);
            }
            int count = 0;
            for (ItemStack stack : items) {
                count += stack.getCount();
                if (!player.getInventory().add(stack) && !player.isCreative()) {
                    player.drop(stack, false);
                }
            }
            player.displayClientMessage(Component.translatable(count > 0
                ? "gui.s018d.ens_withdrawn" : "gui.s018d.ens_nothing", count), true);
            if (count > 0) {
                pling(player);
            } else {
                buzz(player);
            }
        }
        ModPackets.sendToPlayer(player, new EnslavedDataPacket(mob, false));
    }

    private static Mob ownedMob(Player player, int entityId) {
        return ownedMob(player, entityId, true);
    }

    private static Mob ownedMob(Player player, int entityId, boolean checkDistance) {
        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        if (!EnslavementManager.isOwner(mob, player)) {
            player.displayClientMessage(Component.translatable("gui.s018d.enslaved_denied"), true);
            buzz(player);
            return null;
        }
        if (checkDistance && mob.distanceToSqr(player) > 64.0 * 64.0) {
            player.displayClientMessage(Component.translatable("gui.s018d.ens_too_far"), true);
            buzz(player);
            return null;
        }
        return mob;
    }
}

