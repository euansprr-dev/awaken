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
 * What the Hearth suggests right now (the Now tracker's lists in the K menu; the steps themselves are on the task card,
 * {@link HearthTask}), one step at a time: find your home; right-click the fire or Anchor for the guide; then, at home,
 * the next room to make with live progress ("Sanctuary: bed ✓ · mat ✗"), or why a room you're building isn't recognised
 * yet ("Sanctuary: no roof", with a compass to the spot); and go home to mend.
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
            // the carried flame: under a tracked chain's step (~68), over bookkeeping
            if (holds(p, HearthContent.EMBER_ITEM.get())) {
                out.add(NowHooks.Suggestion.of("hearth:ember", 66, HearthTask.EMBER));
            } else if (holds(p, HearthContent.HEARTH_ANCHOR_ITEM.get())) {
                out.add(NowHooks.Suggestion.of("hearth:place", 72, HearthTask.ANCHOR));
            } else if (holds(p, CoreApi.LORD_HEART.get())) {
                out.add(NowHooks.Suggestion.of("hearth:craft", 70, HearthTask.CRAFT));
            }
            return out;
        }
        HearthRecord h = home.get();
        var prof = Progression.profile(p);
        long now = p.level().getGameTime();
        if (prof.fractureUntil() > now) {
            out.add(NowHooks.Suggestion.at("hearth:mend", 60, "Rest at home to heal your Fracture", h.pos));
        }
        if (!atHome(p, h)) return out;
        HearthPlayer hp = HearthService.player(p);
        if (h.hearthfire() && h.rooms.isEmpty()) {
            // the first night: walls and a roof (using the fire now would lift the coal again)
            out.add(NowHooks.Suggestion.of("hearth:shelter", 58, HearthTask.SHELTER));
            return out;
        }
        if (h.hearthfire() && holds(p, CoreApi.LORD_HEART.get())) {
            out.add(NowHooks.Suggestion.at("hearth:heart", 64, "Hold the Lord's Heart and right-click your campfire", h.pos));
        }
        if (!hp.tips.contains(Guide.TIP_SEEN)) {
            boolean fresh = h.placedAt > 0 && now - h.placedAt < FRESH_TICKS;
            out.add(NowHooks.Suggestion.of("hearth:guide", fresh ? 74 : 56, h.hearthfire() ? HearthTask.GUIDE : HearthTask.GUIDE_ANCHOR));
            return out;
        }
        NowHooks.Suggestion why = whyLine(p, h);
        if (why != null) {
            out.add(why);
            return out;
        }
        // (W6) someone you know waits for a bed, or a room for their work: "Oren will come when there's a bed for him"
        var w = waitingFor(p, h);
        if (w != null) {
            out.add(NowHooks.Suggestion.of("hearth:people/" + w.who().id(), 52, Fit.first(Fit.TRACKER,
                    w.line(), w.who().given() + " needs a room", w.who().given() + ": a room")));
            return out;
        }
        RoomType next = nextRoom(p, h);
        if (next != null) out.add(NowHooks.Suggestion.of("hearth:room/" + next.name().toLowerCase(java.util.Locale.ROOT), 50, progress(next, h)));
        return out;
    }

    /** A room being built that isn't recognised, and why (one line, the first that applies). */
    static NowHooks.@Nullable Suggestion whyLine(ServerPlayer p, HearthRecord h) {
        WhyNot.Reason reason = why(p, h);
        if (reason == null) return null;
        String id = "hearth:why/" + reason.type().name().toLowerCase(java.util.Locale.ROOT);
        return reason.compass() && reason.at() != null
                ? NowHooks.Suggestion.at(id, 62, reason.tracker(), reason.at())
                : NowHooks.Suggestion.of(id, 62, reason.tracker());
    }

    /** Why the room being built isn't recognised (the first that applies), or null. */
    static WhyNot.@Nullable Reason why(ServerPlayer p, HearthRecord h) {
        RoomType goal = nextRoom(p, h);
        for (RoomType t : RoomType.values()) {
            if (t != goal && !clearlyBuilding(t, h)) continue;
            Optional<WhyNot.Reason> r = WhyNot.of(t, h.rooms, h.misses);
            if (r.isPresent()) return r.get();
        }
        return null;
    }

    /** Someone you know who waits for a bed or a room for their work (W6), or null. */
    static dev.awaken.hearth.settle.Settlers.@Nullable Waiting waitingFor(ServerPlayer p, HearthRecord h) {
        if (h.hearthfire() || !(p.level() instanceof net.minecraft.server.level.ServerLevel sl)) return null;
        for (var w : dev.awaken.hearth.settle.Settlers.waiting(sl, h)) {
            if (w.why().startsWith("Tier") || w.why().startsWith("on the")) continue;
            return w;
        }
        return null;
    }

    /** The same, as the card says it ("Oren will come when there's a bed for him"), or null. */
    static @Nullable String waiting(ServerPlayer p, HearthRecord h) {
        var w = waitingFor(p, h);
        return w == null ? null : w.line();
    }

    /** Standing in the home's land (the overworld, inside its radius). */
    static boolean atHome(ServerPlayer p, HearthRecord h) {
        return p.level().dimension() == Level.OVERWORLD && h.contains(p.blockPosition());
    }

    /**
     * Vanilla beds and anvils turn up in villages; a why-line for those would nag. A room counts as being built when it's
     * the current goal, when it needs only mod furniture (pads, mounts, machines), or when two of its pieces are here.
     */
    /** The order rooms are suggested in: the Armoury right after a bed, then the enum's order. */
    static final List<RoomType> ORDER = order();

    private static List<RoomType> order() {
        List<RoomType> out = new ArrayList<>(List.of(RoomType.SHELTER, RoomType.BEDROOM, RoomType.FITTING));
        for (RoomType t : RoomType.values()) if (!out.contains(t)) out.add(t);
        return List.copyOf(out);
    }

    static boolean clearlyBuilding(RoomType t, HearthRecord h) {
        int present = 0;
        boolean vanilla = false;
        for (Furniture f : t.furniture) {
            if (f.recipe == null && f != Furniture.MACHINE) vanilla = true; // beds, anvils, stoves: villages have them too
            if (h.furniture.length > f.ordinal() && h.furniture[f.ordinal()] > 0) present++;
        }
        return !vanilla || present >= 2;
    }

    /**
     * The next room to suggest: shelter, a bedroom, then the Armoury, your first workshop (SPEC §27.13); a Sanctuary and the
     * Forge once there is an Anchor; the others once you have what they're made of.
     */
    static @Nullable RoomType nextRoom(ServerPlayer p, HearthRecord h) {
        for (RoomType t : ORDER) {
            if (h.hasRoom(t)) continue;
            boolean relevant = switch (t) {
                case SHELTER, BEDROOM, FITTING -> true;
                // the Anchor's rooms once there is an Anchor; the rest once you have what they're made of
                case SANCTUARY, ARMORY -> !h.hearthfire();
                case TROPHY_HALL -> h.furniture[Furniture.TROPHY.ordinal()] > 0 || holds(p, HearthContent.TROPHY_ITEM.get());
                case WORKSHOP -> h.furniture[Furniture.MACHINE.ordinal()] > 0;
                case KITCHEN, STUDY, GREENHOUSE, STABLE, HALL, BELL_TOWER -> started(t, h);
                case LOOKOUT, MINE_HEAD, SMELTERY, ROOKERY, FOUNDRY, STOREHOUSE -> false;
            };
            if (relevant) return t;
        }
        return null;
    }

    /**
     * Two kinds of this room's furniture are already in the radius, so suggesting it isn't a guess (a flower pot in a
     * bedroom is not a greenhouse being built; a bell on its own isn't a tower).
     */
    static boolean started(RoomType t, HearthRecord h) {
        if (t.furniture.size() < 2) return false;
        int kinds = 0;
        for (Furniture f : t.furniture) if (h.furniture.length > f.ordinal() && h.furniture[f.ordinal()] > 0) kinds++;
        return kinds >= 2;
    }

    /** "Sanctuary: bed ✓ · mat ✗", shortened until it fits the tracker. Public for the tests. */
    public static String progress(RoomType t, HearthRecord h) {
        if (t.furniture.isEmpty()) return Fit.first(Fit.TRACKER, t.label + ": " + t.needs, t.label);
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

    static boolean holds(ServerPlayer p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }
}
