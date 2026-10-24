package dev.awaken.hearth;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.NowHooks;
import dev.awaken.hearth.guide.Fit;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.WhyNot;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the Hearth suggests right now (the Now tracker), one step at a time: find your home; use the Anchor for the
 * guide; then, at home, the next room to make with live progress ("Sanctuary: bed ✓ · mat ✗"), or why a room you're
 * building isn't recognised yet ("Sanctuary: no roof", with a compass to the spot); and go home to mend.
 */
public final class HearthNow {
    private HearthNow() {}

    /** The guide's first nudge outranks other reminders for ten minutes after the Anchor takes root. */
    static final int FRESH_TICKS = 20 * 60 * 10;

    static void register() {
        NowHooks.register(HearthNow::suggest);
    }

    private static List<NowHooks.Suggestion> suggest(ServerPlayer p) {
        List<NowHooks.Suggestion> out = new ArrayList<>();
        if (!Progression.profile(p).awakened()) return out;
        var home = HearthService.homeOf(p);
        if (home.isEmpty()) {
            if (holds(p, HearthContent.HEARTH_ANCHOR_ITEM.get())) {
                out.add(NowHooks.Suggestion.of("hearth:place", 72, "Place your Hearth Anchor where you want your home"));
            } else if (holds(p, CoreApi.LORD_HEART.get())) {
                out.add(NowHooks.Suggestion.of("hearth:craft", 70, "Craft a Hearth Anchor: Lord's Heart, 4 stone bricks, 4 copper"));
            }
            return out;
        }
        HearthRecord h = home.get();
        var prof = Progression.profile(p);
        long now = p.level().getGameTime();
        if (prof.fractureUntil() > now) {
            out.add(NowHooks.Suggestion.at("hearth:mend", 60, "Rest at your Hearth to mend the Fracture", h.pos));
        }
        boolean atHome = p.level().dimension() == Level.OVERWORLD && h.contains(p.blockPosition());
        if (!atHome) return out;
        HearthPlayer hp = HearthService.player(p);
        if (!hp.tips.contains(Guide.TIP_SEEN)) {
            boolean fresh = h.placedAt > 0 && now - h.placedAt < FRESH_TICKS;
            out.add(NowHooks.Suggestion.of("hearth:guide", fresh ? 74 : 56, "Use the Anchor for a guide"));
            return out;
        }
        NowHooks.Suggestion why = whyLine(p, h);
        if (why != null) {
            out.add(why);
            return out;
        }
        RoomType next = nextRoom(p, h);
        if (next != null) out.add(NowHooks.Suggestion.of("hearth:room/" + next.name().toLowerCase(java.util.Locale.ROOT), 50, progress(next, h)));
        return out;
    }

    /** A room being built that isn't recognised, and why (one line, the first that applies). */
    static NowHooks.@Nullable Suggestion whyLine(ServerPlayer p, HearthRecord h) {
        RoomType goal = nextRoom(p, h);
        for (RoomType t : RoomType.values()) {
            if (t != goal && !clearlyBuilding(t, h)) continue;
            Optional<WhyNot.Reason> r = WhyNot.of(t, h.rooms, h.misses);
            if (r.isEmpty()) continue;
            String id = "hearth:why/" + t.name().toLowerCase(java.util.Locale.ROOT);
            WhyNot.Reason reason = r.get();
            return reason.compass() && reason.at() != null
                    ? NowHooks.Suggestion.at(id, 62, reason.tracker(), reason.at())
                    : NowHooks.Suggestion.of(id, 62, reason.tracker());
        }
        return null;
    }

    /**
     * Vanilla beds and anvils turn up in villages; a why-line for those would nag. A room counts as being built when it's
     * the current goal, when it needs only mod furniture (pads, mounts, machines), or when two of its pieces are here.
     */
    static boolean clearlyBuilding(RoomType t, HearthRecord h) {
        int present = 0;
        boolean vanilla = false;
        for (Furniture f : t.furniture) {
            if (f == Furniture.BED || f == Furniture.ANVIL || f == Furniture.STAND) vanilla = true;
            if (h.furniture.length > f.ordinal() && h.furniture[f.ordinal()] > 0) present++;
        }
        return !vanilla || present >= 2;
    }

    /** The next room to suggest: a Sanctuary, then an Armory; the others once you have what they're made of. */
    static @Nullable RoomType nextRoom(ServerPlayer p, HearthRecord h) {
        for (RoomType t : RoomType.values()) {
            if (h.hasRoom(t)) continue;
            boolean relevant = switch (t) {
                case SANCTUARY, ARMORY -> true;
                case BARRACKS -> h.furniture[Furniture.PAD.ordinal()] > 0 || holdsPad(p);
                case TROPHY_HALL -> h.furniture[Furniture.TROPHY.ordinal()] > 0 || holds(p, HearthContent.TROPHY_ITEM.get());
                case WORKSHOP -> h.furniture[Furniture.MACHINE.ordinal()] > 0;
            };
            if (relevant) return t;
        }
        return null;
    }

    /** "Sanctuary: bed ✓ · mat ✗", shortened until it fits the tracker. Public for the tests. */
    public static String progress(RoomType t, HearthRecord h) {
        StringBuilder full = new StringBuilder(t.label).append(": "), tight = new StringBuilder(t.label).append(": "),
                missing = new StringBuilder(t.label).append(": "), plain = new StringBuilder(t.label).append(": ");
        boolean first = true, firstMissing = true;
        for (Furniture f : t.furniture) {
            boolean have = h.furniture.length > f.ordinal() && h.furniture[f.ordinal()] > 0;
            String mark = have ? "✓" : "✗";
            if (!first) {
                full.append(" · ");
                tight.append(' ');
            }
            full.append(f.shortName).append(' ').append(mark);
            tight.append(f.shortName).append(mark);
            if (!have) {
                if (!firstMissing) {
                    missing.append(' ');
                    plain.append(", ");
                }
                missing.append(f.shortName).append(" ✗");
                plain.append(f.shortName);
                firstMissing = false;
            }
            first = false;
        }
        return Fit.first(Fit.TRACKER, full.toString(), tight.toString(), missing.toString(), plain.toString());
    }

    private static boolean holdsPad(ServerPlayer p) {
        for (var item : HearthContent.ROLE_PAD_ITEMS.values()) if (holds(p, item.get())) return true;
        return false;
    }

    private static boolean holds(ServerPlayer p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }
}
