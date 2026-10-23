package dev.awaken.story;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.CoreApi;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
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
    private static final int GATE_AFTER_TICKS = 20 * 60 * 2;

    static StoryProgress progress(ServerPlayer p) {
        return p.getData(StoryModule.PROGRESS.get());
    }

    static void sync(ServerPlayer p) {
        p.syncData(StoryModule.PROGRESS.get());
    }

    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        StoryProgress s = progress(p);
        boolean awakened = Progression.profile(p).awakened();
        if (awakened) {
            if (s.prologue != StoryProgress.Prologue.DONE && s.prologue != StoryProgress.Prologue.ACCEPTING) {
                s.prologue = StoryProgress.Prologue.DONE;
                sync(p);
            }
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
                // Waiting for the answer. If the window was missed (relog), ask again every 30 seconds.
                if ((now - s.prologueSince) % 600 < 20) askToAccept(p);
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
            openFirstGate(p, s);
        }
    }

    static void openFirstGate(ServerPlayer p, StoryProgress s) {
        if (p.level().dimension() != Level.OVERWORLD) return;
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        if (look.lengthSqr() < 0.01) look = new Vec3(1, 0, 0);
        BlockPos near = BlockPos.containing(p.position().add(look.scale(55)));
        var id = GateHooks.openScripted(p, "awaken:bell_crypt", 0, near, 3);
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
            MobRole role = target.getData(CoreApi.MOB_ROLE.get());
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
        Vec3 back = gateMouth(p, s).orElse(p.position());
        if (p.level().dimension() == Level.OVERWORLD && !GateHooks.inGate(p)) back = p.position();
        s.returnX = back.x;
        s.returnY = back.y;
        s.returnZ = back.z;
        sync(p);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 30, 4, false, false));
        ServerLevel still = p.level().getServer().getLevel(STILLPOINT);
        if (still != null) {
            still.getChunk(0, 0);
            int y = Math.max(still.getMinY() + 1, still.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
            p.teleportTo(still, 0.5, y, 0.5, Set.<Relative>of(), p.getYRot(), 0, true);
        }
        p.level().getServer().execute(() -> askToAccept(p));
    }

    static java.util.Optional<Vec3> gateMouth(ServerPlayer p, StoryProgress s) {
        if (s.prologueGate.isEmpty()) return java.util.Optional.empty();
        return GateHooks.get(p.level().getServer().overworld(), s.prologueGate).map(g -> Vec3.atBottomCenterOf(g.pos()).add(2, 0, 2));
    }

    static void askToAccept(ServerPlayer p) {
        var r = StoryContent.revelation("prologue_accept");
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION,
                        Component.literal(r == null ? "You have met the conditions." : r.title()))
                .body(Component.literal(r == null ? "Will you accept?" : r.body()))
                .action(ACCEPT_ACTION).priority(10).ttl(20 * 60).build());
    }

    /** Signal answer: both answers are yes. */
    static void onAnswer(ServerPlayer p, String action, boolean accepted) {
        if (!ACCEPT_ACTION.equals(action)) return;
        StoryProgress s = progress(p);
        if (s.prologue != StoryProgress.Prologue.ACCEPTING && Progression.profile(p).awakened()) return;
        Progression.awaken(p);
        s.prologue = StoryProgress.Prologue.DONE;
        s.seen.add("revelation:prologue_accept");
        sync(p);
        ServerLevel overworld = p.level().getServer().overworld();
        p.teleportTo(overworld, s.returnX, s.returnY, s.returnZ, Set.<Relative>of(), p.getYRot(), 0, true);
        p.setHealth(p.getMaxHealth());
        p.removeEffect(MobEffects.BLINDNESS);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(accepted ? "Accepted." : "Accepted anyway."))
                .body(Component.literal("Press K to see who you are now. The gate is still open. Finish what you started."))
                .ttl(20 * 10).priority(9).build());
    }

    static void skip(ServerPlayer p) {
        StoryProgress s = progress(p);
        Progression.awaken(p);
        s.prologue = StoryProgress.Prologue.DONE;
        sync(p);
    }

    static boolean storyOn() {
        try {
            return AwakenConfig.STORY.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
