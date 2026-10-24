package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.map.validate.Problem;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentAuditTest {
    private static final ResourceLocation ZOMBIE = ResourceLocation.tryBuild("test", "zombie");
    private static final ResourceLocation MISSING = ResourceLocation.tryBuild("test", "missing");

    @Test
    void aRosterWithNothingToSpawnIsAnErrorAndAnOverrideOfAMissingProfileIsAWarning() {
        MobRoster none = new MobRoster(List.of(new MobRoster.Entry(ZOMBIE, 0, 1, Optional.empty())));
        List<Problem> problems = ContentAudit.spawnReadiness(none, Map.of(ResourceLocation.tryBuild("test", "hounds"), List.of(MISSING)), ZOMBIE::equals);
        assertEquals(List.of(ContentAudit.NO_MOB_FOR_ROUND, ContentAudit.WAVE_OVERRIDE_UNSPAWNABLE), problems.stream().map(Problem::code).toList());
        assertEquals(Problem.Severity.ERROR, problems.get(0).severity());
        assertEquals(Problem.Severity.WARNING, problems.get(1).severity());
        MobRoster fine = new MobRoster(List.of(new MobRoster.Entry(ZOMBIE, 10, 1, Optional.empty())));
        assertEquals(List.of(), ContentAudit.spawnReadiness(fine, Map.of(ResourceLocation.tryBuild("test", "hounds"), List.of(ZOMBIE)), ZOMBIE::equals));
    }
}
