package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.client.outline.ZoneOutlines;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** `/zombiesdebug outline` outlines the block under the crosshair, to check the Ponder outline renderer. */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID, value = Dist.CLIENT)
public final class ClientDebug {
    private static final Object SLOT = new Object();
    private static boolean outlineTargetedBlock;

    private ClientDebug() {}

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zombiesdebug")
                .then(Commands.literal("outline").executes(ctx -> {
                    outlineTargetedBlock = !outlineTargetedBlock;
                    ctx.getSource().sendSuccess(() -> Component.literal("Outline test " + (outlineTargetedBlock ? "on" : "off")), false);
                    return 1;
                })));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !outlineTargetedBlock) {
            return;
        }
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            ZoneOutlines.box(SLOT, new AABB(blockHit.getBlockPos()).inflate(1), 0x4D9162, ZoneOutlines.THICK);
        }
    }
}
