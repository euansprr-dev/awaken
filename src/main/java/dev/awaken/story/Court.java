package dev.awaken.story;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.AwakenNet;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** The Hollow Court's eight Sovereigns, the Seed's final boss, and the three endings. */
final class Court {
    private Court() {}

    static final String FINAL_BOSS = "awaken:signals_shadow";

    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity e)) return;
        // Court Sovereigns are summoned fresh each visit; a copy reloaded from disk would be a duplicate.
        if (event.loadedFromDisk() && e.getPersistentData().getBooleanOr(COURT_TAG, false)) {
            event.setCanceled(true);
            return;
        }
        MobRole role = MobRole.of(e);
        if (role.bossId().isEmpty()) return;
        StoryContent.Sovereign sov = StoryContent.SOVEREIGNS.get(net.minecraft.resources.Identifier.tryParse(role.bossId().get()));
        String boss = role.bossId().get();
        if (sov == null && !boss.equals(FINAL_BOSS)) return;
        event.getLevel().getServer().execute(() -> {
            for (ServerPlayer p : e.level().getServer().getPlayerList().getPlayers()) {
                if (p.level() != e.level() || p.distanceTo(e) > 64) continue;
                StoryProgress s = Prologue.progress(p);
                if (!s.seen.add("met:" + boss)) continue;
                Prologue.sync(p);
                if (sov != null) {
                    Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(sov.name() + ", " + sov.title()))
                            .body(Component.literal(sov.intro())).priority(9).ttl(20 * 8).build());
                } else {
                    Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("The Signal's Shadow"))
                            .body(Component.literal("It has your face. It moves the way you move when you're tired.")).priority(9).ttl(20 * 8).build());
                }
            }
        });
    }

    static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide() || !Prologue.storyOn()) return;
        MobRole role = MobRole.of(e);
        if (role.bossId().isEmpty()) return;
        String boss = role.bossId().get();
        StoryContent.Sovereign sov = StoryContent.SOVEREIGNS.get(net.minecraft.resources.Identifier.tryParse(boss));
        // a Sovereign set free by a fight, not one removed by a command, the void or a despawn
        LivingEntity credit = e.getKillCredit();
        boolean earned = credit instanceof ServerPlayer
                || (credit != null && MobRole.of(credit).role() == MobRole.Role.ECHO);
        boolean grieving = e.getPersistentData().getBooleanOr(GRIEVING_TAG, false);
        for (ServerPlayer p : e.level().getServer().getPlayerList().getPlayers()) {
            if (p.level() != e.level() || p.distanceTo(e) > 96) continue;
            StoryProgress s = Prologue.progress(p);
            // a Grieving form set to rest (SPEC §43.14): once each, a great deal of experience and its harder hoard
            if (sov != null && earned && grieving && s.seen.add("grieving:" + boss)) {
                Progression.grantLevels(p, 0.5, Progression.XpSource.STORY);
                Progression.bestMoment(p, "Laid " + sov.name() + "'s grief to rest");
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("Grieving " + sov.name()))
                        .body(Component.literal("It's quiet now. What it carried was grief, and it's put it down at last.")).priority(9).ttl(20 * 8).build());
                Prologue.sync(p);
                continue;
            }
            if (sov != null && earned && s.sovereignsDefeated.add(boss)) {
                Progression.count(p, "awaken:sovereigns_defeated");
                Progression.markNewThing(p, "sovereign");
                Progression.bestMoment(p, sov.name() + " set down what they carried");
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(sov.name()))
                        .body(Component.literal(sov.defeat())).priority(9).ttl(20 * 8).build());
                Prologue.sync(p);
            }
            if (boss.equals(FINAL_BOSS) && s.seen.add("seed:defeated")) {
                Progression.count(p, "awaken:story/seed_reached");
                Beats.fire(p, "seed_reached");
                Prologue.sync(p);
                // the world's story has one ending: the first to reach it chooses, later ones are told it
                p.level().getServer().execute(() -> {
                    if (StoryWorld.get(p.level().getServer()).ended()) tell(p);
                    else AwakenNet.send(p, offer(p));
                });
            }
        }
    }

    // ---- the Hollow Court (stratum 9) and the Seed (stratum 10) --------------------------------------------------------

    private static final java.util.List<String> ORDER = java.util.List.of("awaken:sovereign_hunger", "awaken:sovereign_silence",
            "awaken:sovereign_sleep", "awaken:sovereign_machines", "awaken:sovereign_mirrors", "awaken:sovereign_storms",
            "awaken:sovereign_roots", "awaken:sovereign_ash");

    /**
     * Every 30 seconds: an S-rank player in the Hollow Court with no Sovereign nearby meets the next one they haven't set
     * free; a player in the Seed whose Court is empty starts the finale (once at a time).
     */
    static final String COURT_TAG = "awaken_court";
    /** A Sovereign's Grieving form (SPEC §43.14): met again after the Deep answered, harder, once each. */
    static final String GRIEVING_TAG = "awaken_grieving";
    /** How much harder the Court is after the Deep answered: an unbeaten Sovereign, and a Grieving form. */
    static final int ANGRIER = 10, GRIEVING = 20;
    private static final java.util.Map<java.util.UUID, java.util.UUID> SUMMONED = new java.util.HashMap<>();

    static void forget() {
        SUMMONED.clear();
        PLAYING.clear();
        LATER.clear();
    }

    /**
     * Every 30 seconds an S-rank player in the Hollow Court with no Sovereign of theirs alive meets the next one they
     * haven't set free. One per player at a time, never near the camp, never for spectators or creative players.
     * The finale in the Seed is started by the Descent's own arena check.
     */
    static void courtTick(MinecraftServer server) {
        if (server.getTickCount() % 600 != 300 || !Prologue.storyOn()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p.level() instanceof net.minecraft.server.level.ServerLevel level) || p.isSpectator() || p.isCreative()) continue;
            if (dev.awaken.descent.Descent.depthOf(p) != 9 || Progression.rank(p).ordinal() < Rank.S.ordinal()) continue;
            if (dev.awaken.descent.Descent.nearCamp(p, 9, 40)) continue;
            java.util.UUID mine = SUMMONED.get(p.getUUID());
            if (mine != null && level.getEntity(mine) instanceof LivingEntity alive && alive.isAlive()) continue;
            StoryProgress s = Prologue.progress(p);
            boolean answered = dev.awaken.core.api.BellHooks.deepAnswered(server);
            String next = ORDER.stream().filter(id -> !s.sovereignsDefeated.contains(id)).findFirst().orElse(null);
            // all eight set free, and the Deep has answered the Hundred Peal: their Grieving forms, one by one
            boolean grief = next == null && answered;
            if (grief) next = ORDER.stream().filter(id -> !s.seen.contains("grieving:" + id)).findFirst().orElse(null);
            if (next == null) continue;
            net.minecraft.core.BlockPos at = floorNear(level, p.blockPosition().offset(p.getRandom().nextInt(31) - 15, 0, p.getRandom().nextInt(31) - 15));
            int lv = Rank.S.contentLevel + (grief ? GRIEVING : answered ? ANGRIER : 0);
            LivingEntity boss = dev.awaken.core.api.MobHooks.spawnBoss(level, next, at, lv, "");
            if (boss != null) {
                boss.getPersistentData().putBoolean(COURT_TAG, true);
                if (grief) {
                    boss.getPersistentData().putBoolean(GRIEVING_TAG, true);
                    StoryContent.Sovereign sov = StoryContent.SOVEREIGNS.get(net.minecraft.resources.Identifier.tryParse(next));
                    if (sov != null) boss.setCustomName(Component.literal("Grieving " + sov.name()));
                }
                SUMMONED.put(p.getUUID(), boss.getUUID());
            }
        }
    }

    /**
     * Re-offer the ending choice if it was lost (disconnect, crash, death screen), or tell the world's ending if
     * someone chose it meanwhile. Also records players who chose before the world kept who reached its ending.
     */
    static void reofferEnding(ServerPlayer p) {
        StoryProgress s = Prologue.progress(p);
        StoryWorld w = StoryWorld.get(p.level().getServer());
        if (s.seen.contains("ending:chosen") && w.ended() && !w.reached(p.getUUID())) {
            // a save from before schema 2: whoever chose an ending took part in the world's tier
            w.reached.put(p.getUUID(), w.ascension);
            w.setDirty();
        }
        if (Prologue.storyOn() && s.seen.contains("seed:defeated") && !s.seen.contains("ending:chosen")) {
            if (w.ended()) tell(p);
            else AwakenNet.send(p, offer(p));
        }
    }

    /** A standing spot near pos: the first solid block under 2 air, searching down then up. */
    static net.minecraft.core.BlockPos floorNear(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        for (int dy = 4; dy > -24; dy--) {
            var at = pos.above(dy);
            if (level.getBlockState(at.below()).isSolid() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) return at;
        }
        return pos;
    }

    /** Whether the player may enter the Seed: S-rank or above and all eight Sovereigns defeated. */
    static boolean seedOpen(ServerPlayer p) {
        return Progression.rank(p).ordinal() >= Rank.S.ordinal() && Prologue.progress(p).sovereignsDefeated.size() >= 8;
    }

    // ---- endings ----------------------------------------------------------------------------------------------------

    /** An ending playing for one player; {@code chosenBy} names who chose it when the world chose before them. */
    private record Playing(ServerPlayer p, StoryContent.Ending ending, long start, String chosenBy) {}

    private static final List<Playing> PLAYING = new ArrayList<>();

    /** The ending choice for this player: Wake up is open only once they've rung their Hundred Peal (SPEC §43.14). */
    static StoryPayloads.OpenEnding offer(ServerPlayer p) {
        return new StoryPayloads.OpenEnding(dev.awaken.core.api.BellHooks.pealed(p));
    }

    /** Players who said "not yet" at the Seed: false while they're still in it, true once they've left. Not saved. */
    private static final java.util.Map<java.util.UUID, Boolean> LATER = new java.util.HashMap<>();

    /** Every 5 s: one who said "not yet", left the Seed and has come back into it is asked again. */
    static void laterTick(MinecraftServer server) {
        if (LATER.isEmpty() || server.getTickCount() % 100 != 50) return;
        LATER.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) return true;
            boolean in = dev.awaken.descent.Descent.depthOf(p) == 10;
            if (!in) {
                e.setValue(true);
                return false;
            }
            if (!e.getValue()) return false;
            StoryProgress s = Prologue.progress(p);
            if (s.seen.contains("ending:chosen")) return true;
            if (StoryWorld.get(server).ended()) tell(p);
            else AwakenNet.send(p, offer(p));
            return true;
        });
    }

    static void choose(ServerPlayer p, String which) {
        StoryProgress s = Prologue.progress(p);
        if (!s.seen.contains("seed:defeated") || s.seen.contains("ending:chosen")) return;
        if (which.equals("later")) {
            LATER.put(p.getUUID(), false);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Seed will wait."))
                    .body(Component.literal("Come back down to it when you're ready, and it'll ask you again."
                            + (dev.awaken.core.api.BellHooks.pealed(p) ? "" : " To wake, you'll need all hundred bells ringing together in your Great Belfry first.")))
                    .priority(8).ttl(20 * 10).build());
            return;
        }
        // Wake up needs the Hundred Peal: without it the choice is asked again
        if (which.equals("wake") && !dev.awaken.core.api.BellHooks.pealed(p)) {
            AwakenNet.send(p, offer(p));
            return;
        }
        StoryWorld w = StoryWorld.get(p.level().getServer());
        if (w.ended()) {
            // someone chose first (both stood at the Seed): the world keeps its ending, this player is told it
            tell(p);
            return;
        }
        var endingId = net.minecraft.resources.Identifier.tryParse("awaken:" + which);
        StoryContent.Ending ending = endingId == null ? null : StoryContent.ENDINGS.get(endingId);
        if (ending == null) return;
        s.seen.add("ending:chosen");
        s.seen.add("ending:" + which);
        Prologue.sync(p);
        w.ending = which;
        w.endingBy = p.getUUID();
        w.endingByName = p.getGameProfile().name();
        w.endingDay = dev.awaken.core.PlayDays.current(p.level().getServer());
        // the world's one milestone: its effect applies once, whoever and however many reach the Seed after
        switch (ending.effect()) {
            case "ascension" -> w.ascension = Math.min(10, w.ascension + 1);
            case "calm_gates" -> w.gatesCalm = true;
            case "colour" -> w.colourRestored = true;
            default -> {}
        }
        w.reached.put(p.getUUID(), w.ascension);
        w.setDirty();
        finishStory(p);
        PLAYING.add(new Playing(p, ending, p.level().getGameTime(), ""));
        // anyone else who stood at the same Seed and was still choosing is told the world's ending now
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            StoryProgress os = Prologue.progress(o);
            if (o != p && os.seen.contains("seed:defeated") && !os.seen.contains("ending:chosen")) tell(o);
        }
    }

    /**
     * A player reaches the world's ending after it was chosen: no choice, no second effect. They see the ending the
     * world chose (with who chose it) and the credits, take part in its Ascension tier, and their own story is done.
     */
    static void tell(ServerPlayer p) {
        StoryProgress s = Prologue.progress(p);
        StoryWorld w = StoryWorld.get(p.level().getServer());
        if (!w.ended() || s.seen.contains("ending:chosen")) return;
        var endingId = net.minecraft.resources.Identifier.tryParse("awaken:" + w.ending);
        StoryContent.Ending ending = endingId == null ? null : StoryContent.ENDINGS.get(endingId);
        s.seen.add("ending:chosen");
        s.seen.add("ending:" + w.ending);
        s.seen.add("ending:told");
        Prologue.sync(p);
        w.reached.put(p.getUUID(), w.ascension);
        w.setDirty();
        finishStory(p);
        String by = w.endingByName.isEmpty() ? "" : w.endingByName.equals(p.getGameProfile().name()) ? "" : w.endingByName;
        if (ending != null) PLAYING.add(new Playing(p, ending, p.level().getGameTime(), by));
        else AwakenNet.send(p, new StoryPayloads.Credits(w.ending));
    }

    private static void finishStory(ServerPlayer p) {
        if (Progression.rank(p) == Rank.S) Progression.passReassessment(p);
        dev.awaken.descent.Descent.completeSeed(p);
        Progression.markNewThing(p, "ending");
    }

    static void tick(MinecraftServer server) {
        Iterator<Playing> it = PLAYING.iterator();
        while (it.hasNext()) {
            Playing pl = it.next();
            if (pl.p().isRemoved()) { it.remove(); continue; }
            long t = pl.p().level().getGameTime() - pl.start();
            int index = (int) (t / 100);
            if (t % 100 == 0 && index < pl.ending().lines().size()) {
                var w = SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(index == 0 ? pl.ending().title() : ""))
                        .body(Component.literal(pl.ending().lines().get(index))).priority(10).ttl(95);
                if (index == 0 && !pl.chosenBy().isEmpty()) w.footer(Component.literal("This world's ending: " + pl.chosenBy() + " chose it"));
                Signal.send(pl.p(), w.build());
            }
            if (t % 100 == 0 && index == pl.ending().lines().size() && "ascension".equals(pl.ending().effect())) {
                int tier = StoryWorld.get(server).ascensionOf(pl.p().getUUID());
                Signal.send(pl.p(), SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Ascension " + tier))
                        .body(Component.literal("Everything that hunts you is stronger now. You learn faster to match."))
                        .row("Monsters", "+" + (30 * Math.min(tier, 10)) + "% health, +" + (15 * Math.min(tier, 10)) + "% damage")
                        .row("XP", "+" + (15 * Math.min(tier, 10)) + "%").priority(10).ttl(95).build());
            }
            if (index >= pl.ending().lines().size() + 1) {
                AwakenNet.send(pl.p(), new StoryPayloads.Credits(pl.ending().title()));
                it.remove();
            }
        }
    }
}
