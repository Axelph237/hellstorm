package dev.candycanecazoo.packapunch.net;

import dev.candycanecazoo.packapunch.client.ClientMapState;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Results of the last `/pap map validate`, drawn at their positions while a map tool is held. */
public record ProblemsPacket(List<Problem> problems) {
    void encode(FriendlyByteBuf buf) {
        buf.writeCollection(problems, (b, element) -> b.writeWithCodec(NbtOps.INSTANCE, Problem.CODEC, element));
    }

    static ProblemsPacket decode(FriendlyByteBuf buf) {
        return new ProblemsPacket(buf.readList(b -> b.readWithCodec(NbtOps.INSTANCE, Problem.CODEC)));
    }

    void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMapState.setProblems(problems));
    }
}
