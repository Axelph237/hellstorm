package dev.candycanecazoo.hellstorm.client;

import dev.candycanecazoo.hellstorm.feature.VendingMachineBlock;
import dev.candycanecazoo.hellstorm.registry.PapRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** A lit vending machine's buzz, looping from its middle, as loud as the machine is lit, so it fades with the light. */
public final class VendingMachineHum extends AbstractTickableSoundInstance {
    private static final float VOLUME = 0.15f;
    /** Each machine's hum, held weakly both ways so neither keeps a machine of a world that's gone. */
    private static final Map<VendingMachineBlock.Entity, WeakReference<VendingMachineHum>> PLAYING = new WeakHashMap<>();

    private final VendingMachineBlock.Entity machine;

    private VendingMachineHum(VendingMachineBlock.Entity machine, float lit) {
        super(PapRegistries.VENDING_MACHINE_IDLE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.machine = machine;
        Vec3 at = machine.center();
        x = at.x;
        y = at.y;
        z = at.z;
        looping = true;
        delay = 0;
        volume = VOLUME * lit;
    }

    /** Starts the machine's hum if it is lit and not already humming. Called every client tick. */
    public static void keep(VendingMachineBlock.Entity machine) {
        float lit = machine.lit(0);
        WeakReference<VendingMachineHum> playing = PLAYING.get(machine);
        VendingMachineHum hum = playing == null ? null : playing.get();
        if (lit > 0 && (hum == null || !Minecraft.getInstance().getSoundManager().isActive(hum))) {
            hum = new VendingMachineHum(machine, lit);
            PLAYING.put(machine, new WeakReference<>(hum));
            Minecraft.getInstance().getSoundManager().play(hum);
        }
    }

    /** So it keeps humming, and isn't started again every tick, while block sounds are turned all the way down. */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        float lit = machine.isRemoved() || machine.getLevel() == null ? 0 : machine.lit(0);
        if (lit <= 0) {
            PLAYING.remove(machine);
            stop();
            return;
        }
        volume = VOLUME * lit;
    }
}
