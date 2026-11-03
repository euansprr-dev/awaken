package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.data.DreamRecord;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.trial.Course;
import dev.awaken.lucidium.trial.Trial;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A trial dream while it runs (SPEC §9.3). You arrive in your room and the dream waits for you. H takes control: the
 * doorway clears and the clock starts (the trial's own time plus a second for each point of lucidity you brought, up to
 * 60). H again makes the true path glint for 2 s of the clock, once every 4 s. A dream sign noticed is 8 more seconds.
 * A fall is caught: back to the last ring passed, 3 s gone; nothing in a dream ends it by falling. The goal wins; the
 * prize plays for 3 s and you wake. The clock running out wakes you with the dream still waiting.
 */
final class Trials {
    private Trials() {}

    static final int PRIZE = 60, GLINT_COST = 40, GLINT_EVERY = 80, SIGN_TICKS = 160, CATCH_COST = 60, CATCH_BELOW = 6;
    static final int BONUS_MAX = 60;
    /** An untaken dream fades after five minutes: the night waits for you, but not forever. */
    static final int WAIT_MAX = 20 * 60 * 5;
    static final int REVEALS_PER_TICK = 24;
    /** A dreamer still waiting who walks out through an open doorway (a shared dream) takes control as they go. */
    static final double PAST_DOORWAY = 1.0;

    static boolean is(ActiveDream ad) {
        return ad.bp.course != null;
    }

    // ---- arriving ----------------------------------------------------------------------------------------------------

    static void arrive(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course c = ad.bp.course;
        st.trial = c.trial().id;
        st.phase = 0;
        st.bonus = Math.min(BONUS_MAX, Math.round(st.lucidity));
        st.endTick = now + WAIT_MAX;
        st.ring = 0;
        Dreamer d = Dreams.dreamer(sp);
        AwakenNet.send(sp, new LucidiumNet.TrialStart(goal(c), sub(c), ringCentres(c), end(c), c.bell() != null,
                c.path().size() > 512 ? c.path().subList(0, 512) : c.path(), Math.max(0, d.dreams - 1)));
        hud(sp, st, c, now, "", false);
    }

    /** The goal line at the top of the screen. */
    static String goal(Course c) {
        if (c.bell() != null) return "Follow the road and take the bell before the dream fades.";
        return c.trial().goal;
    }

    static String sub(Course c) {
        return c.trial().sub;
    }

    static List<Vec3> ringCentres(Course c) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 1; i < c.rings().size() && out.size() < 64; i++) out.add(c.rings().get(i).box().getCenter());
        return out;
    }

    static Vec3 end(Course c) {
        return c.bell() != null ? Vec3.atBottomCenterOf(c.bell()) : c.goal().getCenter();
    }

    // ---- the world ---------------------------------------------------------------------------------------------------

    /** Once a tick for the dream: the course lays itself ahead of whoever is furthest along it. */
    static void tickWorld(ActiveDream ad, List<ServerPlayer> players) {
        Course c = ad.bp.course;
        if (c == null || ad.revealed >= c.reveals().size() || players.isEmpty()) return;
        double front = Double.NEGATIVE_INFINITY;
        for (ServerPlayer p : players) front = Math.max(front, p.getZ());
        int reach = (int) Math.floor(front) + Course.AHEAD;
        for (int placed = 0; ad.revealed < c.reveals().size() && placed < REVEALS_PER_TICK; placed++) {
            Course.Reveal r = c.reveals().get(ad.revealed);
            if (r.along() > reach || !ad.level.isLoaded(r.pos())) break;
            ad.level.setBlock(r.pos(), r.state(), ActiveDream.UPDATE);
            if (placed % 3 == 0)
                ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), r.pos().getX() + 0.5, r.pos().getY() + 0.9, r.pos().getZ() + 0.5,
                        2, 0.35, 0.2, 0.35, 0.01);
            ad.revealed++;
        }
    }

    /** The doorway clears (the first to take control opens it for everyone). */
    static void open(ActiveDream ad) {
        Course c = ad.bp.course;
        if (c == null || ad.doorwayOpen) return;
        ad.doorwayOpen = true;
        for (BlockPos p : c.doorway()) {
            ad.level.setBlock(p, Blocks.AIR.defaultBlockState(), ActiveDream.UPDATE);
            ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.1, 0.02);
        }
        BlockPos mid = c.doorway().get(c.doorway().size() / 2);
        ad.level.playSound(null, mid, LucidiumContent.DOOR.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1f, 1.3f);
    }

    static double doorwayZ(Course c) {
        return c.doorway().isEmpty() ? c.start().z : c.doorway().getFirst().getZ();
    }

    // ---- each dreamer ------------------------------------------------------------------------------------------------

    /** One tick for one dreamer. Returns false when it woke them. */
    static boolean tick(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course c = ad.bp.course;
        // a fall is caught, never the end
        if (sp.getY() < c.floorY() - CATCH_BELOW) caught(sp, st, c, now);

        switch (st.phase) {
            case 0 -> {
                if (now >= st.endTick) {
                    Dreams.wake(sp, Dreams.Wake.SLIPPED);
                    return false;
                }
                if (ad.doorwayOpen && sp.getZ() > doorwayZ(c) + PAST_DOORWAY) takeControl(sp, st, ad, now);
            }
            case 1 -> {
                if (now >= st.clockEnd) {
                    Dreams.wake(sp, Dreams.Wake.SLIPPED);
                    return false;
                }
                if (st.runStart == 0 && sp.getZ() > doorwayZ(c) + PAST_DOORWAY) st.runStart = now;
                AABB box = sp.getBoundingBox();
                for (int i = st.ring + 1; i < c.rings().size(); i++) {
                    if (!box.intersects(c.rings().get(i).box())) continue;
                    st.ring = i;
                    Dreams.playTo(sp, LucidiumContent.SIGN.get(), 0.5f, 1.5f + 0.1f * i);
                    hud(sp, st, c, now, "", false);
                    break;
                }
                if (box.intersects(c.goal())) {
                    if (c.bell() == null) {
                        won(sp, st, ad, now);
                        return true;
                    }
                    if (now - st.lastDoor > 60) {
                        st.lastDoor = now;
                        hud(sp, st, c, now, "Take the bell first. It's just behind you.", false);
                    }
                }
            }
            case 2 -> {
                if (now >= st.wonAt + PRIZE) {
                    Dreams.wake(sp, Dreams.Wake.WON);
                    return false;
                }
                if ((now - st.wonAt) % 6 == 0)
                    ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), sp.getX(), sp.getY() + 1, sp.getZ(), 6, 0.6, 0.8, 0.6, 0.02);
            }
            default -> {}
        }
        if ((now + sp.getId()) % 20 == 0) hud(sp, st, c, now, "", false);
        return true;
    }

    /** H: take control, or, in control, make the true path glint. */
    static void press(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course c = ad.bp.course;
        if (st.phase == 0) {
            takeControl(sp, st, ad, now);
            return;
        }
        if (st.phase != 1 || now - st.lastGlint < GLINT_EVERY) return;
        st.lastGlint = now;
        st.clockEnd -= GLINT_COST;
        Dreams.playTo(sp, LucidiumContent.REALITY_CHECK.get(), 0.6f, 1.3f);
        hud(sp, st, c, now, "", true);
    }

    static void takeControl(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course c = ad.bp.course;
        if (st.phase != 0) return;
        st.phase = 1;
        st.lucidAt = now;
        st.clockTotal = (c.seconds() + st.bonus) * 20;
        st.clockEnd = now + st.clockTotal;
        st.endTick = Long.MAX_VALUE / 4; // the trial's own clock ends it
        open(ad);
        sp.removePostEffect(Dreams.FX);
        sp.addPostEffect(Dreams.FX_LUCID);
        Dreams.playTo(sp, LucidiumContent.LUCID.get(), 0.8f, 1.0f);
        if (!st.lucidCounted) {
            st.lucidCounted = true;
            Progression.count(sp, Counters.LUCID_DREAMS);
            Dreamer d = Dreams.dreamer(sp);
            if (!d.lucidEver) {
                d.lucidEver = true;
                Progression.markNewThing(sp, "lucid dream");
                Progression.bestMoment(sp, "You took control of a dream");
            }
        }
        hud(sp, st, c, now, "", false);
    }

    /** A dream sign noticed: more time, or, before you've taken control, more time waiting for you. */
    static void signNoticed(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        if (st.phase == 1) {
            st.clockEnd += SIGN_TICKS;
            st.clockTotal += SIGN_TICKS;
        } else if (st.phase == 0) {
            st.bonus += SIGN_TICKS / 20;
        }
        hud(sp, st, ad.bp.course, now, "That's not right. +8 seconds", false);
    }

    /** Fell below the course: a soft swoop back to the last ring passed (the start in your room before any). */
    static void caught(ServerPlayer sp, DreamState st, Course c, long now) {
        Course.Ring r = c.ring(st.ring);
        Dreams.moveWithin(sp, r.at(), r.yaw());
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
        st.catches++;
        String line = "The dream caught you.";
        if (st.phase == 1) {
            st.clockEnd -= CATCH_COST;
            line = "The dream caught you. 3 seconds gone.";
        }
        Dreams.playTo(sp, LucidiumContent.ENTER.get(), 0.6f, 1.4f);
        hud(sp, st, c, now, line, false);
    }

    /** The bell at the end of the road is in your hands (bells, through {@link dev.awaken.lucidium.Lucidium#bellTaken}). */
    static void bellTaken(ServerPlayer sp) {
        DreamState st = Dreams.state(sp);
        ActiveDream ad = Dreams.active(sp);
        if (ad == null || !is(ad) || st.phase != 1) return;
        won(sp, st, ad, Dreams.now(sp.level().getServer()));
    }

    static void won(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course c = ad.bp.course;
        st.phase = 2;
        st.wonAt = now;
        st.wonTicks = (int) Math.max(1, now - (st.runStart > 0 ? st.runStart : st.lucidAt));
        Dreams.playTo(sp, LucidiumContent.LUCID.get(), 1f, 1.25f);
        ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), sp.getX(), sp.getY() + 1, sp.getZ(), 40, 1.2, 1.2, 1.2, 0.05);
        hud(sp, st, c, now, prizeLine(c, Dreams.dreamer(sp)), false);
    }

    static String prizeLine(Course c, Dreamer d) {
        if (c.bell() != null) return "You're holding the bell. Wake, and it's yours.";
        Trial t = c.trial();
        if (t.run() && !t.move.isEmpty() && !d.ledger.remembered.contains(t.id)) return "Your body will remember this.";
        return "You made it to the end of the dream.";
    }

    // ---- waking ------------------------------------------------------------------------------------------------------

    /** What the morning card says about a trial dream. */
    record Waking(String title, String body, List<SignalWindow.Row> rows, String footer) {}

    /** World XP for a finish: 6% of a level the first time, 2% for another finish on a later day (a Gauntlet run's). */
    static final double XP_FIRST = 0.06, XP_AGAIN = 0.02;
    static final int RESIDUE = 2;
    static final String FINISHED = "awaken:dreams_finished";

    /**
     * Waking from a trial: a win is written in the ledger (off the waiting list, best time and medal kept) and its prize
     * given; any other way out leaves the dream waiting. Every trial dream is written in the Dream Journal by itself.
     */
    static Waking woke(ServerPlayer sp, DreamState st, ActiveDream ad, Dreams.Wake why, Dreamer d, long day) {
        Course c = ad.bp.course;
        Trial t = c.trial();
        List<SignalWindow.Row> rows = new ArrayList<>();
        String summary;
        Waking w;
        if (why == Dreams.Wake.WON) {
            int medal = medal(c, st.wonTicks);
            DreamLedger.Finished before = d.ledger.finish(t.id, st.wonTicks, medal, day);
            Progression.count(sp, FINISHED);
            double xp = before == null ? XP_FIRST : before.day() != day ? XP_AGAIN : 0;
            if (xp > 0) Progression.worldXp(sp, "awaken:dream_trial", xp);
            DreamOffers.give(sp, new ItemStack(LucidiumContent.DREAM_RESIDUE.get(), RESIDUE));
            boolean best = before != null && st.wonTicks < before.best();
            rows.add(new SignalWindow.Row(Component.literal("Time"), Component.literal(time(st.wonTicks) + (best ? ", your best" : ""))));
            if (medal > 0) rows.add(new SignalWindow.Row(Component.literal("Medal"), Component.literal(medalName(medal))));
            List<String> kept = new ArrayList<>();
            if (c.bell() != null) kept.add("a bell");
            kept.add(RESIDUE + " Dream Residue");
            rows.add(new SignalWindow.Row(Component.literal("You kept"), Component.literal(String.join(", ", kept))));
            summary = t.title + ": reached the end in " + time(st.wonTicks) + (medal > 0 ? " (" + medalName(medal).toLowerCase() + ")" : "");
            w = new Waking("You reached the end of the dream", "", rows, "Your Dream Journal has written it down.");
            DirectorLog.log(sp, "dream", "trial " + t.id + " won in " + st.wonTicks + " ticks (par " + c.par() + ", medal " + medal
                    + ", catches " + st.catches + ")");
        } else {
            String still = d.ledger.isWaiting(t.id) ? "The dream is still waiting. Try it another night." : "";
            w = switch (why) {
                case SLIPPED -> new Waking(st.phase == 0 ? "The dream faded" : "The dream slipped away",
                        st.phase == 0 ? "You never took control. Next time, press H as soon as you arrive. " + still : still, rows, "");
                case CHOSE -> new Waking("You woke up", ("Nothing lost. " + still).trim(), rows, "");
                case DIED -> new Waking("You died in the dream", ("Nothing lost. Just a little shaken. " + still).trim(), rows, "");
                case BELL -> new Waking("The Dream Bell woke you", "Midnight. Sleep again within the hour and the dream comes back.", rows, "");
                default -> new Waking("You wake up", still, rows, "");
            };
            summary = t.title + (st.phase == 0 ? ": you never took control" : ": it slipped away before the end");
        }
        d.addHistory(new DreamRecord(st.dreamId, day, summary, Math.round(st.peak), st.lucidCounted, true, false, ad.bp.plan));
        DreamOffers.changed(sp);
        return w;
    }

    // ---- medals ------------------------------------------------------------------------------------------------------

    /** A Run's medal by the Gauntlet's rule: gold within par x 1.1 + 1 s, silver within par x 1.5 + 2 s, else bronze. */
    /** Recall entries in a trial: two for each dream finished (a second each), or what an old save wrote, if more. */
    static int recall(Dreamer d) {
        return Math.max(d.recall, 2 * d.ledger.finished.size());
    }

    static int medal(Course c, int ticks) {
        if (!c.trial().run() || c.par() <= 0) return 0;
        if (ticks <= c.par() * 1.1 + 20) return DreamLedger.GOLD;
        if (ticks <= c.par() * 1.5 + 40) return DreamLedger.SILVER;
        return DreamLedger.BRONZE;
    }

    static String medalName(int medal) {
        return switch (medal) {
            case 3 -> "Gold";
            case 2 -> "Silver";
            case 1 -> "Bronze";
            default -> "";
        };
    }

    /** "1:12.4" */
    static String time(int ticks) {
        int tenths = ticks / 2;
        return (tenths / 600) + ":" + String.format("%02d.%d", (tenths / 10) % 60, tenths % 10);
    }

    // ---- HUD ---------------------------------------------------------------------------------------------------------

    static void hud(ServerPlayer sp, DreamState st, Course c, long now, String flash, boolean glint) {
        int left, total;
        switch (st.phase) {
            case 1 -> {
                left = (int) Math.max(0, st.clockEnd - now);
                total = Math.max(1, st.clockTotal);
            }
            case 2 -> {
                left = (int) Math.max(0, st.clockEnd - st.wonAt);
                total = Math.max(1, st.clockTotal);
            }
            default -> {
                left = (c.seconds() + st.bonus) * 20;
                total = left;
            }
        }
        AwakenNet.send(sp, new LucidiumNet.TrialHud(st.phase, left, total, st.ring, flash, glint));
    }
}
