package dev.awaken.hearth;

import dev.awaken.core.api.TrailsApi;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;

/**
 * The Hearth's words for trails (docs/trails/PLAN.md §3.3, §4.1): the tokens a trail's steps are written in, read from the
 * home the player lives at (their own, else the one they are a member of), and the place "hearth" a step can point at.
 * <pre>
 * room:sanctuary      a room of that kind stands (shelter, bedroom, sanctuary, kitchen, forge, study, greenhouse, stable, hall,
 *                     bell_tower, lookout, trophy_hall, workshop, barracks, mine_head)
 * rooms:bedroom:2     that many rooms of the kind        grade:fine   a room of that grade or better (snug, fine, grand)
 * hearth_tier:2       the Hearth's tier or more          forge_tier:2 the Forge's tier or more
 * settlers:2          that many people live here         pads:1       role pads in the radius      duty:1  Echoes working them
 * cottage             Oren raised a cottage              named        the ground has a name
 * tip:guide_seen      one of the Hearth's markers for the player (Your Hearth opened, cottage offered...)
 * furniture:forge     that piece is in the radius (bed, mat, anvil, forge, stand, trophy, pad, cooker, lectern...)
 * </pre>
 */
final class HearthTrails {
    private HearthTrails() {}

    static void init() {
        TrailsApi.condition("room", (p, rest) -> rooms(p, rest) >= 1);
        TrailsApi.condition("rooms", (p, rest) -> {
            int at = rest.lastIndexOf(':');
            return at > 0 && rooms(p, rest.substring(0, at)) >= number(rest.substring(at + 1));
        });
        TrailsApi.condition("grade", (p, rest) -> {
            int want = grade(rest);
            if (want < 0) return false;
            var h = home(p);
            if (h.isEmpty()) return false;
            for (Room r : h.get().rooms) if (r.reading() != null && r.reading().grade().ordinal() >= want) return true;
            return false;
        });
        TrailsApi.condition("hearth_tier", (p, rest) -> home(p).map(h -> h.tier >= number(rest)).orElse(false));
        TrailsApi.condition("forge_tier", (p, rest) -> home(p).map(h -> h.forgeTier >= number(rest)).orElse(false));
        TrailsApi.condition("settlers", (p, rest) -> home(p).map(h -> h.folk.settlers.size() >= number(rest)).orElse(false));
        TrailsApi.condition("pads", (p, rest) -> home(p).map(h -> h.pads.size() >= number(rest)).orElse(false));
        TrailsApi.condition("duty", (p, rest) -> home(p).map(h -> h.padEchoes.size() >= number(rest)).orElse(false));
        TrailsApi.condition("cottage", (p, rest) -> home(p).map(h -> h.cottage != null).orElse(false));
        TrailsApi.condition("named", (p, rest) -> home(p).map(h -> !h.name.isEmpty()).orElse(false));
        TrailsApi.condition("tip", (p, rest) -> HearthService.player(p).tips.contains(rest));
        TrailsApi.condition("furniture", (p, rest) -> {
            try {
                return home(p).map(h -> h.furniture[Furniture.valueOf(rest.toUpperCase(Locale.ROOT)).ordinal()] >= 1).orElse(false);
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
        TrailsApi.place("hearth", p -> HearthService.homeOf(p).map(h -> h.pos).orElse(null));
    }

    private static Optional<HearthRecord> home(ServerPlayer p) {
        return HearthService.homeOf(p);
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static int grade(String word) {
        for (Reading.Grade g : Reading.Grade.values()) if (g.word.equals(word)) return g.ordinal();
        return -1;
    }

    /** The room kind a token names; "forge" is the Armory room (its id in the code stays ARMORY). */
    static RoomType kind(String name) {
        if (name.equals("forge")) return RoomType.ARMORY;
        for (RoomType t : RoomType.values()) if (t.name().toLowerCase(Locale.ROOT).equals(name)) return t;
        return null;
    }

    private static int rooms(ServerPlayer p, String name) {
        RoomType t = kind(name);
        var h = home(p);
        if (t == null || h.isEmpty()) return 0;
        int n = 0;
        for (Room r : h.get().rooms) if (r.types().contains(t)) n++;
        return n;
    }
}
