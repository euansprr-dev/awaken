package dev.awaken.body.ascent;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.TrialHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The Ascension trials on the Ascent's side (SPEC §17.10). The Leaper's Gauntlet: once you hold the rope at D-rank and
 * have swung enough to know it (Tether mastery {@link #LEAPER_FROM}, a few evenings of rope), a trial gate opens near
 * you, once. Its course is a
 * Gauntlet whose last chasm no sprint jump crosses: at its edge your legs remember the air (the leap is given there, with
 * a short moment that says how), and at the far landing the leap is yours (the gift's scene).
 */
final class AscentTrials {
    private AscentTrials() {}

    /**
     * Tether mastery that opens the Leaper's Gauntlet (level 4: 400 points of rope, about 80-130 swings and launches),
     * so the leap is its own rung, hours after the rope (progression plan decision 2), not minutes.
     */
    static final int LEAPER_FROM = 4;
    /** The rank the Gauntlet waits for. */
    static final dev.awaken.progression.Rank LEAPER_RANK = dev.awaken.progression.Rank.D;

    /** Wings mastery that opens the Sky Leviathan's trial (with rank A: SPEC §17.18). */
    static final int SKYBORN_FROM = 3;

    static void register() {
        TrialHooks.install(new TrialHooks.Trials() {
            @Override
            public boolean atEdge(ServerPlayer p, String trial) {
                if (TrialHooks.SKYBORN.equals(trial)) return skyEdge(p, "sky leviathan's trial");
                if (TrialHooks.SKY_WARDEN.equals(trial)) return skyEdge(p, "the Sky Warden's roof"); // SPEC §47.8
                return TrialHooks.LEAPER.equals(trial) && edge(p);
            }

            @Override
            public void done(ServerPlayer p, String trial, int ticks, int medal) {
                if (TrialHooks.LEAPER.equals(trial)) leaperDone(p, ticks, medal);
                else if (TrialHooks.SKYBORN.equals(trial)) skybornDone(p);
                else if (TrialHooks.SKY_WARDEN.equals(trial)) skybornDone(p); // SPEC §47.8
                else if (TrialHooks.RINGER.equals(trial)) {
                    // SPEC §63.2: the chain is cold iron until a vault wakes it (the Tether Vow); before The Way Up it was the rope
                    if (!dev.awaken.body.BodyConfig.wayUp()) Ascent.grant(p, Ascent.Tier.TETHER, "the Ringer's chain"); // SPEC §47.4
                    else DirectorLog.log(p, "ascent", "the Ringer's chain is theirs: a vault will wake it");
                }
            }

            // the Leaper's Gauntlet as a place of stands and halls (SPEC §71.4): its marks, its pay, whether it was run
            @Override
            public void at(ServerPlayer p, String trial, String mark, net.minecraft.core.BlockPos from, net.minecraft.core.BlockPos to) {
                if (TrialHooks.LEAPER.equals(trial)) WayUp.mark(p, mark, from, to);
            }

            @Override
            public TrialHooks.Pay pay(ServerPlayer p, String trial, int ticks, int medal) {
                return TrialHooks.LEAPER.equals(trial) ? WayUp.pay(p, ticks, medal) : TrialHooks.Pay.NONE;
            }

            @Override
            public boolean finished(ServerPlayer p, String trial) {
                return TrialHooks.LEAPER.equals(trial) && WayUp.gauntletRun(p);
            }
        });
    }

    /** Once every five seconds a player who has earned one is offered its trial (a gate opens in front of them). */
    static void tick(ServerPlayer p, long now) {
        if ((now + (p.getUUID().hashCode() & 127)) % 100 != 0) return;
        if (leaperDue(p)) openLeaper(p);
        else if (skybornDue(p)) openSkyborn(p);
    }

    // ---- the Sky Leviathan's trial (SPEC §17.18) -------------------------------------------------------------------------

    /** Awake, rank A, on the ground in the overworld, holding Wings and not Skyborn, Wings mastery reached, not offered yet. */
    static boolean skybornDue(ServerPlayer p) {
        // retired by The Way Up (SPEC §63.2): A-rank always comes with flight, so nobody could be offered it
        if (dev.awaken.body.BodyConfig.wayUp()) return false;
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        return p.level().dimension() == Level.OVERWORLD && p.onGround() && !p.isSpectator() && !p.isPassenger()
                && Progression.profile(p).awakened() && dev.awaken.body.BodyConfig.ascent()
                && Progression.rank(p).ordinal() >= dev.awaken.progression.Rank.A.ordinal()
                && Ascent.has(p, Ascent.Tier.WINGS) && !Ascent.has(p, Ascent.Tier.SKYBORN)
                && (mem.flags() & AscentMemory.SKYBORN_TRIAL) == 0 && mem.level(AscentMemory.Track.WINGS) >= SKYBORN_FROM
                && !TetherVow.running(p);
    }

    /** Opens the Sky Leviathan's trial in front of the player (a real A-rank gate that never breaks). The gate's id, or -1. */
    static int openSkyborn(ServerPlayer p) {
        long seed = p.getUUID().getMostSignificantBits() ^ p.level().getGameTime();
        int id = dev.awaken.gates.Gates.openTrial(p, dev.awaken.gates.Gates.skybornTrialPlan(seed));
        if (id < 0) return -1;
        p.setData(AscentContent.MEMORY, p.getData(AscentContent.MEMORY).withFlag(AscentMemory.SKYBORN_TRIAL));
        Signal.toast(p, Component.translatable("awaken.ascent.trial.skyborn.open"));
        DirectorLog.log(p, "ascent", "the Sky Leviathan's trial opens (gate " + id + ")");
        return id;
    }

    /** At the open sky's edge: flight, given the first time with its moment. */
    private static boolean skyEdge(ServerPlayer p, String source) {
        if (Ascent.has(p, Ascent.Tier.SKYBORN)) return true;
        // wings never earned come first, with their own beat, two seconds before the sky (SPEC §63.2): three gifts never
        // land at once
        if (dev.awaken.body.BodyConfig.wayUp() && !Ascent.has(p, Ascent.Tier.WINGS)) {
            Ascent.grant(p, Ascent.Tier.WINGS, source + " (wings first)");
            // a fight is no place to learn them: the first flight's lesson is owed (the fight's own glides may pay it)
            WayUp.owe(p, WayUp.Rung.WINGS);
            AwakenNet.send(p, new AscentPayloads.Remember(AscentPayloads.Tier.WINGS.bit(), AscentPayloads.WINGS_EDGE));
            DirectorLog.log(p, "ascent", source + ": wings never earned are given first");
            dev.awaken.core.Later.run(p.level().getServer(), WINGS_FIRST, () -> {
                if (!p.hasDisconnected() && p.isAlive()) skyGive(p, source);
            });
            return true;
        }
        skyGive(p, source);
        return true;
    }

    /** Ticks between wings given at the sky's edge and the sky itself. */
    static final int WINGS_FIRST = 40;

    private static void skyGive(ServerPlayer p, String source) {
        if (Ascent.has(p, Ascent.Tier.SKYBORN)) return;
        Ascent.grant(p, Ascent.Tier.SKYBORN, source);
        AwakenNet.send(p, new AscentPayloads.Remember(AscentPayloads.Tier.SKYBORN.bit(), AscentPayloads.SKY_EDGE));
        DirectorLog.log(p, "ascent", source + ": at the open sky, the sky remembers them");
    }

    /** The Leviathan fallen: the sky is theirs (once: the scene, mastery, a fifth of a level). */
    private static void skybornDone(ServerPlayer p) {
        AscentQuests.advance(p, AscentQuests.SKY_TRIAL);
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        if ((mem.flags() & AscentMemory.SKYBORN_TRIAL_DONE) != 0) return;
        Ascent.grant(p, Ascent.Tier.SKYBORN, "sky leviathan's trial");
        p.setData(AscentContent.MEMORY, mem.withFlag(AscentMemory.SKYBORN_TRIAL_DONE));
        AwakenNet.send(p, new AscentPayloads.Remember(AscentPayloads.Tier.SKYBORN.bit(), AscentPayloads.GIFT_SKY));
        AscentServer.show(p, new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_REMEMBER, 0));
        AscentServer.link(p, AscentMemory.Track.SKYBORN, 10);
        double toNext = dev.awaken.progression.Curve.xpToNext(Progression.profile(p).level());
        Progression.grantXp(p, toNext * 0.2, Progression.XpSource.DISCOVERY);
        Progression.count(p, "awaken:ascent_skyborn_trial");
        DirectorLog.log(p, "ascent", "the Sky Leviathan falls: the sky is theirs");
    }

    /** Awake, D-rank, in the overworld on the ground, holding the rope and not the leap, its mastery reached, not offered yet. */
    static boolean leaperDue(ServerPlayer p) {
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        return p.level().dimension() == Level.OVERWORLD && p.onGround() && !p.isSpectator() && !p.isPassenger()
                && Progression.profile(p).awakened() && dev.awaken.body.BodyConfig.ascent()
                && Progression.rank(p).ordinal() >= LEAPER_RANK.ordinal()
                && Ascent.has(p, Ascent.Tier.TETHER)
                && (dev.awaken.body.BodyConfig.wayUp() ? !WayUp.gauntletRun(p) : !Ascent.has(p, Ascent.Tier.LEAPER))
                && (mem.flags() & AscentMemory.LEAPER_TRIAL) == 0 && mem.level(AscentMemory.Track.TETHER) >= LEAPER_FROM
                && !TetherVow.running(p);
    }

    /** Opens the Leaper's Gauntlet in front of the player. Returns the gate's id (-1: none could form). */
    static int openLeaper(ServerPlayer p) {
        return openLeaper(p, null);
    }

    /**
     * Opens the Leaper's Gauntlet on the ground at {@code near} (the foot of a crown just climbed, SPEC §63.2), or in
     * front of the player when null. With The Way Up the gate goes on the player's road: a Tasks row with its place until
     * it is run, opened again if it is ever gone. Returns the gate's id (-1: none could form).
     */
    static int openLeaper(ServerPlayer p, net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos near) {
        long seed = p.getUUID().getLeastSignificantBits() ^ p.level().getGameTime();
        var plan = dev.awaken.gates.Gates.leaperTrialPlan(seed);
        int id = near == null ? dev.awaken.gates.Gates.openScripted(p, plan) : dev.awaken.gates.Gates.openScripted(p, plan, near);
        if (id < 0) return -1;
        p.setData(AscentContent.MEMORY, p.getData(AscentContent.MEMORY).withFlag(AscentMemory.LEAPER_TRIAL));
        boolean way = dev.awaken.body.BodyConfig.wayUp();
        if (way && p.level() instanceof net.minecraft.server.level.ServerLevel level) {
            net.minecraft.core.BlockPos at = near;
            if (at == null) {
                // where it stands: the scripted gate 16 to 24 ahead
                at = p.blockPosition();
                for (var g : dev.awaken.gates.Gates.near(level, p.blockPosition(), 48)) if (g.id() == id) at = g.pos();
            }
            WayUp.gateOpened(p, id, at);
        }
        // with The Way Up it answers the climber's own deed, so it is said at once (a paced line can be filed unseen)
        // and it says its point (Euan, 2026-11-11: "what was the point of that gate?"): the kind of thing, never which
        if (way) {
            WayUp.say(p, "A gate has opened for you", (near != null ? "It stands at the foot of what you climbed. " : "It stands in front of you. ")
                    + (Ascent.has(p, Ascent.Tier.LEAPER) ? "It's the Leaper's Gauntlet, a course for the leap. No fighting."
                    : "It's the Leaper's Gauntlet. It holds a new way to move. No fighting."));
        }
        else Signal.toast(p, Component.translatable("awaken.ascent.trial.leaper.open"));
        DirectorLog.log(p, "ascent", "the Leaper's Gauntlet opens (gate " + id + ")");
        return id;
    }

    /** At the chasm: the leap, given the first time with its moment. Whether they can cross now. */
    private static boolean edge(ServerPlayer p) {
        if (Ascent.has(p, Ascent.Tier.LEAPER)) return true;
        Ascent.grant(p, Ascent.Tier.LEAPER, "leaper's gauntlet");
        AwakenNet.send(p, new AscentPayloads.Remember(AscentPayloads.Tier.LEAPER.bit(), AscentPayloads.LEAP_EDGE));
        DirectorLog.log(p, "ascent", "the Leaper's Gauntlet: at the chasm, the legs remember the air");
        return true;
    }

    /** Across and on the far landing: the leap is yours (once: the scene, mastery, a fifth of a level). */
    private static void leaperDone(ServerPlayer p, int ticks, int medal) {
        AscentQuests.advance(p, AscentQuests.GAUNTLET);
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        if ((mem.flags() & AscentMemory.LEAPER_TRIAL_DONE) != 0) return;
        Ascent.grant(p, Ascent.Tier.LEAPER, "leaper's gauntlet");
        p.setData(AscentContent.MEMORY, mem.withFlag(AscentMemory.LEAPER_TRIAL_DONE));
        AwakenNet.send(p, new AscentPayloads.Remember(AscentPayloads.Tier.LEAPER.bit(), AscentPayloads.GIFT_LEAP));
        AscentServer.show(p, new AscentPayloads.SeenEvent(p.getId(), AscentPayloads.EV_REMEMBER, 0));
        AscentServer.link(p, AscentMemory.Track.LEAPER, 10);
        double toNext = dev.awaken.progression.Curve.xpToNext(Progression.profile(p).level());
        Progression.grantXp(p, toNext * (dev.awaken.body.BodyConfig.wayUp() ? WayUp.GAUNTLET_XP : 0.2), Progression.XpSource.DISCOVERY);
        Progression.count(p, "awaken:ascent_leaper_trial");
        DirectorLog.log(p, "ascent", String.format("the Leaper's Gauntlet is run (%.1f s, medal %d): the leap is yours", ticks / 20.0, medal));
    }
}
