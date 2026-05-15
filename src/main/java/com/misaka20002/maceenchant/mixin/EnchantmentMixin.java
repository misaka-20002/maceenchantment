package com.misaka20002.maceenchant.mixin;

import com.misaka20002.maceenchant.MaceEnchantment;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {

    private static final ResourceKey<Enchantment> MACE_SMASH = ResourceKey.create(
            Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(MaceEnchantment.MODID, "mace_smash"));

    @Inject(method = "canEnchant", at = @At("RETURN"), cancellable = true)
    private void maceenchantment$allowMaceCompanionEnchantments(
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        Enchantment enchantment = (Enchantment) (Object) this;

        // mace_smash 兼容风暴、致密、破甲
        if (!cir.getReturnValueZ()
                && maceenchantment$hasMaceSmash(stack)
                && (maceenchantment$isWindBurst()
                        || maceenchantment$isDensity()
                        || maceenchantment$isBreach())) {
            cir.setReturnValue(true);
            return;
        }

        // Density/Breach 兼容锋利、亡灵杀手、节肢杀手、穿刺
        if (!cir.getReturnValueZ()
                && maceenchantment$isDamageCompatibilityEnchantment(enchantment)
                && (maceenchantment$hasDensity(stack)
                        || maceenchantment$hasBreach(stack))) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "areCompatible", at = @At("HEAD"), cancellable = true)
    private static void maceenchantment$allowExtraCompatibility(
            Holder<Enchantment> first,
            Holder<Enchantment> second,
            CallbackInfoReturnable<Boolean> cir) {
        Enchantment firstEnchantment = first.value();
        Enchantment secondEnchantment = second.value();

        boolean firstDensity = maceenchantment$isDensity(firstEnchantment);
        boolean firstBreach = maceenchantment$isBreach(firstEnchantment);

        boolean secondDensity = maceenchantment$isDensity(secondEnchantment);
        boolean secondBreach = maceenchantment$isBreach(secondEnchantment);

        boolean firstDamage = maceenchantment$isDamageCompatibilityEnchantment(firstEnchantment);
        boolean secondDamage = maceenchantment$isDamageCompatibilityEnchantment(secondEnchantment);

        // Density 和 Breach 仍然冲突
        if ((firstDensity && secondBreach)
                || (firstBreach && secondDensity)) {
            cir.setReturnValue(false);
            return;
        }

        // Density/Breach 兼容 锋利/亡灵/节肢/穿刺
        if ((firstDensity || firstBreach) && secondDamage) {
            cir.setReturnValue(true);
            return;
        }

        if ((secondDensity || secondBreach) && firstDamage) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "modifyFallBasedDamage", at = @At("HEAD"), cancellable = true)
    private void maceenchantment$disableVanillaDensityOnMaceSmashItems(
            ServerLevel level,
            int enchantmentLevel,
            ItemStack stack,
            Entity entity,
            DamageSource source,
            MutableFloat damage,
            CallbackInfo ci) {
        if (maceenchantment$isDensity() && maceenchantment$hasMaceSmash(stack)) {
            ci.cancel();
        }
    }

    private boolean maceenchantment$isWindBurst() {
        return maceenchantment$isWindBurst((Enchantment) (Object) this);
    }

    private boolean maceenchantment$isDensity() {
        return maceenchantment$isDensity((Enchantment) (Object) this);
    }

    private boolean maceenchantment$isBreach() {
        return maceenchantment$isBreach((Enchantment) (Object) this);
    }

    private static boolean maceenchantment$isWindBurst(Enchantment enchantment) {
        return enchantment.getMaxLevel() == 3
                && enchantment.getAnvilCost() == 4
                && enchantment.canEnchant(Items.MACE.getDefaultInstance())
                && !enchantment.getEffects(EnchantmentEffectComponents.POST_ATTACK).isEmpty();
    }

    private static boolean maceenchantment$isDensity(Enchantment enchantment) {
        return enchantment.getMaxLevel() == 5
                && enchantment.getAnvilCost() == 2
                && enchantment.canEnchant(Items.MACE.getDefaultInstance())
                && !enchantment.getEffects(EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK).isEmpty();
    }

    private static boolean maceenchantment$isBreach(Enchantment enchantment) {
        return enchantment.getMaxLevel() == 4
                && enchantment.getAnvilCost() == 4
                && enchantment.canEnchant(Items.MACE.getDefaultInstance())
                && !enchantment.getEffects(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS).isEmpty();
    }

    private static boolean maceenchantment$isDamageCompatibilityEnchantment(Enchantment enchantment) {
        return enchantment.getMaxLevel() == 5
                && enchantment.getAnvilCost() == 1;
    }

    private static boolean maceenchantment$hasMaceSmash(ItemStack stack) {
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(MACE_SMASH)) {
                return true;
            }
        }
        return false;
    }

    private static boolean maceenchantment$hasDensity(ItemStack stack) {
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (maceenchantment$isDensity(enchantment.value())) {
                return true;
            }
        }
        return false;
    }

    private static boolean maceenchantment$hasBreach(ItemStack stack) {
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (maceenchantment$isBreach(enchantment.value())) {
                return true;
            }
        }
        return false;
    }
}