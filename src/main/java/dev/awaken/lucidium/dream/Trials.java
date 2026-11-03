package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.AtlasHooks;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.core.api.TipHooks;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.data.DreamRecord;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Vision;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.Nightmares;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** A finished trial dreamt again (SPEC §9.6): times, medals and the ghost count; nothing else it gave comes again. */
    static boolean replay(ActiveDream ad) {
        return DreamLedger.REPLAY.equals(ad.bp.plan.trialRef());
    }

    // ---- arriving ----------------------------------------------------------------------------------------------------

    static void arrive(ServerPlayer sp, DreamState st, ActiveDream ad, long now) {
        Course c = ad.bp.course;
        st.trial = c.trial().id;
        st.phase = 0;
        st.bonus = Math.min(BONUS_MAX, Math.round(st.lucidity));
        st.endTick = now + WAIT_MAX;
        st.ring = 0;
        st.lastX = sp.getX();
        st.lastZ = sp.getZ();
        Dreamer d = Dreams.dreamer(sp);
        String sub = sub(c);
        if (replay(ad)) {
            DreamLedger.Finished f = d.ledger.finished.get(c.trial().id);
            if (f != null) sub = "Your best: " + time(f.best()) + (f.medal() > 0 ? ", " + medalName(f.medal()).toLowerCase() : "") + "."
                    + (d.ledger.ghosts.containsKey(c.trial().id) ? " Your ghost runs it with you." : "");
        } else {
            // tonight's dream: one a night, and the Journal's choice is spent on it
            d.ledger.lastDreamDay = dev.awaken.core.PlayDays.current(sp.level().getServer());
            if (sp.getUUID().equals(ad.host)) d.ledger.choice = "";
        }
        Vec3 end = end(c, ad);
        AwakenNet.send(sp, new LucidiumNet.TrialStart(goal(c, ad), sub, ringCentres(c), end != null ? end : c.start(), bell(c, ad),
                c.path().size() > 512 ? c.path().subList(0, 512) : c.path(), Math.max(0, d.dreams - 1)));
        if (end == null) AwakenNet.send(sp, new LucidiumNet.TrialGoal(false, Vec3.ZERO)); // the rule shows it later
        hud(sp, st, c, now, "", false);
    }

    /** The goal line at the top of the screen. */
    static String goal(Course c, ActiveDream ad) {
        if (!c.trial().run()) return RoomRules.goal(c, RoomRules.bell(ad));
        if (c.bell() != null) return "Follow the road and take the bell before the dream fades.";
        return c.trial().goal;
    }

    static String sub(Course c) {
        return c.trial().run() ? c.trial().sub : RoomRules.sub(c);
    }

    /** Whether this dream's goal is a bell (taken by striking it). */
    static boolean bell(Course c, ActiveDream ad) {
        return c.bell() != null || RoomRules.bell(ad);
    }

    static List<Vec3> ringCentres(Course c) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 1; i < c.rings().size() && out.size() < 64; i++) out.add(c.rings().get(i).box().getCenter());
        return out;
    }

    static Vec3 end(Course c) {
        return c.bell() != null ? Vec3.atBottomCenterOf(c.bell()) : c.goal().getCenter();
    }

    /** Where the end stands now: a Run's is fixed; a Room's goal moves, or hides until its rule shows it (null). */
    static @Nullable Vec3 end(Course c, ActiveDream ad) {
        return c.trial().run() ? end(c) : RoomRules.end(ad);
    }

    // ---- the world ---------------------------------------------------------------------------------------------------

    /** Once a tick for the dream: the course lays itself ahead of whoever is furthest along it; a Room's rule runs. */
    static void tickWorld(ActiveDream ad, List<ServerPlayer> players, long now) {
        Course c = ad.bp.course;
        if (c == null || players.isEmpty()) return;
        if (RoomRules.is(ad)) RoomRules.tickDream(ad, players, now);
        if (ad.revealed >= c.reveals().size()) return;
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
        // a fall is caught, never the end (after the win, quietly back to the end while the prize plays)
        if (sp.getY() < c.floorY() - CATCH_BELOW - RoomRules.extraDepth(c)) {
            Vec3 end = end(c, ad);
            if (st.phase == 2 && end != null) Dreams.moveWithin(sp, end, sp.getYRot());
            else caught(sp, st, c, now);
        }

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
                if (st.runStart == 0 && sp.getZ() > doorwayZ(c) + PAST_DOORWAY) {
                    st.runStart = now;
                    ghosts(sp, ad, c);
                }
                // the run as a ghost, from the doorway on
                if (st.runStart > 0 && (now - st.runStart) % DreamLedger.GHOST_EVERY == 0 && st.traceLen < DreamLedger.GHOST_MAX)
                    st.trace[st.traceLen++] = DreamLedger.pack(sp.getX() - c.start().x, sp.getY() - c.start().y, sp.getZ() - c.start().z);
                if (RoomRules.is(ad)) {
                    RoomRules.tick(sp, st, ad, now);
                    if (st.phase != 1) return true; // the rule won it (for everyone in a Room)
                }
                AABB box = sp.getBoundingBox();
                for (int i = st.ring + 1; i < c.rings().size(); i++) {
                    if (!box.intersects(c.rings().get(i).box())) continue;
                    st.ring = i;
                    Dreams.playTo(sp, LucidiumContent.RING.get(), 0.6f, Math.min(1.6f, 0.9f + 0.06f * i));
                    hud(sp, st, c, now, "", false);
                    break;
                }
                if (box.intersects(c.goal()) || RoomRules.touched(ad, box)) {
                    if (!bell(c, ad)) {
                        if (RoomRules.room(ad)) RoomRules.won(ad, now);
                        else won(sp, st, ad, now);
                        return true;
                    }
                    if (now - st.lastDoor > 60) {
                        st.lastDoor = now;
                        hud(sp, st, c, now, c.trial() == Trial.CHASE ? "Take the bell first. Your fear is carrying it."
                                : "Take the bell first. It's just behind you.", false);
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
        if (st.phase != 1) return;
        if (RoomRules.is(ad) && RoomRules.press(sp, st, ad, now)) return; // the rule's own use of H
        if (now - st.lastGlint < GLINT_EVERY) return;
        st.lastGlint = now;
        st.clockEnd -= GLINT_COST;
        Dreams.playTo(sp, LucidiumContent.REALITY_CHECK.get(), 0.6f, 1.3f);
        if (RoomRules.is(ad)) RoomRules.glint(ad);
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

    /**
     * A Run's ghosts start with you at the doorway (SPEC §9.6): your best run, and in a shared dream the best runs of
     * the others in it (theirs in rose). Nothing to send without one.
     */
    static void ghosts(ServerPlayer sp, ActiveDream ad, Course c) {
        if (!c.trial().run()) return;
        List<LucidiumNet.Ghost> out = new ArrayList<>();
        ghost(out, sp, true, c);
        for (java.util.UUID id : ad.participants) {
            if (out.size() >= 4) break;
            ServerPlayer p = id.equals(sp.getUUID()) ? null : sp.level().getServer().getPlayerList().getPlayer(id);
            if (p != null) ghost(out, p, false, c);
        }
        if (out.isEmpty()) return;
        AwakenNet.send(sp, new LucidiumNet.TrialGhosts(c.start(), out));
        dev.awaken.Awaken.LOG.info("[lucidium] {} runs {} with {} ghost(s), the first {} samples long",
                sp.getGameProfile().name(), c.trial().id, out.size(), out.getFirst().samples().size());
    }

    private static void ghost(List<LucidiumNet.Ghost> out, ServerPlayer whose, boolean own, Course c) {
        int[] g = Dreams.dreamer(whose).ledger.ghosts.get(c.trial().id);
        if (g == null || g.length == 0) return;
        List<Integer> samples = new ArrayList<>(g.length);
        for (int v : g) samples.add(v);
        out.add(new LucidiumNet.Ghost(whose.getGameProfile().name(), own, samples));
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
        Dreams.playTo(sp, LucidiumContent.CATCH.get(), 0.8f, 1.0f);
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
        Dreams.playTo(sp, LucidiumContent.WON.get(), 1f, 1f);
        ad.level.sendParticles(LucidiumContent.PEARL_MOTE.get(), sp.getX(), sp.getY() + 1, sp.getZ(), 40, 1.2, 1.2, 1.2, 0.05);
        hud(sp, st, c, now, prizeLine(c, ad, Dreams.dreamer(sp)), false);
    }

    /** The Duel ended: the mentor yields and it's won for everyone in the ring; or you're worn down, 10 s gone, and again. */
    static void duelEnded(ActiveDream ad, ServerPlayer sp, boolean won) {
        long now = Dreams.now(sp.level().getServer());
        if (won) {
            RoomRules.won(ad, now);
            return;
        }
        ad.mentorState = -1;
        ad.roomNext = now + DUEL_AGAIN;
        for (java.util.UUID id : ad.participants) {
            ServerPlayer p = sp.level().getServer().getPlayerList().getPlayer(id);
            if (p == null || !Dreams.isDreaming(p)) continue;
            DreamState s = Dreams.state(p);
            if (s.phase != 1) continue;
            s.clockEnd -= DUEL_COST;
            hud(p, s, ad.bp.course, now, "\"Not yet. Catch your breath, and again.\" 10 seconds gone.", false);
        }
    }

    /** A duel lost: this much of the clock, and the mentor's back this long after. */
    static final int DUEL_COST = 200, DUEL_AGAIN = 60;

    static String prizeLine(Course c, ActiveDream ad, Dreamer d) {
        if (bell(c, ad)) return "You're holding the bell. Wake, and it's yours.";
        if (c.room() != null && c.room().place() != null) return "Look through the glass. That place is real, and it's in your world.";
        Trial t = c.trial();
        if (t == Trial.CHASE) return "It's gone. Your fear let go of you.";
        if (t == Trial.DUEL) return "Your mentor yields. \"Good. Again, another night.\"";
        if (t.run() && !t.move.isEmpty() && !d.ledger.remembered.contains(t.id)) return "Your body remembers " + t.moveName() + ".";
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
            DreamLedger.Finished before = d.ledger.finish(t.id, st.wonTicks, medal, day, ad.bp.plan.trialRef());
            Progression.count(sp, FINISHED);
            double xp = before == null ? XP_FIRST : before.day() != day ? XP_AGAIN : 0;
            if (xp > 0) Progression.worldXp(sp, "awaken:dream_trial", xp);
            boolean again = replay(ad);
            boolean residue = !again || xp > 0; // a replay's residue comes with its day's XP, at most once a day
            if (residue) DreamOffers.give(sp, new ItemStack(LucidiumContent.DREAM_RESIDUE.get(), RESIDUE));
            boolean firstGold = medal == DreamLedger.GOLD && (before == null || before.medal() < DreamLedger.GOLD);
            boolean learnt = remember(sp, t, d, before == null, firstGold);
            boolean best = before != null && st.wonTicks < before.best();
            if (t.run() && (before == null || best)) d.ledger.ghosts.put(t.id, java.util.Arrays.copyOf(st.trace, st.traceLen));
            rows.add(new SignalWindow.Row(Component.literal("Time"), Component.literal(time(st.wonTicks) + (best ? ", your best" : ""))));
            if (medal > 0) rows.add(new SignalWindow.Row(Component.literal("Medal"), Component.literal(medalName(medal))));
            List<String> kept = new ArrayList<>();
            if (learnt) kept.add(t.moveName());
            if (bell(c, ad)) kept.add("a bell");
            // the move goes in the body: the card says how to do it awake; a Room's place, the Chase's fear, the Duel's lesson
            String body = learnt ? "Your body remembers " + t.moveName() + ". " + t.how
                    : again ? (best ? "A new best." : "") : t.run() ? "" : prize(sp, ad, d, day, kept);
            if (residue) kept.add(RESIDUE + " Dream Residue");
            if (kept.isEmpty()) kept.add(again ? "your time" : "nothing new");
            rows.add(new SignalWindow.Row(Component.literal("You kept"), Component.literal(String.join(", ", kept))));
            summary = t.title + ": " + (t.run() ? "reached the end" : "won") + " in " + time(st.wonTicks)
                    + (medal > 0 ? " (" + medalName(medal).toLowerCase() + ")" : "");
            String title = switch (t.kind) {
                case RUN -> "You reached the end of the dream";
                case ROOM -> "You found your way through the dream";
                case CHASE -> bell(c, ad) ? "You took the bell from your fear" : "You got away from your fear";
                case DUEL -> "Your mentor yielded";
            };
            w = new Waking(title, body, rows, "Your Dream Journal has written it down.");
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

    /** XP for letting a fear go (the Chase) and for the mentor's lesson (the Duel), as the island dreams gave. */
    static final double FEAR_XP = 0.10, LESSON_XP = 0.20;

    /**
     * What a Room, the Chase and the Duel give on waking (SPEC §9.5), into {@code kept}; returns the card's line. A Room's
     * place goes on your Atlas and into the Journal's Visions (everyone in the dream gets it); the Chase lets your
     * week's fear go; the Duel is your mentor's lesson.
     */
    static String prize(ServerPlayer sp, ActiveDream ad, Dreamer d, long day, List<String> kept) {
        Course c = ad.bp.course;
        switch (c.trial()) {
            case CHASE -> {
                Nightmares.Family f = RoomRules.fear(ad, sp);
                Progression.count(sp, Counters.NIGHTMARES_BEATEN);
                for (String counter : Nightmares.weeklyCountersOf(f, java.util.Map.copyOf(Progression.profile(sp).counters())))
                    Progression.setCounter(sp, counter, 0);
                d.clearFamily(f);
                Progression.grantLevels(sp, FEAR_XP, Progression.XpSource.DREAM);
                kept.add("a fear let go");
                return "You stopped running from " + f.fear + ". It won't come for you in your dreams this week.";
            }
            case DUEL -> {
                Progression.grantLevels(sp, LESSON_XP, Progression.XpSource.DREAM);
                Progression.count(sp, Mentors.MENTOR_DUELS_WON);
                kept.add("your mentor's lesson");
                return "\"Good. Again, another night.\"";
            }
            default -> {}
        }
        Vision v = placeOf(sp, ad, d, day);
        if (v == null) return "";
        Visions.reveal(sp, v, day);
        AtlasHooks.reveal(sp, v.pos(), PLACE_REVEAL);
        Vision shown = d.vision(v.id()).orElse(v);
        kept.add("a real place");
        Progression.markNewThing(sp, "vision " + v.id());
        DirectorLog.log(sp, "vision", "shown by the " + c.trial().id + " room: " + v.label());
        return "You saw a real place: " + v.label() + ". It's on your map now. " + shown.hint();
    }

    /** How much of the Atlas a dreamt place draws in round it. */
    static final int PLACE_REVEAL = 40;

    /**
     * This dream's hidden place for one dreamer: theirs if it was planned for them; for anyone else in a shared dream, a
     * copy of the host's (the place is everyone's who saw it). Null without one.
     */
    static @Nullable Vision placeOf(ServerPlayer sp, ActiveDream ad, Dreamer d, long day) {
        if (ad.bp.plan.vision().isEmpty()) return null; // shown even if its snapshot couldn't be drawn: the place is real
        String id = ad.bp.plan.vision().get();
        Optional<Vision> own = d.vision(id);
        if (own.isPresent()) return own.get();
        ServerPlayer host = sp.level().getServer().getPlayerList().getPlayer(ad.host);
        if (host == null) return null;
        Optional<Vision> theirs = Dreams.dreamer(host).vision(id);
        if (theirs.isEmpty()) return null;
        Vision h = theirs.get();
        Vision copy = new Vision(h.id(), h.snapshotId(), h.label(), h.dim(), h.pos(), "", day, -1, false);
        d.putVision(copy);
        return copy;
    }

    // ---- medals ------------------------------------------------------------------------------------------------------

    /** A Run's medal by the Gauntlet's rule: gold within par x 1.1 + 1 s, silver within par x 1.5 + 2 s, else bronze. */
    /** A Run's mastery: on its Ascent track the first time you finish it, and again the first time you take its gold. */
    static final int MASTERY_FIRST = 50, MASTERY_GOLD = 50;

    /**
     * A Run finished (SPEC §9.4): the first time, the body remembers its moves (THE BODY marks them), its track gets
     * mastery and the free-run tip counts done; the first gold, mastery again. Returns whether the moves were remembered
     * just now.
     */
    static boolean remember(ServerPlayer sp, Trial t, Dreamer d, boolean first, boolean firstGold) {
        if (!t.run() || t.move.isEmpty()) return false;
        if (!t.track.isEmpty()) {
            int points = (first ? MASTERY_FIRST : 0) + (firstGold ? MASTERY_GOLD : 0);
            if (points > 0) MovementHooks.addMastery(sp, t.track, points);
        }
        if (!d.ledger.remembered.add(t.id)) return false;
        MovementHooks.remember(sp, t.moves());
        TipHooks.done(sp, tipOf(t));
        Progression.markNewThing(sp, "dream move " + t.id);
        return true;
    }

    /** The tip that teaches a Run's moves awake. */
    static net.minecraft.resources.Identifier tipOf(Trial t) {
        return dev.awaken.Awaken.id(switch (t.track) {
            case "TETHER" -> "ascent/rope";
            case "LEAPER" -> "ascent/leaper";
            case "WINGS" -> "ascent/wings";
            default -> "ascent/freerun";
        });
    }

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
