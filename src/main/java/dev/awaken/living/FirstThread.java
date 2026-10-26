package dev.awaken.living;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.living.quest.QuestBook;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import dev.awaken.living.wayfarer.Scenes;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The first thread out of Carillon, and chains on the Now line (SPEC §27.6). When the Association registers a player
 * (the opening ends or is skipped) Kestrel's errand, The Last Delivery, starts by itself and is tracked: tell Kestrel
 * what you saw, out the High Gate (a shot of the camp's smoke), run the letters to the Wayfarer camp, deliver them to
 * Ilse by her fire (a scene that ends with her coal, the Ember, in your hand). The thread lets go there: the Hearth's own
 * line ("Place your Ember where you want your home") takes over and the rest of the chain waits in the Journal, so after it the
 * player chooses. The tracked chain's current step is on the Now line with a compass to its place, over bookkeeping
 * (points to spend, the next level's unlock).
 */
public final class FirstThread {
    private FirstThread() {}

    /** The errand that walks a new hunter out of the city. */
    static final String CHAIN = "awaken:the_last_delivery";
    /** Over bookkeeping (points 55), under danger, rifts (80+) and a waiting Reassessment (72). */
    static final int PRIORITY = 68;

    static void init() {
        OpeningHooks.onRegistered(FirstThread::registered);
        NowHooks.register(FirstThread::now);
    }

    /** The opening is over: Kestrel's errand starts, tracked (once; a player who already has it or did it is left be). */
    static void registered(ServerPlayer p) {
        if (!Progression.profile(p).awakened()) return;
        QuestState s = QuestEngine.state(p);
        String id = chainId();
        if (id == null || s.has(id) || s.done().contains(id)) return;
        if (QuestEngine.start(p, id)) {
            QuestEngine.track(p, id);
            DirectorLog.log(p, "living", "the first thread: " + id + " starts by itself at registration");
        }
        // the first tips (SPEC §27.7): the band, then the two lines at the top
        var server = p.level().getServer();
        dev.awaken.core.Later.run(server, 120, () -> dev.awaken.core.api.TipHooks.offer(p, dev.awaken.Awaken.id("first/band")));
        dev.awaken.core.Later.run(server, 520, () -> dev.awaken.core.api.TipHooks.offer(p, dev.awaken.Awaken.id("first/now")));
    }

    /** The errand's key as the quest book holds it (with or without the namespace). */
    static @Nullable String chainId() {
        if (QuestBook.get(CHAIN) != null) return CHAIN;
        String bare = CHAIN.substring(CHAIN.indexOf(':') + 1);
        return QuestBook.get(bare) != null ? bare : null;
    }

    /** The tracked chain's current step (else the first chain's), with a compass when its place is known. */
    static List<NowHooks.Suggestion> now(ServerPlayer p) {
        if (!Progression.profile(p).awakened() || GateHooks.inGate(p) || OpeningHooks.inOpening(p)) return List.of();
        QuestState s = QuestEngine.state(p);
        if (s.active().isEmpty()) return List.of();
        QuestState.Active a = s.active().getFirst();
        for (QuestState.Active x : s.active()) if (x.chain().equals(s.tracked())) a = x;
        // the thread lets go at the Ember: the Hearth's line takes it from there, Kestrel waits in the Journal
        if (a.chain().equals(chainId()) && Progression.profile(p).counter("awaken:claim/quest/" + a.chain() + "/ilse") > 0) return List.of();
        String line = QuestEngine.line(a);
        QuestDef d = QuestBook.get(a.chain());
        if (line == null || d == null) return List.of();
        BlockPos where = where(p, a, d.steps().get(a.step()));
        String id = "living:chain/" + a.chain() + "/" + a.step();
        return List.of(where == null ? NowHooks.Suggestion.of(id, PRIORITY, line) : NowHooks.Suggestion.at(id, PRIORITY, line, where));
    }

    /** Where a step happens: a place it names, or the person it asks you to talk to. Null when it has no one place. */
    static @Nullable BlockPos where(ServerPlayer p, QuestState.Active a, QuestDef.Step step) {
        var server = p.level().getServer();
        long scene = a.scene().orElse(0L);
        QuestDef.Objective o = step.objective();
        return switch (o.type()) {
            case "reach" -> Scenes.marker(server, scene, o.target());
            case "talk" -> switch (o.target()) {
                // a named citizen where her body is now (the Roost's street door), else the Roost
                case "kestrel" -> {
                    var at = dev.awaken.core.api.CitizenHooks.where(server.overworld(), "kestrel");
                    yield at != null ? BlockPos.containing(at) : OpeningHooks.place("roost");
                }
                case "hale" -> Scenes.marker(server, scene, "spot:quartermaster");
                case "ilse", "hettie" -> {
                    BlockPos at = Scenes.residentAt(server, o.target());
                    yield at != null ? at : Scenes.marker(server, scene, "camp");
                }
                default -> Scenes.marker(server, scene, "scene");
            };
            default -> null;
        };
    }
}
