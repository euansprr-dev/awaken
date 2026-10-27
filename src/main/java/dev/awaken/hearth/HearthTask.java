package dev.awaken.hearth;

import dev.awaken.core.api.CoreApi;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.WhyNot;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Checklist;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Your home on the task card (SPEC §28.2; Euan 2026-10-27, on "Use your fire for a guide": "I have NO CLUE what that
 * means"): the Hearth's first steps are a checklist, where tasks live, not a line in the corner. Without a home, what
 * you carry says how to make one. With a fire: build a room near it, then right-click it for the guide (it lists every
 * room and what each is missing), then the Lord's Heart on it when you have one. With an Anchor: right-click it. After
 * that, at home only, the one thing to do next: why a room isn't counted yet, someone waiting for a room, or the next
 * room and what it needs.
 * <p>At home the first steps lead the card (after the first moves); from afar they wait until nothing else is on it,
 * and send you home (the Now line names it). What comes after shows only at home.
 */
public final class HearthTask {
    private HearthTask() {}

    static final String TITLE = "Your home", HOME = "Your home";
    static final String EMBER = "Place your Ember on the ground where you want your home",
            ANCHOR = "Place your Hearth Anchor on the ground where you want your home",
            CRAFT = "Craft a Hearth Anchor: the Lord's Heart, 4 stone bricks and 4 copper ingots",
            SHELTER = "Build a room near your fire: walls, a roof and a door",
            SHELTER_ANCHOR = "Build a room near your Anchor: walls, a roof and a door",
            GUIDE = "Right-click your campfire to see what your home needs",
            GUIDE_ANCHOR = "Right-click your Anchor to see what your home needs",
            HEART = "Hold the Lord's Heart and right-click your campfire to make it an Anchor";

    static void register() {
        Checklist.registerHere(HearthTask::here);
        Checklist.registerLast(HearthTask::task);
    }

    /** At home while a first step is still to do: the card is the home's. */
    static Checklist.@Nullable Task here(ServerPlayer p) {
        var home = HearthService.homeOf(p);
        if (home.isEmpty() || !HearthNow.atHome(p, home.get()) || !firstSteps(p, home.get())) return null;
        return task(p);
    }

    /** The home's card for this player now, or null. Public for the tests. */
    public static Checklist.@Nullable Task task(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return null;
        var home = HearthService.homeOf(p);
        if (home.isEmpty()) {
            String text = HearthNow.holds(p, HearthContent.EMBER_ITEM.get()) ? EMBER
                    : HearthNow.holds(p, HearthContent.HEARTH_ANCHOR_ITEM.get()) ? ANCHOR
                    : HearthNow.holds(p, CoreApi.LORD_HEART.get()) ? CRAFT : null;
            return text == null ? null : new Checklist.Task("hearth:start", TITLE, List.of(Checklist.Row.of(text, false)));
        }
        HearthRecord h = home.get();
        boolean here = HearthNow.atHome(p, h);
        if (firstSteps(p, h)) {
            // only the step being done points: home on the Now line from afar, the fire on the card's own compass at home
            String place = here ? "" : HOME;
            boolean fire = h.hearthfire(), room = !h.rooms.isEmpty(), seen = seen(p);
            List<Checklist.Row> rows = new ArrayList<>(3);
            if (fire) {
                // the guide waits for a room: before that, an empty hand on the fire lifts the coal again
                rows.add(new Checklist.Row(SHELTER, "", room, 0, 0, room ? null : h.pos, place));
                rows.add(new Checklist.Row(GUIDE, "", seen, 0, 0, room && !seen ? h.pos : null, place));
                if (HearthNow.holds(p, CoreApi.LORD_HEART.get())) rows.add(new Checklist.Row(HEART, "", false, 0, 0, room && seen ? h.pos : null, place));
            } else {
                rows.add(new Checklist.Row(GUIDE_ANCHOR, "", false, 0, 0, h.pos, place));
            }
            return new Checklist.Task("hearth:first", TITLE, rows);
        }
        if (!here) return null;
        Checklist.Row next = next(p, h);
        return next == null ? null : new Checklist.Task("hearth:home", TITLE, List.of(next));
    }

    /** A room by the fire, the guide seen, the Heart in hand put on the fire: until then the card is the home's. */
    static boolean firstSteps(ServerPlayer p, HearthRecord h) {
        if (!seen(p)) return true;
        return h.hearthfire() && (h.rooms.isEmpty() || HearthNow.holds(p, CoreApi.LORD_HEART.get()));
    }

    private static boolean seen(ServerPlayer p) {
        return HearthService.player(p).tips.contains(Guide.TIP_SEEN);
    }

    /** At home, the one thing to do next: why a room isn't counted, someone waiting for a room, the next room. */
    static Checklist.@Nullable Row next(ServerPlayer p, HearthRecord h) {
        WhyNot.Reason why = HearthNow.why(p, h);
        if (why != null) return new Checklist.Row(whyText(why), "", false, 0, 0, why.compass() ? why.at() : null, "");
        String waiting = HearthNow.waiting(p, h);
        if (waiting != null) return Checklist.Row.of(waiting, false);
        RoomType t = HearthNow.nextRoom(p, h);
        if (t == null) return null;
        if (t.furniture.isEmpty()) return Checklist.Row.of(h.hearthfire() ? SHELTER : SHELTER_ANCHOR, false);
        int have = 0;
        for (Furniture f : t.furniture) if (h.furniture.length > f.ordinal() && h.furniture[f.ordinal()] > 0) have++;
        return new Checklist.Row("Make a " + t.label + ": " + t.needs + " in one room", "", false, have, t.furniture.size(), null);
    }

    /** "Your Sanctuary isn't a room yet: it sees the sky above the bed (no roof)." / "Sanctuary: closed and roofed, but missing: …" */
    static String whyText(WhyNot.Reason r) {
        String card = r.card(), not = "Not a room yet: ";
        if (card.startsWith(not)) return "Your " + r.type().label + " isn't a room yet: " + card.substring(not.length());
        return r.type().label + ": " + Character.toLowerCase(card.charAt(0)) + card.substring(1);
    }
}
