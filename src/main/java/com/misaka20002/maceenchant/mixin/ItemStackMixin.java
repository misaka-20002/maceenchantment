package com.misaka20002.maceenchant.mixin;

import com.misaka20002.maceenchant.SpearThrustComponents;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DataComponentHolder.class)
public interface ItemStackMixin {

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private <T> void maceenchantment$getSpearThrustComponent(
            DataComponentType<? extends T> componentType,
            CallbackInfoReturnable<T> cir) {
        if (cir.getReturnValue() == null
                && (Object) this instanceof ItemStack stack
                && SpearThrustComponents.hasSpearThrust(stack)) {
            T spearComponent = SpearThrustComponents.getSpearComponent(stack, componentType);
            if (spearComponent != null) {
                cir.setReturnValue(spearComponent);
            }
        }
    }

    @Inject(method = "has", at = @At("RETURN"), cancellable = true)
    private void maceenchantment$hasSpearThrustComponent(
            DataComponentType<?> componentType,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && (Object) this instanceof ItemStack stack
                && SpearThrustComponents.isSpearComponent(componentType)
                && SpearThrustComponents.hasSpearThrust(stack)) {
            cir.setReturnValue(true);
        }
    }
}
