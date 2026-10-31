package dev.candycanecazoo.hellstorm.client.gen;

import dev.candycanecazoo.hellstorm.client.WrappedText;
import dev.candycanecazoo.hellstorm.client.ui.BoxButton;
import dev.candycanecazoo.hellstorm.client.ui.Toggle;
import dev.candycanecazoo.hellstorm.client.ui.UiKit;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.gen.place.RoomSaver;
import dev.candycanecazoo.hellstorm.gen.world.HellstormLevelSource;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Customize screen of a Hellstorm Generated Map world: which packs in {@code hellstorm/maps/} the map is generated
 * from. None ticked means every pack there. The first ticked pack's rules shape the map; every ticked pack's rooms are in
 * the pool.
 */
public final class GeneratedMapPresetScreen extends Screen {
    private static final int ROW = 20;
    private static final int WIDTH = 240;
    private final CreateWorldScreen parent;
    private final HellstormLevelSource source;
    private final List<String> available;
    private final Set<String> chosen;

    public GeneratedMapPresetScreen(CreateWorldScreen parent, WorldCreationContext context) {
        super(Component.literal("Generated map: packs"));
        this.parent = parent;
        this.source = context.selectedDimensions().overworld() instanceof HellstormLevelSource s ? s : null;
        this.available = RoomPack.available(RoomSaver.packs());
        this.chosen = new LinkedHashSet<>(source == null ? List.of() : source.packs());
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int y = 40;
        for (String pack : available) {
            addRenderableWidget(new Toggle(left, y + 2, Component.literal(pack), chosen.contains(pack), on -> {
                if (on) {
                    chosen.add(pack);
                } else {
                    chosen.remove(pack);
                }
            }));
            y += ROW;
        }
        int buttons = Math.max(y + 10, 80);
        addRenderableWidget(new BoxButton(left, buttons, 90, ROW, CommonComponents.GUI_DONE, this::done));
        addRenderableWidget(new BoxButton(left + 94, buttons, 90, ROW, CommonComponents.GUI_CANCEL, this::onClose));
    }

    private void done() {
        if (source != null) {
            List<String> packs = available.stream().filter(chosen::contains).toList();
            HellstormLevelSource updated = source.withPacks(new ArrayList<>(packs));
            parent.getUiState().updateDimensions((access, dimensions) -> dimensions.replaceOverworldGenerator(access, updated));
        }
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiKit.tint(graphics, width, height);
        int left = (width - WIDTH) / 2;
        WrappedText.drawCentered(graphics, font, title, width / 2, 14, width, UiKit.TEXT);
        graphics.drawString(font, available.isEmpty() ? "No packs in hellstorm/maps yet: build rooms in a Map Workshop world"
                : "None ticked: every pack. The first ticked pack's rules shape the map.", left, 26, 0xA0A0A0);
        int y = 40;
        for (String pack : available) {
            graphics.drawString(font, pack, left + UiKit.SWITCH_SIZE + 6, y + 6, UiKit.TEXT);
            y += ROW;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
