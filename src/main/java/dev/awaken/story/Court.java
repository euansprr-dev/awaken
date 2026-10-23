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
        MobRole role = e.getData(CoreApi.MOB_ROLE.get());
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
        if (e.level().isClientSide()) return;
        MobRole role = e.getData(CoreApi.MOB_ROLE.get());
        if (role.bossId().isEmpty()) return;
        String boss = role.bossId().get();
        StoryContent.Sovereign sov = StoryContent.SOVEREIGNS.get(net.minecraft.resources.Identifier.tryParse(boss));
        for (ServerPlayer p : e.level().getServer().getPlayerList().getPlayers()) {
            if (p.level() != e.level() || p.distanceTo(e) > 96) continue;
            StoryProgress s = Prologue.progress(p);
            if (sov != null && s.sovereignsDefeated.add(boss)) {
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
                p.level().getServer().execute(() -> AwakenNet.send(p, new StoryPayloads.OpenEnding()));
            }
        }
    }

    /** Whether the player may enter the Seed: S-rank or above and all eight Sovereigns defeated. */
    static boolean seedOpen(ServerPlayer p) {
        return Progression.rank(p).ordinal() >= Rank.S.ordinal() && Prologue.progress(p).sovereignsDefeated.size() >= 8;
    }

    // ---- endings ----------------------------------------------------------------------------------------------------

    private record Playing(ServerPlayer p, StoryContent.Ending ending, long start) {}

    private static final List<Playing> PLAYING = new ArrayList<>();

    static void choose(ServerPlayer p, String which) {
        StoryProgress s = Prologue.progress(p);
        if (!s.seen.contains("seed:defeated") || s.seen.contains("ending:chosen")) return;
        StoryContent.Ending ending = StoryContent.ENDINGS.get(Awaken.id(which));
        if (ending == null) return;
        s.seen.add("ending:chosen");
        s.seen.add("ending:" + which);
        Prologue.sync(p);
        StoryWorld w = StoryWorld.get(p.level().getServer());
        w.ending = which;
        switch (ending.effect()) {
            case "ascension" -> w.ascension++;
            case "calm_gates" -> w.gatesCalm = true;
            case "colour" -> w.colourRestored = true;
            default -> {}
        }
        w.setDirty();
        if (Progression.rank(p) == Rank.S) Progression.passReassessment(p);
        Progression.markNewThing(p, "ending");
        PLAYING.add(new Playing(p, ending, p.level().getGameTime()));
    }

    static void tick(MinecraftServer server) {
        Iterator<Playing> it = PLAYING.iterator();
        while (it.hasNext()) {
            Playing pl = it.next();
            if (pl.p().isRemoved()) { it.remove(); continue; }
            long t = pl.p().level().getGameTime() - pl.start();
            int index = (int) (t / 100);
            if (t % 100 == 0 && index < pl.ending().lines().size()) {
                Signal.send(pl.p(), SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(index == 0 ? pl.ending().title() : ""))
                        .body(Component.literal(pl.ending().lines().get(index))).priority(10).ttl(95).build());
            }
            if (index >= pl.ending().lines().size() + 1) {
                AwakenNet.send(pl.p(), new StoryPayloads.Credits(pl.ending().title()));
                it.remove();
            }
        }
    }
}
