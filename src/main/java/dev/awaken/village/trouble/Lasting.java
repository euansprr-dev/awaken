package dev.awaken.village.trouble;

import dev.awaken.city.CityPlan;
import dev.awaken.living.LivingContent;
import dev.awaken.village.VillageService;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What troubles leave in a village between them (SPEC §28.7): the houses a fire has yet to finish (a burn that
 * happened while nobody was near shows when someone next is), smoke rising over charred roofs for days (seen from far
 * off: the call of the Rebuild), and the survivors camped by the well round a fire with their bedrolls until their homes
 * are rebuilt. Ticked each second for every awake village.
 */
final class Lasting {
    private Lasting() {}

    static void tick(Plot.Ctx c, VillageService.Live l) {
        TroubleState.Memory mem = c.st().peek(l.key());
        if (mem == null) return;
        // a burn nobody was near for: now the village is here, it happened
        long pending = mem.get("burn_pending", 0);
        if (pending > 0) {
            mem.data.remove("burn_pending");
            Burning.burnHouses(c, l, mem, (int) pending, BlockPos.of(mem.get("burn_from", BlockPos.asLong(l.plan().ox + 100, 0, l.plan().oz))), "raiders");
        }
        // the fire's work, a house a second
        if (mem.get("char_queue", 0) != 0) {
            boolean left = false;
            for (int plot : mem.charred) {
                if (mem.get("charred_" + plot, 0) != 0) continue;
                left = true;
                if (Char.house(c.level(), l, plot, Char.GUTTED, Taken.mix(l.plan().seed, plot, 17), c.day()) > 0 || charredAlready(c, l, plot)) {
                    mem.data.put("charred_" + plot, 1L);
                    c.st().setDirty();
                }
                break;
            }
            if (!left) mem.data.remove("char_queue");
        }
        // smoke over what burned
        if (c.day() <= mem.get("smoke_until", -1) && c.now() % 40 == 13) smoke(c, l, mem);
        // the survivors by the well
        if (!mem.homeless.isEmpty()) camp(c, l, mem);
    }

    private static boolean charredAlready(Plot.Ctx c, VillageService.Live l, int plot) {
        CityPlan.Plot q = l.plan().plots.get(plot);
        return c.level().isLoaded(new BlockPos(l.plan().ox + q.x0(), 64, l.plan().oz + q.z0()));
    }

    private static void smoke(Plot.Ctx c, VillageService.Live l, TroubleState.Memory mem) {
        VillagePlan p = l.plan();
        for (int plot : mem.charred) {
            CityPlan.Plot q = p.plots.get(plot);
            double x = p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, z = p.oz + (q.z0() + q.z1()) / 2.0 + 0.5;
            double y = p.baseY + q.lift() + 3;
            // a tall column (seen from far off: overrideLimiter sends it to 512) and a few embers low in the ruin
            c.level().sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, x, y, z, 2, 1.2, 0.4, 1.2, 0.02);
            c.level().sendParticles(ParticleTypes.SMOKE, x, y - 1.5, z, 4, 2, 0.5, 2, 0.01);
        }
    }

    /** The households of charred houses camp by the well. */
    static void homeless(Plot.Ctx c, VillageService.Live l, TroubleState.Memory mem) {
        VillagePlan p = l.plan();
        for (int plot : mem.charred) {
            int building = -1;
            for (int i = 0; i < p.buildings.size(); i++) if (p.buildings.get(i).plot() == plot) building = i;
            if (building < 0) continue;
            for (int i = 0; i < p.people.size(); i++) {
                if (p.households.get(p.people.get(i).household()).building() != building) continue;
                if (!mem.homeless.contains(i) && !mem.left.contains(i)) mem.homeless.add(i);
            }
        }
        c.st().setDirty();
    }

    /** Their camp by the well: a fire and bedrolls (laid once), and them round it, sitting at night. */
    private static void camp(Plot.Ctx c, VillageService.Live l, TroubleState.Memory mem) {
        double a = l.plan().seed % 628 / 100.0;
        Vec3 fire = TroubleDirector.green(l, a, l.plan().greenR - 1.5);
        if (mem.camp.isEmpty()) layCamp(c.level(), l, mem, BlockPos.containing(fire));
        boolean night = c.tod() >= 13000 && c.tod() < 23000;
        int n = Math.min(10, mem.homeless.size());
        for (int k = 0; k < n; k++) {
            int rec = mem.homeless.get(k);
            if (l.settlement().isAway(rec)) continue;
            double ang = a + Math.PI * 2 * k / Math.max(3, n);
            Vec3 at = new Vec3(fire.x + Math.cos(ang) * 2.2, fire.y, fire.z + Math.sin(ang) * 2.2);
            at = TroubleDirector.stand(c.level(), l, at.x, at.z);
            String act = night ? "sit" : k % 3 == 0 ? "talk" : k % 3 == 1 ? "arms_crossed" : "sit";
            TroubleDirector.stage(l, rec, at, TroubleDirector.yaw(at, fire), act, "");
        }
        if (c.now() % 300 == 13 && n > 0) {
            int rec = mem.homeless.get((int) Math.floorMod(c.now() / 300, (long) n));
            TroubleDirector.say(c.level(), l, rec, TroubleText.line(c.level(), "village.trouble.burning.survivor", c.now() / 300), 90);
        }
    }

    private static void layCamp(ServerLevel level, VillageService.Live l, TroubleState.Memory mem, BlockPos fire) {
        if (!level.isLoaded(fire)) return;
        BlockPos f = fire;
        if (level.getBlockState(f).isAir() && level.getBlockState(f.below()).isSolid()) {
            level.setBlock(f, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
            mem.camp.add(f.asLong());
        }
        Direction[] faces = {Direction.EAST, Direction.SOUTH, Direction.WEST};
        int[][] at = {{-3, 1}, {1, -3}, {3, 2}};
        for (int i = 0; i < faces.length; i++) {
            BlockPos foot = new BlockPos(f.getX() + at[i][0], f.getY(), f.getZ() + at[i][1]);
            BlockPos head = foot.relative(faces[i]);
            if (!level.getBlockState(foot).isAir() || !level.getBlockState(head).isAir()) continue;
            if (!level.getBlockState(foot.below()).isSolid() || !level.getBlockState(head.below()).isSolid()) continue;
            BlockState s = LivingContent.BEDROLL.get().defaultBlockState().setValue(AbstractBedBlock.FACING, faces[i]);
            level.setBlock(foot, s.setValue(AbstractBedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
            level.setBlock(head, s.setValue(AbstractBedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
            mem.camp.add(foot.asLong());
            mem.camp.add(head.asLong());
        }
    }

    /** Rebuilt: the camp by the well is struck, its people go home. */
    static void breakCamp(ServerLevel level, VillageService.Live l, TroubleState.Memory mem) {
        for (long p : List.copyOf(mem.camp)) {
            BlockPos pos = BlockPos.of(p);
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (s.is(Blocks.CAMPFIRE) || s.getBlock() instanceof dev.awaken.living.wayfarer.Bedroll) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            mem.camp.remove(p);
        }
        for (int rec : mem.homeless) TroubleDirector.release(l, rec);
        mem.homeless.clear();
        mem.charred.clear();
        mem.data.keySet().removeIf(k -> k.startsWith("charred_"));
        mem.data.remove("smoke_until");
    }
}
