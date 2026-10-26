package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.village.Requests;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Rebuild (SPEC §28.7, PLAN 6.3): after a Burning, or any damage done (W3's damage record, every block with what
 * stood there), the elder asks at the well for timber, stone and iron; three notices go up on the board with counts from
 * the damage itself. When they're filled the masons raise scaffolding round each house and put it back over three
 * play-days, block by block from the footings up, exactly as it was; each house they finish carries a beam of your
 * colour over its door, a family that had left for Carillon comes back, and the survivors' camp by the well is struck.
 * Missed, the village mends what it can on its own and the worst house stays a shell.
 */
final class Rebuild implements Plot {
    static final int ASK = 0, BUILDING = 1;
    /** Damage enough to need a rebuild (blocks), and how long the masons take (play ticks: three play-days). */
    static final int ENOUGH = 24;
    static final long WORK = 3L * 24000;

    static @Nullable String cause(Ctx c, VillageService.Live l) {
        int n = Villages.damage(c.level().getServer(), l.key()).size();
        return n >= ENOUGH ? "damage:" + n : null;
    }

    @Override
    public boolean begin(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return false;
        List<Villages.Damage> dmg = Villages.damage(c.level().getServer(), l.key());
        if (dmg.isEmpty()) return false;
        int wood = 0, stone = 0;
        for (Villages.Damage d : dmg) {
            BlockState s = d.original();
            if (s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS) || s.ignitedByLava()) wood++;
            else stone++;
        }
        Set<Integer> houses = houses(l, dmg);
        int timber = Math.clamp(wood / 5, 12, 64), cobble = Math.clamp(stone / 4, 12, 64), iron = Math.clamp(3 + houses.size() * 2, 4, 12);
        var server = c.level().getServer();
        String a = Requests.post(server, l, "rebuild_timber", "timber", timber, 4, 6, "Timber for the Rebuild. Beams, joists, the roofs that fell in.");
        String b = Requests.post(server, l, "rebuild_stone", "minecraft:cobblestone", cobble, 3, 5, "Stone for the Rebuild. Footings and chimneys first.");
        String d = Requests.post(server, l, "rebuild_iron", "minecraft:iron_ingot", iron, 5, 6, "Iron for the Rebuild: nails, hinges, a new hearth-plate.");
        t.text = a + "|" + b + "|" + d;
        t.kin = Burning.elderOf(l);
        t.put("total", dmg.size());
        t.due = c.day() + 5;
        t.placeName = l.plan().name + "'s notice board";
        t.place = BlockPos.asLong(l.plan().ox + l.plan().notice[0], l.plan().baseY, l.plan().oz + l.plan().notice[1]);
        return true;
    }

    static Set<Integer> houses(VillageService.Live l, List<Villages.Damage> dmg) {
        Set<Integer> out = new HashSet<>();
        VillagePlan p = l.plan();
        for (Villages.Damage d : dmg) {
            int plot = p.plotAt(d.pos().getX() - p.ox, d.pos().getZ() - p.oz);
            if (plot >= 0) out.add(plot);
        }
        return out;
    }

    String[] notices(Trouble t) {
        return t.text.split("\\|");
    }

    // ---- asking -------------------------------------------------------------------------------------------------------------

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        if (t.stage == ASK) {
            elder(c, t, l);
            boolean all = true;
            for (String id : notices(t)) all &= Requests.progress(l, id) == null;
            if (all) startWork(c, t, l);
            return;
        }
        work(c, t, l);
    }

    private void elder(Ctx c, Trouble t, VillageService.Live l) {
        if (t.kin < 0) return;
        Vec3 w = TroubleDirector.well(l);
        Vec3 board = new Vec3(l.plan().ox + l.plan().notice[0] + 0.5, w.y, l.plan().oz + l.plan().notice[1] + 0.5);
        double a = Math.atan2(board.z - w.z, board.x - w.x);
        Vec3 at = TroubleDirector.green(l, a, Math.max(1.5, Math.hypot(board.x - w.x, board.z - w.z) - 2.2));
        boolean someone = false;
        for (ServerPlayer p : c.level().players()) someone |= p.distanceToSqr(at) < 12 * 12 && !t.helped(p.getUUID());
        TroubleDirector.stage(l, t.kin, at, someone ? Float.NaN : TroubleDirector.yaw(at, board), someone ? "talk" : "arms_crossed", "cane");
        if (someone && c.now() % 180 == 13)
            TroubleDirector.say(c.level(), l, t.kin, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.rebuild.call", c.now() / 180)), 100);
    }

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null || rec != t.kin && rec != Runner.of(t)) return false;
        if (t.helped(p.getUUID()) || t.stage == BUILDING) {
            l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), t.stage == BUILDING ? "village.trouble.rebuild.working"
                    : "village.trouble.rebuild.again", c.now() / 100)), 100);
            return true;
        }
        l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.rebuild.ask", c.now() / 100 + p.getId())), 140);
        TroubleDirector.card(p, t, "The Rebuild · " + l.plan().name, "Bring timber, stone and iron to " + l.plan().name
                + "'s notice board. The masons will put the houses back.");
        return true;
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l != null && t.kin >= 0) l.settlement().say(p, t.kin, TroubleText.line(c.level(), "village.trouble.rebuild.thanks", c.now()), 120);
    }

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l == null) return "Bring timber, stone and iron to the village's notice board.";
        if (t.stage == BUILDING) {
            int total = (int) t.get("total", 1), left = Villages.damage(c.level().getServer(), l.key()).size();
            return "The masons are rebuilding " + l.plan().name + ": " + Math.round(100f * (total - left) / Math.max(1, total)) + "% done.";
        }
        StringBuilder b = new StringBuilder();
        String[] names = {"Timber", "Stone", "Iron"};
        String[] ids = notices(t);
        for (int i = 0; i < ids.length && i < 3; i++) {
            int[] pr = Requests.progress(l, ids[i]);
            if (!b.isEmpty()) b.append(" · ");
            b.append(names[i]).append(pr == null ? " done" : " " + pr[0] + "/" + pr[1]);
        }
        return b + " (the notice board in " + l.plan().name + ")";
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        return BlockPos.of(t.place);
    }

    // ---- the masons -----------------------------------------------------------------------------------------------------------

    private void startWork(Ctx c, Trouble t, VillageService.Live l) {
        t.stage = BUILDING;
        t.since = c.now();
        List<Villages.Damage> dmg = Villages.damage(c.level().getServer(), l.key());
        t.put("total", dmg.size());
        // scaffolding round each house to be rebuilt
        for (int plot : houses(l, dmg)) scaffold(c, t, l, plot);
        Villages.history(c.level().getServer(), l.key(), "The masons began the Rebuild.");
        List<ServerPlayer> paid = TroubleDirector.pay(c, t, TroubleDirector.well(l), 160, 8, 10, 0.25, "The masons can begin the Rebuild", null);
        if (!paid.isEmpty()) t.put("by_lo", paid.getFirst().getUUID().getLeastSignificantBits());
        if (!paid.isEmpty()) t.put("by_hi", paid.getFirst().getUUID().getMostSignificantBits());
        c.st().setDirty();
        Awaken.LOG.info("[trouble] the Rebuild of {} begins: {} blocks", l.plan().name, dmg.size());
    }

    /** Scaffolding at the corners and down the long sides of a house, a block out from its walls, up to its eaves. */
    private void scaffold(Ctx c, Trouble t, VillageService.Live l, int plot) {
        VillagePlan p = l.plan();
        CityPlan.Plot q = p.plots.get(plot);
        int y0 = p.baseY + q.lift() + 1, h = Math.max(4, q.eave() + 2);
        int x0 = p.ox + q.x0() - 1, x1 = p.ox + q.x1() + 1, z0 = p.oz + q.z0() - 1, z1 = p.oz + q.z1() + 1;
        List<int[]> cols = new ArrayList<>();
        cols.add(new int[]{x0, z0});
        cols.add(new int[]{x1, z0});
        cols.add(new int[]{x0, z1});
        cols.add(new int[]{x1, z1});
        for (int x = x0 + 3; x < x1 - 1; x += 3) {
            cols.add(new int[]{x, z0});
            cols.add(new int[]{x, z1});
        }
        for (int[] col : cols) {
            for (int dy = 0; dy < h; dy++) {
                BlockPos at = new BlockPos(col[0], y0 + dy, col[1]);
                if (!c.level().isLoaded(at) || !c.level().getBlockState(at).isAir()) break;
                TroubleDirector.lay(c.level(), t, at, Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, 0));
            }
        }
    }

    /** The work: blocks put back at the pace of three play-days (caught up when the village wakes), masons at the house. */
    private void work(Ctx c, Trouble t, VillageService.Live l) {
        var server = c.level().getServer();
        List<Villages.Damage> dmg = new ArrayList<>(Villages.damage(server, l.key()));
        int total = (int) t.get("total", dmg.size());
        if (dmg.isEmpty()) {
            finish(c, t, l);
            return;
        }
        double done = Math.min(1.0, (c.now() - t.since) / (double) WORK);
        int target = (int) Math.ceil(total * done);
        int restored = total - dmg.size();
        // footings up: the lowest blocks first, then the next course
        dmg.sort((a, b) -> Integer.compare(a.pos().getY(), b.pos().getY()));
        int budget = Math.min(40, target - restored);
        int plot = -1;
        for (int i = 0; i < dmg.size() && budget > 0; i++) {
            Villages.Damage d = dmg.get(i);
            if (!c.level().isLoaded(d.pos())) continue;
            BlockState now = c.level().getBlockState(d.pos());
            if (!now.isAir() && !Char.isCharred(now) && !now.canBeReplaced() && !now.is(Blocks.SCAFFOLDING)) {
                Villages.repaired(server, l.key(), d.pos()); // someone already put something there: theirs stands
                continue;
            }
            c.level().setBlock(d.pos(), d.original(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (budget <= 3) {
                c.level().playSound(null, d.pos(), d.original().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7f, 0.9f);
                c.level().sendParticles(ParticleTypes.POOF, d.pos().getX() + 0.5, d.pos().getY() + 0.5, d.pos().getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
            }
            Villages.repaired(server, l.key(), d.pos());
            plot = l.plan().plotAt(d.pos().getX() - l.plan().ox, d.pos().getZ() - l.plan().oz);
            budget--;
        }
        if (plot < 0 && !dmg.isEmpty()) plot = l.plan().plotAt(dmg.getFirst().pos().getX() - l.plan().ox, dmg.getFirst().pos().getZ() - l.plan().oz);
        masons(c, t, l, plot);
    }

    /** Three villagers at the house being rebuilt, hammering and laying. */
    private void masons(Ctx c, Trouble t, VillageService.Live l, int plot) {
        if (plot < 0 || c.tod() > 12000 && c.tod() < 23500) {
            for (int i = 0; i < 3; i++) TroubleDirector.release(l, (int) t.get("m" + i, -1));
            return;
        }
        VillagePlan p = l.plan();
        CityPlan.Plot q = p.plots.get(plot);
        Set<Integer> not = new HashSet<>(c.st().memory(l.key()).homeless);
        List<Integer> who = TroubleDirector.crowd(l, 3, not, "smith", "woodcutter", "carter", "miner");
        Vec3 mid = new Vec3(p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, p.baseY + q.lift() + 1, p.oz + (q.z0() + q.z1()) / 2.0 + 0.5);
        int[][] spots = {{q.x0() - 2, (q.z0() + q.z1()) / 2}, {q.x1() + 2, (q.z0() + q.z1()) / 2}, {(q.x0() + q.x1()) / 2, q.z1() + 2}};
        for (int i = 0; i < who.size(); i++) {
            t.put("m" + i, who.get(i));
            Vec3 at = TroubleDirector.stand(c.level(), l, p.ox + spots[i][0] + 0.5, p.oz + spots[i][1] + 0.5);
            TroubleDirector.stage(l, who.get(i), at, TroubleDirector.yaw(at, mid), i == 1 ? "trowel" : "hammer", i == 1 ? "trowel" : "hammer");
        }
    }

    /** Rebuilt: a beam of your colour over each door, the family that left comes home, the camp by the well struck. */
    private void finish(Ctx c, Trouble t, VillageService.Live l) {
        var server = c.level().getServer();
        TroubleState.Memory mem = c.st().memory(l.key());
        UUID by = new UUID(t.get("by_hi", 0), t.get("by_lo", 0));
        DyeColor colour = colourOf(by);
        Set<Integer> beamed = new HashSet<>();
        for (int plot : List.copyOf(mem.charred)) beamed.add(plot);
        for (int i = 0; i < 4 && beamed.size() < 6; i++) {
            int m = (int) t.get("m" + i, -1);
            if (m >= 0) TroubleDirector.release(l, m);
        }
        for (int plot : beamed) beam(c, l, mem, plot, colour);
        // a family that had left for Carillon comes back to a mended village
        if (!mem.left.isEmpty()) {
            Map<String, List<Integer>> families = new LinkedHashMap<>();
            for (int rec : mem.left) families.computeIfAbsent(l.plan().people.get(rec).family(), k -> new ArrayList<>()).add(rec);
            var first = families.entrySet().iterator().next();
            for (int rec : first.getValue()) {
                l.settlement().away(rec, false);
                mem.left.remove((Integer) rec);
            }
            Villages.history(server, l.key(), "The " + first.getKey() + "s came back from Carillon.");
        }
        Lasting.breakCamp(c.level(), l, mem);
        Villages.history(server, l.key(), l.plan().name + " was rebuilt" + (by.getLeastSignificantBits() == 0 && by.getMostSignificantBits() == 0 ? "."
                : ", every new door under a " + colour.getName().replace('_', ' ') + " beam."));
        Villages.addMood(server, l.key(), 15);
        Villages.addProsperity(server, l.key(), 6);
        TroubleNet.ring(c.level(), TroubleDirector.well(l), 0xE9C77B, 40);
        TroubleDirector.end(c, t);
    }

    /** A helper's colour: the dye their name falls on (stable for a player). */
    static DyeColor colourOf(UUID id) {
        return DyeColor.byId((int) Math.floorMod(id.getLeastSignificantBits() ^ id.getMostSignificantBits(), 16L));
    }

    /** The lintel over a house's door painted in a helper's colour (terracotta: painted timber). */
    static void beam(Ctx c, VillageService.Live l, TroubleState.Memory mem, int plot, DyeColor colour) {
        VillagePlan p = l.plan();
        for (VillagePlan.Building b : p.buildings) {
            if (b.plot() != plot) continue;
            BlockPos door = p.ground(b.doorX(), b.doorZ());
            BlockPos lintel = door.above(3);
            if (!c.level().isLoaded(lintel)) return;
            BlockState was = c.level().getBlockState(lintel);
            if (was.isAir() || was.hasBlockEntity()) lintel = lintel.above();
            if (c.level().getBlockState(lintel).isAir()) return;
            c.level().setBlock(lintel, terracotta(colour), Block.UPDATE_ALL);
            mem.beams.add(lintel.asLong());
            return;
        }
    }

    static BlockState terracotta(DyeColor c) {
        Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(c.getName() + "_terracotta"));
        return (b == Blocks.AIR ? Blocks.TERRACOTTA : b).defaultBlockState();
    }

    // ---- without you ----------------------------------------------------------------------------------------------------------

    @Override
    public boolean holds(Ctx c, Trouble t) {
        return t.stage == BUILDING;
    }

    @Override
    public void miss(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        var server = c.level().getServer();
        // they mend what they can alone; the worst house stays a shell (its damage kept: a later Rebuild can still come)
        List<Villages.Damage> dmg = Villages.damage(server, l.key());
        Map<Integer, Integer> perPlot = new LinkedHashMap<>();
        for (Villages.Damage d : dmg) perPlot.merge(l.plan().plotAt(d.pos().getX() - l.plan().ox, d.pos().getZ() - l.plan().oz), 1, Integer::sum);
        int worst = perPlot.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(-1);
        int put = 0;
        for (Villages.Damage d : dmg) {
            int plot = l.plan().plotAt(d.pos().getX() - l.plan().ox, d.pos().getZ() - l.plan().oz);
            if (plot == worst || !c.level().isLoaded(d.pos())) continue;
            BlockState now = c.level().getBlockState(d.pos());
            if (now.isAir() || Char.isCharred(now) || now.canBeReplaced()) c.level().setBlock(d.pos(), d.original(), Block.UPDATE_CLIENTS);
            Villages.repaired(server, l.key(), d.pos());
            put++;
        }
        for (String id : notices(t)) Requests.unpost(server, l, id);
        TroubleState.Memory mem = c.st().memory(l.key());
        mem.charred.removeIf(p -> p != worst);
        Villages.history(server, l.key(), l.plan().name + " mended what it could on its own. One house still stands empty, black to the rafters.");
        Villages.addProsperity(server, l.key(), -4);
        Awaken.LOG.info("[trouble] {} mended {} blocks alone", l.plan().name, put);
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        TroubleDirector.release(l, t.kin);
        for (int i = 0; i < 4; i++) TroubleDirector.release(l, (int) t.get("m" + i, -1));
        if (t.stage == ASK) for (String id : notices(t)) Requests.unpost(c.level().getServer(), l, id);
        Runner.release(c, t, l);
    }

    // ---- the mending notices (any damage): the masons put that much back -----------------------------------------------------

    /** A filled "mend" notice puts back up to 24 of the oldest damaged blocks (where loaded). */
    static void mend(ServerLevel level, VillageService.Live l, int n) {
        var server = level.getServer();
        int put = 0;
        for (Villages.Damage d : Villages.damage(server, l.key())) {
            if (put >= n) break;
            if (!level.isLoaded(d.pos())) continue;
            BlockState now = level.getBlockState(d.pos());
            if (now.isAir() || Char.isCharred(now) || now.canBeReplaced()) {
                level.setBlock(d.pos(), d.original(), Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.POOF, d.pos().getX() + 0.5, d.pos().getY() + 0.5, d.pos().getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
            }
            Villages.repaired(server, l.key(), d.pos());
            put++;
        }
    }
}
