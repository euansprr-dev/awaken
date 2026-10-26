package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.signal.Signal;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Troubles;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Deep Mine (SPEC §28.9, PLAN 6.3), a mining town's trouble on W4's framework. Caused by the mine itself: the
 * miners working the second level's face break through into the cavern behind it, a Hollows' nest. The call: the mine
 * bell ringing the alarm at the shaft head, the trapped miners' families at the mouth with lanterns, looking down. The
 * deed: go down the town's mine (the shaft's ladder or its rope, the first level's gallery, the winze), clear the nest,
 * and bring the miners up: they follow you back along the second level while the roof comes down behind you (W2's
 * cave-in, its cracks and dust and falling stone), then climb after you. The lasting change: the mine works again and
 * its store sells ore and lode maps; or, if nobody came, the town seals the shaft for good (the miners get out through
 * old workings two days on, hurt, never dead).
 */
public final class DeepMine implements Plot {
    static final int BELL = 0, FREED = 2, HOME = Taken.HOME;
    static final int MINERS = 3;
    /** How near the cavern a player wakes the nest; how far the nest's creatures may be and still guard the miners. */
    static final int WAKE = 28, GUARD = 24;

    // ---- the mine's places (world) ----------------------------------------------------------------------------------

    static BlockPos head(VillagePlan p) {
        VillagePlan.Mine m = p.mine;
        return new BlockPos(p.ox + m.x(), p.baseY + m.lift() + 1, p.oz + m.z());
    }

    /** Where families stand: round the mouth on the town's side. */
    static Vec3 mouth(VillagePlan p, double a, double r) {
        VillagePlan.Mine m = p.mine;
        int back = (m.side() + 2) & 3;
        double cx = p.ox + m.x() + 0.5 + STEP_X[back] * 3.5, cz = p.oz + m.z() + 0.5 + STEP_Z[back] * 3.5;
        return new Vec3(cx + Math.cos(a) * r, p.baseY + m.lift() + 1, cz + Math.sin(a) * r);
    }

    static BlockPos cavern(VillagePlan p) {
        VillagePlan.Mine m = p.mine;
        return new BlockPos(p.ox + m.cavernX(), p.baseY + m.cavernY(), p.oz + m.cavernZ());
    }

    static BlockPos face(VillagePlan p) {
        VillagePlan.Mine m = p.mine;
        int[] f = m.along2(m.reach2());
        return new BlockPos(p.ox + f[0], p.baseY + m.level2() + 1, p.oz + f[1]);
    }

    static BlockPos winze(VillagePlan p) {
        VillagePlan.Mine m = p.mine;
        int[] w = m.along(m.winze());
        return new BlockPos(p.ox + w[0], p.baseY + m.level1() + 1, p.oz + w[1]);
    }

    static final int[] STEP_X = {0, 1, 0, -1}, STEP_Z = {-1, 0, 1, 0};

    // ---- the cause ----------------------------------------------------------------------------------------------------

    /**
     * The miners break through into the nest behind the face: once in a town's life, after it has been worked three play-days
     * since it was met, one working morning in four.
     */
    static @Nullable String cause(Ctx c, VillageService.Live l) {
        if (l.plan().mine == null) return null;
        TroubleState.Memory mem = c.st().memory(l.key());
        if (mem.data.getOrDefault("mine_broke", 0L) != 0 || mem.data.getOrDefault("mine_sealed", 0L) != 0) return null;
        long since = mem.data.computeIfAbsent("mine_since", k -> c.day());
        if (c.day() - since < 3 || c.tod() < 1500 || c.tod() > 5000) return null;
        if (Math.floorMod(Taken.mix(l.plan().seed, c.day(), 41), 4L) != 0) return null;
        return "nest";
    }

    /** Whether a mining town's store has no ore: its mine sealed, or the Deep Mine on now. */
    public static boolean shut(MinecraftServer server, String village) {
        TroubleState st = TroubleState.get(server);
        if (st.memory(village).data.getOrDefault("mine_sealed", 0L) != 0) return true;
        Trouble t = st.of(village);
        return t != null && t.kind.equals(TroubleKind.DEEP_MINE.id);
    }

    // ---- the plot -----------------------------------------------------------------------------------------------------

    @Override
    public boolean begin(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null || l.plan().mine == null) return false;
        // who's below: up to three of its miners, each with someone at the mouth waiting
        List<Integer> miners = new ArrayList<>();
        for (int i = 0; i < l.plan().people.size() && miners.size() < MINERS; i++) {
            if (!l.settlement().job(i).equals("miner") || l.settlement().isAway(i) || c.st().memory(l.key()).left.contains(i)) continue;
            miners.add(i);
        }
        if (miners.isEmpty()) return false;
        t.person = miners.getFirst();
        t.kin = -1;
        for (int m : TroubleDirector.household(l, t.person)) if (m != t.person && l.settlement().age(m) >= 1 && !l.settlement().isAway(m)) t.kin = m;
        if (t.kin < 0) t.kin = TroubleDirector.crowd(l, 1, new java.util.HashSet<>(miners), "assayer", "elder").stream().findFirst().orElse(-1);
        if (t.kin < 0) return false;
        for (int k = 0; k < miners.size(); k++) {
            t.put("miner" + k, miners.get(k) + 1);
            l.settlement().away(miners.get(k), true);
        }
        t.put("miners", miners.size());
        t.place = head(l.plan()).asLong();
        t.placeName = "the mine";
        t.due = c.day() + 2;
        c.st().memory(l.key()).data.put("mine_broke", c.day());
        l.settlement().alarm(head(l.plan()), 36);
        t.put("rung", c.now());
        String who = TroubleText.given(l.settlement().personName(t.person));
        Villages.history(c.level().getServer(), l.key(), "The miners broke into a Hollows' nest on the second level. " + who
                + (miners.size() > 1 ? " and " + (miners.size() - 1) + " more are" : " is") + " trapped below.");
        Villages.addMood(c.level().getServer(), l.key(), -10);
        return true;
    }

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null || l.plan().mine == null) return;
        VillagePlan p = l.plan();
        breach(c, t, p);
        if (t.stage < HOME) mouthScene(c, t, l);
        nest(c, t, l);
        if (t.stage == FREED) collapse(c, t, l);
        // the miners come up: at the mouth they go to their families
        if (t.stage == FREED) {
            int home = 0, n = (int) t.get("miners", 0);
            for (int k = 0; k < n; k++) {
                Rescuee r = miner(c, t, k);
                if (r == null) {
                    home++;
                    continue;
                }
                if (r.mode() == Rescuee.HOME || r.mode() == Rescuee.EMBRACE) {
                    home++;
                    continue;
                }
                if (r.getY() >= p.baseY + p.mine.lift() - 1 && r.position().distanceToSqr(Vec3.atBottomCenterOf(head(p))) < 7 * 7) {
                    if (r.carried()) r.setDown(false);
                    r.home = mouth(p, -0.6 + k * 0.6, 2.4);
                    r.mode(Rescuee.HOME);
                    r.say(TroubleText.line(c.level(), "village.trouble.deep_mine.up", c.now() + k));
                }
            }
            if (home >= n && n > 0) {
                t.stage = HOME;
                t.since = c.now();
                c.st().setDirty();
            }
        }
        if (t.stage == HOME && c.now() - t.since > 60 && t.get("paid", 0) == 0) {
            t.put("paid", 1);
            done(c, t, l);
        }
        if (t.stage == HOME && c.now() - t.since > 20 * 14) TroubleDirector.end(c, t);
    }

    /** The wall between the face and the nest gives once its ground is loaded (where the miners broke through). */
    private void breach(Ctx c, Trouble t, VillagePlan p) {
        if (t.get("wall", 0) != 0) return;
        BlockPos f = face(p);
        if (!c.level().isLoaded(f)) return;
        VillagePlan.Mine m = p.mine;
        int sx = STEP_X[m.side2()], sz = STEP_Z[m.side2()], cx = STEP_X[(m.side2() + 1) & 3], cz = STEP_Z[(m.side2() + 1) & 3];
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int d = 1; d <= 4; d++)
            for (int w = -1; w <= 1; w++)
                for (int h = 0; h <= 2; h++) {
                    q.set(f.getX() + sx * d + cx * w, f.getY() + h, f.getZ() + sz * d + cz * w);
                    BlockState s = c.level().getBlockState(q);
                    // the ore of the face stays (the lode the mine was driven to)
                    if (s.isAir() || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) continue;
                    c.level().setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
        // rubble where it broke
        for (int w = -1; w <= 1; w += 2) {
            q.set(f.getX() + sx + cx * w, f.getY(), f.getZ() + sz + cz * w);
            if (c.level().getBlockState(q).isAir()) c.level().setBlock(q, Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_ALL);
        }
        t.put("wall", 1);
        c.st().setDirty();
    }

    /** At the mouth: the families with lanterns looking down the shaft, the bell again every hundred seconds till someone helps. */
    private void mouthScene(Ctx c, Trouble t, VillageService.Live l) {
        VillagePlan p = l.plan();
        Vec3 down = Vec3.atBottomCenterOf(head(p));
        List<Integer> waiting = new ArrayList<>();
        int n = (int) t.get("miners", 0);
        for (int k = 0; k < n; k++) {
            int rec = (int) t.get("miner" + k, 0) - 1;
            if (rec < 0) continue;
            for (int m : TroubleDirector.household(l, rec)) if (m != rec && !waiting.contains(m) && !l.settlement().isAway(m) && l.settlement().age(m) >= 1) waiting.add(m);
        }
        if (!waiting.contains(t.kin)) waiting.addFirst(t.kin);
        for (int i = 0; i < Math.min(5, waiting.size()); i++) {
            Vec3 at = mouth(p, -1.0 + i * 0.5, 2.6 + (i % 2) * 0.6);
            TroubleDirector.stage(l, waiting.get(i), at, TroubleDirector.yaw(at, down), i == 0 && t.helpers.isEmpty() ? "weep" : "look_around", i % 2 == 0 ? "lantern" : "");
        }
        if (t.helpers.isEmpty() && c.now() - t.get("rung", 0) > 20 * 100 && t.get("rings", 0) < 3) {
            l.settlement().alarm(head(p), 24);
            t.put("rung", c.now());
            t.put("rings", t.get("rings", 0) + 1);
        }
        if (TroubleDirector.every(c, 140, 7)) TroubleDirector.say(c.level(), l, t.kin, TroubleDirector.fill(c, t,
                TroubleText.line(c.level(), "village.trouble.deep_mine.mouth", c.now() / 140)), 80);
    }

    /** A player coming near the nest wakes it: the Hollows rise from it, and the miners are found in its far corner. */
    private void nest(Ctx c, Trouble t, VillageService.Live l) {
        VillagePlan p = l.plan();
        BlockPos cav = cavern(p);
        if (!c.level().isLoaded(cav)) return;
        if (t.get("woke", 0) == 0) {
            boolean near = false;
            for (ServerPlayer pl : c.level().players()) near |= !pl.isSpectator() && pl.distanceToSqr(Vec3.atCenterOf(cav)) < WAKE * WAKE;
            if (!near) return;
            t.put("woke", 1);
            wake(c, t, l, cav);
        }
        if (t.stage == BELL && t.get("woke", 0) != 0 && guards(c, t) == 0) {
            t.stage = FREED;
            t.since = c.now();
            c.st().setDirty();
            for (UUID h : t.helpers) {
                ServerPlayer pl = c.level().getServer().getPlayerList().getPlayer(h);
                if (pl != null) {
                    pl.sendOverlayMessage(Component.literal(TroubleDirector.fill(c, t, "The nest is quiet · bring the miners up")));
                    dev.awaken.living.api.Quests.journalChanged(pl);
                }
            }
        }
    }

    private void wake(Ctx c, Trouble t, VillageService.Live l, BlockPos cav) {
        ServerLevel level = c.level();
        VillagePlan.Mine m = l.plan().mine;
        // the miners, hurt, at the cavern's far side
        int n = (int) t.get("miners", 0);
        int fx = STEP_X[m.side2()], fz = STEP_Z[m.side2()], cx = STEP_X[(m.side2() + 1) & 3], cz = STEP_Z[(m.side2() + 1) & 3];
        for (int k = 0; k < n; k++) {
            int rec = (int) t.get("miner" + k, 0) - 1;
            Rescuee r = TroubleContent.RESCUEE.get().create(level, EntitySpawnReason.EVENT);
            if (r == null || rec < 0) continue;
            var look = l.settlement().look(rec);
            r.become((int) t.id, t.village, rec, l.settlement().personName(rec), look.texture(), look.slim(), false);
            BlockPos at = floor(level, cav.offset(fx * 4 + cx * (k - 1) * 2, 2, fz * 4 + cz * (k - 1) * 2));
            r.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0);
            r.mode(Rescuee.LOST);
            level.addFreshEntity(r);
            t.put("m" + k + "_hi", r.getUUID().getMostSignificantBits());
            t.put("m" + k + "_lo", r.getUUID().getLeastSignificantBits());
        }
        // the nest's creatures between the breach and the miners
        var pool = dev.awaken.mobs.def.Bestiary.of(dev.awaken.mobs.def.Theme.HOLLOWS, dev.awaken.mobs.def.Tier.NORMAL);
        int lv = dev.awaken.living.api.Regions.creatureLevel(level, cav, dev.awaken.living.api.Regions.Role.HUNTER);
        int count = 4 + Math.floorMod((int) t.id, 2);
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            var def = pool.get(Math.floorMod((int) t.id + i, pool.size()));
            BlockPos at = floor(level, cav.offset(level.getRandom().nextInt(7) - 3 - fx * 2, 2, level.getRandom().nextInt(7) - 3 - fz * 2));
            var role = new dev.awaken.core.api.MobRole(dev.awaken.core.api.MobRole.Role.NORMAL, lv, def.theme.id, java.util.Optional.empty(), "");
            Entity e = dev.awaken.mobs.Mobs.spawn(level, def, Vec3.atBottomCenterOf(at), lv, role, java.util.Set.of());
            if (e == null) continue;
            e.addTag(TAG);
            e.addTag(TAG + "_" + t.id);
            if (e instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
        }
        level.playSound(null, cav, dev.awaken.delve.DelveContent.CAVE_CREAK.get(), SoundSource.HOSTILE, 1.6f, 0.7f);
        c.st().setDirty();
        Awaken.LOG.info("[trouble] the Deep Mine's nest woke at {}", cav.toShortString());
    }

    static final String TAG = "awaken_deep_mine";

    /** The nest's creatures still alive within reach of the cavern. */
    private int guards(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return 0;
        BlockPos cav = cavern(l.plan());
        int n = 0;
        for (Entity e : c.level().getEntities((Entity) null, new net.minecraft.world.phys.AABB(cav).inflate(GUARD), e -> e.entityTags().contains(TAG + "_" + t.id)))
            if (e.isAlive()) n++;
        return n;
    }

    /** The first standing room at or below a point (a cavern's floor), within eight. */
    static BlockPos floor(ServerLevel level, BlockPos at) {
        BlockPos.MutableBlockPos q = at.mutable();
        for (int k = 0; k < 8; k++) {
            if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isSolid()) return q.immutable();
            q.move(0, -1, 0);
        }
        return at;
    }

    @Nullable Rescuee miner(Ctx c, Trouble t, int k) {
        long hi = t.get("m" + k + "_hi", 0), lo = t.get("m" + k + "_lo", 0);
        if (hi == 0 && lo == 0) return null;
        Entity e = c.level().getEntity(new UUID(hi, lo));
        return e instanceof Rescuee r && r.isAlive() ? r : null;
    }

    /**
     * Back along the second level with the miners: the roof comes down behind you, from the face toward the winze, a
     * point every half second (W2's cave-in: its cracks and dust a moment ahead, then the stone, which lands as rubble and
     * hurts whoever's under it). It stops short of the winze.
     */
    private void collapse(Ctx c, Trouble t, VillageService.Live l) {
        VillagePlan p = l.plan();
        VillagePlan.Mine m = p.mine;
        BlockPos f = face(p);
        long front = t.get("fall", -1);
        if (front < 0) {
            // it starts once someone is on their way back with a miner behind them, eight or more from the face
            boolean leaving = false;
            for (int k = 0; k < t.get("miners", 0); k++) {
                Rescuee r = miner(c, t, (int) k);
                if (r == null || r.follow == null) continue;
                Entity lead = c.level().getEntity(r.follow);
                leaving |= lead != null && lead.position().distanceTo(Vec3.atCenterOf(f)) > 8 && Math.abs(lead.getY() - f.getY()) < 4;
            }
            if (!leaving) return;
            t.put("fall", m.reach2());
            t.put("fall_at", c.now());
            c.level().playSound(null, f, dev.awaken.delve.DelveContent.CAVE_CREAK.get(), SoundSource.BLOCKS, 2.0f, 0.75f);
            for (UUID h : t.helpers) {
                ServerPlayer pl = c.level().getServer().getPlayerList().getPlayer(h);
                if (pl != null) pl.sendOverlayMessage(Component.literal("The roof is giving way behind you · run").withColor(0xE8C9A0));
            }
            return;
        }
        if (front <= 3) return;
        // one point a second (the tick runs each second), warned a second ahead
        int d = (int) front;
        int y = p.baseY + m.level2();
        int sx = STEP_X[m.side2()], sz = STEP_Z[m.side2()], cx = STEP_X[(m.side2() + 1) & 3], cz = STEP_Z[(m.side2() + 1) & 3];
        int[] at = m.along2(d);
        long[] roof = new long[6];
        int ri = 0;
        for (int w = -1; w <= 1; w++)
            for (int h = 5; h <= 6; h++) roof[ri++] = BlockPos.asLong(p.ox + at[0] + cx * w, y + h, p.oz + at[1] + cz * w);
        int[] next = m.along2(d - 1);
        long[] warn = new long[3];
        for (int w = -1; w <= 1; w++) warn[w + 1] = BlockPos.asLong(p.ox + next[0] + cx * w, y + 5, p.oz + next[1] + cz * w);
        BlockPos mid = BlockPos.of(warn[1]);
        for (ServerPlayer pl : c.level().players())
            if (pl.distanceToSqr(Vec3.atCenterOf(mid)) < 48 * 48)
                dev.awaken.core.AwakenNet.send(pl, new dev.awaken.delve.DelveNet.CaveWarn(mid.asLong(), warn, 20, 6));
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (long r : roof) {
            q.set(r);
            BlockState s = c.level().getBlockState(q);
            if (s.isAir() || !s.getFluidState().isEmpty()) s = Blocks.COBBLESTONE.defaultBlockState();
            FallingBlockEntity fb = FallingBlockEntity.fall(c.level(), q.immutable(), s);
            fb.setHurtsEntities(1.5f, 6);
        }
        c.level().playSound(null, BlockPos.of(roof[2]), dev.awaken.delve.DelveContent.CAVE_FALL.get(), SoundSource.BLOCKS, 1.6f, 0.8f + 0.2f * c.level().getRandom().nextFloat());
        t.put("fall", front - 1);
    }

    private void done(Ctx c, Trouble t, VillageService.Live l) {
        List<String> names = new ArrayList<>();
        int n = (int) t.get("miners", 0);
        for (int k = 0; k < n; k++) {
            int rec = (int) t.get("miner" + k, 0) - 1;
            if (rec >= 0) names.add(TroubleText.given(l.settlement().personName(rec)));
        }
        String who = String.join(", ", names);
        List<ServerPlayer> paid = TroubleDirector.pay(c, t, Vec3.atCenterOf(head(l.plan())), 48, 16, 20, 0.5, "the miners are up",
                p -> Keepsake.make("The Miners' Lamp", "A brass lamp, its glass smoked from the deep.", "From the miners of " + l.plan().name
                        + ", for bringing " + who + " up from the nest."));
        Villages.history(c.level().getServer(), l.key(), TroubleDirector.names(paid) + " went down and brought " + who + " up from the nest. The mine's working again.");
        Villages.addMood(c.level().getServer(), l.key(), 14);
        for (ServerPlayer p : paid) {
            for (int k = 0; k < n; k++) {
                int rec = (int) t.get("miner" + k, 0) - 1;
                if (rec >= 0) c.st().rescued(p.getUUID(), new Troubles.Rescued(l.key(), l.plan().name, rec, l.settlement().personName(rec), "miner",
                        l.settlement().age(rec), c.day(), "deep_mine", false));
            }
            l.settlement().acquaint(p, 30, 100);
        }
        TroubleNet.ring(c.level(), Vec3.atCenterOf(head(l.plan())), 0xF6D58A, 14);
    }

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null || t.stage >= HOME) return false;
        boolean family = rec == t.kin;
        for (int k = 0; k < t.get("miners", 0) && !family; k++) family = TroubleDirector.household(l, (int) t.get("miner" + k, 0) - 1).contains(rec);
        if (!family && !l.settlement().job(rec).equals("assayer") && rec != Runner.of(t)) return false;
        if (t.helped(p.getUUID())) {
            l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.deep_mine.again", c.now() / 100)), 100);
            return true;
        }
        l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.deep_mine.ask", c.now() / 100 + p.getId())), 140);
        TroubleDirector.card(p, t, "The Deep Mine · " + l.plan().name, TroubleDirector.fill(c, t,
                "The miners broke into a Hollows' nest on the second level. {person} and the others are trapped down there. Go down the shaft, clear the nest and bring them up."));
        return true;
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l != null) l.settlement().say(p, t.kin, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.deep_mine.thanks", c.now())), 120);
        Signal.toast(p, Component.literal(TroubleDirector.fill(c, t, "Down the shaft at {village}'s mine head")), dev.awaken.core.api.Notices.Tier.IMPORTANT, "village_trouble");
    }

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        return t.stage >= FREED ? TroubleDirector.fill(c, t, "Bring the miners up {village}'s shaft.")
                : TroubleDirector.fill(c, t, "Go down {village}'s mine to the second level and find the miners.");
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l == null) return null;
        VillagePlan pl = l.plan();
        if (t.stage >= FREED) return head(pl);
        // down the shaft, along to the winze, along to the face
        double y = p.getY();
        if (y > pl.baseY + pl.mine.level1() + 6) return head(pl);
        if (y > pl.baseY + pl.mine.level2() + 6) return winze(pl);
        return face(pl);
    }

    @Override
    public boolean holds(Ctx c, Trouble t) {
        if (c.day() > t.due + 1) return false;
        if (t.stage == HOME) return true;
        for (int k = 0; k < t.get("miners", 0); k++) {
            Rescuee r = miner(c, t, k);
            if (r != null && (r.mode() == Rescuee.FOLLOW || r.mode() == Rescuee.CARRIED || r.mode() == Rescuee.HOME)) return true;
        }
        return false;
    }

    @Override
    public void touched(Ctx c, Trouble t, ServerPlayer p, Rescuee r) {
        if (r.mode() != Rescuee.LOST && r.mode() != Rescuee.WAIT && r.mode() != Rescuee.FOLLOW) return;
        if (guards(c, t) > 0) {
            r.say(TroubleText.line(c.level(), "village.trouble.deep_mine.guarded", c.now()));
            return;
        }
        if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
        // they walk out behind you (there are three, and ladders to climb): sneak to have one hold on to your back instead
        if (p.isShiftKeyDown() && r.climbOn(p)) {
            p.sendOverlayMessage(Component.literal(TroubleDirector.fill(c, t, "They hold on to your back · sneak a moment to set them down")));
        } else {
            r.follow = p.getUUID();
            r.mode(Rescuee.FOLLOW);
            r.say(TroubleText.line(c.level(), "village.trouble.deep_mine.found", c.now() + r.getId()));
        }
        if (t.stage == BELL) {
            t.stage = FREED;
            t.since = c.now();
        }
        c.st().setDirty();
        dev.awaken.living.api.Quests.journalChanged(p);
    }

    /** Nobody came: the town seals the shaft for good; the miners get out through the old workings two days on, hurt. */
    @Override
    public void miss(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        TroubleState.Memory mem = c.st().memory(t.village);
        mem.data.put("mine_sealed", c.day());
        c.st().setDirty();
        if (l != null && l.plan().mine != null) seal(c.level(), l.plan());
        Villages.history(c.level().getServer(), t.village, "Nobody came down. The miners got out through the old workings two days on, hurt, and the town sealed the mine for good.");
        Villages.addMood(c.level().getServer(), t.village, -15);
        Villages.addProsperity(c.level().getServer(), t.village, -10);
    }

    /** Planks across the shaft's mouth, nailed down for good. */
    static void seal(ServerLevel level, VillagePlan p) {
        VillagePlan.Mine m = p.mine;
        BlockPos h = head(p).below();
        if (!level.isLoaded(h)) return;
        String wood = switch (p.family) {
            case dev.awaken.city.VillageStyles.FROST, dev.awaken.city.VillageStyles.HIGHLAND -> "spruce";
            case dev.awaken.city.VillageStyles.DESERT, dev.awaken.city.VillageStyles.SAVANNA -> "acacia";
            default -> "oak";
        };
        BlockState planks = dev.awaken.village.plan.VillageBlocks.of("minecraft:" + wood + "_planks");
        for (int dz = -1; dz <= 1; dz++)
            for (int dx = -1; dx <= 1; dx++) {
                BlockPos q = h.offset(dx, 0, dz);
                level.setBlock(q, planks, Block.UPDATE_ALL);
            }
        Awaken.LOG.info("[trouble] {}'s mine sealed at {}", p.name, h.toShortString());
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        int n = (int) t.get("miners", 0);
        for (int k = 0; k < n; k++) {
            Rescuee r = miner(c, t, k);
            if (r != null) r.discard();
            int rec = (int) t.get("miner" + k, 0) - 1;
            if (l != null && rec >= 0) l.settlement().away(rec, false);
        }
        for (Entity e : c.level().getAllEntities()) if (e.entityTags().contains(TAG + "_" + t.id) && c.level().getNearestPlayer(e, 24) == null) e.discard();
        if (l == null) return;
        Runner.release(c, t, l);
        TroubleDirector.release(l, t.kin);
        for (int k = 0; k < n; k++) {
            int rec = (int) t.get("miner" + k, 0) - 1;
            if (rec >= 0) for (int m : TroubleDirector.household(l, rec)) TroubleDirector.release(l, m);
        }
    }
}
