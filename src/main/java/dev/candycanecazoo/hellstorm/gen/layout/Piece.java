package dev.candycanecazoo.hellstorm.gen.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A room as the layout sees it: its size, tags, how often it's picked ({@code weight}, 0 for only when a rule needs it),
 * at most how many of it a map has ({@code max}), and its connectors, all relative to its corner.
 */
public record Piece(String id, Size size, Set<String> tags, int weight, OptionalInt max, List<Connector> connectors) {
    public Piece {
        tags = Set.copyOf(tags);
        connectors = List.copyOf(connectors);
    }

    /** What's wrong with it as a room, if anything: each connector must be one block thick, on the side it faces out of. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (connectors.isEmpty()) {
            problems.add("Room " + id + " has no connectors, so nothing can join it");
        }
        if (weight < 0) {
            problems.add("Room " + id + " has a weight below 0");
        }
        Box room = size.box();
        for (Connector c : connectors) {
            Box o = c.opening();
            boolean inside = o.minX() >= 0 && o.minY() >= 0 && o.minZ() >= 0 && o.maxX() <= room.maxX() && o.maxY() <= room.maxY() && o.maxZ() <= room.maxZ();
            boolean onFace = switch (c.facing()) {
                case NORTH -> o.sizeZ() == 1 && o.minZ() == 0;
                case SOUTH -> o.sizeZ() == 1 && o.maxZ() == room.maxZ();
                case WEST -> o.sizeX() == 1 && o.minX() == 0;
                case EAST -> o.sizeX() == 1 && o.maxX() == room.maxX();
            };
            if (!inside || !onFace) {
                problems.add("Room " + id + "'s connector " + c.id() + " isn't one block thick on its " + c.facing().name().toLowerCase() + " side");
            }
        }
        return problems;
    }
}
