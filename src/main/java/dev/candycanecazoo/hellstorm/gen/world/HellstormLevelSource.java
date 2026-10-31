package dev.candycanecazoo.hellstorm.gen.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The overworld of a Hellstorm world type: a flat world (void by default) that also says what to build in it on first
 * start ({@link GeneratedWorlds}). {@code MAP} generates a map from {@code packs} (every installed pack if empty) with
 * the world's seed; {@code WORKSHOP} builds the map workshop. The base is plain flat ground for now: a later base can
 * hand chunks to Minecraft's terrain and put the map into it.
 */
public final class HellstormLevelSource extends FlatLevelSource {
    public enum Mode { MAP, WORKSHOP }

    private static final Codec<Mode> MODE = Codec.STRING.comapFlatMap(
            name -> Arrays.stream(Mode.values()).filter(m -> m.name().toLowerCase(Locale.ROOT).equals(name)).findFirst()
                    .map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a Hellstorm world mode: " + name)),
            m -> m.name().toLowerCase(Locale.ROOT));

    public static final Codec<HellstormLevelSource> CODEC = RecordCodecBuilder.create(i -> i.group(
            FlatLevelGeneratorSettings.CODEC.fieldOf("settings").forGetter(FlatLevelSource::settings),
            Codecs.strictOptional(MODE, "mode", Mode.MAP).forGetter(HellstormLevelSource::mode),
            Codecs.strictOptional(Codec.STRING.listOf(), "packs", List.of()).forGetter(HellstormLevelSource::packs)
    ).apply(i, i.stable(HellstormLevelSource::new)));

    private final Mode mode;
    private final List<String> packs;

    public HellstormLevelSource(FlatLevelGeneratorSettings settings, Mode mode, List<String> packs) {
        super(settings);
        this.mode = mode;
        this.packs = List.copyOf(packs);
    }

    public Mode mode() {
        return mode;
    }

    public List<String> packs() {
        return packs;
    }

    public HellstormLevelSource withPacks(List<String> packs) {
        return new HellstormLevelSource(settings(), mode, packs);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
