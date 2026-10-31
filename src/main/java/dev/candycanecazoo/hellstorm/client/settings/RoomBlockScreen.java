package dev.candycanecazoo.hellstorm.client.settings;

import com.mojang.serialization.DataResult;
import dev.candycanecazoo.hellstorm.gen.block.RoomBlock;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import dev.candycanecazoo.hellstorm.net.SaveRoomPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The Room Block's settings: the pack and room it saves, the room's tags, weight and max, and its box. Save room saves it. */
final class RoomBlockScreen extends SettingsScreen<RoomBlock.Config> {
    private String pack;
    private String room;
    private String tags;
    private String weight;
    private String max;
    private String offset;
    private String size;

    RoomBlockScreen(RoomBlock.Entity entity) {
        super(Component.literal("Room Block"), entity);
        RoomBlock.Config c = entity.config();
        pack = c.pack();
        room = c.room();
        tags = String.join(", ", c.tags());
        weight = String.valueOf(c.weight());
        max = c.max().map(String::valueOf).orElse("");
        offset = c.offset().getX() + " " + c.offset().getY() + " " + c.offset().getZ();
        size = c.size().getX() + " " + c.size().getY() + " " + c.size().getZ();
    }

    @Override
    protected void rows() {
        text(Component.literal("Pack"), pack, 32, Component.literal("hellstorm/maps/<pack>"), v -> pack = v);
        text(Component.literal("Room"), room, 64, Component.literal("its name in the pack"), v -> room = v);
        text(Component.literal("Tags"), tags, 256, Component.literal("start, hallway, pack_a_punch, ..."), v -> tags = v);
        number(Component.literal("Weight"), weight, Component.literal("how often it's picked; 0 for only when a rule needs it"), 1, v -> weight = v);
        text(Component.literal("At most"), max, 6, Component.literal("blank for no limit"), v -> max = v);
        text(Component.literal("Offset"), offset, 16, Component.literal("x y z from this block"), v -> offset = v);
        text(Component.literal("Size"), size, 16, Component.literal("x y z, 1 to " + RoomBlock.MAX_SIZE + " each"), v -> size = v);
        button(Component.literal("Save the room"), Component.literal("Save room"),
                () -> encodedSettings().ifPresent(tag -> PapNetwork.CHANNEL.sendToServer(new SaveRoomPacket(pos(), tag))));
    }

    @Override
    protected DataResult<RoomBlock.Config> settings() {
        try {
            List<String> tagList = Arrays.stream(tags.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
            Optional<Integer> limit = max.isBlank() ? Optional.empty() : Optional.of(Integer.parseInt(max.trim()));
            int[] o = three(offset);
            int[] sz = three(size);
            RoomBlock.Config config = new RoomBlock.Config(pack.trim(), room.trim(), tagList, Integer.parseInt(weight.trim()), limit,
                    new BlockPos(o[0], o[1], o[2]), new Vec3i(sz[0], sz[1], sz[2]));
            return config.problem().<DataResult<RoomBlock.Config>>map(p -> DataResult.error(() -> p)).orElseGet(() -> DataResult.success(config));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Weight and at most are whole numbers; offset and size are three each, like 1 0 1");
        }
    }

    /** Three whole numbers apart by spaces or commas. */
    private static int[] three(String text) {
        String[] parts = text.trim().split("[\\s,]+");
        if (parts.length != 3) {
            throw new NumberFormatException(text);
        }
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }
}
