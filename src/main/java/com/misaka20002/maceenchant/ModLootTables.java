package com.misaka20002.maceenchant;

import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;

public final class ModLootTables {
    private static final ResourceKey<LootTable> VAULT_UNIQUE = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.parse("minecraft:chests/trial_chambers/reward_unique"));
    private static final ResourceKey<LootTable> OMINOUS_VAULT_UNIQUE = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.parse("minecraft:chests/trial_chambers/reward_ominous_unique"));

    private ModLootTables() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            if (source.isBuiltin() && (key.equals(VAULT_UNIQUE) || key.equals(OMINOUS_VAULT_UNIQUE))) {
                ((FabricLootTableBuilder) builder).modifyPools(pool -> pool.add(
                        LootItem.lootTableItem(Items.ENCHANTED_BOOK)
                                .setWeight(1)
                                .apply(SetComponentsFunction.setComponent(
                                        DataComponents.STORED_ENCHANTMENTS,
                                        createMaceSmashBookEnchantments(registries, 1)))));
            }
        });
    }

    private static ItemEnchantments createMaceSmashBookEnchantments(HolderLookup.Provider registries, int level) {
        Holder<Enchantment> maceSmash = registries.lookupOrThrow(Registries.ENCHANTMENT)
                .get(ModEnchantments.MACE_SMASH)
                .orElseThrow();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(maceSmash, level);
        return enchantments.toImmutable();
    }
}
