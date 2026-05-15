package com.misaka20002.maceenchant;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.random.WeightedList;

public class MaceSmashHandler {
    private static final float SMASH_ATTACK_FALL_THRESHOLD = 1.5F;
    private static final float SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F;
    private static final float SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5F;
    private static final float SMASH_ATTACK_KNOCKBACK_POWER = 0.7F;

    private final Set<LivingEntity> pendingFallDistanceResets = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<LivingEntity, Double> elytraFlightApexY = new WeakHashMap<>();
    private final Map<LivingEntity, Double> elytraPreviousYVel = new WeakHashMap<>();
    private final Map<LivingEntity, Double> elytraPreviousY = new WeakHashMap<>();
    private final Map<LivingEntity, Long> lastElytraWindBurstTick = new WeakHashMap<>();

    /**
     * Called from MaceEnchantment.onInitialize() to register Fabric API callbacks.
     * The remaining two handlers (entity tick + incoming damage) live in LivingEntityMixin
     * since Fabric has no event equivalent for those.
     */
    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> resetFallDistanceAfterPostAttackEffects());
    }

    // -------------------------------------------------------------------------
    // Called by LivingEntityMixin — replaces EntityTickEvent.Post
    // -------------------------------------------------------------------------

    public void trackElytraFlightStart(LivingEntity entity) {
        if (entity.isFallFlying()) {
            double currentY = entity.getY();
            double currentYVel = entity.getDeltaMovement().y;
            double previousYVel = this.elytraPreviousYVel.getOrDefault(entity, currentYVel);
            double previousY = this.elytraPreviousY.getOrDefault(entity, currentY);

            if (previousYVel > 0.0 && currentYVel <= 0.0) {
                this.elytraFlightApexY.put(entity, previousY);
            } else if (previousYVel <= 0.0 && currentYVel > 0.0) {
                this.elytraFlightApexY.put(entity, currentY);
            } else {
                this.elytraFlightApexY.putIfAbsent(entity, currentY);
            }

            this.elytraPreviousYVel.put(entity, currentYVel);
            this.elytraPreviousY.put(entity, currentY);
        } else {
            this.elytraFlightApexY.remove(entity);
            this.elytraPreviousYVel.remove(entity);
            this.elytraPreviousY.remove(entity);
            this.lastElytraWindBurstTick.remove(entity);
        }
    }

    // -------------------------------------------------------------------------
    // Called by LivingEntityMixin — replaces LivingIncomingDamageEvent
    // -------------------------------------------------------------------------

    /**
     * Returns the modified damage amount (with smash bonus added), or the
     * original {@code amount} unchanged if this hit is not a smash attack.
     */
    public float calculateModifiedDamage(LivingEntity target, DamageSource source, float amount) {
        LivingEntity attacker = getSmashAttacker(source);
        if (attacker == null || !(attacker.level() instanceof ServerLevel level)) {
            return amount;
        }

        int enchantmentLevel = getMaceSmashLevel(level, attacker);
        SmashAttack smashAttack = getSmashAttack(attacker, enchantmentLevel);
        if (smashAttack == null) {
            return amount;
        }

        ItemStack weapon = attacker.getMainHandItem();
        double fallDistance = smashAttack.distance();
        double baseSmashDamage = calculateSmashDamage(fallDistance);
        double attackDamage = Math.max(0.0, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));
        double damage = Math.max(baseSmashDamage, baseSmashDamage * attackDamage * 0.1);
        int densityLevel = getDensityLevel(level, weapon);
        double baseDensityBonus = densityLevel * 0.5 * fallDistance;
        double densityBonus = Math.max(baseDensityBonus, densityLevel * attackDamage * 0.05 * fallDistance);
        float levelScale = enchantmentLevel * 0.25F;
        return amount + (float) ((damage + densityBonus) * levelScale);
    }

    // -------------------------------------------------------------------------
    // Replaces LivingDamageEvent.Post — registered via ServerLivingEntityEvents.AFTER_DAMAGE
    // -------------------------------------------------------------------------

    public void applySmashEffects(LivingEntity target, DamageSource source, float damageTaken) {
        if (damageTaken <= 0.0F) {
            return;
        }

        LivingEntity attacker = getSmashAttacker(source);
        if (attacker == null || !(attacker.level() instanceof ServerLevel level)) {
            return;
        }

        int enchantmentLevel = getMaceSmashLevel(level, attacker);
        SmashAttack smashAttack = getSmashAttack(attacker, enchantmentLevel);
        if (smashAttack == null) {
            return;
        }

        attacker.setDeltaMovement(attacker.getDeltaMovement().with(Axis.Y, 0.01F));
        if (attacker instanceof ServerPlayer player) {
            if (smashAttack.elytra()) {
                triggerElytraWindBurst(level, attacker, attacker.getMainHandItem());
            }
            player.currentImpulseImpactPos = calculateImpactPosition(player);
            player.setIgnoreFallDamageFromCurrentImpulse(true);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }

        if (target.onGround()) {
            if (attacker instanceof ServerPlayer player) {
                player.setSpawnExtraParticlesOnFall(true);
            }

            SoundEvent sound = smashAttack.distance() > SMASH_ATTACK_HEAVY_THRESHOLD
                    ? SoundEvents.MACE_SMASH_GROUND_HEAVY
                    : SoundEvents.MACE_SMASH_GROUND;
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), sound,
                    attacker.getSoundSource(), 1.0F, 1.0F);
        } else {
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    SoundEvents.MACE_SMASH_AIR, attacker.getSoundSource(), 1.0F, 1.0F);
        }

        knockback(level, attacker, target, smashAttack.distance());
        if (smashAttack.elytra()) {
            this.elytraFlightApexY.put(attacker, attacker.getY());
        }
        this.pendingFallDistanceResets.add(attacker);
    }

    // -------------------------------------------------------------------------
    // Replaces ServerTickEvent.Post — registered via ServerTickEvents.END_SERVER_TICK
    // -------------------------------------------------------------------------

    private void resetFallDistanceAfterPostAttackEffects() {
        this.pendingFallDistanceResets.removeIf(attacker -> {
            attacker.resetFallDistance();
            return true;
        });
    }

    // -------------------------------------------------------------------------
    // Static helpers — unchanged from NeoForge version
    // -------------------------------------------------------------------------

    private static LivingEntity getSmashAttacker(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity directAttacker
                && source.getEntity() == directAttacker
                ? directAttacker
                : null;
    }

    private static Holder<Enchantment> getMaceSmash(ServerLevel level) {
        return level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(ModEnchantments.MACE_SMASH)
                .orElseThrow();
    }

    private static int getMaceSmashLevel(ServerLevel level, LivingEntity attacker) {
        ItemStack weapon = attacker.getMainHandItem();
        return weapon.isEmpty() || weapon.is(Items.MACE) ? 0 : weapon.getEnchantments().getLevel(getMaceSmash(level));
    }

    private static int getDensityLevel(ServerLevel level, ItemStack weapon) {
        Holder<Enchantment> density = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(Enchantments.DENSITY)
                .orElseThrow();
        return weapon.getEnchantments().getLevel(density);
    }

    private static int getWindBurstLevel(ServerLevel level, ItemStack weapon) {
        Holder<Enchantment> windBurst = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(Enchantments.WIND_BURST)
                .orElseThrow();
        return weapon.getEnchantments().getLevel(windBurst);
    }

    private static Vec3 calculateImpactPosition(ServerPlayer player) {
        return player.isIgnoringFallDamageFromCurrentImpulse()
                && player.currentImpulseImpactPos != null
                && player.currentImpulseImpactPos.y <= player.position().y
                ? player.currentImpulseImpactPos
                : player.position();
    }

    private static double calculateSmashDamage(double fallDistance) {
        if (fallDistance <= 3.0) {
            return 4.0 * fallDistance;
        } else if (fallDistance <= 8.0) {
            return 12.0 + 2.0 * (fallDistance - 3.0);
        } else {
            return 22.0 + fallDistance - 8.0;
        }
    }

    private void triggerElytraWindBurst(ServerLevel level, LivingEntity attacker, ItemStack weapon) {
        long gameTime = level.getGameTime();
        if (this.lastElytraWindBurstTick.getOrDefault(attacker, Long.MIN_VALUE) == gameTime) {
            return;
        }

        int windBurstLevel = getWindBurstLevel(level, weapon);
        if (windBurstLevel <= 0) {
            return;
        }

        this.lastElytraWindBurstTick.put(attacker, gameTime);
        HolderSet<Block> immuneBlocks = level.registryAccess()
                .lookupOrThrow(Registries.BLOCK)
                .getOrThrow(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS);
        level.explode(
                null,
                null,
                new SimpleExplosionDamageCalculator(
                        true,
                        false,
                        Optional.of(getWindBurstKnockbackMultiplier(windBurstLevel)),
                        Optional.of(immuneBlocks)),
                attacker.getX(),
                attacker.getY(),
                attacker.getZ(),
                3.5F,
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                WeightedList.of(),
                SoundEvents.WIND_CHARGE_BURST);
    }

    private static float getWindBurstKnockbackMultiplier(int windBurstLevel) {
        return switch (windBurstLevel) {
            case 1 -> 1.2F;
            case 2 -> 1.75F;
            case 3 -> 2.2F;
            default -> 1.5F + 0.35F * (windBurstLevel - 1);
        };
    }

    private SmashAttack getSmashAttack(LivingEntity attacker, int enchantmentLevel) {
        if (enchantmentLevel <= 0) {
            return null;
        }

        if (attacker.isFallFlying()) {
            if (enchantmentLevel < 4) {
                return null;
            }

            double apexY = this.elytraFlightApexY.getOrDefault(attacker, attacker.getY());
            double flightFallDistance = Math.max(0.0, apexY - attacker.getY());
            return flightFallDistance > SMASH_ATTACK_FALL_THRESHOLD
                    ? new SmashAttack(flightFallDistance, true)
                    : null;
        }

        return attacker.fallDistance > SMASH_ATTACK_FALL_THRESHOLD
                ? new SmashAttack(attacker.fallDistance, false)
                : null;
    }

    private static void knockback(Level level, Entity attacker, Entity entity, double smashDistance) {
        level.levelEvent(2013, entity.getOnPos(), 750);
        level.getEntitiesOfClass(
                LivingEntity.class,
                entity.getBoundingBox().inflate(SMASH_ATTACK_KNOCKBACK_RADIUS),
                knockbackPredicate(attacker, entity)
        ).forEach(nearby -> {
            Vec3 direction = nearby.position().subtract(entity.position());
            double knockbackPower = getKnockbackPower(nearby, direction, smashDistance);
            Vec3 knockbackVector = direction.normalize().scale(knockbackPower);
            if (knockbackPower > 0.0) {
                nearby.push(knockbackVector.x, SMASH_ATTACK_KNOCKBACK_POWER, knockbackVector.z);
                if (nearby instanceof ServerPlayer player) {
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }
            }
        });
    }

    private static Predicate<LivingEntity> knockbackPredicate(Entity attacker, Entity entity) {
        return nearby -> {
            boolean notSpectator = !nearby.isSpectator();
            boolean notAttackerOrTarget = nearby != attacker && nearby != entity;
            boolean notAlliedToAttacker = !attacker.isAlliedTo(nearby);
            boolean notTamedByAttacker = !(nearby instanceof TamableAnimal animal
                    && attacker instanceof LivingEntity livingAttacker
                    && animal.isTame()
                    && animal.isOwnedBy(livingAttacker));
            boolean notArmorStandMarker = !(nearby instanceof ArmorStand armorStand
                    && armorStand.isMarker());
            boolean withinRange = entity.distanceToSqr(nearby)
                    <= Math.pow(SMASH_ATTACK_KNOCKBACK_RADIUS, 2.0);
            boolean notFlyingInCreative = !(nearby instanceof Player player
                    && player.isCreative()
                    && player.getAbilities().flying);
            return notSpectator && notAttackerOrTarget && notAlliedToAttacker
                    && notTamedByAttacker && notArmorStandMarker
                    && withinRange && notFlyingInCreative;
        };
    }

    private static double getKnockbackPower(LivingEntity nearby, Vec3 direction,
                                             double smashDistance) {
        return (SMASH_ATTACK_KNOCKBACK_RADIUS - direction.length())
                * SMASH_ATTACK_KNOCKBACK_POWER
                * (smashDistance > SMASH_ATTACK_HEAVY_THRESHOLD ? 2 : 1)
                * (1.0 - nearby.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    private record SmashAttack(double distance, boolean elytra) {}
}
