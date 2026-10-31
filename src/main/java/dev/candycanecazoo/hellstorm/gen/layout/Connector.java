package dev.candycanecazoo.hellstorm.gen.layout;

/**
 * An opening on a room's outer face, where another room can join it. {@code opening} is the opening's blocks, one block
 * thick, on the side of the room it faces out of. Two connectors join when their openings are the same size, face each
 * other and sit side by side, so the rooms touch wall to wall. An empty {@code group} fits any opening of its size; two
 * groups that aren't empty must match. {@link Kind} says what a joined opening becomes.
 */
public record Connector(String id, Box opening, Facing facing, Kind kind, String group) {
    public enum Kind {
        /** A door to buy, its cost from the pack's rules by depth. */
        DOOR,
        /** Carved through, so the rooms are one walk. */
        OPEN
    }

    public boolean fits(Connector other) {
        return opening.sameSize(other.opening) && (group.isEmpty() || other.group.isEmpty() || group.equals(other.group));
    }
}
