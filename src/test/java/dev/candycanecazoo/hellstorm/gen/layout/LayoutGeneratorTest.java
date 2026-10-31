package dev.candycanecazoo.hellstorm.gen.layout;

import dev.candycanecazoo.hellstorm.gen.layout.Connector.Kind;
import dev.candycanecazoo.hellstorm.gen.layout.Layout.Joint;
import dev.candycanecazoo.hellstorm.gen.layout.Layout.PlacedRoom;
import dev.candycanecazoo.hellstorm.gen.layout.Rules.TagRule;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LayoutGeneratorTest {
    /** A 3 by 3 doorway in the middle of a side of a room {@code x} by {@code z}, 5 tall. */
    private static Connector door(String id, Facing facing, int x, int z, Kind kind, String group) {
        Box opening = switch (facing) {
            case NORTH -> new Box(x / 2 - 1, 1, 0, x / 2 + 1, 3, 0);
            case SOUTH -> new Box(x / 2 - 1, 1, z - 1, x / 2 + 1, 3, z - 1);
            case WEST -> new Box(0, 1, z / 2 - 1, 0, 3, z / 2 + 1);
            case EAST -> new Box(x - 1, 1, z / 2 - 1, x - 1, 3, z / 2 + 1);
        };
        return new Connector(id, opening, facing, kind, group);
    }

    private static Piece room(String id, int x, int z, Set<String> tags, int weight, OptionalInt max, Kind kind, Facing... sides) {
        List<Connector> connectors = new ArrayList<>();
        for (Facing side : sides) {
            connectors.add(door(side.name().toLowerCase(), side, x, z, kind, ""));
        }
        return new Piece(id, new Size(x, 5, z), tags, weight, max, connectors);
    }

    private static final List<Piece> PIECES = List.of(
            room("start", 9, 9, Set.of("start"), 1, OptionalInt.empty(), Kind.DOOR, Facing.values()),
            room("hall", 5, 11, Set.of("hallway"), 6, OptionalInt.empty(), Kind.OPEN, Facing.NORTH, Facing.SOUTH),
            room("square", 9, 9, Set.of(), 4, OptionalInt.empty(), Kind.DOOR, Facing.values()),
            room("long", 9, 15, Set.of(), 3, OptionalInt.empty(), Kind.DOOR, Facing.NORTH, Facing.SOUTH, Facing.EAST),
            room("pap_a", 7, 7, Set.of("pack_a_punch"), 1, OptionalInt.empty(), Kind.DOOR, Facing.SOUTH),
            room("pap_b", 9, 7, Set.of("pack_a_punch"), 1, OptionalInt.empty(), Kind.DOOR, Facing.SOUTH),
            room("power", 7, 7, Set.of("power"), 1, OptionalInt.of(1), Kind.DOOR, Facing.SOUTH));

    private static final Rules RULES = new Rules(8, 14, "start", List.of(
            new TagRule("pack_a_punch", 1, Optional.of(1), 3, Optional.empty()),
            new TagRule("power", 1, Optional.of(1), 1, Optional.of(5))));

    private static Layout layout(long seed) {
        LayoutGenerator.Result result = LayoutGenerator.generate(PIECES, RULES, seed);
        return assertInstanceOf(LayoutGenerator.Result.Generated.class, result, () -> "seed " + seed + ": " + result).layout();
    }

    @Test
    void theSameSeedGivesTheSameLayoutAndOthersDiffer() {
        assertEquals(layout(42), layout(42));
        Set<Layout> seen = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            seen.add(layout(seed));
        }
        assertTrue(seen.size() > 15, "twenty seeds gave only " + seen.size() + " layouts");
    }

    @Test
    void theOrderPiecesComeInDoesntMatter() {
        List<Piece> reversed = new ArrayList<>(PIECES);
        java.util.Collections.reverse(reversed);
        assertEquals(layout(7), ((LayoutGenerator.Result.Generated) LayoutGenerator.generate(reversed, RULES, 7)).layout());
    }

    @Test
    void overAThousandSeedsEveryLayoutIsPlayable() {
        int loops = 0;
        for (long seed = 0; seed < 1000; seed++) {
            Layout layout = layout(seed);
            List<PlacedRoom> rooms = layout.rooms();
            assertTrue(rooms.size() >= RULES.minRooms() && rooms.size() <= RULES.maxRooms(), "seed " + seed + " has " + rooms.size() + " rooms");
            for (int i = 0; i < rooms.size(); i++) {
                for (int j = i + 1; j < rooms.size(); j++) {
                    assertFalse(rooms.get(i).box().intersects(rooms.get(j).box()), "seed " + seed + ": rooms " + i + " and " + j + " overlap");
                }
            }
            for (Joint joint : layout.joints()) {
                PlacedRoom a = rooms.get(joint.roomA());
                PlacedRoom b = rooms.get(joint.roomB());
                Connector ca = connector(a, joint.connectorA());
                Connector cb = connector(b, joint.connectorB());
                assertEquals(a.facing(ca).opposite(), b.facing(cb), "seed " + seed + ": a joint's openings face each other");
                assertEquals(a.opening(ca).stepped(a.facing(ca)), b.opening(cb), "seed " + seed + ": a joint's openings sit side by side");
                loops += joint.loop() ? 1 : 0;
            }
            assertEquals(rooms.size(), reached(layout), "seed " + seed + ": every room is reached from the start");
            assertEquals(1, rooms.stream().filter(r -> r.piece().tags().contains("start")).count(), "one start room");
            assertEquals(0, rooms.get(0).depth(), "the first room is the start");
            for (TagRule rule : RULES.tags()) {
                List<PlacedRoom> tagged = rooms.stream().filter(r -> r.piece().tags().contains(rule.tag())).toList();
                assertTrue(tagged.size() >= rule.min() && rule.max().map(m -> tagged.size() <= m).orElse(true),
                        "seed " + seed + ": " + tagged.size() + " rooms tagged " + rule.tag());
                assertTrue(tagged.stream().allMatch(r -> rule.allows(r.depth())), "seed " + seed + ": " + rule.tag() + " at a depth its rule allows");
            }
            Map<String, Long> counts = rooms.stream().collect(Collectors.groupingBy(r -> r.piece().id(), Collectors.counting()));
            assertTrue(counts.getOrDefault("power", 0L) <= 1, "a room with max 1 appears at most once");
        }
        assertTrue(loops > 0, "rooms that happen to line up close loops");
    }

    private static Connector connector(PlacedRoom room, String id) {
        return room.piece().connectors().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    /** How many rooms the start reaches through the joints. */
    private static int reached(Layout layout) {
        Map<Integer, List<Integer>> next = new java.util.HashMap<>();
        for (Joint j : layout.joints()) {
            next.computeIfAbsent(j.roomA(), k -> new ArrayList<>()).add(j.roomB());
            next.computeIfAbsent(j.roomB(), k -> new ArrayList<>()).add(j.roomA());
        }
        Set<Integer> seen = new HashSet<>(List.of(0));
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(0));
        while (!queue.isEmpty()) {
            for (int n : next.getOrDefault(queue.poll(), List.of())) {
                if (seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return seen.size();
    }

    @Test
    void aJointIsADoorIfEitherSideIsAndOpenOnlyIfBothAre() {
        Map<String, Piece> byId = PIECES.stream().collect(Collectors.toMap(Piece::id, Function.identity()));
        for (long seed = 0; seed < 50; seed++) {
            Layout layout = layout(seed);
            for (Joint joint : layout.joints()) {
                boolean bothOpen = connector(layout.rooms().get(joint.roomA()), joint.connectorA()).kind() == Kind.OPEN
                        && connector(layout.rooms().get(joint.roomB()), joint.connectorB()).kind() == Kind.OPEN;
                assertEquals(bothOpen ? Kind.OPEN : Kind.DOOR, joint.kind());
            }
        }
        assertNotNull(byId.get("hall"));
    }

    @Test
    void groupsOnlyJoinTheirOwnKindOfOpening() {
        Connector hall = new Connector("a", new Box(0, 0, 0, 2, 2, 0), Facing.NORTH, Kind.OPEN, "hall");
        Connector vent = new Connector("b", new Box(0, 0, 0, 2, 2, 0), Facing.SOUTH, Kind.OPEN, "vent");
        Connector any = new Connector("c", new Box(0, 0, 0, 2, 2, 0), Facing.SOUTH, Kind.OPEN, "");
        Connector wide = new Connector("d", new Box(0, 0, 0, 4, 2, 0), Facing.SOUTH, Kind.OPEN, "");
        assertFalse(hall.fits(vent));
        assertTrue(hall.fits(any) && any.fits(hall));
        assertFalse(any.fits(wide), "openings must be the same size");
    }

    @Test
    void aQuarterTurnSwapsTheSidesAndKeepsBoxesInside() {
        Size size = new Size(9, 5, 15);
        assertEquals(new Size(15, 5, 9), Rotation.CLOCKWISE_90.apply(size));
        assertEquals(Facing.EAST, Rotation.CLOCKWISE_90.apply(Facing.NORTH));
        assertEquals(Facing.WEST, Rotation.CLOCKWISE_180.apply(Facing.EAST));
        assertEquals(Facing.WEST, Rotation.COUNTERCLOCKWISE_90.apply(Facing.NORTH));
        Box north = new Box(3, 1, 0, 5, 3, 0);
        for (Rotation r : Rotation.values()) {
            Box turned = r.apply(north, size);
            Size turnedSize = r.apply(size);
            assertTrue(turned.minX() >= 0 && turned.minZ() >= 0 && turned.maxX() < turnedSize.x() && turned.maxZ() < turnedSize.z(), r + " keeps it inside");
            Piece piece = new Piece("p", size, Set.of(), 1, OptionalInt.empty(), List.of(new Connector("n", north, Facing.NORTH, Kind.OPEN, "")));
            Piece turnedPiece = new Piece("t", turnedSize, Set.of(), 1, OptionalInt.empty(),
                    List.of(new Connector("n", turned, r.apply(Facing.NORTH), Kind.OPEN, "")));
            assertEquals(List.of(), piece.problems());
            assertEquals(List.of(), turnedPiece.problems(), r + " leaves the opening on the side it now faces");
        }
    }

    @Test
    void aConnectorOffItsSideOrARoomWithoutOneIsAProblem() {
        Piece floating = new Piece("p", new Size(9, 5, 9), Set.of(), 1, OptionalInt.empty(),
                List.of(new Connector("n", new Box(3, 1, 2, 5, 3, 2), Facing.NORTH, Kind.OPEN, "")));
        assertEquals(1, floating.problems().size());
        assertEquals(1, new Piece("q", new Size(9, 5, 9), Set.of(), 1, OptionalInt.empty(), List.of()).problems().size());
    }

    @Test
    void layoutsThatCantMeetTheRulesAreRefusedWithAReason() {
        LayoutGenerator.Result noStart = LayoutGenerator.generate(PIECES, new Rules(2, 4, "lobby", List.of()), 1);
        assertTrue(assertInstanceOf(LayoutGenerator.Result.Failed.class, noStart).reason().contains("lobby"));
        Rules twoPowers = new Rules(8, 14, "start", List.of(new TagRule("power", 2, Optional.empty(), 1, Optional.empty())));
        LayoutGenerator.Result impossible = LayoutGenerator.generate(PIECES, twoPowers, 1);
        assertTrue(assertInstanceOf(LayoutGenerator.Result.Failed.class, impossible).reason().contains("power"));
    }
}
