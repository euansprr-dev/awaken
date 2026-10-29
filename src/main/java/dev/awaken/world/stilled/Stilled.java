package dev.awaken.world.stilled;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.citizens.api.Settlement;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.core.api.Destinations;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Stilled's saved state and what reading one does (SPEC §43.3): which spots hold a figure (never placed twice), the
 * left chests' places, and per player what they've taken, read, shown and opened. What a Stilled carries is per player:
 * each takes it once, and one lost before it's used (a key before its chest opens, a keepsake before it's shown) is still
 * there to take again; a letter can always be taken again while you don't hold it.
 */
public final class Stilled extends SavedData {
    static final int SCHEMA = 1;
    /** A spot looked at and found unfit (water, a Hearth, a build): never tried again. */
    static final long SKIPPED = Long.MIN_VALUE;

    static final Codec<Stilled> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("placed", Map.of()).forGetter(s -> s.placed),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("chests", Map.of()).forGetter(s -> s.chests),
            Codec.STRING.listOf().optionalFieldOf("keyed", List.of()).forGetter(s -> List.copyOf(s.keyed)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf()).optionalFieldOf("marks", Map.of())
                    .forGetter(s -> { Map<UUID, List<String>> m = new HashMap<>(); s.marks.forEach((u, v) -> m.put(u, List.copyOf(v))); return m; })
    ).apply(i, (schema, placed, chests, keyed, marks) -> new Stilled(placed, chests, keyed, marks)));

    static final SavedDataType<Stilled> TYPE = new SavedDataType<>(Awaken.id("world_stilled"), Stilled::new, CODEC);

    /** Spot id to its block (or {@link #SKIPPED}). */
    final Map<String, Long> placed = new HashMap<>();
    /** A settlement's key to its left chest's block. */
    final Map<String, Long> chests = new HashMap<>();
    /** The settlements a placed Stilled's key opens (one key to a chest). */
    final Set<String> keyed = new HashSet<>();
    /** Per player: "taken:fig", "done:fig", "read:n", "shown:fig", "opened:settlement", "relic:id", "met". */
    final Map<UUID, Set<String>> marks = new HashMap<>();

    public Stilled() {}

    Stilled(Map<String, Long> placed, Map<String, Long> chests, List<String> keyed, Map<UUID, List<String>> marks) {
        this.placed.putAll(placed);
        this.chests.putAll(chests);
        this.keyed.addAll(keyed);
        marks.forEach((u, v) -> this.marks.put(u, new HashSet<>(v)));
    }

    static Stilled get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    static boolean has(ServerPlayer p, String mark) {
        return get(p.level().getServer()).marks.getOrDefault(p.getUUID(), Set.of()).contains(mark);
    }

    static boolean mark(ServerPlayer p, String mark) {
        Stilled s = get(p.level().getServer());
        boolean added = s.marks.computeIfAbsent(p.getUUID(), u -> new HashSet<>()).add(mark);
        if (added) s.setDirty();
        return added;
    }

    // ---- reading one ------------------------------------------------------------------------------------------------

    /** The System reads a Stilled, in brackets, and what they carry is handed over (once, per player). */
    static void read(ServerPlayer p, ServerLevel level, StilledBlockEntity be) {
        if (be.place.isEmpty() && !be.settlement.isEmpty())
            for (var e : dev.awaken.village.api.Villages.emptyNear(level, new BlockPos(be.targetX, 0, be.targetZ), 16))
                if (e.key().equals(be.settlement) && e.name() != null) {
                    be.place = e.name();
                    be.changed();
                }
        StilledText.Pose pose = be.pose();
        StilledText.Look look = be.look();
        String fig = be.figure();
        StringBuilder line = new StringBuilder("[");
        if (!be.name.isEmpty()) line.append(be.name).append(". ");
        line.append(StilledText.fill(pose.reading, look));
        String holder = pose.two() ? "One of them" : StilledText.cap(look.he);
        String his = pose.two() ? "their" : look.his;
        ItemStack give = ItemStack.EMPTY;
        switch (be.carry) {
            case "letter" -> {
                if (holds(p, fig)) line.append(" You've got the letter ").append(pose.two() ? "they were" : look.he + " was").append(" carrying.");
                else {
                    give = letter(be);
                    line.append(' ').append(holder).append(pose.two() ? " is" : "'s").append(" holding a letter.");
                }
            }
            case "key" -> {
                if (has(p, "done:" + fig)) line.append(" You opened the chest ").append(his).append(" key was for.");
                else if (holds(p, fig)) line.append(" You've got ").append(his).append(" key.");
                else {
                    give = key(be);
                    line.append(" There's a key in ").append(pose.two() ? "one of their hands" : look.his + " hand").append(" with a tag on it.");
                }
            }
            case "keepsake" -> {
                String thing = StilledText.KEEPSAKES[Math.floorMod(be.n, StilledText.KEEPSAKES.length)].thing();
                if (has(p, "done:" + fig) || holds(p, fig)) line.append(" You've got ").append(his).append(' ').append(thing).append('.');
                else {
                    give = keepsake(be);
                    line.append(' ').append(holder).append(pose.two() ? " is" : "'s").append(" holding something small: a ").append(thing).append('.');
                }
            }
            default -> {}
        }
        line.append(']');
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line.toString())).topic("stilled").ttl(20 * 10).build());
        if (!give.isEmpty()) {
            mark(p, "taken:" + fig);
            p.getInventory().placeItemBackInInventory(give, net.minecraft.util.Prediction.SERVER_ONLY);
            level.playSound(null, be.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.9F);
        }
        if (mark(p, "met:" + fig)) dev.awaken.progression.Progression.count(p, "awaken:stilled_met");
    }

    /** Whether a player holds the thing this Stilled carried. */
    static boolean holds(ServerPlayer p, String fig) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (fig.equals(figureOf(inv.getItem(i)))) return true;
        return false;
    }

    static @Nullable String figureOf(ItemStack st) {
        if (st.isEmpty()) return null;
        var l = st.get(StilledContent.LETTER.get());
        if (l != null) return l.figure();
        var k = st.get(StilledContent.KEY.get());
        if (k != null) return k.figure();
        var s = st.get(StilledContent.KEEPSAKE.get());
        return s != null ? s.figure() : null;
    }

    static ItemStack letter(StilledBlockEntity be) {
        ItemStack st = new ItemStack(StilledContent.LETTER_ITEM.get());
        st.set(StilledContent.LETTER.get(), new StilledContent.LetterData(be.n, be.place, be.settlement, be.figure()));
        st.set(DataComponents.WRITTEN_BOOK_CONTENT, book(be.n, be.place));
        st.set(DataComponents.TOOLTIP_DISPLAY, net.minecraft.world.item.component.TooltipDisplay.DEFAULT.withHidden(DataComponents.WRITTEN_BOOK_CONTENT, true));
        return st;
    }

    /** The letter as a book's pages (a page break at a paragraph once a page grows long). */
    static WrittenBookContent book(int n, String place) {
        StilledText.Letter l = StilledText.LETTERS[Math.floorMod(n, StilledText.LETTERS.length)];
        String text = StilledText.letter(n, place);
        List<Filterable<Component>> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String para : text.split("\n\n")) {
            if (page.length() > 0 && page.length() + para.length() > 200) {
                pages.add(Filterable.passThrough(Component.literal(page.toString().strip())));
                page.setLength(0);
            }
            page.append(para).append("\n\n");
        }
        if (page.length() > 0) pages.add(Filterable.passThrough(Component.literal(page.toString().strip())));
        return new WrittenBookContent(Filterable.passThrough("A letter"), l.from(), 0, pages, true);
    }

    static ItemStack key(StilledBlockEntity be) {
        ItemStack st = new ItemStack(StilledContent.KEY_ITEM.get());
        st.set(StilledContent.KEY.get(), new StilledContent.KeyData(be.settlement, be.place, be.targetX, be.targetZ, be.figure()));
        return st;
    }

    static ItemStack keepsake(StilledBlockEntity be) {
        ItemStack st = new ItemStack(StilledContent.KEEPSAKE_ITEM.get());
        st.set(StilledContent.KEEPSAKE.get(), new StilledContent.KeepsakeData(be.n, be.family, be.relation, be.figure()));
        return st;
    }

    // ---- what they carried, used ------------------------------------------------------------------------------------

    /** A letter read: kept (stage 4 files the lead it names as Found). */
    static void readLetter(ServerPlayer p, StilledContent.LetterData d) {
        if (mark(p, "read:" + d.n() + ":" + d.lead())) dev.awaken.progression.Progression.count(p, "awaken:stilled_letters_read");
    }

    /** A key read: its tag aloud, and Track points at the settlement (its well once planned). */
    static void readKey(ServerPlayer p, ItemStack st, StilledContent.KeyData d) {
        ServerLevel level = p.level().getServer().overworld();
        String name = d.name();
        BlockPos at = new BlockPos(d.x(), p.getBlockY(), d.z());
        for (var e : dev.awaken.village.api.Villages.emptyNear(level, at, 16)) {
            if (!e.key().equals(d.settlement())) continue;
            if (e.name() != null) name = e.name();
            if (e.well() != null) at = e.well();
        }
        if (!name.equals(d.name())) st.set(StilledContent.KEY.get(), new StilledContent.KeyData(d.settlement(), name, d.x(), d.z(), d.figure()));
        double dx = at.getX() - p.getX(), dz = at.getZ() - p.getZ();
        int far = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
        String where = far < 24 ? "You're nearly there." : "That's about " + far + " blocks " + StilledText.bearing(dx, dz) + ".";
        String tag = name.isEmpty() ? "The tag says \"By the well.\" The rest of it has worn away. " : "The tag says \"" + name + ", by the well.\" ";
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("[" + tag + where + "]")).topic("stilled").ttl(20 * 8).build());
        if (far >= 24) Destinations.set(p, new Destinations.Target(name.isEmpty() ? "The key's chest" : name, Level.OVERWORLD.identifier(), at, "spot"));
    }

    /**
     * A keepsake looked at: the family name on it; in Carillon, where somebody of that family is right now (and Track
     * points there). The city's own register is the System's (SPEC §43.3).
     */
    static void readKeepsake(ServerPlayer p, StilledContent.KeepsakeData d) {
        String thing = StilledText.KEEPSAKES[Math.floorMod(d.n(), StilledText.KEEPSAKES.length)].thing();
        String name = StilledText.family(d.family());
        ServerLevel level = p.level().getServer().overworld();
        Settlement city = Settlements.byId(Settlements.CARILLON).orElse(null);
        if (city == null || p.level() != level || !dev.awaken.core.api.CityHooks.inCity(level, p.blockPosition())) {
            say(p, "[A " + thing + " with " + name + " scratched into it. Somebody in Carillon might know this name.]");
            return;
        }
        int best = -1;
        Vec3 where = null;
        for (int rec = 0; rec < city.records(); rec++) {
            if (!ofFamily(city, rec, d.family())) continue;
            Vec3 w = city.where(rec);
            if (w == null) continue;
            if (where == null || w.distanceToSqr(p.position()) < where.distanceToSqr(p.position())) {
                best = rec;
                where = w;
            }
        }
        String family = StilledText.cap(StilledText.plural(d.family()));
        if (best < 0) {
            say(p, "[" + family + " live in Carillon, but none of them are out and about right now. Try again in the daytime.]");
            return;
        }
        BlockPos at = BlockPos.containing(where);
        String person = city.personName(best);
        say(p, "[" + family + " live in Carillon. " + person + " is in " + city.district(at) + " right now. Show them the " + thing + ".]");
        Destinations.set(p, new Destinations.Target(person, Level.OVERWORLD.identifier(), at, "spot"));
    }

    static boolean ofFamily(Settlement s, int rec, String family) {
        String n = s.personName(rec);
        return n.endsWith(" " + family);
    }

    /** A keepsake shown to one of its family: their reaction in plain words, once; you keep it. */
    static boolean shown(ServerPlayer p, Settlement s, int rec, ItemStack held) {
        var d = held.get(StilledContent.KEEPSAKE.get());
        if (d == null || s.id() != Settlements.CARILLON || !ofFamily(s, rec, d.family())) return false;
        String given = s.personName(rec);
        given = given.substring(0, Math.max(0, given.indexOf(' ')));
        String[] rel = StilledText.relationWord(d.relation());
        if (mark(p, "done:" + d.figure())) {
            s.say(p, rec, StilledText.reaction(given, d.n(), rel), 20 * 14);
            dev.awaken.progression.Progression.count(p, "awaken:keepsakes_shown");
        } else {
            s.say(p, rec, given + ": You keep it. Really. I'm glad it's out there with you.", 20 * 5);
        }
        return true;
    }

    static void say(ServerPlayer p, String line) {
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line)).topic("stilled").ttl(20 * 9).build());
    }
}
