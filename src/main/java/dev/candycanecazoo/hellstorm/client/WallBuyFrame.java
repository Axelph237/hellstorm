package dev.candycanecazoo.hellstorm.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The wall-buy's frame, from the user's Blockbench model ({@code assets/hellstorm/shapes/wall_buy.json}), laid out at any
 * length. The model lies flat: {@code y} is its thickness, {@code x} its width and {@code z} its length, in pixels. Its
 * groups are the {@code left} end cap, the {@code mid_section} and the {@code right} end cap along {@code z}. The caps
 * keep their size; the middle is repeated, cut short at the end, to fill the rest, so its texture is never stretched.
 * Each box keeps where on the texture each of its faces came from, so a face cut short shows only that much.
 */
final class WallBuyFrame {
    enum Side { DOWN, UP, NORTH, SOUTH, WEST, EAST }

    enum Part { LEFT, MID, RIGHT }

    /** A face's texture square, in the model's 0 to 16 units, as written. */
    record Uv(float u0, float v0, float u1, float v1) {}

    /** A box in the model's pixels, and its faces' texture squares. */
    record Box(float[] from, float[] to, Map<Side, Uv> faces) {}

    private record Element(Part part, Box box) {}

    private final List<Element> elements;
    private final float midStart;
    private final float midEnd;

    private WallBuyFrame(List<Element> elements) {
        this.elements = elements;
        float start = Float.MAX_VALUE;
        float end = -Float.MAX_VALUE;
        for (Element e : elements) {
            if (e.part() == Part.MID) {
                start = Math.min(start, e.box().from()[2]);
                end = Math.max(end, e.box().to()[2]);
            }
        }
        midStart = start;
        midEnd = end;
    }

    /** The caps' length together. */
    float capsLength() {
        return midStart + (16 - midEnd);
    }

    static WallBuyFrame load(InputStream in) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    static WallBuyFrame parse(JsonObject model) {
        JsonArray boxes = model.getAsJsonArray("elements");
        Map<Integer, Part> parts = new java.util.HashMap<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            Part part = switch (group.getAsJsonObject().get("name").getAsString()) {
                case "left" -> Part.LEFT;
                case "right" -> Part.RIGHT;
                default -> Part.MID;
            };
            for (JsonElement child : group.getAsJsonObject().getAsJsonArray("children")) {
                parts.put(child.getAsInt(), part);
            }
        }
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            JsonObject box = boxes.get(i).getAsJsonObject();
            Map<Side, Uv> faces = new EnumMap<>(Side.class);
            for (Map.Entry<String, JsonElement> face : box.getAsJsonObject("faces").entrySet()) {
                JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                faces.put(Side.valueOf(face.getKey().toUpperCase(java.util.Locale.ROOT)),
                        new Uv(uv.get(0).getAsFloat(), uv.get(1).getAsFloat(), uv.get(2).getAsFloat(), uv.get(3).getAsFloat()));
            }
            elements.add(new Element(parts.getOrDefault(i, Part.MID), new Box(floats(box, "from"), floats(box, "to"), faces)));
        }
        return new WallBuyFrame(elements);
    }

    private static float[] floats(JsonObject box, String key) {
        JsonArray a = box.getAsJsonArray(key);
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    /**
     * Every box of the frame {@code length} pixels long ({@code z} from 0 to {@code length}): the left cap as drawn, the
     * middle repeated to fill what the caps leave, the right cap moved to the far end. No shorter than the caps.
     */
    List<Box> laidOut(float length) {
        float mid = Math.max(0, length - capsLength());
        float midLength = midEnd - midStart;
        List<Box> out = new ArrayList<>();
        for (Element e : elements) {
            switch (e.part()) {
                case LEFT -> out.add(e.box());
                case RIGHT -> out.add(shifted(e.box(), midStart + mid - midEnd));
                case MID -> {
                    for (float offset = 0; offset < mid - 1e-4f; offset += midLength) {
                        float shown = Math.min(midLength, mid - offset);
                        cut(e.box(), offset, midStart + offset + shown, offset == 0, offset + midLength >= mid - 1e-4f).ifPresent(out::add);
                    }
                }
            }
        }
        return out;
    }

    private static Box shifted(Box box, float by) {
        return new Box(new float[] {box.from()[0], box.from()[1], box.from()[2] + by},
                new float[] {box.to()[0], box.to()[1], box.to()[2] + by}, box.faces());
    }

    /**
     * A copy of a middle box moved {@code by} along {@code z}, cut off at {@code end}, its texture squares cut to match.
     * Its end faces show only at the very start and end of the whole middle, not between copies.
     */
    private static java.util.Optional<Box> cut(Box box, float by, float end, boolean first, boolean last) {
        float z0 = box.from()[2] + by;
        float z1 = Math.min(box.to()[2] + by, end);
        if (z1 <= z0) {
            return java.util.Optional.empty();
        }
        float kept = (z1 - z0) / (box.to()[2] - box.from()[2]);
        Map<Side, Uv> faces = new EnumMap<>(Side.class);
        box.faces().forEach((side, uv) -> {
            switch (side) {
                case NORTH -> {
                    if (first) {
                        faces.put(side, uv);
                    }
                }
                case SOUTH -> {
                    if (last) {
                        faces.put(side, uv);
                    }
                }
                // Up and down run their v along z, west and east their u (see WallBuyRenderer's face layout).
                case UP -> faces.put(side, new Uv(uv.u0(), uv.v0(), uv.u1(), uv.v0() + (uv.v1() - uv.v0()) * kept));
                case DOWN -> faces.put(side, new Uv(uv.u0(), uv.v1() - (uv.v1() - uv.v0()) * kept, uv.u1(), uv.v1()));
                case WEST -> faces.put(side, new Uv(uv.u0(), uv.v0(), uv.u0() + (uv.u1() - uv.u0()) * kept, uv.v1()));
                case EAST -> faces.put(side, new Uv(uv.u1() - (uv.u1() - uv.u0()) * kept, uv.v0(), uv.u1(), uv.v1()));
            }
        });
        return java.util.Optional.of(new Box(new float[] {box.from()[0], box.from()[1], z0}, new float[] {box.to()[0], box.to()[1], z1}, faces));
    }
}
