package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.feature.Prompted;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * "AK-47 [1000]" (gold brackets) while a player in a game looks at something they can buy, with the cost in red when they
 * can't afford it, and just "No power" in red when it needs the power on. Drawn here under the crosshair when the client
 * config asks for the HUD style; otherwise {@link WorldPrompt} floats it at the target.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PromptOverlay {
    /** How far above a machine or a player the text floats. */
    private static final double ABOVE = 0.35;
    private static final double TOWARD_VIEWER = 0.4;

    private PromptOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "prompt", (gui, graphics, partialTick, width, height) -> draw(graphics, width, height));
    }

    private static void draw(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!HudState.inGame() || mc.level == null || mc.options.hideGui || ClientConfig.PROMPT_STYLE.get() != ClientConfig.PromptStyle.HUD) {
            return;
        }
        target(mc).ifPresent(t -> WrappedText.drawCentered(graphics, mc.font, text(t.prompt()), width / 2, height / 2 + 14, width, 0xFFFFFF));
    }

    /** A prompt and the point in the world it belongs to: above a machine or a downed teammate, or on a door's near face. */
    record Target(Prompted.Prompt prompt, Vec3 anchor) {}

    /** What the crosshair is on, if it is a downed teammate, a door, or a machine with something to sell. */
    static Optional<Target> target(Minecraft mc) {
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult target && HudState.downed(target.getEntity())) {
            Entity entity = target.getEntity();
            return Optional.of(new Target(new Prompted.Prompt("Revive " + entity.getName().getString(), 0, false),
                    entity.position().add(0, entity.getBbHeight() + ABOVE, 0)));
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = hit.getBlockPos();
        Optional<DoorDef> door = ClientMapState.map().doorAt(pos);
        if (door.isPresent()) {
            return Optional.of(new Target(new Prompted.Prompt(door.get().style() == DoorDef.Style.DEBRIS ? "Debris" : "Door",
                    door.get().cost(), door.get().requiresPower()), nearFace(door.get(), hit.getDirection(), mc.gameRenderer.getMainCamera().getPosition())));
        }
        BlockState state = mc.level.getBlockState(pos);
        BlockPos main = state.getBlock() instanceof FeatureBlock feature ? feature.mainPos(state, pos) : pos;
        if (!(mc.level.getBlockEntity(main) instanceof Prompted prompted)) {
            return Optional.empty();
        }
        AABB box = bounds(mc, pos);
        if (state.getBlock() instanceof FeatureBlock feature) {
            for (BlockPos part : feature.blocks(state, pos)) {
                box = box.minmax(bounds(mc, part));
            }
        }
        Vec3 above = new Vec3((box.minX + box.maxX) / 2, box.maxY + ABOVE, (box.minZ + box.maxZ) / 2);
        // A wall-buy's plate sits against the wall; pulled toward the viewer, its text doesn't sink into the wall when seen from the side.
        Vec3 toward = mc.gameRenderer.getMainCamera().getPosition().subtract(above).multiply(1, 0, 1);
        Vec3 anchor = toward.lengthSqr() > TOWARD_VIEWER * TOWARD_VIEWER ? above.add(toward.normalize().scale(TOWARD_VIEWER)) : above;
        return prompted.prompt().map(p -> new Target(p, anchor));
    }

    private static AABB bounds(Minecraft mc, BlockPos pos) {
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    /**
     * The middle of the side of the door you're looking at: the face your crosshair hit, moved out to the door's outer
     * edge on that side and centered on the whole door. Looking at its top or bottom, the side nearer the camera instead.
     */
    private static Vec3 nearFace(DoorDef door, Direction hitFace, Vec3 camera) {
        AABB box = null;
        for (BlockPos member : door.members()) {
            box = box == null ? new AABB(member) : box.minmax(new AABB(member));
        }
        Vec3 center = box.getCenter();
        Direction face = hitFace.getAxis().isHorizontal() ? hitFace : nearestSide(box, camera);
        double out = 0.05;
        return switch (face) {
            case WEST -> new Vec3(box.minX - out, center.y, center.z);
            case EAST -> new Vec3(box.maxX + out, center.y, center.z);
            case NORTH -> new Vec3(center.x, center.y, box.minZ - out);
            default -> new Vec3(center.x, center.y, box.maxZ + out);
        };
    }

    /** The side of the box the camera is furthest out past, relative to the box's size. */
    private static Direction nearestSide(AABB box, Vec3 camera) {
        Vec3 center = box.getCenter();
        double dx = (camera.x - center.x) / Math.max(box.getXsize(), 1e-3);
        double dz = (camera.z - center.z) / Math.max(box.getZsize(), 1e-3);
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? Direction.WEST : Direction.EAST;
        }
        return dz < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    static Component text(Prompted.Prompt prompt) {
        if (prompt.needsPower() && !HudState.powerOn()) {
            return Component.literal("No power").withStyle(ChatFormatting.RED);
        }
        MutableComponent text = Component.literal(prompt.label());
        if (prompt.cost() > 0) {
            text.append(" ").append(Component.literal("[").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(String.valueOf(prompt.cost())).withStyle(HudState.points() >= prompt.cost() ? ChatFormatting.WHITE : ChatFormatting.RED))
                    .append(Component.literal("]").withStyle(ChatFormatting.GOLD));
        }
        return text;
    }
}
