package dev.candycanecazoo.packapunch.tools;

import dev.candycanecazoo.packapunch.map.DoorDef;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Optional;

/**
 * Picks the blocks of a door. Right-click a block to add it to the door being edited, or take it out again; with no door
 * being edited, right-clicking a door's block starts editing that door. Sneak-right-click stops editing.
 * Start a new door with {@code /pap door create <id>}.
 */
public final class DoorWandItem extends Item implements MapTool {
    public static final int MAX_MEMBERS = 256;

    public DoorWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.hasPermissions(2)) {
            dev.candycanecazoo.packapunch.game.Notices.show(player, Component.literal("Map tools need operator permission"));
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos();
        ZombiesMap map = MapSavedData.get(level).map();
        Optional<DoorDef> clicked = map.doorAt(pos);
        Optional<DoorDef> editing = ToolState.door(player).flatMap(id -> Optional.ofNullable(map.doors().get(id)));
        if (player.isShiftKeyDown() || editing.isEmpty()) {
            ToolState.setDoor(player, clicked.map(DoorDef::id));
            dev.candycanecazoo.packapunch.game.Notices.show(player, Component.literal(clicked.map(d -> "Editing door '" + d.id() + "' (" + d.members().size() + " blocks)")
                    .orElse(player.isShiftKeyDown() ? "Stopped editing doors" : "Click a door's block, or start one with /pap door create <id>")));
            return InteractionResult.SUCCESS;
        }
        dev.candycanecazoo.packapunch.game.Notices.show(player, Component.literal(toggle(level, editing.get(), pos)));
        return InteractionResult.SUCCESS;
    }

    /** Adds or removes one block. Returns what happened, for the action bar. */
    static String toggle(ServerLevel level, DoorDef door, BlockPos pos) {
        if (door.members().contains(pos)) {
            MapEdits.apply(level, m -> m.withDoor(door.withoutMember(pos)));
            return "Removed a block from '" + door.id() + "' (" + (door.members().size() - 1) + " blocks)";
        }
        Optional<DoorDef> other = MapSavedData.get(level).map().doorAt(pos);
        if (other.isPresent()) {
            return "That block already belongs to door '" + other.get().id() + "'";
        }
        if (level.getBlockState(pos).isAir()) {
            return "Doors are made of blocks, not air";
        }
        if (door.members().size() >= MAX_MEMBERS) {
            return "A door can have at most " + MAX_MEMBERS + " blocks";
        }
        MapEdits.apply(level, m -> m.withDoor(door.withMember(pos)));
        return "Added a block to '" + door.id() + "' (" + (door.members().size() + 1) + " blocks)";
    }
}
