package dev.awaken.living.wayfarer;

import dev.awaken.living.quest.QuestBook;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import dev.awaken.progression.Requirements;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.signal.ui.StoryPager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The Wayfarers (SPEC §21, §43.1): Ilse at the Last Fire, Hettie in Carillon's market square, Hale at the High Gate; and
 * what happens when you talk to one: a step waiting on them moves on, something you carry for them changes hands, or they
 * say something of their own. None of them gives a task. Lines are authored; none is generated.
 */
public final class Wayfarers {
    private Wayfarers() {}

    /** A Wayfarer: display name, slim arms, and a few lines for when nothing else is to be said. */
    public record Person(String name, boolean slim, List<String> idle) {}

    static final Map<String, Person> PEOPLE = Map.of(
            "ilse", new Person("Ilse", true, List.of(
                    "Ilse: Sit down a minute. The kettle's on.",
                    "Ilse: Tell me what it looks like out there. I haven't seen past that ridge in eleven years.",
                    "Ilse: Did you say hello to Aldo for me? He's on the milestone, just past the ridge.",
                    "Ilse: Watch before you draw. Most of what lives out there just wants to be left alone.")),
            "hettie", new Person("Hettie", true, List.of(
                    "Hettie: I had forty sheep up in the hills once. Now I've got a stall and a view of the fountain.",
                    "Hettie: When the bells went quiet up our way, we walked here. All of us, and the dogs. It took nine days.",
                    "Hettie: The city's loud. You get used to it. I still keep waiting for a ewe to shout at me.",
                    "Hettie: If you're going out past the ridge, wrap up. It's colder out there than it looks from here.")),
            "hale", new Person("Hale", false, List.of("Hale: Licence? Fine. The Commons bite less than they look. Mostly.", "Hale: Board's there. Work for new licences.")));

    public static @Nullable Person get(String who) {
        return PEOPLE.get(who);
    }

    /** Talks to a Wayfarer as if used (dev and tours). */
    public static void talkTo(ServerPlayer p, Wayfarer w) {
        w.getLookControl().setLookAt(p);
        talk(p, w);
    }

    /** Use on a Wayfarer: whatever the chains want with them, else one of their own lines. */
    static void talk(ServerPlayer p, Wayfarer w) {
        if (talkAs(p, w.who(), w.scene())) return;
        Person person = get(w.who());
        String rumour = person == null ? null : dev.awaken.living.pulse.Rumours.tell(p, person.name(), w);
        if (rumour != null) {
            QuestEngine.sayLine(p, rumour);
            return;
        }
        if (person != null && !person.idle().isEmpty()) {
            QuestEngine.sayLine(p, person.idle().get(p.getRandom().nextInt(person.idle().size())));
        }
    }

    /**
     * Someone the chains know by {@code who} is talked to (a Wayfarer, or another module's person through
     * {@code Quests.talk}: Kestrel, Oren). True when a chain had something to do: a step moved on or said what it waits
     * for, a delivery changed hands, Hale read the board, or a chain was offered. False: the person speaks for themself.
     */
    public static boolean talkAs(ServerPlayer p, String who, long scene) {
        if (!dev.awaken.living.LivingModule.shown(p.level().getServer())) return false; // pure survival (§21.0.13)
        QuestState s = QuestEngine.state(p);
        // a step waiting on this person: talking, or handing over what they asked for
        for (QuestState.Active a : s.active()) {
            QuestDef d = QuestBook.get(a.chain());
            if (d == null || a.step() >= d.steps().size()) continue;
            QuestDef.Step step = d.steps().get(a.step());
            QuestDef.Objective o = step.objective();
            if (o.type().equals("talk") && o.target().equals(who)) {
                if (QuestEngine.blocked(p, step)) Signal.toast(p, Component.literal(step.needsText().isEmpty() ? step.text() : step.needsText()));
                // Ilse with the letters: her scene by the fire, not a bare line (SPEC §27.6)
                else if (!dev.awaken.living.FirstScenes.talk(p, who)) QuestEngine.advance(p, "talk", who, 1);
                return true;
            }
            if (o.type().equals("deliver") && o.target().startsWith(who + "|")) {
                if (QuestEngine.deliver(p, a.chain(), o.target().substring(who.length() + 1), o.count())) return true;
                Signal.toast(p, Component.literal(d.steps().get(a.step()).text()));
                return true;
            }
        }
        if (dev.awaken.living.IssueScene.talk(p, who)) return true; // Hale's issue, while it waits (docs/arms-progression/PLAN.md §3.3)
        if (who.equals("hale") && dev.awaken.living.bounty.Bounties.board(p)) return true;
        // a chain this person gives that can be taken now
        for (var e : QuestBook.all().entrySet()) {
            QuestDef d = e.getValue();
            if (!d.giver().equals(who) || s.has(e.getKey()) || s.done().contains(e.getKey()) || !Requirements.met(p, d.requires())) continue;
            offer(p, e.getKey(), d, scene);
            return true;
        }
        return false;
    }

    /**
     * A chain offered on a choice card: Y takes it into the Journal, N leaves it (it can be asked for again). The giver
     * says their lines first; the card says in plain words what you agree to and shows once they've finished (the
     * client holds a card asked during speech), so it lives 30 s past the time the lines take to say.
     */
    static void offer(ServerPlayer p, String chain, QuestDef d, long scene) {
        if (!d.ask().isEmpty()) QuestEngine.sayLines(p, d.ask());
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.title())).dialogue()
                .body(Component.literal(d.summary().isEmpty() ? "Help?" : d.summary()))
                .choice(ACTION + ":" + chain + "#" + scene, "Help", "Not now")
                .ttl(offerTtl(d.ask())).build());
    }

    /** How long an offer stays open: 30 s, plus the time its ask takes to say (the card shows after it). */
    public static int offerTtl(List<String> ask) {
        return 20 * 30 + StoryPager.readTicks(ask);
    }

    public static final String ACTION = "living_quest";

    /** The choice card's answer. */
    public static void answer(ServerPlayer p, String action, boolean accepted) {
        if (!accepted) return;
        String rest = action.substring(ACTION.length() + 1);
        int hash = rest.lastIndexOf('#');
        long scene = 0;
        if (hash > 0) {
            try {
                scene = Long.parseLong(rest.substring(hash + 1));
            } catch (NumberFormatException ignored) {
                // an old card: no scene
            }
            rest = rest.substring(0, hash);
        }
        QuestEngine.start(p, rest, scene);
    }
}
