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
                if (TrialHooks.SKYBORN.equals(trial)) return skyEdge(p);
                return TrialHooks.LEAPER.equals(trial) && edge(p);
            }

            @Override
            public void done(ServerPlayer p, String trial, int ticks, int medal) {
                if (TrialHooks.LEAPER.equals(trial)) leaperDone(p, ticks, medal);
                else if (TrialHooks.SKYBORN.equals(trial)) skybornDone(p);
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
    private static boolean skyEdge(ServerPlayer p) {
        if (Ascent.has(p, Ascent.Tier.SKYBORN)) return true;
        Ascent.grant(p, Ascent.Tier.SKYBORN, "sky leviathan's trial");
        AwakenNet.send(p, new AscentPayloads.Remember(AscentPayloads.Tier.SKYBORN.bit(), AscentPayloads.SKY_EDGE));
        DirectorLog.log(p, "ascent", "the Sky Leviathan's trial: at the open sky, the sky remembers them");
        return true;
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
                && Ascent.has(p, Ascent.Tier.TETHER) && !Ascent.has(p, Ascent.Tier.LEAPER)
                && (mem.flags() & AscentMemory.LEAPER_TRIAL) == 0 && mem.level(AscentMemory.Track.TETHER) >= LEAPER_FROM
                && !TetherVow.running(p);
    }

    /** Opens the Leaper's Gauntlet in front of the player. Returns the gate's id (-1: none could form). */
    static int openLeaper(ServerPlayer p) {
        long seed = p.getUUID().getLeastSignificantBits() ^ p.level().getGameTime();
        int id = dev.awaken.gates.Gates.openScripted(p, dev.awaken.gates.Gates.leaperTrialPlan(seed));
        if (id < 0) return -1;
        p.setData(AscentContent.MEMORY, p.getData(AscentContent.MEMORY).withFlag(AscentMemory.LEAPER_TRIAL));
        Signal.toast(p, Component.translatable("awaken.ascent.trial.leaper.open"));
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
        Progression.grantXp(p, toNext * 0.2, Progression.XpSource.DISCOVERY);
        Progression.count(p, "awaken:ascent_leaper_trial");
        DirectorLog.log(p, "ascent", String.format("the Leaper's Gauntlet is run (%.1f s, medal %d): the leap is yours", ticks / 20.0, medal));
    }
}
