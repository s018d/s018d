package com.s018d.enslavement;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

/** Species-flavoured voices with a little deterministic pitch variation. */
public final class ServantVoices {
    private ServantVoices() {
    }

    public static void bark(Mob mob) {
        if (mob.level().isClientSide()) {
            return;
        }
        SoundEvent sound = switch (species(mob)) {
            case "zombie", "drowned", "husk", "zombie_villager" -> SoundEvents.ZOMBIE_AMBIENT;
            case "skeleton", "stray", "wither_skeleton" -> SoundEvents.SKELETON_AMBIENT;
            case "spider", "cave_spider" -> SoundEvents.SPIDER_AMBIENT;
            case "enderman" -> SoundEvents.ENDERMAN_AMBIENT;
            case "wolf" -> SoundEvents.WOLF_AMBIENT;
            case "cat", "ocelot" -> SoundEvents.CAT_AMBIENT;
            case "blaze" -> SoundEvents.BLAZE_AMBIENT;
            case "villager", "wandering_trader" -> SoundEvents.VILLAGER_AMBIENT;
            default -> SoundEvents.ALLAY_AMBIENT_WITH_ITEM;
        };
        float pitch = 0.85f + Math.floorMod(mob.getUUID().hashCode(), 30) / 100.0f;
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound,
            SoundSource.NEUTRAL, 0.65f, pitch);
    }

    private static String species(Mob mob) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return key == null ? "" : key.getPath();
    }
}
