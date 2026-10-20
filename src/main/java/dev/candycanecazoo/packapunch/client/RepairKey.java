package dev.candycanecazoo.packapunch.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.net.PapNetwork;
import dev.candycanecazoo.packapunch.net.RepairPacket;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * A second way to repair windows, for when right click is busy aiming a gun. Defaults to the same key as TacZ's Interact
 * (O), and can be rebound under Controls. Holding it repairs board after board, like holding use.
 */
public final class RepairKey {
    public static final KeyMapping KEY = new KeyMapping("key.packapunch.repair", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.packapunch");
    private static final int SEND_EVERY_TICKS = 2;

    private RepairKey() {}

    @Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT)
    public static final class Ticks {
        private static int sinceSent;

        private Ticks() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null || mc.screen != null) {
                return;
            }
            sinceSent++;
            if (!KEY.isDown() || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            if (mc.level.getBlockState(hit.getBlockPos()).is(PapRegistries.BARRICADE.get()) && sinceSent >= SEND_EVERY_TICKS) {
                sinceSent = 0;
                PapNetwork.CHANNEL.sendToServer(new RepairPacket(hit.getBlockPos()));
            }
        }
    }
}
