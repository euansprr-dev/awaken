package dev.awaken.living;

import dev.awaken.core.api.FarPost;
import dev.awaken.living.quest.QuestBook;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import dev.awaken.living.wayfarer.Wayfarers;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Requirements;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The city's errands in a world played far from Carillon (SPEC §67). What a city person's step waits for goes in the
 * skiff's hold at the landing (as if handed over in the city; Ilse's own step stays at her fire), Hale's board stands
 * beside it, and Oren writes to offer his lesson once you're out on the land. The skiff takes you once Ilse has given you
 * her coal.
 */
final class FarErrands {
    private FarErrands() {}

    /** Carillon's people whose steps the hold can carry. Ilse isn't here: she keeps the Last Fire, by the skiff. */
    static final Set<String> CITY = Set.of("oren", "hale", "hettie", "kestrel", "bram");
    static final String BRAM = "the_last_delivery", OREN = "orens_first_steel";
    /** Oren has written (a counter, so it rides with the player's profile). */
    static final String OREN_WROTE = "awaken:far_oren_wrote";
    /** The most steps one use of the hold moves on one chain (Oren's three loads, sent together). */
    static final int MOST = 6;

    static final String OREN_LETTER = "You'll be the new hunter Bram sent out past the ridge. I'm Oren, the smith on Forge Row."
            + " Most hunters out there buy cheap blades off a cart, and they snap the first time they hit bone."
            + " If you want one that lasts, make it yourself, and I'll teach you how. Once."
            + " Put 6 raw iron, 6 raw copper and 8 coal in the skiff's hold and it'll bring them to me. I'll write back with what to do.";

    static void install() {
        FarPost.installErrands(new FarPost.Errands() {
            @Override
            public int hold(ServerPlayer p) {
                return FarErrands.hold(p);
            }

            @Override
            public boolean board(ServerPlayer p) {
                return LivingModule.shown(p.level().getServer()) && dev.awaken.living.bounty.Bounties.board(p);
            }

            @Override
            public void out(ServerPlayer p) {
                FarErrands.out(p);
            }
        });
        FarPost.installReady(FarErrands::ready);
    }

    /** The skiff takes you once Ilse has given you her coal (or the story is off: pure survival takes it at once). */
    static boolean ready(ServerPlayer p) {
        if (!LivingModule.shown(p.level().getServer())) return true;
        QuestState s = QuestEngine.state(p);
        if (s.done().contains(BRAM)) return true;
        QuestDef d = QuestBook.get(BRAM);
        if (d == null) return true;
        int ilse = -1;
        for (int i = 0; i < d.steps().size(); i++) if (d.steps().get(i).objective().type().equals("talk") && d.steps().get(i).objective().target().equals("ilse")) ilse = i;
        for (QuestState.Active a : s.active()) if (a.chain().equals(BRAM)) return ilse >= 0 && a.step() > ilse;
        return false;
    }

    /**
     * The hold: every waiting step of a city person's, handed over (a talk is a letter sent; a delivery is the things put
     * in). How many it moved; a delivery that isn't all there says what's missing and counts, so the hold doesn't read
     * out old letters over it.
     */
    static int hold(ServerPlayer p) {
        if (!LivingModule.shown(p.level().getServer())) return 0;
        int moved = 0;
        QuestState s = QuestEngine.state(p);
        for (QuestState.Active first : List.copyOf(s.active())) {
            for (int n = 0; n < MOST; n++) {
                QuestState.Active a = active(QuestEngine.state(p), first.chain());
                QuestDef d = QuestBook.get(first.chain());
                if (a == null || d == null || a.step() >= d.steps().size()) break;
                QuestDef.Step step = d.steps().get(a.step());
                QuestDef.Objective o = step.objective();
                String who = o.type().equals("talk") ? o.target()
                        : o.type().equals("deliver") && o.target().indexOf('|') > 0 ? o.target().substring(0, o.target().indexOf('|')) : "";
                if (!CITY.contains(who)) break;
                if (QuestEngine.blocked(p, step)) {
                    Signal.toast(p, Component.literal(step.needsText().isEmpty() ? step.text() : step.needsText()));
                    moved++;
                    break;
                }
                String name = Character.toUpperCase(who.charAt(0)) + who.substring(1);
                if (o.type().equals("talk")) {
                    Signal.toast(p, Component.literal("A note for " + name + " goes in the hold. The skiff will take it to Carillon."));
                    QuestEngine.advance(p, "talk", who, 1);
                    moved++;
                    continue;
                }
                String item = o.target().substring(who.length() + 1);
                if (QuestEngine.deliver(p, a.chain(), item, o.count())) {
                    Signal.toast(p, Component.literal("It goes in the hold for " + name + ". The skiff will take it to Carillon."));
                    moved++;
                    continue;
                }
                if (n == 0) {
                    Signal.toast(p, Component.literal(step.text() + ". The hold takes it once you have it all with you."));
                    moved++;
                }
                break;
            }
        }
        if (moved == 0 && offerOren(p, false)) moved++;
        return moved;
    }

    private static QuestState.@Nullable Active active(QuestState s, String chain) {
        for (QuestState.Active a : s.active()) if (a.chain().equals(chain)) return a;
        return null;
    }

    /** Out on the land: Oren writes once, when his lesson can be taken. */
    static void out(ServerPlayer p) {
        if (!LivingModule.shown(p.level().getServer())) return;
        if (Progression.profile(p).counters().getOrDefault(OREN_WROTE, 0L) > 0) return;
        if (offerOren(p, true)) Progression.count(p, OREN_WROTE);
    }

    /** Oren's letter, with his offer on it: Help takes the chain into the Journal. From the hold, again on asking. */
    static boolean offerOren(ServerPlayer p, boolean keep) {
        QuestDef d = QuestBook.get(OREN);
        QuestState s = QuestEngine.state(p);
        if (d == null || s.has(OREN) || s.done().contains(OREN) || !Requirements.met(p, d.requires())) return false;
        if (keep) FarPost.keepLetter(p, "Oren at the Forge", OREN_LETTER);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("A letter from Oren at the Forge")).dialogue()
                .body(Component.literal(OREN_LETTER))
                .choice(Wayfarers.ACTION + ":" + OREN + "#0", "Help", "Not now")
                .ttl(20 * 60).priority(7).build());
        return true;
    }
}
