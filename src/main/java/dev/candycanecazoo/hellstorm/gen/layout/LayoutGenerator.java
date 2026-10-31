package dev.candycanecazoo.hellstorm.gen.layout;

import dev.candycanecazoo.hellstorm.gen.layout.Layout.Joint;
import dev.candycanecazoo.hellstorm.gen.layout.Layout.PlacedRoom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Grows a map out from a start room by joining open connectors, in the spirit of jigsaw with nothing capped: no depth or
 * distance limit, only the {@link Rules}. Each step takes an open connector, finds every room and quarter turn whose
 * connector fits it without overlapping a placed room or breaking a tag rule, and picks one by weight. A tag rule still
 * owed a room is favoured, and forced once the rooms left are as few as those owed. When the map is grown, open
 * connectors that happen to face each other are joined too, closing loops. A layout that misses the rules is tried
 * again from the next sub-seed. The same pieces, rules and seed always give the same layout.
 */
public final class LayoutGenerator {
    /** Tries before giving up on a seed. */
    public static final int ATTEMPTS = 100;
    /** How much more likely a room is picked while it fills a tag rule still owed rooms. */
    private static final int OWED_FAVOUR = 4;

    /** A layout, or why none could be made. */
    public sealed interface Result {
        record Generated(Layout layout, int attempts) implements Result {}

        record Failed(String reason) implements Result {}
    }

    private LayoutGenerator() {}

    public static Result generate(List<Piece> pieces, Rules rules, long seed) {
        List<Piece> sorted = pieces.stream().sorted(Comparator.comparing(Piece::id)).toList();
        List<Piece> starts = sorted.stream().filter(p -> p.tags().contains(rules.startTag())).toList();
        if (starts.isEmpty()) {
            return new Result.Failed("No room is tagged " + rules.startTag() + " to start from");
        }
        String lastReason = "";
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Attempt tried = new Attempt(sorted, starts, rules, new SplittableRandom(seed ^ attempt * 0x9E3779B97F4A7C15L));
            String reason = tried.run();
            if (reason.isEmpty()) {
                return new Result.Generated(tried.layout(), attempt + 1);
            }
            lastReason = reason;
        }
        return new Result.Failed("No layout in " + ATTEMPTS + " tries met the rules; the last: " + lastReason);
    }

    /** A connector of a placed room that nothing has joined yet. */
    private record Open(int room, Connector connector, Box opening, Facing facing, int depth) {}

    /** A room that could join an open connector: which, how turned, where, and through which of its connectors. */
    private record Candidate(Piece piece, Rotation rotation, Connector connector, int x, int y, int z, Box box, int weight) {}

    /** One try at a layout from one sub-seed. */
    private static final class Attempt {
        private final List<Piece> pieces;
        private final List<Piece> starts;
        private final Rules rules;
        private final SplittableRandom random;
        private final List<PlacedRoom> placed = new ArrayList<>();
        private final List<Joint> joints = new ArrayList<>();
        private final List<Open> open = new ArrayList<>();
        /** Connectors nothing joined while growing: walled, unless they close a loop at the end. */
        private final List<Open> sealed = new ArrayList<>();
        private final Map<String, Integer> tagCounts = new HashMap<>();
        private final Map<String, Integer> pieceCounts = new HashMap<>();

        Attempt(List<Piece> pieces, List<Piece> starts, Rules rules, SplittableRandom random) {
            this.pieces = pieces;
            this.starts = starts;
            this.rules = rules;
            this.random = random;
        }

        Layout layout() {
            return new Layout(placed, joints);
        }

        /** Grows the map; empty if it meets the rules, else why not. */
        String run() {
            int target = rules.minRooms() + random.nextInt(rules.maxRooms() - rules.minRooms() + 1);
            Piece start = starts.get(random.nextInt(starts.size()));
            place(start, Rotation.values()[random.nextInt(4)], 0, 0, 0, 0);
            while (placed.size() < target && !open.isEmpty()) {
                Open at = open.remove(random.nextInt(open.size()));
                List<Candidate> candidates = candidates(at, target);
                if (candidates.isEmpty()) {
                    // Nothing fits here; the connector stays walled, and may still close a loop later.
                    sealed.add(at);
                    continue;
                }
                Candidate chosen = pick(candidates);
                int index = place(chosen.piece(), chosen.rotation(), chosen.x(), chosen.y(), chosen.z(), at.depth() + 1);
                open.removeIf(o -> o.room() == index && o.connector().id().equals(chosen.connector().id()));
                joints.add(new Joint(at.room(), at.connector().id(), index, chosen.connector().id(), kind(at.connector(), chosen.connector()), false));
            }
            sealed.addAll(open);
            open.clear();
            closeLoops();
            if (placed.size() < rules.minRooms()) {
                return "only " + placed.size() + " rooms fit, fewer than " + rules.minRooms();
            }
            for (Rules.TagRule rule : rules.tags()) {
                int count = tagCounts.getOrDefault(rule.tag(), 0);
                if (count < rule.min()) {
                    return "only " + count + " rooms tagged " + rule.tag() + ", fewer than " + rule.min();
                }
            }
            return "";
        }

        private int place(Piece piece, Rotation rotation, int x, int y, int z, int depth) {
            int index = placed.size();
            PlacedRoom room = new PlacedRoom(index, piece, rotation, x, y, z, depth);
            placed.add(room);
            piece.tags().forEach(tag -> tagCounts.merge(tag, 1, Integer::sum));
            pieceCounts.merge(piece.id(), 1, Integer::sum);
            for (Connector c : piece.connectors()) {
                open.add(new Open(index, c, room.opening(c), room.facing(c), depth));
            }
            return index;
        }

        /** Every room, turn and connector that can join {@code at} here, weighted, with owed tag rules favoured or forced. */
        private List<Candidate> candidates(Open at, int target) {
            int depth = at.depth() + 1;
            Box joinAt = at.opening().stepped(at.facing());
            List<Candidate> all = new ArrayList<>();
            for (Piece piece : pieces) {
                if (!allowed(piece, depth)) {
                    continue;
                }
                for (Rotation rotation : Rotation.values()) {
                    for (Connector c : piece.connectors()) {
                        if (rotation.apply(c.facing()) != at.facing().opposite() || !c.fits(at.connector())) {
                            continue;
                        }
                        Box local = rotation.apply(c.opening(), piece.size());
                        if (!local.sameSize(joinAt)) {
                            continue;
                        }
                        int x = joinAt.minX() - local.minX();
                        int y = joinAt.minY() - local.minY();
                        int z = joinAt.minZ() - local.minZ();
                        Box box = rotation.apply(piece.size()).box().moved(x, y, z);
                        if (placed.stream().noneMatch(p -> p.box().intersects(box))) {
                            all.add(new Candidate(piece, rotation, c, x, y, z, box, piece.weight()));
                        }
                    }
                }
            }
            List<Candidate> owed = all.stream().filter(c -> fillsOwed(c.piece(), depth)).toList();
            int stillOwed = rules.tags().stream().mapToInt(r -> Math.max(0, r.min() - tagCounts.getOrDefault(r.tag(), 0))).sum();
            if (!owed.isEmpty() && target - placed.size() <= stillOwed) {
                return owed.stream().map(c -> withWeight(c, Math.max(1, c.weight()))).toList();
            }
            return all.stream().map(c -> fillsOwed(c.piece(), depth) ? withWeight(c, Math.max(1, c.weight()) * OWED_FAVOUR) : c)
                    .filter(c -> c.weight() > 0).toList();
        }

        private static Candidate withWeight(Candidate c, int weight) {
            return new Candidate(c.piece(), c.rotation(), c.connector(), c.x(), c.y(), c.z(), c.box(), weight);
        }

        /** Whether another of {@code piece} may go in at {@code depth}: not a second start, and within its own and its tags' limits. */
        private boolean allowed(Piece piece, int depth) {
            if (piece.tags().contains(rules.startTag())) {
                return false;
            }
            if (piece.max().isPresent() && pieceCounts.getOrDefault(piece.id(), 0) >= piece.max().getAsInt()) {
                return false;
            }
            for (String tag : piece.tags()) {
                var rule = rules.rule(tag);
                if (rule.isPresent() && (!rule.get().allows(depth) || rule.get().full(tagCounts.getOrDefault(tag, 0)))) {
                    return false;
                }
            }
            return true;
        }

        /** Whether {@code piece} at {@code depth} would count toward a tag rule still short of its min. */
        private boolean fillsOwed(Piece piece, int depth) {
            return piece.tags().stream().anyMatch(tag -> rules.rule(tag)
                    .filter(r -> r.allows(depth) && tagCounts.getOrDefault(tag, 0) < r.min()).isPresent());
        }

        private Candidate pick(List<Candidate> candidates) {
            int total = candidates.stream().mapToInt(Candidate::weight).sum();
            int roll = random.nextInt(total);
            for (Candidate c : candidates) {
                roll -= c.weight();
                if (roll < 0) {
                    return c;
                }
            }
            return candidates.get(candidates.size() - 1);
        }

        /** Joins walled connectors of different rooms that happen to face each other, side by side and the same size. */
        private void closeLoops() {
            List<Open> left = new ArrayList<>(sealed);
            for (int i = 0; i < left.size(); i++) {
                Open a = left.get(i);
                if (a == null) {
                    continue;
                }
                for (int j = i + 1; j < left.size(); j++) {
                    Open b = left.get(j);
                    if (b != null && a.room() != b.room() && b.facing() == a.facing().opposite() && a.connector().fits(b.connector())
                            && b.opening().equals(a.opening().stepped(a.facing()))) {
                        joints.add(new Joint(a.room(), a.connector().id(), b.room(), b.connector().id(), kind(a.connector(), b.connector()), true));
                        left.set(i, null);
                        left.set(j, null);
                        break;
                    }
                }
            }
        }

        private static Connector.Kind kind(Connector a, Connector b) {
            return a.kind() == Connector.Kind.DOOR || b.kind() == Connector.Kind.DOOR ? Connector.Kind.DOOR : Connector.Kind.OPEN;
        }
    }
}
