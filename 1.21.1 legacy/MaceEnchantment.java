package com.misaka20002.maceenchantment;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MaceEnchantment.MODID)
public class MaceEnchantment {
    public static final String MODID = "maceenchantment";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaceEnchantment(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(new MaceSmashHandler());
    }
}
