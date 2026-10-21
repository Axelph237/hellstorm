package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.net.RevivePacket;
import net.minecraft.world.phys.EntityHitResult;
import com.mojang.blaze3d.platform.InputConstants;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import dev.candycanecazoo.hellstorm.net.UsePacket;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
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
 * A second use button for windows and machines, for when right click is busy aiming a gun. Defaults to the same key as
 * TacZ's Interact (O), and can be rebound under Controls. Holding it repairs board after board and revives a downed
 * teammate; machines are used once per press.
 */
public final class UseKey {
    public static final KeyMapping KEY = new KeyMapping("key.hellstorm.repair", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.hellstorm");
    private static final int SEND_EVERY_TICKS = 2;

    private UseKey() {}

    @Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT)
    public static final class Ticks {
        private static int sinceSent;
        private static boolean wasDown;

        private Ticks() {}

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null || mc.screen != null) {
                return;
            }
            sinceSent++;
            boolean down = KEY.isDown();
            boolean firstPress = down && !wasDown;
            wasDown = down;
            if (down && mc.hitResult instanceof EntityHitResult target && HudState.downed(target.getEntity())) {
                if (firstPress || sinceSent >= SEND_EVERY_TICKS) {
                    sinceSent = 0;
                    PapNetwork.CHANNEL.sendToServer(new RevivePacket(target.getEntity().getId()));
                }
                return;
            }
            if (!down || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
                return;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            boolean barricade = state.is(PapRegistries.BARRICADE.get());
            boolean buyable = state.getBlock() instanceof FeatureBlock || ClientMapState.map().doorAt(pos).isPresent();
            if ((firstPress && buyable) || (barricade && (firstPress || sinceSent >= SEND_EVERY_TICKS))) {
                sinceSent = 0;
                PapNetwork.CHANNEL.sendToServer(new UsePacket(pos, firstPress));
            }
        }
    }
}
