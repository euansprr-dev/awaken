package dev.awaken.story;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.CoreApi;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

/**
 * The prologue (SPEC section 10, story bible). A new player sees the Signal glitch, a white pillar rises, they walk into
 * the Bell Crypt, the statues move, and they die. They wake in the Stillpoint: "Will you accept?" Both answers say yes.
 * Players who were awakened another way (command, skip) are marked done.
 */
final class Prologue {
    private Prologue() {}

    static final String ACCEPT_ACTION = "story:accept_only";
    private static final ResourceKey<Level> STILLPOINT = ResourceKey.create(Registries.DIMENSION, Awaken.id("stillpoint"));
    static final int GATE_AFTER_TICKS = 20 * 60 * 2;

    static StoryProgress progress(ServerPlayer p) {
        return p.getData(StoryModule.PROGRESS.get());
    }

    static void sync(ServerPlayer p) {
        p.syncData(StoryModule.PROGRESS.get());
    }

    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        StoryProgress s = progress(p);
        trackCrypt(p, s);
        boolean awakened = Progression.profile(p).awakened();
        if (awakened) {
            if (s.prologue != StoryProgress.Prologue.DONE && s.prologue != StoryProgress.Prologue.ACCEPTING) {
                s.prologue = StoryProgress.Prologue.DONE;
                sync(p);
                if (Ritual.storyGate(p) >= 0) AwakenNet.send(p, StoryPayloads.RitualScene.of(StoryPayloads.RitualScene.NONE));
            }
            // awakened some other way while the question was open: never leave the black on for good
            if (s.prologue == StoryProgress.Prologue.ACCEPTING) liftTheBlack(p);
            return;
        }
        if (!storyOn()) return;
        long now = p.level().getGameTime();
        switch (s.prologue) {
            case NOT_STARTED -> {
                if (p.tickCount < 100) return;
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE,
                                Component.literal(StoryContent.line("first_contact", "[ERROR] … candidate found.")))
                        .ttl(20 * 4).priority(9).build());
                s.prologue = StoryProgress.Prologue.GLITCHED;
                s.prologueSince = now;
                sync(p);
            }
            case GLITCHED -> {
                if (now - s.prologueSince >= GATE_AFTER_TICKS) openFirstGate(p, s);
            }
            case ACCEPTING -> {
                holdTheBlack(p); // milk, a relog or a command can't lift it before the answer
                // Waiting for the answer. If the window was missed (relog), ask again every 30 seconds.
                if (now - s.prologueSince > 20 * 70 && (now - s.prologueSince) % (20 * 70) < 20) askToAccept(p);
            }
            default -> {}
        }
    }

    /** Punching trees counts as "normal Minecraft" too: the pillar rises a little sooner. */
    static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p) || !storyOn()) return;
        StoryProgress s = progress(p);
        if (s.prologue == StoryProgress.Prologue.GLITCHED && event.getState().is(BlockTags.LOGS)
                && p.level().getGameTime() - s.prologueSince > 20 * 30) {
            // never teleport or open gates inside the break event: the break is still running
            dev.awaken.core.Later.run(p.level().getServer(), 1, () -> {
                if (!p.isRemoved() && progress(p).prologue == StoryProgress.Prologue.GLITCHED) openFirstGate(p, progress(p));
            });
        }
    }

    static void openFirstGate(ServerPlayer p, StoryProgress s) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        // the story starts together: a Bell Crypt someone online is already walking to (its doors still open) is yours too
        String shared = sharedCrypt(p);
        if (!shared.isEmpty()) {
            joinCrypt(p, s, shared);
            return;
        }
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        if (look.lengthSqr() < 0.01) look = new Vec3(1, 0, 0);
        BlockPos near = BlockPos.containing(p.position().add(look.scale(55)));
        // one floor: the Ritual of the Bell's hall (docs/proposals/the-ritual.md); old worlds keep their three-floor crypt
        var id = GateHooks.openScripted(p, "awaken:bell_crypt", 0, near, 1);
        if (id.isEmpty()) {
            // Gates disabled or unavailable: awaken here instead so nobody gets stuck.
            DirectorLog.log(p, "story", "prologue: no gates available, asking to accept in place");
            beginAcceptance(p, s);
            return;
        }
        s.prologue = StoryProgress.Prologue.GATE_OPEN;
        s.prologueGate = id.get();
        s.prologueSince = p.level().getGameTime();
        sync(p);
        DirectorLog.log(p, "story", "prologue: opened the Bell Crypt " + id.get() + " near " + near.toShortString());
        Signal.toast(p, Component.literal("[ERROR] … gate detected … [ERROR]"));
        // everyone else online who hasn't awakened is called to the same crypt
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o == p || Progression.profile(o).awakened() || !storyOn()) continue;
            StoryProgress os = progress(o);
            if (os.prologue == StoryProgress.Prologue.NOT_STARTED || os.prologue == StoryProgress.Prologue.GLITCHED) joinCrypt(o, os, id.get());
        }
    }

    /** A ritual Bell Crypt an online player in the story is walking to and whose doors haven't closed, or "". */
    static String sharedCrypt(ServerPlayer p) {
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o == p) continue;
            StoryProgress os = progress(o);
            if (os.prologue != StoryProgress.Prologue.GATE_OPEN || Ritual.storyGate(o) < 0) continue;
            int gate = Ritual.storyGate(o);
            String state = GateHooks.get(p.level().getServer().overworld(), os.prologueGate).map(GateHooks.GateView::state).orElse("RELEASED");
            if (!(state.equals("BUILDING") || state.equals("READY") || state.equals("ACTIVE") || state.equals("OPEN"))) continue;
            var st = RitualData.get(p.level().getServer()).rituals.get(gate);
            if (st == null || st.beat == Ritual.Beat.WAITING) return os.prologueGate;
        }
        return "";
    }

    /** Points this player's story at a Bell Crypt someone else opened. */
    static void joinCrypt(ServerPlayer p, StoryProgress s, String gate) {
        s.prologue = StoryProgress.Prologue.GATE_OPEN;
        s.prologueGate = gate;
        s.prologueSince = p.level().getGameTime();
        sync(p);
        DirectorLog.log(p, "story", "prologue: called to the shared Bell Crypt " + gate);
        Signal.toast(p, Component.literal("[ERROR] … gate detected … [ERROR]"));
    }

    /**
     * The scripted death: a player who hasn't awakened can't die inside a gate. At the moment of the killing blow the
     * world goes dark and the Signal finally speaks clearly.
     */
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target instanceof ServerPlayer p) {
            if (Progression.profile(p).awakened() || !storyOn()) return;
            StoryProgress s = progress(p);
            if (s.prologue == StoryProgress.Prologue.ACCEPTING) {
                event.setCanceled(true);
                return;
            }
            // the ritual's hall clamps every hit instead (Ritual.onDamagePre): only its hand kills
            if (Ritual.protects(p)) return;
            boolean inGate = GateHooks.inGate(p) || s.prologue == StoryProgress.Prologue.GATE_OPEN && !s.prologueGate.isEmpty() && GateHooks.inGate(p);
            if (inGate && event.getAmount() >= p.getHealth()) {
                event.setCanceled(true);
                p.setHealth(1.0f);
                beginAcceptance(p, s);
            }
            return;
        }
        // "The statues move": the first time an unawakened player gets a Gate Lord below half health, it calls for help.
        if (event.getSource().getEntity() instanceof ServerPlayer p && !Progression.profile(p).awakened()) {
            MobRole role = MobRole.of(target);
            if (role.role() == MobRole.Role.BOSS && target.getHealth() - event.getAmount() < target.getMaxHealth() / 2
                    && !target.getPersistentData().getBooleanOr("awaken_statues_moved", false)) {
                target.getPersistentData().putBoolean("awaken_statues_moved", true);
                ServerLevel level = (ServerLevel) target.level();
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 2 * i;
                    BlockPos pos = BlockPos.containing(p.getX() + Math.cos(a) * 5, p.getY(), p.getZ() + Math.sin(a) * 5);
                    MobHooks.spawnThemed(level, "awaken:bell_crypt", MobRole.Role.ELITE, pos, role.contentLevel() + 6, Set.of(), role.gateId());
                }
                Signal.toast(p, Component.literal("The statues move."));
            }
        }
    }

    static void beginAcceptance(ServerPlayer p, StoryProgress s) {
        s.prologue = StoryProgress.Prologue.ACCEPTING;
        s.prologueSince = p.level().getGameTime();
        java.util.Optional<Vec3> mouth = gateMouth(p, s).or(() -> Ritual.mouthOf(p.level().getServer(), s.prologueGate));
        Vec3 back;
        if (p.level().dimension() == Level.OVERWORLD && !GateHooks.inGate(p)) back = p.position();
        else if (mouth.isPresent()) back = mouth.get();
        else if (GateHooks.inGate(p)) {
            // nothing to come back to: wake at the world's spawn rather than at gate-world coordinates
            var spawn = p.level().getServer().overworld().getRespawnData().pos();
            back = Vec3.atBottomCenterOf(p.level().getServer().overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
        } else back = p.position();
        s.returnX = back.x;
        s.returnY = back.y;
        s.returnZ = back.z;
        sync(p);
        holdTheBlack(p);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 30, 4, false, false));
        AwakenNet.send(p, StoryPayloads.RitualScene.of(StoryPayloads.RitualScene.VOID)); // the Void look, the heartbeat
        // the damage event that got us here is still running: move the player on the next tick
        dev.awaken.core.Later.run(p.level().getServer(), 1, () -> {
            if (p.isRemoved()) return;
            ServerLevel still = p.level().getServer().getLevel(STILLPOINT);
            if (still != null) {
                still.getChunk(0, 0);
                int y = Math.max(still.getMinY() + 1, still.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
                p.teleportTo(still, 0.5, y, 0.5, Set.<Relative>of(), p.getYRot(), 0, true);
            }
            askToAccept(p);
        });
    }

    static java.util.Optional<Vec3> gateMouth(ServerPlayer p, StoryProgress s) {
        if (s.prologueGate.isEmpty()) return java.util.Optional.empty();
        return GateHooks.get(p.level().getServer().overworld(), s.prologueGate).map(g -> Vec3.atBottomCenterOf(g.mouth()));
    }

    static void askToAccept(ServerPlayer p) {
        var r = StoryContent.revelation("prologue_accept");
        // both keys accept: the card says so, in the story's own voice
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION,
                        Component.literal(r == null ? "You have met the conditions." : r.title()))
                .body(Component.literal(r == null ? "Will you accept?" : r.body()))
                .choice(ACCEPT_ACTION, "Accept", "Accept anyway").priority(10).ttl(20 * 60).build());
    }

    /** Waking is worth at least this level, whatever the prologue banked (plan v2 §4.1: level 2–3 on waking). */
    static final int WAKE_LEVEL = 2;

    /** Signal answer: both answers are yes. */
    static void onAnswer(ServerPlayer p, String action, boolean accepted) {
        if (!ACCEPT_ACTION.equals(action)) return;
        StoryProgress s = progress(p);
        if (s.prologue != StoryProgress.Prologue.ACCEPTING || Progression.profile(p).awakened()) return;
        Progression.awaken(p); // pays everything the prologue banked: kills, floors, the Satchel's claim
        Progression.topUpToLevel(p, WAKE_LEVEL, Progression.XpSource.STORY);
        s.prologue = StoryProgress.Prologue.DONE;
        s.seen.add("revelation:prologue_accept");
        sync(p);
        StoryModule.sendSeen(p); // the Codex shows the acceptance right away, not after a relog
        ServerLevel overworld = p.level().getServer().overworld();
        p.teleportTo(overworld, s.returnX, s.returnY, s.returnZ, Set.<Relative>of(), p.getYRot(), 0, true);
        p.setHealth(p.getMaxHealth());
        liftTheBlack(p);
        AwakenNet.send(p, StoryPayloads.RitualScene.of(StoryPayloads.RitualScene.TEAR)); // the black tears open from the centre
        // what's true: the Bell Crypt is scripted and never breaks, so there's no hurry to go back in
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(accepted ? "Accepted." : "Accepted anyway."))
                .body(Component.literal("The gate will wait. It never breaks."))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 8).priority(9).build());
    }

    /**
     * Remembers how far into the Bell Crypt the player got (deepest floor, floors cleared there), for the Now
     * tracker's "Bell Crypt · floor N". Floors cleared elsewhere don't count: the counter is only read while inside it.
     */
    static void trackCrypt(ServerPlayer p, StoryProgress s) {
        if (s.prologueGate.isEmpty() || Ritual.storyGate(p) >= 0) return; // the ritual's hall has no floors to track
        long floors = Progression.profile(p).counter(Counters.FLOORS_CLEARED);
        var run = dev.awaken.gates.Gates.activeRun(p);
        if (run.isPresent() && String.valueOf(run.get().gateId()).equals(s.prologueGate)) {
            if (run.get().area() == 0) s.cryptFloor = Math.max(s.cryptFloor, run.get().floor());
            if (s.cryptCounterSeen >= 0 && floors > s.cryptCounterSeen) s.cryptCleared += (int) (floors - s.cryptCounterSeen);
        }
        s.cryptCounterSeen = floors;
    }

    /** The next Bell Crypt floor to fight, or 0 when the story's gate is done (cleared, released) or unknown. */
    static int cryptNextFloor(ServerPlayer p, StoryProgress s) {
        if (s.prologueGate.isEmpty() || Ritual.storyGate(p) >= 0) return 0; // the ritual's hall is never a floor to go back to
        var view = GateHooks.get(p.level().getServer().overworld(), s.prologueGate);
        if (view.isEmpty()) return 0;
        String state = view.get().state();
        if (!(state.equals("BUILDING") || state.equals("READY") || state.equals("ACTIVE") || state.equals("OPEN"))) return 0;
        return Math.max(1, Math.min(view.get().floors(), Math.max(s.cryptFloor, s.cryptCleared + 1)));
    }

    static void skip(ServerPlayer p) {
        StoryProgress s = progress(p);
        if (s.prologue == StoryProgress.Prologue.ACCEPTING) {
            onAnswer(p, ACCEPT_ACTION, true); // also brings the player back from the Stillpoint
            liftTheBlack(p); // onAnswer bails out for an already awakened player
            return;
        }
        boolean inRitual = s.prologue == StoryProgress.Prologue.RITUAL || Ritual.inRitualGate(p);
        Progression.awaken(p);
        Progression.topUpToLevel(p, WAKE_LEVEL, Progression.XpSource.STORY);
        s.prologue = StoryProgress.Prologue.DONE;
        sync(p);
        if (inRitual) {
            // skipped from inside the sealed hall: out through the gate's mouth, the scene ends here
            dev.awaken.gates.Gates.forceExtract(p, true);
            AwakenNet.send(p, StoryPayloads.RitualScene.of(StoryPayloads.RitualScene.NONE));
        }
    }

    /**
     * The black (Euan, 2026-10-24): from the death until the answer you can't see past arm's length. An infinite
     * Blindness is vanilla's 5-block fog with no fade-out; the Lens Void look sits on top of it.
     */
    static void holdTheBlack(ServerPlayer p) {
        MobEffectInstance now = p.getEffect(MobEffects.BLINDNESS);
        if (now != null && now.isInfiniteDuration()) return;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, MobEffectInstance.INFINITE_DURATION, 0, false, false));
    }

    /** Lifts only the prologue's own black: a timed Blindness from a mob or power runs its course. */
    static void liftTheBlack(ServerPlayer p) {
        MobEffectInstance now = p.getEffect(MobEffects.BLINDNESS);
        if (now != null && now.isInfiniteDuration()) p.removeEffect(MobEffects.BLINDNESS);
    }

    /** On login: still at the question, still in the dark; or back in the ritual where it is now. */
    static void onLogin(ServerPlayer p) {
        if (progress(p).prologue == StoryProgress.Prologue.ACCEPTING && !Progression.profile(p).awakened()) {
            holdTheBlack(p);
            AwakenNet.send(p, StoryPayloads.RitualScene.of(StoryPayloads.RitualScene.VOID));
            return;
        }
        if (!Progression.profile(p).awakened()) Ritual.onLogin(p);
    }

    static boolean storyOn() {
        try {
            return AwakenConfig.STORY.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
