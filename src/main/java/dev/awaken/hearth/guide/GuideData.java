package dev.awaken.hearth.guide;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything the "Your Hearth" screen shows, built on the server ({@link Guide#build}) and sent when the Anchor is used
 * and while the screen is open. Labels, icons and rules come from {@code RoomType} and {@code Furniture} on both sides;
 * this only carries the state.
 */
public record GuideData(int tier, int radius, int rest, int sanctuaryRest, boolean respawnHere, BlockPos anchor,
                        List<RoomCard> rooms, List<RecipeView> recipes, List<Person> town, List<Person> coming,
                        Upgrade upgrade, int cottage, Home home) {

    /**
     * The home as the Hearth reads it (W1): its name, Comfort (out of {@code Comfort.MAX}) and what would add a point,
     * its Style in words, the land's features, the Rested minutes it gives, and every room it read.
     */
    public record Home(String name, int comfort, String style, String comfortNext, List<String> land, int restedMinutes, List<RoomView> rooms) {
        public static final Home NONE = new Home("", 0, "", "", List.of(), 8, List.of());
    }

    /** One read room: its name, grade (Reading.Grade ordinal), the six readings, what it is for, what would lift it, where. */
    public record RoomView(String name, int grade, List<Integer> readings, String purpose, String lift, BlockPos centre) {
        public int reading(int i) {
            return i < readings.size() ? readings.get(i) : 0;
        }
    }

    /** {@link #cottage}: Oren's offer is open, being built, built, waiting for open ground, or politely declined. */
    public static final int COTTAGE_NONE = 0, COTTAGE_OFFER = 1, COTTAGE_BUILDING = 2, COTTAGE_BUILT = 3, COTTAGE_NO_ROOM = 4,
            COTTAGE_DECLINED = 5;

    /**
     * One room type: which of its furniture the radius already holds ({@code have}, a bit per Furniture ordinal), whether
     * some of it already stands in a closed room, the rooms of this type (their centres), why it isn't recognised yet
     * ("" when nothing to say), where to put a marker, and whether Oren's cottage is one of these rooms.
     */
    public record RoomCard(int type, int have, boolean closed, List<BlockPos> built, String why, Optional<BlockPos> mark, boolean byOren) {}

    /** A crafting grid row by row ({@code slots}: the items an ingredient accepts, empty = an empty slot). */
    public record RecipeView(int furniture, int width, int height, List<List<Identifier>> slots, Identifier result, int count) {}

    /** A townsperson: name and role, and for one still to come, the rank they arrive at ("D-rank"). */
    public record Person(String name, String role, String when) {}

    /** The next Anchor tier: its numbers, the rank it needs, the price with what you carry, and whether Y can build it. */
    public record Upgrade(boolean maxed, int next, int radius, int rest, String rank, boolean rankMet, List<Part> parts,
                          int discount, boolean affordable) {
        public static final Upgrade NONE = new Upgrade(true, 0, 0, 0, "", true, List.of(), 0, false);
    }

    public record Part(Identifier item, int need, int have) {}

    public static final StreamCodec<FriendlyByteBuf, GuideData> STREAM_CODEC = StreamCodec.of(GuideData::write, GuideData::read);

    private static void write(FriendlyByteBuf b, GuideData d) {
        b.writeVarInt(d.tier).writeVarInt(d.radius).writeVarInt(d.rest).writeVarInt(d.sanctuaryRest).writeBoolean(d.respawnHere);
        b.writeBlockPos(d.anchor);
        b.writeVarInt(d.rooms.size());
        for (RoomCard c : d.rooms) {
            b.writeVarInt(c.type).writeVarInt(c.have).writeBoolean(c.closed);
            b.writeVarInt(c.built.size());
            for (BlockPos p : c.built) b.writeBlockPos(p);
            b.writeUtf(c.why, 512);
            b.writeOptional(c.mark, (buf, p) -> buf.writeBlockPos(p));
            b.writeBoolean(c.byOren);
        }
        b.writeVarInt(d.recipes.size());
        for (RecipeView r : d.recipes) {
            b.writeVarInt(r.furniture).writeVarInt(r.width).writeVarInt(r.height);
            b.writeVarInt(r.slots.size());
            for (List<Identifier> slot : r.slots) {
                b.writeVarInt(slot.size());
                for (Identifier id : slot) b.writeIdentifier(id);
            }
            b.writeIdentifier(r.result).writeVarInt(r.count);
        }
        writePeople(b, d.town);
        writePeople(b, d.coming);
        Upgrade u = d.upgrade;
        b.writeBoolean(u.maxed).writeVarInt(u.next).writeVarInt(u.radius).writeVarInt(u.rest).writeUtf(u.rank, 32).writeBoolean(u.rankMet);
        b.writeVarInt(u.parts.size());
        for (Part p : u.parts) b.writeIdentifier(p.item).writeVarInt(p.need).writeVarInt(p.have);
        b.writeVarInt(u.discount).writeBoolean(u.affordable);
        b.writeVarInt(d.cottage);
        Home h = d.home;
        b.writeUtf(h.name, 64).writeVarInt(h.comfort).writeUtf(h.style, 64).writeUtf(h.comfortNext, 96).writeVarInt(h.restedMinutes);
        b.writeVarInt(h.land.size());
        for (String l : h.land) b.writeUtf(l, 32);
        b.writeVarInt(h.rooms.size());
        for (RoomView r : h.rooms) {
            b.writeUtf(r.name, 96).writeVarInt(r.grade);
            for (int i = 0; i < 6; i++) b.writeVarInt(r.reading(i));
            b.writeUtf(r.purpose, 48).writeUtf(r.lift, 96).writeBlockPos(r.centre);
        }
    }

    private static GuideData read(FriendlyByteBuf b) {
        int tier = b.readVarInt(), radius = b.readVarInt(), rest = b.readVarInt(), sRest = b.readVarInt();
        boolean respawn = b.readBoolean();
        BlockPos anchor = b.readBlockPos();
        int n = Math.min(32, b.readVarInt());
        List<RoomCard> rooms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int type = b.readVarInt(), have = b.readVarInt();
            boolean closed = b.readBoolean();
            int bn = Math.min(64, b.readVarInt());
            List<BlockPos> built = new ArrayList<>(bn);
            for (int j = 0; j < bn; j++) built.add(b.readBlockPos());
            String why = b.readUtf(512);
            Optional<BlockPos> mark = b.readOptional(buf -> buf.readBlockPos());
            rooms.add(new RoomCard(type, have, closed, built, why, mark, b.readBoolean()));
        }
        int rn = Math.min(32, b.readVarInt());
        List<RecipeView> recipes = new ArrayList<>(rn);
        for (int i = 0; i < rn; i++) {
            int f = b.readVarInt(), w = b.readVarInt(), h = b.readVarInt();
            int sn = Math.min(9, b.readVarInt());
            List<List<Identifier>> slots = new ArrayList<>(sn);
            for (int j = 0; j < sn; j++) {
                int in = Math.min(32, b.readVarInt());
                List<Identifier> ids = new ArrayList<>(in);
                for (int k = 0; k < in; k++) ids.add(b.readIdentifier());
                slots.add(ids);
            }
            recipes.add(new RecipeView(f, w, h, slots, b.readIdentifier(), b.readVarInt()));
        }
        List<Person> town = readPeople(b), coming = readPeople(b);
        boolean maxed = b.readBoolean();
        int next = b.readVarInt(), uRadius = b.readVarInt(), uRest = b.readVarInt();
        String rank = b.readUtf(32);
        boolean rankMet = b.readBoolean();
        int pn = Math.min(16, b.readVarInt());
        List<Part> parts = new ArrayList<>(pn);
        for (int i = 0; i < pn; i++) parts.add(new Part(b.readIdentifier(), b.readVarInt(), b.readVarInt()));
        int discount = b.readVarInt();
        boolean affordable = b.readBoolean();
        Upgrade up = new Upgrade(maxed, next, uRadius, uRest, rank, rankMet, parts, discount, affordable);
        int cottage = b.readVarInt();
        String name = b.readUtf(64);
        int comfort = b.readVarInt();
        String style = b.readUtf(64), comfortNext = b.readUtf(96);
        int restedMinutes = b.readVarInt();
        int ln = Math.min(8, b.readVarInt());
        List<String> land = new ArrayList<>(ln);
        for (int i = 0; i < ln; i++) land.add(b.readUtf(32));
        int vn = Math.min(64, b.readVarInt());
        List<RoomView> views = new ArrayList<>(vn);
        for (int i = 0; i < vn; i++) {
            String roomName = b.readUtf(96);
            int grade = b.readVarInt();
            List<Integer> readings = new ArrayList<>(6);
            for (int k = 0; k < 6; k++) readings.add(b.readVarInt());
            views.add(new RoomView(roomName, grade, readings, b.readUtf(48), b.readUtf(96), b.readBlockPos()));
        }
        Home home = new Home(name, comfort, style, comfortNext, land, restedMinutes, views);
        return new GuideData(tier, radius, rest, sRest, respawn, anchor, rooms, recipes, town, coming, up, cottage, home);
    }

    private static void writePeople(FriendlyByteBuf b, List<Person> people) {
        b.writeVarInt(people.size());
        for (Person p : people) b.writeUtf(p.name, 64).writeUtf(p.role, 64).writeUtf(p.when, 32);
    }

    private static List<Person> readPeople(FriendlyByteBuf b) {
        int n = Math.min(16, b.readVarInt());
        List<Person> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new Person(b.readUtf(64), b.readUtf(64), b.readUtf(32)));
        return out;
    }

    public RoomCard card(int type) {
        for (RoomCard c : rooms) if (c.type == type) return c;
        return new RoomCard(type, 0, false, List.of(), "", Optional.empty(), false);
    }

    public Optional<RecipeView> recipe(int furniture) {
        for (RecipeView r : recipes) if (r.furniture == furniture) return Optional.of(r);
        return Optional.empty();
    }
}
