package dev.candycanecazoo.hellstorm;

import com.mojang.logging.LogUtils;
import dev.candycanecazoo.hellstorm.command.HellstormCommand;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import dev.candycanecazoo.hellstorm.config.CommonConfig;
import dev.candycanecazoo.hellstorm.config.DefaultsConfig;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Hellstorm.MOD_ID)
public final class Hellstorm {
    public static final String MOD_ID = "hellstorm";
    public static final Logger LOG = LogUtils.getLogger();

    // The static context getters are deprecated in Forge 47.4, but their replacement (constructor injection) doesn't exist
    // in NeoForge 1.20.1 (Forge 47.1 based). Keeping them lets one jar load on both loaders.
    @SuppressWarnings("removal")
    public Hellstorm() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        PapRegistries.register(modBus);
        dev.candycanecazoo.hellstorm.powerup.PowerUpRegistries.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(PapNetwork::register));
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DefaultsConfig.SPEC, DefaultsConfig.FILE);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(HellstormCommand::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild(MOD_ID, path);
    }
}
