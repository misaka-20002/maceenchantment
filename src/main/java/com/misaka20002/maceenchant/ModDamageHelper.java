package com.misaka20002.maceenchant;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public final class ModDamageHelper {
    private ModDamageHelper() {
    }

    public static float getWeaponAttackDamageWithSharpness(ItemStack stack) {
        return getWeaponAttackDamage(stack) + getSharpnessDamageBonus(stack);
    }

    private static float getWeaponAttackDamage(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getComponents()
                .getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return (float) modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0D, EquipmentSlot.MAINHAND);
    }

    private static float getSharpnessDamageBonus(ItemStack stack) {
        int sharpnessLevel = 0;
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (enchantment.is(Enchantments.SHARPNESS)) {
                sharpnessLevel = stack.getEnchantments().getLevel(enchantment);
                break;
            }
        }

        return sharpnessLevel > 0 ? 0.5F * sharpnessLevel + 0.5F : 0.0F;
    }
}
