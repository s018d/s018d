package com.s018d.entity;

import com.s018d.enslavement.EnslavedMobGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * One modular hybrid body. Visual identity and inherited stats come from the
 * two parent species; behaviour is still the normal servant brain.
 */
public class HybridServantEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_PRIMARY =
        SynchedEntityData.defineId(HybridServantEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_SECONDARY =
        SynchedEntityData.defineId(HybridServantEntity.class, EntityDataSerializers.STRING);

    public HybridServantEntity(EntityType<? extends HybridServantEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new EnslavedMobGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PRIMARY, "unknown");
        builder.define(DATA_SECONDARY, "unknown");
    }

    public String primarySpecies() {
        return entityData.get(DATA_PRIMARY);
    }

    public String secondarySpecies() {
        return entityData.get(DATA_SECONDARY);
    }

    public void configure(String primary, String secondary) {
        entityData.set(DATA_PRIMARY, primary);
        entityData.set(DATA_SECONDARY, secondary);
        applyInheritedTraits(primary);
        applyInheritedTraits(secondary);
        setHealth(getMaxHealth());
    }

    private void applyInheritedTraits(String species) {
        if (species == null || species.isBlank()) {
            return;
        }
        String key = "hybrid_" + species.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (species.contains("skeleton") || species.contains("stray") || species.contains("wither_skeleton")) {
            add(Attributes.ARMOR, 4.0, key + "_armor");
        }
        if (species.contains("zombie") || species.contains("drowned") || species.contains("husk")) {
            add(Attributes.MAX_HEALTH, 6.0, key + "_health");
        }
        if (species.contains("spider") || species.contains("cave_spider")) {
            add(Attributes.MOVEMENT_SPEED, 0.02, key + "_speed");
            add(Attributes.ATTACK_DAMAGE, 1.0, key + "_damage");
        }
        if (species.contains("wolf") || species.contains("fox") || species.contains("cat")) {
            add(Attributes.MOVEMENT_SPEED, 0.03, key + "_speed");
        }
        if (species.contains("iron_golem")) {
            add(Attributes.MAX_HEALTH, 10.0, key + "_health");
            add(Attributes.KNOCKBACK_RESISTANCE, 0.4, key + "_knockback");
        }
        if (species.contains("slime") || species.contains("magma_cube")) {
            add(Attributes.MAX_HEALTH, 4.0, key + "_health");
        }
        if (species.contains("blaze") || species.contains("ghast")) {
            add(Attributes.ARMOR, 2.0, key + "_armor");
        }
    }

    private void add(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                     double amount, String id) {
        var instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("s018d", id);
        if (instance.getModifier(location) == null) {
            instance.addPermanentModifier(new AttributeModifier(location, amount,
                AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Spider-born hybrids can crawl up a wall while their navigator pushes into it. */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || !hasSpecies("spider")) {
            return;
        }
        if (horizontalCollision && getNavigation().isInProgress()) {
            var motion = getDeltaMovement();
            if (motion.y < 0.16) {
                setDeltaMovement(motion.x, 0.16, motion.z);
            }
        }
    }

    public boolean hasSpecies(String needle) {
        return primarySpecies().contains(needle) || secondarySpecies().contains(needle);
    }

    public int getVariant() {
        String species = primarySpecies() + "/" + secondarySpecies();
        return Math.floorMod(species.hashCode(), 8);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("s018d_primary", primarySpecies());
        tag.putString("s018d_secondary", secondarySpecies());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("s018d_primary")) {
            entityData.set(DATA_PRIMARY, tag.getString("s018d_primary"));
        }
        if (tag.contains("s018d_secondary")) {
            entityData.set(DATA_SECONDARY, tag.getString("s018d_secondary"));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
