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
 * The Ascension trials on the Ascent's side (SPEC §17.10). The Leaper's Gauntlet: once you hold the rope and have
 * swung enough to know it (Tether mastery {@link #LEAPER_FROM}), a trial gate opens near you, once. Its course is a
 * Gauntlet whose last chasm no sprint jump crosses: at its edge your legs remember the air (the leap is given there, with
 * a short moment that says how), and at the far landing the leap is yours (the gift's scene).
 */
final class AscentTrials {
    private AscentTrials() {}

    /** Tether mastery that opens the Leaper's Gauntlet (level 2: 100 points of rope). */
    static final int LEAPER_FROM = 2;

    static void register() {
        TrialHooks.install(new TrialHooks.Trials() {
            @Override
            public boolean atEdge(ServerPlayer p, String trial) {
                return TrialHooks.LEAPER.equals(trial) && edge(p);
            }

            @Override
            public void done(ServerPlayer p, String trial, int ticks, int medal) {
                if (TrialHooks.LEAPER.equals(trial)) leaperDone(p, ticks, medal);
            }
        });
    }

    /** Once every five seconds a player who has earned it is offered the trial (a gate opens in front of them). */
    static void tick(ServerPlayer p, long now) {
        if ((now + (p.getUUID().hashCode() & 127)) % 100 != 0 || !leaperDue(p)) return;
        openLeaper(p);
    }

    /** Awake, in the overworld on the ground, holding the rope and not the leap, its mastery reached, not offered yet. */
    static boolean leaperDue(ServerPlayer p) {
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        return p.level().dimension() == Level.OVERWORLD && p.onGround() && !p.isSpectator() && !p.isPassenger()
                && Progression.profile(p).awakened() && dev.awaken.body.BodyConfig.ascent()
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
