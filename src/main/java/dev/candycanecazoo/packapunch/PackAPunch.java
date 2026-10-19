package dev.candycanecazoo.packapunch;

import com.mojang.logging.LogUtils;
import dev.candycanecazoo.packapunch.command.ZombiesCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PackAPunch.MOD_ID)
public final class PackAPunch {
    public static final String MOD_ID = "packapunch";
    public static final Logger LOG = LogUtils.getLogger();

    public PackAPunch() {
        MinecraftForge.EVENT_BUS.addListener(ZombiesCommand::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild(MOD_ID, path);
    }
}
