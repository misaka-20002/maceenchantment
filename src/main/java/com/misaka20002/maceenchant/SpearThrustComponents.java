package com.misaka20002.maceenchant;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class SpearThrustComponents {
    private static final KineticWeapon SPEAR_KINETIC_WEAPON =
            Items.IRON_SPEAR.components().get(DataComponents.KINETIC_WEAPON);
    private static final PiercingWeapon SPEAR_PIERCING_WEAPON =
            Items.IRON_SPEAR.components().get(DataComponents.PIERCING_WEAPON);
    private static final AttackRange SPEAR_ATTACK_RANGE =
            Items.IRON_SPEAR.components().get(DataComponents.ATTACK_RANGE);
    private static final Holder<DamageType> SPEAR_DAMAGE_TYPE =
            Items.IRON_SPEAR.components().get(DataComponents.DAMAGE_TYPE);
    private static final Float SPEAR_MINIMUM_ATTACK_CHARGE =
            Items.IRON_SPEAR.components().get(DataComponents.MINIMUM_ATTACK_CHARGE);
    private static final SwingAnimation SPEAR_SWING_ANIMATION =
            Items.IRON_SPEAR.components().get(DataComponents.SWING_ANIMATION);

    private SpearThrustComponents() {
    }

    public static boolean hasSpearThrust(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ItemEnchantments enchantments = stack.getComponents()
                .getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(ModEnchantments.SPEAR_THRUST)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getSpearComponent(ItemStack stack, DataComponentType<? extends T> componentType) {
        if (componentType == DataComponents.KINETIC_WEAPON) {
            return (T) createScaledKineticWeapon(stack);
        }
        if (componentType == DataComponents.PIERCING_WEAPON) {
            return (T) SPEAR_PIERCING_WEAPON;
        }
        if (componentType == DataComponents.ATTACK_RANGE) {
            return (T) SPEAR_ATTACK_RANGE;
        }
        if (componentType == DataComponents.DAMAGE_TYPE) {
            return (T) SPEAR_DAMAGE_TYPE;
        }
        if (componentType == DataComponents.MINIMUM_ATTACK_CHARGE) {
            return (T) SPEAR_MINIMUM_ATTACK_CHARGE;
        }
        if (componentType == DataComponents.SWING_ANIMATION) {
            return (T) SPEAR_SWING_ANIMATION;
        }
        return null;
    }

    public static boolean isSpearComponent(DataComponentType<?> componentType) {
        return componentType == DataComponents.KINETIC_WEAPON
                || componentType == DataComponents.PIERCING_WEAPON
                || componentType == DataComponents.ATTACK_RANGE
                || componentType == DataComponents.DAMAGE_TYPE
                || componentType == DataComponents.MINIMUM_ATTACK_CHARGE
                || componentType == DataComponents.SWING_ANIMATION;
    }

    private static KineticWeapon createScaledKineticWeapon(ItemStack stack) {
        float weaponAttackDamage = ModDamageHelper.getWeaponAttackDamageWithSharpness(stack);
        float scaledDamageMultiplier = Math.max(
                SPEAR_KINETIC_WEAPON.damageMultiplier(),
                weaponAttackDamage * 0.1F);
        if (scaledDamageMultiplier == SPEAR_KINETIC_WEAPON.damageMultiplier()) {
            return SPEAR_KINETIC_WEAPON;
        }

        return new KineticWeapon(
                SPEAR_KINETIC_WEAPON.contactCooldownTicks(),
                SPEAR_KINETIC_WEAPON.delayTicks(),
                SPEAR_KINETIC_WEAPON.dismountConditions(),
                SPEAR_KINETIC_WEAPON.knockbackConditions(),
                SPEAR_KINETIC_WEAPON.damageConditions(),
                SPEAR_KINETIC_WEAPON.forwardMovement(),
                scaledDamageMultiplier,
                SPEAR_KINETIC_WEAPON.sound(),
                SPEAR_KINETIC_WEAPON.hitSound());
    }
}
