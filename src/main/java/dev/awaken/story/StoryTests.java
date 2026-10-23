package dev.awaken.story;

import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GateState;
import dev.awaken.gates.run.Runs;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/** GameTests for story logic. */
final class StoryTests {
    private StoryTests() {}

    static void register() {
        AwakenTests.register("story_triggers", 20, h -> {
            Profile p = new Profile();
            h.assertFalse(Beats.triggered(p, "counter:awaken:gates_cleared>=1"), "no gates yet");
            h.assertTrue(Beats.triggered(p, "rank:E"), "rank E always");
            h.assertFalse(Beats.triggered(p, "rank:D"), "not D yet");
            h.assertFalse(Beats.triggered(p, "trigger:prologue_death"), "code-only triggers never fire on their own");
            h.assertFalse(Beats.triggered(p, "counter:garbage"), "malformed triggers are false, not crashes");
            h.succeed();
        });
        AwakenTests.register("story_progress_codec", 20, h -> {
            StoryProgress s = new StoryProgress();
            s.prologue = StoryProgress.Prologue.ACCEPTING;
            s.prologueGate = "g42";
            s.returnX = 10.5;
            s.seen.add("revelation:rank_d");
            s.sovereignsDefeated.add("awaken:sovereign_ash");
            var tag = StoryProgress.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
            StoryProgress t = StoryProgress.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(t.prologue == StoryProgress.Prologue.ACCEPTING && t.prologueGate.equals("g42") && t.returnX == 10.5, "fields");
            h.assertTrue(t.seen.contains("revelation:rank_d") && t.sovereignsDefeated.contains("awaken:sovereign_ash"), "sets");
            h.succeed();
        });

        // The whole prologue against the real gates module: the Bell Crypt opens, the killing blow inside becomes the
        // question, Accept brings you back out at the gate. (The GameTest server has no datapack dimensions: the test level
        // stands in for the gate world for one tick, and there is no Stillpoint to visit.)
        AwakenTests.registerIsolated("story_prologue_through_the_bell_crypt", 600, h -> {
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // mock players start in creative
            p.connection.markClientLoaded(); // otherwise a fresh player cannot be hurt
            StoryProgress s = Prologue.progress(p);
            GateRecord[] gate = new GateRecord[1];
            h.startSequence()
                    .thenExecute(() -> {
                        GateService.useTestLevel(level);
                        try {
                            s.prologue = StoryProgress.Prologue.GLITCHED;
                            Prologue.openFirstGate(p, s);
                            h.assertTrue(s.prologue == StoryProgress.Prologue.GATE_OPEN && !s.prologueGate.isEmpty(), "the first gate opened, prologue " + s.prologue);
                            var view = GateHooks.get(level, s.prologueGate);
                            h.assertTrue(view.isPresent() && view.get().theme().equals("awaken:bell_crypt") && view.get().rank() == 0
                                    && view.get().floors() == 3 && view.get().breaksOnPlayDay() == Long.MAX_VALUE,
                                    "a scripted E-rank Bell Crypt of three floors that never breaks");
                            GateRecord g = GateRegistry.get(server).gate(Integer.parseInt(s.prologueGate));
                            gate[0] = g;
                            g.interiorReady = true; // the stand-in level has no gate chunks to wait for
                            h.assertTrue(g.transition(GateState.READY), "ready");
                            h.assertTrue(Runs.enter(p, g), "an unawakened player can walk in");
                            h.assertTrue(GateHooks.inGate(p), "inside the gate");
                            boolean hurt = p.hurtServer((ServerLevel) p.level(), p.damageSources().generic(), 1000F);
                            h.assertTrue(p.isAlive() && s.prologue == StoryProgress.Prologue.ACCEPTING, "the killing blow becomes the question (hurt="
                                    + hurt + " alive=" + p.isAlive() + " hp=" + p.getHealth() + " prologue=" + s.prologue + " awakened=" + Progression.profile(p).awakened()
                                    + " invulnerable=" + p.isInvulnerableTo((ServerLevel) p.level(), p.damageSources().generic()) + " mode=" + p.gameMode.getGameModeForPlayer() + ")");
                        } finally {
                            GateService.useTestLevel(null);
                        }
                    })
                    .thenWaitUntil(() -> h.assertFalse(Runs.run(p).active(), "out of the gate, the first run ends quietly"))
                    .thenExecute(() -> {
                        Prologue.onAnswer(p, Prologue.ACCEPT_ACTION, false);
                        h.assertTrue(Progression.profile(p).awakened() && s.prologue == StoryProgress.Prologue.DONE, "accepted anyway");
                        h.assertTrue(Progression.profile(p).level() >= Prologue.WAKE_LEVEL, "you wake at level 2 or more");
                        GateRecord g = gate[0];
                        h.assertTrue(p.level().dimension() == Level.OVERWORLD && p.blockPosition().closerThan(g.mouth(), 3),
                                "back at the gate's mouth: " + p.blockPosition() + " vs " + g.mouth());
                        // the gate waits, and the Now tracker says where you left off
                        h.assertTrue(Prologue.cryptNextFloor(p, s) == 1, "next is floor 1, got " + Prologue.cryptNextFloor(p, s));
                        var now = dev.awaken.core.api.NowHooks.collect(p, 8);
                        h.assertTrue(now.stream().anyMatch(x -> x.id().equals("story:bell_crypt") && x.text().getString().equals("Bell Crypt · floor 1")
                                && x.where().isPresent()), "Now: Bell Crypt · floor 1, got " + now);
                        GateService.release(server, g);
                        h.assertTrue(Prologue.cryptNextFloor(p, s) == 0, "a released gate is no longer suggested");
                    })
                    .thenSucceed();
        });

        // Accept pays the prologue's bank and never leaves you below level 2; both keys accept.
        AwakenTests.register("story_accept_pays_the_bank", 60, h -> {
            ServerPlayer small = h.makeMockServerPlayerInLevel();
            ServerPlayer big = h.makeMockServerPlayerInLevel();
            for (ServerPlayer p : List.of(small, big)) {
                StoryProgress s = Prologue.progress(p);
                s.prologue = StoryProgress.Prologue.ACCEPTING;
                s.returnX = p.getX();
                s.returnY = p.getY();
                s.returnZ = p.getZ();
            }
            Progression.grantXp(small, 40, Progression.XpSource.GATE_KILL); // one prologue kill
            Progression.grantXp(big, 700, Progression.XpSource.GATE_KILL);  // a floor and more
            h.assertTrue(Progression.profile(small).bankedXp() > 0 && !Progression.profile(small).awakened(), "banked, not paid yet");
            Prologue.onAnswer(small, Prologue.ACCEPT_ACTION, true);
            Prologue.onAnswer(big, Prologue.ACCEPT_ACTION, false);
            Profile a = Progression.profile(small), b = Progression.profile(big);
            h.assertTrue(a.awakened() && b.awakened(), "Y and N both accept");
            h.assertTrue(a.level() == 2 && a.bankedXp() == 0, "a small bank is topped up to level 2, got " + a.level());
            h.assertTrue(b.level() >= 3 && b.bankedXp() == 0, "a big bank lands higher, got " + b.level());
            h.assertTrue(a.truthsOffered().isEmpty(), "no Truths at minute 6 any more");
            h.succeed();
        });

        // A Sovereign is set free by a fight, not by a command or the void.
        AwakenTests.register("story_sovereign_needs_a_fight", 40, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            StoryProgress s = Prologue.progress(p);
            String roots = "awaken:sovereign_roots";
            var removed = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            var fought = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            h.assertTrue(removed != null && fought != null, "stand-ins");
            for (var z : List.of(removed, fought)) {
                z.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
                z.setData(dev.awaken.core.api.CoreApi.MOB_ROLE.get(), new MobRole(MobRole.Role.BOSS, 10, "", Optional.of(roots), ""));
                level.addFreshEntity(z);
            }
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(removed, level.damageSources().genericKill()));
            h.assertFalse(s.sovereignsDefeated.contains(roots), "removed by a command: not set free");
            fought.setLastHurtByPlayer(p, 100);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(fought, p.damageSources().playerAttack(p)));
            h.assertTrue(s.sovereignsDefeated.contains(roots), "set free in a fight");
            removed.discard();
            fought.discard();
            h.succeed();
        });

        // New Game+: each Ascension tier makes monsters sturdier and XP richer; allies stay as they are.
        AwakenTests.registerIsolated("story_ascension_scales_the_world", 40, h -> {
            ServerLevel level = h.getLevel();
            StoryWorld w = StoryWorld.get(level.getServer());
            int before = w.ascension;
            Vec3 at = h.absoluteVec(new Vec3(1.5, 1, 1.5));
            try {
                w.ascension = 0;
                LivingEntity calm = Mobs.spawn(level, Bestiary.get("toll_ghoul"), at, 10, new MobRole(MobRole.Role.NORMAL, 10, "awaken:bell_crypt", Optional.empty(), ""), EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
                ServerPlayer a = h.makeMockServerPlayerInLevel();
                Progression.awaken(a);
                double xa = Progression.profile(a).xp();
                Progression.grantXp(a, 10, Progression.XpSource.COMMAND);
                xa = Progression.profile(a).xp() - xa;

                w.ascension = 2;
                LivingEntity hard = Mobs.spawn(level, Bestiary.get("toll_ghoul"), at, 10, new MobRole(MobRole.Role.NORMAL, 10, "awaken:bell_crypt", Optional.empty(), ""), EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
                LivingEntity ally = Mobs.spawn(level, Bestiary.get("toll_ghoul"), at, 10, new MobRole(MobRole.Role.ECHO, 10, "", Optional.empty(), ""), EnumSet.noneOf(dev.awaken.core.Forces.Tag.class));
                ServerPlayer b = h.makeMockServerPlayerInLevel();
                Progression.awaken(b);
                double xb = Progression.profile(b).xp();
                Progression.grantXp(b, 10, Progression.XpSource.COMMAND);
                xb = Progression.profile(b).xp() - xb;

                double hp = hard.getMaxHealth() / calm.getMaxHealth();
                double dmg = hard.getAttributeValue(Attributes.ATTACK_DAMAGE) / calm.getAttributeValue(Attributes.ATTACK_DAMAGE);
                h.assertTrue(Math.abs(hp - 1.6) < 0.02, "Ascension 2 gives +60% health, got x" + hp);
                h.assertTrue(Math.abs(dmg - 1.3) < 0.02, "and +30% damage, got x" + dmg);
                h.assertTrue(Math.abs(ally.getMaxHealth() - calm.getMaxHealth()) < 0.01, "an Echo ally is untouched");
                h.assertTrue(xa > 0 && Math.abs(xb / xa - 1.3) < 0.01, "and +30% XP, got x" + (xb / xa));
                calm.discard();
                hard.discard();
                ally.discard();
            } finally {
                w.ascension = before;
            }
            h.succeed();
        });
    }
}
