package com.s018d.enslavement;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Small, deterministic mind model.
 *
 * <p>The servant has moods, needs and opinions, but obedience is a hard
 * invariant: mood only changes work speed, barks and animations. There is no
 * code path that lets a servant refuse an order.</p>
 */
public final class ServantMind {
    public static final ResourceLocation SPEED_ID =
        ResourceLocation.fromNamespaceAndPath("s018d", "servant_mood_speed");

    private static final String AFFECTION = "s018d_mind_affection";
    private static final String TRUST = "s018d_mind_trust";
    private static final String STRESS = "s018d_mind_stress";
    private static final String ENERGY = "s018d_mind_energy";
    private static final String MOOD = "s018d_mind_mood";
    private static final String BARK_CD = "s018d_mind_bark_cd";

    public enum Mood {
        HAPPY("gui.s018d.mood_happy"),
        CONTENT("gui.s018d.mood_content"),
        NEUTRAL("gui.s018d.mood_neutral"),
        TIRED("gui.s018d.mood_tired"),
        SAD("gui.s018d.mood_sad"),
        ANGRY("gui.s018d.mood_angry");

        private final String key;

        Mood(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Mood byName(String name) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                return NEUTRAL;
            }
        }
    }

    private ServantMind() {
    }

    public static void init(Mob mob) {
        var tag = mob.getPersistentData();
        if (!tag.contains(AFFECTION)) {
            tag.putInt(AFFECTION, 50);
            tag.putInt(TRUST, 50);
            tag.putInt(STRESS, 10);
            tag.putInt(ENERGY, 80);
            tag.putString(MOOD, Mood.CONTENT.name());
            tag.putInt(BARK_CD, 0);
        }
    }

    public static int affection(Mob mob) {
        return mob.getPersistentData().getInt(AFFECTION);
    }

    public static int trust(Mob mob) {
        return mob.getPersistentData().getInt(TRUST);
    }

    public static int stress(Mob mob) {
        return mob.getPersistentData().getInt(STRESS);
    }

    public static int energy(Mob mob) {
        return mob.getPersistentData().getInt(ENERGY);
    }

    public static Mood mood(Mob mob) {
        return Mood.byName(mob.getPersistentData().getString(MOOD));
    }

    /** Always true by design. Kept as a named API so the rule is explicit. */
    public static boolean obeys(Mob mob) {
        return true;
    }

    /**
     * 0.75 .. 1.10 speed multiplier. Low mood makes work slower, never
     * impossible, and never cancels an order.
     */
    public static double speedFactor(Mob mob) {
        double factor = 0.90 + affection(mob) / 500.0 - stress(mob) / 500.0;
        if (energy(mob) < 20) {
            factor -= 0.10;
        }
        return Math.max(0.75, Math.min(1.10, factor));
    }

    public static void tick(Mob mob) {
        if (mob.level().isClientSide() || !EnslavementManager.isEnslaved(mob)) {
            return;
        }
        init(mob);
        var tag = mob.getPersistentData();
        int bark = tag.getInt(BARK_CD);
        if (bark > 0) {
            tag.putInt(BARK_CD, bark - 1);
        }
        if (mob.tickCount % 40 != 0) {
            return;
        }

        EnslavedCommand command = EnslavementManager.command(mob);
        boolean resting = command == EnslavedCommand.SIT || command == EnslavedCommand.STAY
            || mob.getNavigation().isDone();
        int energy = energy(mob) + (resting ? 2 : -1);
        energy = Math.max(0, Math.min(100, energy));
        tag.putInt(ENERGY, energy);

        int stress = stress(mob);
        if (mob.getHealth() < mob.getMaxHealth() * 0.5f) {
            stress += 2;
        }
        if (energy < 20) {
            stress += 1;
        }
        if (resting && energy > 50) {
            stress -= 1;
        }
        stress = Math.max(0, Math.min(100, stress));
        tag.putInt(STRESS, stress);

        Player owner = owner(mob);
        int affection = affection(mob);
        if (owner != null && mob.distanceToSqr(owner) < 16.0 * 16.0
            && mob.tickCount % 160 == 0) {
            affection += 1;
        }
        if (owner == null || mob.distanceToSqr(owner) > 96.0 * 96.0) {
            if (mob.tickCount % 400 == 0) {
                affection -= 1;
            }
        }
        if (mob.tickCount % 80 == 0) {
            var spouse = MarriageManager.spouse(mob);
            if (spouse != null && mob.distanceToSqr(spouse) < 8.0 * 8.0) {
                affection += 1;
                stress = Math.max(0, stress - 1);
            }
        }
        tag.putInt(AFFECTION, Math.max(0, Math.min(100, affection)));
        tag.putInt(STRESS, Math.max(0, Math.min(100, stress)));

        Mood old = mood(mob);
        Mood next = computeMood(mob);
        tag.putString(MOOD, next.name());
        applySpeed(mob);
        if (next != old) {
            bark(mob, switch (next) {
                case HAPPY -> "gui.s018d.bark_happy";
                case TIRED -> "gui.s018d.bark_tired";
                case SAD -> "gui.s018d.bark_sad";
                case ANGRY -> "gui.s018d.bark_angry";
                default -> "gui.s018d.bark_ok";
            });
        }
    }

    private static Mood computeMood(Mob mob) {
        int stress = stress(mob);
        int energy = energy(mob);
        int affection = affection(mob);
        if (stress >= 75) {
            return Mood.ANGRY;
        }
        if (energy <= 20) {
            return Mood.TIRED;
        }
        if (affection <= 20) {
            return Mood.SAD;
        }
        if (affection >= 70 && stress <= 30) {
            return Mood.HAPPY;
        }
        if (stress <= 45) {
            return Mood.CONTENT;
        }
        return Mood.NEUTRAL;
    }

    public static void applySpeed(Mob mob) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double amount = speedFactor(mob) - 1.0;
        AttributeModifier existing = speed.getModifier(SPEED_ID);
        if (existing != null && Math.abs(existing.amount() - amount) < 0.01) {
            return;
        }
        speed.removeModifier(SPEED_ID);
        speed.addTransientModifier(new AttributeModifier(SPEED_ID, amount,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static void praise(Mob mob, int amount) {
        adjust(mob, AFFECTION, amount);
        adjust(mob, STRESS, -amount / 2);
        bark(mob, "gui.s018d.bark_praise");
    }

    public static void gift(Mob mob, int amount) {
        adjust(mob, AFFECTION, amount);
        adjust(mob, TRUST, amount / 2);
        bark(mob, "gui.s018d.bark_gift");
    }

    public static void onFed(Mob mob) {
        adjust(mob, AFFECTION, 2);
        adjust(mob, STRESS, -2);
        adjust(mob, ENERGY, 8);
    }

    /** Called when a hard order is issued. It adds stress but never blocks it. */
    public static void onForcedWork(Mob mob) {
        adjust(mob, STRESS, 1);
    }

    private static void adjust(Mob mob, String key, int delta) {
        int value = mob.getPersistentData().getInt(key) + delta;
        mob.getPersistentData().putInt(key, Math.max(0, Math.min(100, value)));
    }

    public static void bark(Mob mob, String key) {
        if (mob.level().isClientSide()) {
            return;
        }
        var tag = mob.getPersistentData();
        if (tag.getInt(BARK_CD) > 0) {
            return;
        }
        tag.putInt(BARK_CD, 120);
        ServantVoices.bark(mob);
        Player owner = owner(mob);
        if (owner instanceof ServerPlayer player && mob.distanceToSqr(player) < 32.0 * 32.0) {
            player.displayClientMessage(Component.translatable(key, mob.getName()), true);
        }
    }

    private static Player owner(Mob mob) {
        UUID id = EnslavementManager.ownerId(mob);
        if (id == null || mob.getServer() == null) {
            return null;
        }
        return mob.getServer().getPlayerList().getPlayer(id);
    }
}
