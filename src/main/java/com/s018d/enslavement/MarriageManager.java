package com.s018d.enslavement;

import com.s018d.ModEntityTypes;
import com.s018d.ModItems;
import com.s018d.entity.HybridServantEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Marriage, bonding and the single hybrid child entity. */
public final class MarriageManager {
    private static final String SPOUSE = "s018d_spouse";
    private static final String MARRIAGE_ID = "s018d_marriage_id";
    private static final String CHILDREN = "s018d_children";
    private static final String BOND_CD = "s018d_bond_cd";
    private static final String PARENTS = "s018d_parents";
    private static final long BOND_COOLDOWN = 7L * 24000L;
    private static final int MAX_CHILDREN = 6;

    private static final Map<UUID, UUID> SELECTED = new HashMap<>();

    private MarriageManager() {
    }

    public static boolean select(ServerPlayer player, Mob mob) {
        if (!EnslavementManager.isOwner(mob, player)) {
            return false;
        }
        if (isMarried(mob)) {
            player.displayClientMessage(Component.translatable("message.s018d.marriage_already")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        SELECTED.put(player.getUUID(), mob.getUUID());
        player.displayClientMessage(Component.translatable("message.s018d.marriage_selected", mob.getName())
            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 1.6, mob.getZ(),
                9, 0.3, 0.4, 0.3, 0.01);
        }
        return true;
    }

    public static boolean tryMarry(ServerPlayer player, Mob second) {
        UUID selectedId = SELECTED.get(player.getUUID());
        if (selectedId == null) {
            player.displayClientMessage(Component.translatable("message.s018d.marriage_choose_first")
                .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        Mob first = findMob(player, selectedId);
        if (first == null || !first.isAlive() || !EnslavementManager.isOwner(first, player)) {
            SELECTED.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.s018d.marriage_first_missing")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (first == second || !EnslavementManager.isOwner(second, player)) {
            return false;
        }
        if (isMarried(first) || isMarried(second)) {
            player.displayClientMessage(Component.translatable("message.s018d.marriage_already")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (isBaby(first) || isBaby(second)) {
            player.displayClientMessage(Component.translatable("message.s018d.marriage_too_young")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (first.distanceToSqr(second) > 16.0 * 16.0) {
            player.displayClientMessage(Component.translatable("message.s018d.marriage_too_far")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (ServantMind.affection(first) < 70 || ServantMind.affection(second) < 70) {
            player.displayClientMessage(Component.translatable("message.s018d.marriage_not_ready")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!consume(player, ModItems.ENGAGEMENT_RING.get(), 1)
            || !consume(player, ModItems.WEDDING_RING.get(), 1)) {
            player.displayClientMessage(Component.translatable("message.s018d.marriage_rings")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }

        UUID marriageId = UUID.randomUUID();
        bind(first, second, marriageId);
        bind(second, first, marriageId);
        SELECTED.remove(player.getUUID());
        if (first.level() instanceof ServerLevel level) {
            Vec3 middle = first.position().add(second.position()).scale(0.5);
            level.sendParticles(ParticleTypes.HEART, middle.x, middle.y + 1.2, middle.z,
                28, 0.8, 0.6, 0.8, 0.02);
            level.playSound(null, BlockPos.containing(middle), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 1.0f, 1.15f);
        }
        player.displayClientMessage(Component.translatable("message.s018d.marriage_done",
            first.getName(), second.getName()).withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    private static void bind(Mob mob, Mob spouse, UUID marriageId) {
        CompoundTag tag = mob.getPersistentData();
        tag.putUUID(SPOUSE, spouse.getUUID());
        tag.putUUID(MARRIAGE_ID, marriageId);
        if (!tag.contains(CHILDREN)) {
            tag.put(CHILDREN, new ListTag());
        }
    }

    public static boolean tryBond(ServerPlayer player, Mob parent) {
        Mob spouse = spouse(parent);
        if (!isMarried(parent) || spouse == null || !spouse.isAlive()) {
            player.displayClientMessage(Component.translatable("message.s018d.bond_no_spouse")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (!EnslavementManager.isOwner(spouse, player)) {
            return false;
        }
        if (parent.distanceToSqr(spouse) > 16.0 * 16.0) {
            player.displayClientMessage(Component.translatable("message.s018d.bond_too_far")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (ServantMind.affection(parent) < 80 || ServantMind.affection(spouse) < 80) {
            player.displayClientMessage(Component.translatable("message.s018d.bond_not_ready")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (children(parent) >= MAX_CHILDREN) {
            player.displayClientMessage(Component.translatable("message.s018d.bond_limit")
                .withStyle(ChatFormatting.RED), false);
            return false;
        }
        long now = parent.level().getGameTime();
        if (parent.getPersistentData().getLong(BOND_CD) > now) {
            long days = Math.max(1L, (parent.getPersistentData().getLong(BOND_CD) - now) / 24000L);
            player.displayClientMessage(Component.translatable("message.s018d.bond_cooldown", days)
                .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        if (!(parent.level() instanceof ServerLevel level)) {
            return false;
        }

        HybridServantEntity hybrid = ModEntityTypes.HYBRID_SERVANT.get().create(level);
        if (hybrid == null) {
            return false;
        }
        Vec3 middle = parent.position().add(spouse.position()).scale(0.5);
        hybrid.moveTo(middle.x, middle.y, middle.z, parent.getYRot(), 0.0f);
        hybrid.configure(species(parent), species(spouse));
        level.addFreshEntity(hybrid);
        EnslavementManager.enslave(hybrid, player);
        hybrid.setCustomName(Component.translatable("entity.s018d.hybrid_servant")
            .append(" (" + species(parent) + "/" + species(spouse) + ")"));
        hybrid.setCustomNameVisible(true);
        addChild(parent, hybrid.getUUID());
        addChild(spouse, hybrid.getUUID());

        long next = now + BOND_COOLDOWN;
        parent.getPersistentData().putLong(BOND_CD, next);
        spouse.getPersistentData().putLong(BOND_CD, next);
        level.sendParticles(ParticleTypes.HEART, middle.x, middle.y + 1.2, middle.z,
            34, 0.9, 0.8, 0.9, 0.02);
        level.playSound(null, BlockPos.containing(middle), SoundEvents.PLAYER_LEVELUP,
            SoundSource.PLAYERS, 1.1f, 1.35f);
        player.displayClientMessage(Component.translatable("message.s018d.bond_done", hybrid.getName())
            .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    public static boolean isMarried(Mob mob) {
        return mob.getPersistentData().hasUUID(SPOUSE);
    }

    public static Mob spouse(Mob mob) {
        if (!isMarried(mob) || mob.getServer() == null) {
            return null;
        }
        UUID id = mob.getPersistentData().getUUID(SPOUSE);
        for (ServerLevel level : mob.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob spouse && spouse.isAlive()) {
                return spouse;
            }
        }
        return null;
    }

    public static int children(Mob mob) {
        ListTag list = mob.getPersistentData().getList(CHILDREN, Tag.TAG_COMPOUND);
        return list.size();
    }

    private static void addChild(Mob parent, UUID child) {
        CompoundTag tag = parent.getPersistentData();
        ListTag list = tag.getList(CHILDREN, Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("uuid", child);
        list.add(entry);
        tag.put(CHILDREN, list);
    }

    private static String species(Mob mob) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return key == null ? "unknown" : key.getPath();
    }

    private static Mob findMob(Player player, UUID id) {
        if (player.getServer() == null) {
            return null;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob) {
                return mob;
            }
        }
        return null;
    }

    private static boolean isBaby(Mob mob) {
        return mob instanceof AgeableMob ageable && ageable.isBaby();
    }

    private static boolean consume(Player player, Item item, int count) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                found += stack.getCount();
            }
        }
        if (found < count) {
            return false;
        }
        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                break;
            }
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (left <= 0) {
                break;
            }
            if (stack.is(item)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }
}
