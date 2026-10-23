package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.ClientConfig;
import dev.candycanecazoo.hellstorm.feature.BarricadeBlock;
import dev.candycanecazoo.hellstorm.feature.FeatureBlock;
import dev.candycanecazoo.hellstorm.feature.Prompted;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
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

import java.util.List;
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
    private static final double DOOR_OUT = 0.05;
    /** Clear of the boards, which stand out from the window's inside face by about 0.2 of a block. */
    private static final double BOARDS_OUT = 0.35;
    /** Half a block: over the middle of the space in front of a machine set into a wall. */
    private static final double FRONT_OUT = 0.5;

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

    /** A prompt and the point in the world it belongs to: above a machine or a downed teammate, or on a door or window's near face. */
    record Target(Prompted.Prompt prompt, Vec3 anchor) {}

    /** What the crosshair is on, if it is a downed teammate, a door, a window with boards missing, or a machine with something to sell. */
    static Optional<Target> target(Minecraft mc) {
        Optional<Entity> downed = ReviveTarget.find();
        if (downed.isPresent()) {
            Entity entity = downed.get();
            return Optional.of(new Target(new Prompted.Prompt("Revive " + entity.getName().getString(), 0, false),
                    entity.position().add(0, entity.getBbHeight() + ABOVE, 0)));
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos pos = hit.getBlockPos();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Optional<DoorDef> door = ClientMapState.map().doorAt(pos);
        if (door.isPresent()) {
            AABB box = null;
            for (BlockPos member : door.get().members()) {
                box = box == null ? new AABB(member) : box.minmax(new AABB(member));
            }
            return Optional.of(new Target(new Prompted.Prompt(door.get().style() == DoorDef.Style.DEBRIS ? "Debris" : "Door",
                    door.get().cost(), door.get().requiresPower()), nearFace(box, hit.getDirection(), camera, DOOR_OUT)));
        }
        BlockState state = mc.level.getBlockState(pos);
        Optional<WindowDef> window = ClientMapState.map().windowAt(pos);
        if (window.isPresent()) {
            boolean broken = state.is(PapRegistries.BARRICADE.get()) && state.getValue(BarricadeBlock.BOARDS) < window.get().maxBoards();
            return broken ? Optional.of(new Target(new Prompted.Prompt("Rebuild barricade", 0, false),
                    nearFace(window.get().opening().toAabb(), hit.getDirection(), camera, BOARDS_OUT))) : Optional.empty();
        }
        BlockPos main = state.getBlock() instanceof FeatureBlock feature ? feature.mainPos(state, pos) : pos;
        if (!(mc.level.getBlockEntity(main) instanceof Prompted prompted)) {
            return Optional.empty();
        }
        List<BlockPos> parts = state.getBlock() instanceof FeatureBlock feature ? feature.blocks(state, pos) : List.of(pos);
        AABB box = bounds(mc, pos);
        for (BlockPos part : parts) {
            box = box.minmax(bounds(mc, part));
        }
        Vec3 above = new Vec3((box.minX + box.maxX) / 2, box.maxY + ABOVE, (box.minZ + box.maxZ) / 2);
        BlockPos over = BlockPos.containing(above);
        if (!parts.contains(over) && !mc.level.getBlockState(over).getShape(mc.level, over).isEmpty()) {
            above = overFront(box, hit.getDirection(), camera, above.y);
        }
        // A wall-buy's plate sits against the wall; pulled toward the viewer, its text doesn't sink into the wall when seen from the side.
        Vec3 toward = camera.subtract(above).multiply(1, 0, 1);
        Vec3 anchor = toward.lengthSqr() > TOWARD_VIEWER * TOWARD_VIEWER ? above.add(toward.normalize().scale(TOWARD_VIEWER)) : above;
        return prompted.prompt().map(p -> new Target(p, anchor));
    }

    /**
     * Where the prompt of a machine set into a wall goes, such as a trap's switch with wall above it, where text floating
     * over the machine would be inside the block above and hidden: at the same height, but over the space in front of the
     * face you're looking at (the nearer side, looking at its top or bottom), as if the machine stood a block out from
     * the wall. Text there reads as a sign on the wall over the machine.
     */
    private static Vec3 overFront(AABB box, Direction hitFace, Vec3 camera, double y) {
        Vec3 front = nearFace(box, hitFace, camera, FRONT_OUT);
        return new Vec3(front.x, y, front.z);
    }

    private static AABB bounds(Minecraft mc, BlockPos pos) {
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    /**
     * The middle of the side of {@code box} you're looking at, {@code out} blocks off it: the face your crosshair hit,
     * moved out to the box's outer edge on that side and centered on the whole box. Looking at its top or bottom, the
     * side nearer the camera instead.
     */
    private static Vec3 nearFace(AABB box, Direction hitFace, Vec3 camera, double out) {
        Vec3 center = box.getCenter();
        Direction face = hitFace.getAxis().isHorizontal() ? hitFace : nearestSide(box, camera);
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
