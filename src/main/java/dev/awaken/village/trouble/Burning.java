package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.citizens.block.CityFireBlock;
import dev.awaken.city.CityPlan;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.Notices;
import dev.awaken.living.camp.Camps;
import dev.awaken.signal.Signal;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Burning (SPEC §28.7, PLAN 6.3): a camp grown to raid (Living Wilds §21.8, tier 4) has a village nearer than any
 * Hearth. A ladder you can read: talk of smoke over the ridge while it grows (the elder's news), its scouts at the treeline
 * the dusk before, and the warning a play-day ahead (the elder asks: "they come tomorrow night"). With warning the
 * village holds: the Watch lines the green, the raiders come out of the dark, fire arrows set roofs alight, and the
 * village forms a bucket line from the well you can join (beat the flames out, kill the raiders). Missed, the houses on
 * the raid's side are charred (real block changes into the village's damage record), the survivors camp by the well, and
 * smoke rises over the roofs for days: the Rebuild follows.
 */
final class Burning implements Plot {
    static final int WARNED = 0, RAID = 1;
    /** A player this near the green when the raid comes is there to hold it. */
    static final int THERE = 90;
    /** Fires: how fast one grows, how much each bucket on the line takes, a beaten flame. */
    static final int GROW = 9, LINE = 7, BEAT = 140, FULL = 1000;

    // ---- the warning: a camp's raid becomes the village's Burning ------------------------------------------------------------

    static boolean coming(ServerLevel level, String village, int camp, String campName, int tier, long raidDay) {
        if (TroubleContent.calm(level)) return false;
        TroubleState st = TroubleState.get(level.getServer());
        if (st.of(village) != null) return false;
        VillageService.Live l = VillageService.live(village);
        BlockPos origin = l != null ? new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz) : null;
        if (origin == null) {
            for (Villages.View x : Villages.all(level)) if (x.key().equals(village)) origin = x.origin();
            if (origin == null) {
                TroubleState.Memory mem = st.peek(village);
                if (mem == null) return false;
            }
        }
        if (origin != null && TroubleDirector.crowded(level, st, origin)) return false;
        Camps.Camp c = Camps.get(level.getServer()).byId(camp);
        long day = PlayDays.current(level.getServer());
        Trouble t = st.add(village, TroubleKind.BURNING, day, level.getGameTime(), raidDay + 1, "camp:" + camp);
        t.put("camp", camp);
        t.put("raid_day", raidDay);
        t.put("tier", tier);
        t.text = campName;
        t.placeName = "the " + campName;
        if (c != null) t.place = c.pos().asLong();
        if (l != null) {
            Plot.Ctx ctx = TroubleDirector.ctx(level, village);
            warn(ctx, t, l);
        }
        st.setDirty();
        Awaken.LOG.info("[trouble] begins: {} (the camp's raid on day {})", t, raidDay);
        return true;
    }

    @Override
    public boolean begin(Ctx c, Trouble t) {
        // started by hand (commands, tests): the nearest camp at tier 4+ within 800, else a camp placed for it
        VillageService.Live l = c.live();
        if (l == null) return false;
        BlockPos o = new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz);
        Camps.Camp best = null;
        double bd = 800;
        for (Camps.Camp camp : Camps.get(c.level().getServer()).all()) {
            if (!camp.resolved() || camp.cleared(c.day())) continue;
            // a riftborn camp raids before a den does (a den's Hollows keep to their hole)
            double d = TroubleDirector.horizontal(o, camp.pos()) + (camp.kind.equals("riftborn") ? 0 : 400);
            if (d < bd) {
                bd = d;
                best = camp;
            }
        }
        if (best == null) return false;
        t.put("camp", best.id);
        t.put("raid_day", c.day() + 1);
        t.put("tier", Math.max(4, best.tier));
        t.text = best.name();
        t.placeName = "the " + best.name();
        t.place = best.pos().asLong();
        t.due = c.day() + 2;
        warn(c, t, l);
        return true;
    }

    /** The warning, a play-day ahead: to everyone within 800 of the village, and into its history. */
    private static void warn(Ctx c, Trouble t, VillageService.Live l) {
        if (t.get("warned", 0) != 0) return;
        t.put("warned", 1);
        Vec3 w = TroubleDirector.well(l);
        for (ServerPlayer p : c.level().players()) {
            if (p.distanceToSqr(w) > 800 * 800) continue;
            Signal.toast(p, Component.literal("Scouts saw the " + t.text + " stirring · it means to raid " + l.plan().name + " tomorrow night"),
                    Notices.Tier.IMPORTANT, "village_raid");
        }
        Villages.history(c.level().getServer(), l.key(), "Scouts saw the " + t.text + " stirring. It means to come for " + l.plan().name + ".");
        Villages.addMood(c.level().getServer(), l.key(), -10);
    }

    // ---- the tick -------------------------------------------------------------------------------------------------------------

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        warn(c, t, l);
        long raidDay = t.get("raid_day", c.day());
        if (t.stage == WARNED) {
            elder(c, t, l);
            if (c.day() == raidDay - 1 && c.tod() >= 12000 && c.tod() < 13400 && t.get("scouts", 0) == 0) scouts(c, t, l);
            if (t.get("scouts", 0) > 0 && c.now() - t.get("scouts", 0) > 20 * 80) dismissScouts(c, t);
            if (c.day() > raidDay || c.day() == raidDay && c.tod() >= 13600) raid(c, t, l);
            return;
        }
        if (t.stage == RAID) fight(c, t, l);
    }

    /** The elder at the well, looking the way the camp is: tells whoever comes, and asks. */
    private void elder(Ctx c, Trouble t, VillageService.Live l) {
        int e = elderOf(l);
        if (e < 0) return;
        t.kin = e;
        Vec3 w = TroubleDirector.well(l);
        BlockPos camp = BlockPos.of(t.place);
        double toward = Math.atan2(camp.getZ() - w.z, camp.getX() - w.x);
        Vec3 at = TroubleDirector.green(l, toward, 2.4);
        boolean someone = false;
        for (ServerPlayer p : c.level().players()) someone |= p.distanceToSqr(at) < 12 * 12 && !t.helped(p.getUUID());
        TroubleDirector.stage(l, e, at, someone ? Float.NaN : TroubleDirector.yaw(at, new Vec3(camp.getX(), at.y, camp.getZ())), someone ? "talk" : "arms_crossed", "cane");
        if (someone && c.now() % 160 == 13)
            TroubleDirector.say(c.level(), l, e, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.burning.call", c.now() / 160)), 100);
    }

    static int elderOf(VillageService.Live l) {
        for (int i = 0; i < l.settlement().people(); i++) if (l.settlement().job(i).equals("elder") && !l.settlement().isAway(i)) return i;
        for (int i = 0; i < l.settlement().people(); i++) if (l.settlement().age(i) == 3 && !l.settlement().isAway(i)) return i;
        for (int i = 0; i < l.settlement().people(); i++) if (l.settlement().age(i) >= 2 && !l.settlement().isAway(i)) return i;
        return -1;
    }

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null || rec != t.kin && rec != Runner.of(t) || t.stage != WARNED) return false;
        if (t.helped(p.getUUID())) {
            l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.burning.again", c.now() / 100)), 100);
            return true;
        }
        l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.burning.ask", c.now() / 100 + p.getId())), 140);
        TroubleDirector.card(p, t, "The Burning · " + l.plan().name, "The " + t.text + " means to raid " + l.plan().name
                + " tomorrow night. Stand with them: hold the green, put the fires out.");
        return true;
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l != null && t.kin >= 0) l.settlement().say(p, t.kin, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.burning.thanks", c.now())), 120);
    }

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        String village = c.live() != null ? c.live().plan().name : "the village";
        if (t.stage == RAID) return "Hold " + village + ": kill the raiders, put out the fires.";
        long raidDay = t.get("raid_day", c.day());
        return "The " + t.text + " raids " + village + (c.day() >= raidDay ? " tonight" : " tomorrow night") + ". Be there at dusk.";
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        return c.live() == null ? null : BlockPos.containing(TroubleDirector.well(c.live()));
    }

    // ---- tours and tests: the ladder moved along by hand --------------------------------------------------------------------

    static void forceScouts(Ctx c, Trouble t, VillageService.Live l) {
        ((Burning) TroubleDirector.PLOTS.get(TroubleKind.BURNING)).scouts(c, t, l);
    }

    static void forceRaid(Ctx c, Trouble t, VillageService.Live l) {
        t.put("there", 1);
        ((Burning) TroubleDirector.PLOTS.get(TroubleKind.BURNING)).raid(c, t, l);
    }

    /** Roofs alight now near a place (up to three), each with its bucket line. Returns how many. */
    static int forceFire(Ctx c, Trouble t, VillageService.Live l, Vec3 near) {
        Burning b = (Burning) TroubleDirector.PLOTS.get(TroubleKind.BURNING);
        List<LivingEntity> raiders = b.raiders(c, t);
        int n = 0;
        for (int k = 0; k < 3; k++) {
            int plot = b.nextRoof(c, t, l, near);
            if (plot < 0) break;
            LivingEntity archer = raiders.isEmpty() ? null : raiders.get(k % raiders.size());
            b.light(c, t, l, archer, plot);
            n++;
        }
        return n;
    }

    // ---- scouts at the treeline ----------------------------------------------------------------------------------------------

    void scouts(Ctx c, Trouble t, VillageService.Live l) {
        BlockPos at = edge(c, t, l, 30);
        if (at == null) return;
        List<Entity> made = Camps.raidAt(c.level(), (int) t.get("camp", -1), at, at, 3);
        int k = 0;
        for (Entity e : made) {
            e.addTag("awaken_village_scout");
            if (e instanceof Mob m) {
                m.getNavigation().stop();
                m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 90, 3, false, false));
                m.getLookControl().setLookAt(TroubleDirector.well(l));
            }
            t.put("s" + k++, e.getUUID().getMostSignificantBits());
            t.put("s" + k++, e.getUUID().getLeastSignificantBits());
        }
        t.put("scouts", c.now());
        t.put("scout_n", k);
        for (ServerPlayer p : c.level().players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(at)) > 160 * 160) continue;
            Signal.toast(p, Component.literal("Something watches " + l.plan().name + " from the treeline"), Notices.Tier.AMBIENT, "village_raid");
        }
    }

    private void dismissScouts(Ctx c, Trouble t) {
        int n = (int) t.get("scout_n", 0);
        for (int k = 0; k + 1 < n; k += 2) {
            Entity e = c.level().getEntity(new UUID(t.get("s" + k, 0), t.get("s" + (k + 1), 0)));
            if (e != null && e.isAlive() && e.entityTags().contains("awaken_village_scout")) e.discard();
        }
        t.put("scout_n", 0);
    }

    /** Ground {@code out} blocks past the village's edge on the camp's side (loaded), or null. */
    static @Nullable BlockPos edge(Ctx c, Trouble t, VillageService.Live l, int out) {
        Vec3 w = TroubleDirector.well(l);
        BlockPos camp = BlockPos.of(t.place);
        double a = Math.atan2(camp.getZ() - w.z, camp.getX() - w.x);
        for (int tries = 0; tries < 7; tries++) {
            double aa = a + (tries % 2 == 0 ? 1 : -1) * (tries / 2) * 0.35;
            double r = l.plan().radius() + out;
            int x = (int) Math.floor(w.x + Math.cos(aa) * r), z = (int) Math.floor(w.z + Math.sin(aa) * r);
            BlockPos q = new BlockPos(x, 0, z);
            if (!c.level().isLoaded(q)) continue;
            int y = c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!c.level().getFluidState(at.below()).isEmpty()) continue;
            return at;
        }
        return null;
    }

    // ---- the raid --------------------------------------------------------------------------------------------------------------

    /** The raid's night: held if someone's there, else it burns without you. */
    void raid(Ctx c, Trouble t, VillageService.Live l) {
        Vec3 w = TroubleDirector.well(l);
        boolean there = t.get("there", 0) != 0; // (tours: someone is there, whatever their game mode)
        for (ServerPlayer p : c.level().players()) if (!p.isSpectator() && p.distanceToSqr(w) < THERE * THERE) there = true;
        dismissScouts(c, t);
        if (!there) {
            ruin(c, t, l);
            TroubleDirector.end(c, t);
            return;
        }
        t.stage = RAID;
        t.since = c.now();
        BlockPos from = edge(c, t, l, 34);
        if (from == null) from = BlockPos.containing(w).offset(30, 0, 0);
        int n = 4 + (int) t.get("tier", 4);
        List<Entity> raiders = Camps.raidAt(c.level(), (int) t.get("camp", -1), from, BlockPos.containing(w), n);
        int k = 0;
        for (Entity e : raiders) {
            t.put("r" + k++, e.getUUID().getMostSignificantBits());
            t.put("r" + k++, e.getUUID().getLeastSignificantBits());
        }
        t.put("raider_n", k);
        t.put("next_arrow", c.now() + 20 * 6);
        l.settlement().alarm(BlockPos.containing(w), 40);
        for (ServerPlayer p : c.level().players()) {
            if (p.distanceToSqr(w) > 256 * 256) continue;
            Signal.toast(p, Component.literal("The " + t.text + " is at " + l.plan().name + "!"), Notices.Tier.CRITICAL, "village_raid");
        }
        c.st().setDirty();
        Awaken.LOG.info("[trouble] the raid on {}: {} raiders from {}", l.plan().name, raiders.size(), from.toShortString());
    }

    List<LivingEntity> raiders(Ctx c, Trouble t) {
        List<LivingEntity> out = new ArrayList<>();
        int n = (int) t.get("raider_n", 0);
        for (int k = 0; k + 1 < n; k += 2) {
            Entity e = c.level().getEntity(new UUID(t.get("r" + k, 0), t.get("r" + (k + 1), 0)));
            if (e instanceof LivingEntity le && le.isAlive()) out.add(le);
        }
        return out;
    }

    private void fight(Ctx c, Trouble t, VillageService.Live l) {
        Vec3 w = TroubleDirector.well(l);
        List<LivingEntity> raiders = raiders(c, t);
        // the Watch and the grown-ups line the green, facing out the way they come
        BlockPos camp = BlockPos.of(t.place);
        double toward = Math.atan2(camp.getZ() - w.z, camp.getX() - w.x);
        List<Integer> busy = new ArrayList<>();
        for (int p = 0; p < plots(t).size(); p++) busy.addAll(lineOf(t, plots(t).get(p)));
        Set<Integer> not = new HashSet<>(busy);
        List<Integer> watch = TroubleDirector.crowd(l, 6, not, "watch", "hunter", "smith");
        for (int i = 0; i < watch.size(); i++) {
            double a = toward + (i - (watch.size() - 1) / 2.0) * 0.38;
            Vec3 at = TroubleDirector.green(l, a, l.plan().greenR + 1.2);
            Vec3 out = new Vec3(at.x + Math.cos(a) * 10, at.y, at.z + Math.sin(a) * 10);
            TroubleDirector.stage(l, watch.get(i), at, TroubleDirector.yaw(at, out), "halberd_stand", i % 3 == 1 ? "lantern" : "halberd");
        }
        // fire arrows over the roofs while they live
        if (!raiders.isEmpty() && c.now() >= t.get("next_arrow", 0) && plots(t).size() < 4) {
            LivingEntity archer = raiders.get((int) Math.floorMod(c.now() / 20, (long) raiders.size()));
            int plot = nextRoof(c, t, l, archer.position());
            if (plot >= 0) light(c, t, l, archer, plot);
            t.put("next_arrow", c.now() + 20 * (10 + (int) Math.floorMod(c.now(), 6L)));
        }
        // the fires: the line works them down, alone they grow; at full they eat the house
        boolean burning = false;
        for (int plot : plots(t)) burning |= fire(c, t, l, plot);
        if (c.now() % 200 == 13 && !raiders.isEmpty() && !watch.isEmpty())
            TroubleDirector.say(c.level(), l, watch.get(0), TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.burning.fight", c.now() / 200)), 70);
        boolean dawn = c.tod() >= 22600 || c.tod() < 12000;
        if (raiders.isEmpty() && !burning || dawn) held(c, t, l, raiders, dawn);
    }

    // ---- fires on the roofs -------------------------------------------------------------------------------------------------

    static List<Integer> plots(Trouble t) {
        List<Integer> out = new ArrayList<>();
        int n = (int) t.get("fires", 0);
        for (int i = 0; i < n; i++) out.add((int) t.get("fp" + i, -1));
        return out;
    }

    /** The house nearest the raiders that isn't burning yet (a house, the inn, a barn: something with a roof to catch). */
    int nextRoof(Ctx c, Trouble t, VillageService.Live l, Vec3 from) {
        VillagePlan p = l.plan();
        List<Integer> burning = plots(t);
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (VillagePlan.Building b : p.buildings) {
            if (burning.contains(b.plot()) || b.use().equals("mill")) continue;
            CityPlan.Plot q = p.plots.get(b.plot());
            double d = Math.hypot(p.ox + (q.x0() + q.x1()) / 2.0 - from.x, p.oz + (q.z0() + q.z1()) / 2.0 - from.z);
            if (d < bd) {
                bd = d;
                best = b.plot();
            }
        }
        return best;
    }

    /** A roof set alight (by a fire arrow from a raider, if there is one), and the bucket line forming for it. */
    void light(Ctx c, Trouble t, VillageService.Live l, @Nullable LivingEntity archer, int plot) {
        List<BlockPos> roof = roofTops(c.level(), l, plot, 5, c.now());
        if (roof.isEmpty()) return;
        int i = (int) t.get("fires", 0);
        t.put("fp" + i, plot);
        t.put("fires", i + 1);
        t.put("fi" + plot, 520);
        for (int k = 0; k < roof.size(); k++) t.put("fc" + plot + "_" + k, roof.get(k).asLong());
        t.put("fn" + plot, roof.size());
        // the arrow itself, alight, from the raider to the roof
        BlockPos aim = roof.getFirst();
        if (archer != null) arrow(c, archer, aim);
        setFlames(c, t, plot);
        // the line forms from the well to its door
        bucketLine(c, t, l, plot);
        Awaken.LOG.info("[trouble] a fire on {}'s plot {}", l.plan().name, plot);
    }

    private static void arrow(Ctx c, LivingEntity archer, BlockPos aim) {
        Arrow arrow = new Arrow(c.level(), archer, new ItemStack(Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.igniteForSeconds(8f);
        arrow.setPos(archer.getX(), archer.getEyeY(), archer.getZ());
        double dx = aim.getX() + 0.5 - archer.getX(), dz = aim.getZ() + 0.5 - archer.getZ(), dy = aim.getY() + 0.5 - archer.getEyeY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.18, dz, 1.6f, 2f);
        c.level().addFreshEntity(arrow);
        c.level().playSound(null, archer.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.2f, 0.8f);
    }

    /** The top of a plot's roof at a few columns (where a flame can sit: air above solid). */
    static List<BlockPos> roofTops(ServerLevel level, VillageService.Live l, int plot, int n, long seed) {
        VillagePlan p = l.plan();
        CityPlan.Plot q = p.plots.get(plot);
        List<BlockPos> out = new ArrayList<>();
        java.util.Random r = new java.util.Random(seed ^ plot * 31L);
        for (int tries = 0; tries < 40 && out.size() < n; tries++) {
            int x = p.ox + q.x0() + 1 + r.nextInt(Math.max(1, q.x1() - q.x0() - 1)), z = p.oz + q.z0() + 1 + r.nextInt(Math.max(1, q.z1() - q.z0() - 1));
            BlockPos top = new BlockPos(x, p.baseY + q.lift() + q.top() + 4, z);
            if (!level.isLoaded(top)) continue;
            while (top.getY() > p.baseY + q.lift() + 2 && level.getBlockState(top).isAir()) top = top.below();
            BlockPos at = top.above();
            if (!level.getBlockState(at).isAir() || out.contains(at)) continue;
            out.add(at);
        }
        return out;
    }

    /** Shows a fire's flames by its strength (more of them, taller, as it grows). */
    private void setFlames(Ctx c, Trouble t, int plot) {
        int n = (int) t.get("fn" + plot, 0), intensity = (int) t.get("fi" + plot, 0);
        int want = (int) Math.ceil(intensity / (double) FULL * n);
        int flame = Math.clamp(intensity / 260, 0, 3);
        for (int k = 0; k < n; k++) {
            BlockPos at = BlockPos.of(t.get("fc" + plot + "_" + k, 0));
            if (!c.level().isLoaded(at)) continue;
            BlockState s = c.level().getBlockState(at);
            boolean on = k < want;
            if (on && (s.isAir() || s.is(CitizensContent.CITY_FIRE.get()))) {
                c.level().setBlock(at, CitizensContent.CITY_FIRE.get().defaultBlockState().setValue(CityFireBlock.FLAME, flame), Block.UPDATE_ALL);
                if (!t.laid.contains(at.asLong())) t.laid.add(at.asLong());
            } else if (!on && s.is(CitizensContent.CITY_FIRE.get())) {
                c.level().removeBlock(at, false);
                c.level().sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    /** One fire's second: the line takes it down, alone it grows; at full it eats the house's roof. True while it burns. */
    private boolean fire(Ctx c, Trouble t, VillageService.Live l, int plot) {
        int intensity = (int) t.get("fi" + plot, 0);
        if (intensity <= 0) return false;
        int line = 0;
        for (int rec : lineOf(t, plot)) if (l.settlement().where(rec) != null) line++;
        // a player in the line with a bucket counts twice; one standing at the fire beating it counts in beat()
        for (ServerPlayer p : c.level().players()) {
            if (p.getMainHandItem().is(Items.WATER_BUCKET) || p.getMainHandItem().is(Items.BUCKET)) {
                for (int rec : lineOf(t, plot)) {
                    Vec3 at = l.settlement().where(rec);
                    if (at != null && p.distanceToSqr(at) < 2.5 * 2.5) {
                        line += 2;
                        break;
                    }
                }
            }
        }
        int before = intensity;
        intensity = Math.clamp(intensity + GROW - line * LINE, 0, FULL);
        t.put("fi" + plot, intensity);
        if (intensity >= FULL) {
            long full = t.get("ff" + plot, 0) + 1;
            t.put("ff" + plot, full);
            if (full % 20 == 0) charSome(c, t, l, plot);
        }
        if (before / 120 != intensity / 120) setFlames(c, t, plot);
        // smoke and embers over it
        int n = (int) t.get("fn" + plot, 0);
        for (int k = 0; k < n; k++) {
            BlockPos at = BlockPos.of(t.get("fc" + plot + "_" + k, 0));
            if (!c.level().getBlockState(at).is(CitizensContent.CITY_FIRE.get())) continue;
            c.level().sendParticles(CitizensContent.CHIMNEY_SMOKE.get(), at.getX() + 0.5, at.getY() + 1.4, at.getZ() + 0.5, 2, 0.4, 0.3, 0.4, 0.0);
            c.level().sendParticles(CitizensContent.FIRE_EMBER.get(), at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.02);
        }
        if (intensity == 0) {
            setFlames(c, t, plot);
            // out: a scorch where it burned
            Char.house(c.level(), l, plot, Char.SCORCH, c.now(), c.day());
            for (int rec : lineOf(t, plot)) TroubleDirector.release(l, rec);
            c.level().playSound(null, BlockPos.of(t.get("fc" + plot + "_0", 0)), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.9f);
            return false;
        }
        // the line keeps passing buckets
        for (int rec : lineOf(t, plot)) stageLine(c.level(), l, t, plot, rec);
        return true;
    }

    private void charSome(Ctx c, Trouble t, VillageService.Live l, int plot) {
        int done = (int) t.get("fchar" + plot, 0);
        if (done >= 2) return;
        Char.house(c.level(), l, plot, done == 0 ? Char.SCORCH : Char.GUTTED, c.now() ^ plot, c.day());
        t.put("fchar" + plot, done + 1);
        if (done + 1 >= 2) {
            TroubleState.Memory mem = c.st().memory(l.key());
            if (!mem.charred.contains(plot)) mem.charred.add(plot);
        }
    }

    /** A player beat a flame out with their hands (or a weapon): less fire, and they were seen. */
    static boolean beat(ServerLevel level, ServerPlayer p, BlockPos pos) {
        TroubleState st = TroubleState.get(level.getServer());
        for (Trouble t : st.troubles()) {
            if (!t.kind.equals(TroubleKind.BURNING.id) || t.stage != RAID || !t.laid.contains(pos.asLong())) continue;
            for (int plot : plots(t)) {
                int n = (int) t.get("fn" + plot, 0);
                for (int k = 0; k < n; k++) {
                    if (t.get("fc" + plot + "_" + k, 0) != pos.asLong()) continue;
                    t.put("fi" + plot, Math.max(0, t.get("fi" + plot, 0) - BEAT));
                    level.removeBlock(pos, false);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.1f);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
                    if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID()); // fighting the fire is helping
                    t.put("beaten", t.get("beaten", 0) + 1);
                    st.setDirty();
                    return true;
                }
            }
        }
        return false;
    }

    /** Who passes buckets for a fire (records), from the well to its door. */
    static List<Integer> lineOf(Trouble t, int plot) {
        List<Integer> out = new ArrayList<>();
        int n = (int) t.get("ln" + plot, 0);
        for (int i = 0; i < n; i++) out.add((int) t.get("l" + plot + "_" + i, -1));
        return out;
    }

    private void bucketLine(Ctx c, Trouble t, VillageService.Live l, int plot) {
        Set<Integer> not = new HashSet<>();
        for (int p : plots(t)) not.addAll(lineOf(t, p));
        List<Integer> people = TroubleDirector.crowd(l, 6, not, "farmer", "miller", "tavernkeeper", "woodcutter", "fisher", "carter", "resident");
        for (int i = 0; i < people.size(); i++) t.put("l" + plot + "_" + i, people.get(i));
        t.put("ln" + plot, people.size());
        for (int rec : people) stageLine(c.level(), l, t, plot, rec);
    }

    private void stageLine(ServerLevel level, VillageService.Live l, Trouble t, int plot, int rec) {
        List<Integer> line = lineOf(t, plot);
        int i = line.indexOf(rec);
        if (i < 0) return;
        VillagePlan p = l.plan();
        Vec3 w = TroubleDirector.well(l);
        VillagePlan.Building door = null;
        for (VillagePlan.Building b : p.buildings) if (b.plot() == plot) door = b;
        Vec3 to = door == null ? w : new Vec3(p.ox + door.outX() + 0.5, w.y, p.oz + door.outZ() + 0.5);
        double s = (i + 0.7) / (line.size() + 0.4);
        double x = w.x + (to.x - w.x) * s, z = w.z + (to.z - w.z) * s;
        Vec3 at = TroubleDirector.stand(level, l, x, z);
        // each faces along the line (from the water toward the fire)
        TroubleDirector.stage(l, rec, at, TroubleDirector.yaw(at, to) + (i % 2 == 0 ? 70f : -70f), "bucket_pass", "bucket");
    }

    // ---- the end of the night ---------------------------------------------------------------------------------------------------

    private void held(Ctx c, Trouble t, VillageService.Live l, List<LivingEntity> raiders, boolean dawn) {
        // the raiders still standing at dawn go back into the dark; fires still alight finish their houses
        for (LivingEntity r : raiders) r.discard();
        int lost = 0;
        for (int plot : plots(t)) {
            if (t.get("fi" + plot, 0) > 0) {
                Char.house(c.level(), l, plot, Char.GUTTED, c.now() ^ plot, c.day());
                TroubleState.Memory mem = c.st().memory(l.key());
                if (!mem.charred.contains(plot)) mem.charred.add(plot);
                t.put("fi" + plot, 0);
                setFlames(c, t, plot);
                lost++;
            } else if (t.get("fchar" + plot, 0) >= 2) lost++;
            for (int rec : lineOf(t, plot)) TroubleDirector.release(l, rec);
        }
        int fires = plots(t).size();
        Vec3 w = TroubleDirector.well(l);
        if (!dawn || raiders.isEmpty()) {
            String saved = fires == 0 ? "not a roof caught" : lost == 0 ? fires + (fires == 1 ? " roof was scorched" : " roofs were scorched") + ", none lost"
                    : lost + (lost == 1 ? " house" : " houses") + " lost";
            List<ServerPlayer> paid = TroubleDirector.pay(c, t, w, THERE, lost == 0 ? 18 : 10, lost == 0 ? 20 : 10, 0.45,
                    "You held " + l.plan().name + " against the " + t.text,
                    p -> Keepsake.make(l.plan().name + "'s Bucket Token", "A tin token the bucket line hands round after a fire.",
                            "Given for the night the " + t.text + " came."));
            Villages.history(c.level().getServer(), l.key(), TroubleDirector.names(paid) + " held " + l.plan().name + " against the " + t.text + "; " + saved + ".");
        } else {
            Villages.history(c.level().getServer(), l.key(), "The " + t.text + " burned through " + l.plan().name + " until dawn; " + lost + " houses lost.");
            Villages.addMood(c.level().getServer(), l.key(), -15);
        }
        if (lost > 0) survivors(c, t, l);
        TroubleDirector.end(c, t);
    }

    /** Missed: the raid burned the houses on its side without you (or the village wasn't there to see it: it burns when it next is). */
    static void ruin(Ctx c, Trouble t, VillageService.@Nullable Live l) {
        int tier = (int) t.get("tier", 4);
        int houses = tier >= 5 ? 3 + (int) Math.floorMod(t.id, 3L) : 2 + (int) Math.floorMod(t.id, 2L);
        TroubleState.Memory mem = c.st().memory(t.village);
        if (l == null) {
            // burned while nobody was near: the village shows it when it next runs
            mem.data.put("burn_pending", (long) houses);
            mem.data.put("burn_from", t.place);
            Villages.history(c.level().getServer(), t.village, "The " + t.text + " came in the night. Nobody was there to stand with them.");
            c.st().setDirty();
            return;
        }
        burnHouses(c, l, mem, houses, BlockPos.of(t.place), t.text);
    }

    /** The houses nearest the raid's side, charred; survivors by the well; smoke for days. */
    static void burnHouses(Ctx c, VillageService.Live l, TroubleState.Memory mem, int houses, BlockPos from, String who) {
        VillagePlan p = l.plan();
        List<VillagePlan.Building> order = new ArrayList<>();
        for (VillagePlan.Building b : p.buildings) if (b.use().equals("house") || b.use().equals("barn") || b.use().equals("granary")) order.add(b);
        order.sort((a, b) -> Double.compare(dist(p, a, from), dist(p, b, from)));
        List<String> names = new ArrayList<>();
        for (int i = 0; i < Math.min(houses, order.size()); i++) {
            int plot = order.get(i).plot();
            if (!mem.charred.contains(plot)) mem.charred.add(plot);
            for (VillagePlan.Household h : p.households) if (h.building() == p.buildings.indexOf(order.get(i)) && names.size() < 3) names.add(h.family());
        }
        mem.data.put("char_queue", 1L);
        mem.data.put("smoke_until", c.day() + 3);
        Villages.history(c.level().getServer(), l.key(), "The " + who + " came in the night and burned " + (names.isEmpty() ? "the houses on the near side"
                : "the " + String.join("s', the ", names) + "s' homes") + ".");
        Villages.addMood(c.level().getServer(), l.key(), -25);
        Villages.addProsperity(c.level().getServer(), l.key(), -12);
        c.st().setDirty();
        Lasting.homeless(c, l, mem);
    }

    private static double dist(VillagePlan p, VillagePlan.Building b, BlockPos from) {
        CityPlan.Plot q = p.plots.get(b.plot());
        return Math.hypot(p.ox + (q.x0() + q.x1()) / 2.0 - from.getX(), p.oz + (q.z0() + q.z1()) / 2.0 - from.getZ());
    }

    private void survivors(Ctx c, Trouble t, VillageService.Live l) {
        TroubleState.Memory mem = c.st().memory(l.key());
        mem.data.put("smoke_until", c.day() + 2);
        Lasting.homeless(c, l, mem);
    }

    @Override
    public boolean holds(Ctx c, Trouble t) {
        return t.stage == RAID && c.day() <= t.due + 1;
    }

    @Override
    public void miss(Ctx c, Trouble t) {
        if (t.stage == WARNED) ruin(c, t, c.live());
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        dismissScouts(c, t);
        for (LivingEntity r : raiders(c, t)) r.discard();
        if (l == null) return;
        TroubleDirector.release(l, t.kin);
        for (int plot : plots(t)) for (int rec : lineOf(t, plot)) TroubleDirector.release(l, rec);
        Runner.release(c, t, l);
    }
}
