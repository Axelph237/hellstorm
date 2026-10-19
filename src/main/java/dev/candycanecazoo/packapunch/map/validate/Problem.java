package dev.candycanecazoo.packapunch.map.validate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

public record Problem(Severity severity, String code, String message, Optional<BlockPos> pos) {
    public enum Severity implements StringRepresentable {
        ERROR, WARNING;

        public static final Codec<Severity> CODEC = StringRepresentable.fromEnum(Severity::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public static final Codec<Problem> CODEC = RecordCodecBuilder.create(i -> i.group(
            Severity.CODEC.fieldOf("severity").forGetter(Problem::severity),
            Codec.STRING.fieldOf("code").forGetter(Problem::code),
            Codec.STRING.fieldOf("message").forGetter(Problem::message),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(Problem::pos)
    ).apply(i, Problem::new));

    public static Problem error(String code, String message, BlockPos pos) {
        return new Problem(Severity.ERROR, code, message, Optional.ofNullable(pos));
    }

    public static Problem warning(String code, String message, BlockPos pos) {
        return new Problem(Severity.WARNING, code, message, Optional.ofNullable(pos));
    }
}
