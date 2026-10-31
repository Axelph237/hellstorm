package dev.candycanecazoo.hellstorm.gen.layout;

import java.util.List;
import java.util.Optional;

/**
 * What a layout must be: between {@code minRooms} and {@code maxRooms} rooms, starting from one room tagged
 * {@code startTag}, and each {@link TagRule} met.
 */
public record Rules(int minRooms, int maxRooms, String startTag, List<TagRule> tags) {
    public Rules {
        tags = List.copyOf(tags);
        if (minRooms < 1 || maxRooms < minRooms) {
            throw new IllegalArgumentException("A map's room count must be at least 1 and its max at least its min, not " + minRooms + " to " + maxRooms);
        }
    }

    /**
     * Rooms tagged {@code tag}: at least {@code min} and at most {@code max} of them in a map, each at a depth (rooms from
     * the start) from {@code minDepth} to {@code maxDepth}.
     */
    public record TagRule(String tag, int min, Optional<Integer> max, int minDepth, Optional<Integer> maxDepth) {
        public boolean allows(int depth) {
            return depth >= minDepth && maxDepth.map(m -> depth <= m).orElse(true);
        }

        public boolean full(int count) {
            return max.map(m -> count >= m).orElse(false);
        }
    }

    public Optional<TagRule> rule(String tag) {
        return tags.stream().filter(r -> r.tag().equals(tag)).findFirst();
    }
}
