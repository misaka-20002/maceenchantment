package com.misaka20002.maceenchant;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaceEnchantment implements ModInitializer {
    public static final String MODID = "maceenchantment";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    // Singleton accessed by the mixin for tick tracking and damage modification.
    public static final MaceSmashHandler SMASH_HANDLER = new MaceSmashHandler();

    @Override
    public void onInitialize() {
        SMASH_HANDLER.register();
        ModLootTables.register();
    }
}
