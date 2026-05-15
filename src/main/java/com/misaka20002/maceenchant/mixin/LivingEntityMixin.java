package com.misaka20002.maceenchant.mixin;

import com.misaka20002.maceenchant.MaceEnchantment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * Replaces EntityTickEvent.Post for LivingEntities.
     * Tracks the elytra flight apex so that smash attacks during elytra flight
     * can calculate fall distance correctly.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void maceenchantment$onTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) {
            MaceEnchantment.SMASH_HANDLER.trackElytraFlightStart(self);
        }
    }

    /**
     * Replaces LivingIncomingDamageEvent.
     * Uses @ModifyVariable to intercept and increase the {@code amount} parameter
     * of {@link LivingEntity#hurtServer} before it propagates through the damage pipeline.
     *
     * <p>The {@code DamageSource source} parameter is captured so the handler can
     * identify the attacker; it is not modified.</p>
     */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float maceenchantment$modifyIncomingDamage(
            float amount,
            ServerLevel level,
            DamageSource source,
            float originalAmount
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        return MaceEnchantment.SMASH_HANDLER.calculateModifiedDamage(self, source, amount);
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void maceenchantment$afterDamage(
            ServerLevel level,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            LivingEntity self = (LivingEntity) (Object) this;
            MaceEnchantment.SMASH_HANDLER.applySmashEffects(self, source, amount);
        }
    }
}
