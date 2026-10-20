package dev.candycanecazoo.packapunch;

import com.mojang.logging.LogUtils;
import dev.candycanecazoo.packapunch.command.PapCommand;
import dev.candycanecazoo.packapunch.config.ClientConfig;
import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.net.PapNetwork;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(PackAPunch.MOD_ID)
public final class PackAPunch {
    public static final String MOD_ID = "packapunch";
    public static final Logger LOG = LogUtils.getLogger();

    // The static context getters are deprecated in Forge 47.4, but their replacement (constructor injection) doesn't exist
    // in NeoForge 1.20.1 (Forge 47.1 based). Keeping them lets one jar load on both loaders.
    @SuppressWarnings("removal")
    public PackAPunch() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        PapRegistries.register(modBus);
        dev.candycanecazoo.packapunch.powerup.PowerUpRegistries.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(PapNetwork::register));
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(PapCommand::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild(MOD_ID, path);
    }
}
