package dev.awaken.living.wayfarer;

import dev.awaken.living.quest.QuestBook;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import dev.awaken.progression.Requirements;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The people of the wilds (SPEC §21, Phase C), and what happens when you talk to one: a step waiting on them moves on,
 * something you carry for them changes hands, a chain they give is offered on a choice card, or they say something of
 * their own. Lines are authored; none is generated.
 */
public final class Wayfarers {
    private Wayfarers() {}

    /** A Wayfarer: display name, slim arms, and a few lines for when nothing else is to be said. */
    public record Person(String name, boolean slim, List<String> idle) {}

    static final Map<String, Person> PEOPLE = Map.of(
            "wren", new Person("Wren", true, List.of("Wren: The wind up here has opinions.", "Wren: I'm fine. Mostly fine. Ledge-fine.")),
            "hettie", new Person("Hettie", true, List.of("Hettie: They come back, the sheep. Usually.", "Hettie: Wipe your boots. The ewes judge.")),
            "ilse", new Person("Ilse", true, List.of("Ilse: Moths know where the ground is generous.", "Ilse: Sit. Nobody out here runs anywhere.")),
            "gus", new Person("Gus", false, List.of("Gus: Mountain's quiet today. Don't trust it.", "Gus: You're standing on my good rock.")),
            "pell", new Person("Pell", false, List.of("Pell: Two good legs once. Faster than you. Ask anyone.", "Pell: The roads aren't roofs. Too flat. Too honest.")),
            "hale", new Person("Hale", false, List.of("Hale: Licence? Fine. The Commons bite less than they look. Mostly.", "Hale: Board's there. Work for new licences.")),
            "marta", new Person("Marta", true, List.of("Marta: Everything's for sale. Except the wagons. And me.", "Marta: Roads are long. Bring a story.")));

    public static @Nullable Person get(String who) {
        return PEOPLE.get(who);
    }

    /** Talks to a Wayfarer as if used (dev and tours). */
    public static void talkTo(ServerPlayer p, Wayfarer w) {
        w.getLookControl().setLookAt(p);
        talk(p, w);
    }

    /** Use on a Wayfarer. */
    static void talk(ServerPlayer p, Wayfarer w) {
        String who = w.who();
        QuestState s = QuestEngine.state(p);
        if (!dev.awaken.living.LivingModule.shown(p.level().getServer())) { // pure survival: people, no quests (§21.0.13)
            Person person = get(who);
            if (person != null) QuestEngine.sayLine(p, person.idle().get(p.getRandom().nextInt(person.idle().size())));
            return;
        }
        // a step waiting on this person: talking, or handing over what they asked for
        for (QuestState.Active a : s.active()) {
            QuestDef d = QuestBook.get(a.chain());
            if (d == null || a.step() >= d.steps().size()) continue;
            QuestDef.Step step = d.steps().get(a.step());
            QuestDef.Objective o = step.objective();
            if (o.type().equals("talk") && o.target().equals(who)) {
                if (QuestEngine.blocked(p, step)) Signal.toast(p, Component.literal(step.needsText().isEmpty() ? step.text() : step.needsText()));
                else QuestEngine.advance(p, "talk", who, 1);
                return;
            }
            if (o.type().equals("deliver") && o.target().startsWith(who + "|")) {
                if (QuestEngine.deliver(p, a.chain(), o.target().substring(who.length() + 1), o.count())) return;
                Signal.toast(p, Component.literal(d.steps().get(a.step()).text()));
                return;
            }
        }
        if (who.equals("hale") && dev.awaken.living.bounty.Bounties.board(p)) return;
        // a chain this person gives that can be taken now
        for (var e : QuestBook.all().entrySet()) {
            QuestDef d = e.getValue();
            if (!d.giver().equals(who) || s.has(e.getKey()) || s.done().contains(e.getKey()) || !Requirements.met(p, d.requires())) continue;
            offer(p, e.getKey(), d, w.scene());
            return;
        }
        Person person = get(who);
        if (person != null && !person.idle().isEmpty()) {
            QuestEngine.sayLine(p, person.idle().get(p.getRandom().nextInt(person.idle().size())));
        }
    }

    /** A chain offered on a choice card: Y takes it into the Journal, N leaves it (it can be asked for again). */
    static void offer(ServerPlayer p, String chain, QuestDef d, long scene) {
        if (!d.ask().isEmpty()) QuestEngine.sayLines(p, d.ask());
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.title()))
                .body(Component.literal(d.summary().isEmpty() ? "Help?" : d.summary()))
                .choice(ACTION + ":" + chain + "#" + scene, "Help", "Not now").ttl(20 * 30).build());
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
