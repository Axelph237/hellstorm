package dev.candycanecazoo.hellstorm.client.outline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OutlinesTest {
    private static Set<Outlines.Edge> worldEdges(Outlines.Cluster cluster) {
        Set<Outlines.Edge> edges = new HashSet<>();
        cluster.edges().forEach(e -> edges.add(new Outlines.Edge(e.axis(), e.pos().offset(cluster.anchor()))));
        return edges;
    }

    @Test
    void oneBlockHasTwelveEdges() {
        assertEquals(12, Outlines.cluster(List.of(new BlockPos(5, 2, -3))).edges().size());
    }

    @Test
    void twoBlocksSideBySideLoseTheFaceTheyShare() {
        Outlines.Cluster pair = Outlines.cluster(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)));
        assertEquals(16, pair.edges().size(), "a 2x1x1 box: four long edges in two pieces each, and the two ends");
        assertFalse(worldEdges(pair).contains(new Outlines.Edge(Direction.Axis.Y, new BlockPos(1, 0, 0))), "the shared face's edge is gone");
    }

    @Test
    void theInsideOfABendKeepsItsEdge() {
        Outlines.Cluster ell = Outlines.cluster(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 0, 1)));
        assertTrue(worldEdges(ell).contains(new Outlines.Edge(Direction.Axis.Y, new BlockPos(1, 0, 1))), "the inner corner of the L");
        Outlines.Cluster square = Outlines.cluster(List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(1, 0, 1)));
        assertFalse(worldEdges(square).contains(new Outlines.Edge(Direction.Axis.Y, new BlockPos(1, 0, 1))), "four blocks round an edge hide it");
        assertEquals(20, square.edges().size(), "a 2x1x2 box");
    }

    @Test
    void aLinePointsFromItsStartToItsEnd() {
        for (Vec3 d : List.of(new Vec3(3, 0, 0), new Vec3(0, 0, -2), new Vec3(1, 2, 3), new Vec3(0, 4, 0), new Vec3(0, -1, 0), new Vec3(-2, -1, 0.5))) {
            Vector3f along = Outlines.aim(d).transform(new Vector3f(0, 0, (float) d.length()));
            assertEquals(d.x, along.x(), 1e-4, d + " x");
            assertEquals(d.y, along.y(), 1e-4, d + " y");
            assertEquals(d.z, along.z(), 1e-4, d + " z");
        }
    }

    @Test
    void anOutlineFadesOverEightTicksOnceNoLongerShown() {
        assertEquals(1, Outlines.fade(1, 0.5f));
        assertEquals(1, Outlines.fade(0, 0.9f));
        assertEquals(Math.pow(7 / 8.0, 3), Outlines.fade(-1, 1), 1e-6);
        assertEquals(Math.pow(1 - 0.5 / 8, 3), Outlines.fade(-1, 0.5f), 1e-6, "eased from full during the first fading tick");
        assertTrue(Outlines.fade(-7, 0) < 1 / 8f, "a nearly faded outline drops under the cutoff and isn't drawn");
    }
}
