package dev.candycanecazoo.hellstorm.gen.layout;

import java.util.List;

/** A generated map's rooms, where each sits and how it's turned, and which of their connectors are joined. */
public record Layout(List<PlacedRoom> rooms, List<Joint> joints) {
    public Layout {
        rooms = List.copyOf(rooms);
        joints = List.copyOf(joints);
    }

    /** A room in the map: its corner {@code x}, {@code y}, {@code z} after turning, and its distance from the start in joints. */
    public record PlacedRoom(int index, Piece piece, Rotation rotation, int x, int y, int z, int depth) {
        /** The blocks it takes up. */
        public Box box() {
            return rotation.apply(piece.size()).box().moved(x, y, z);
        }

        /** Where one of its connectors' openings is in the map, and which way it faces. */
        public Box opening(Connector connector) {
            return rotation.apply(connector.opening(), piece.size()).moved(x, y, z);
        }

        public Facing facing(Connector connector) {
            return rotation.apply(connector.facing());
        }
    }

    /** Two rooms joined at a connector each, and what the opening becomes: a door if either side says so. */
    public record Joint(int roomA, String connectorA, int roomB, String connectorB, Connector.Kind kind, boolean loop) {}
}
