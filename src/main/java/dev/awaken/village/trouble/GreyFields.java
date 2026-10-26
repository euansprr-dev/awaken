package dev.awaken.village.trouble;

import dev.awaken.core.api.GateHooks;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The Grey Fields (SPEC §28.7, PLAN 6.3): a gate near a village is due to break (its own breaks-in clock). The elder
 * asks you to close it before it spills onto their fields. Close it and nothing happens: the best ending, and they know
 * it. Let it break and the blight greys the fields (the crops wither, the tilled earth goes dead) and some of the people
 * are Hollowed: they lie abed and don't know their own. An herb remedy (sunleaf, moonmoss and a bottle) given to the healer
 * or the elder brings one back; when the last is back the fields are tilled and sown again. A village whose bell a player
 * hung keeps the blight off its fields.
 */
final class GreyFields implements Plot {
    static final int REACH = 500;

    static @Nullable String cause(Ctx c, VillageService.Live l) {
        BlockPos o = new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz);
        for (GateHooks.GateView g : GateHooks.near(c.level(), o, REACH)) {
            long in = g.breaksOnPlayDay() - c.day();
            boolean standing = g.state().equals("READY") || g.state().equals("OPEN") || g.state().equals("ACTIVE");
            if (standing && in >= 1 && in <= 2) return "gate:" + g.id();
        }
        return null;
    }

    static Optional<GateHooks.GateView> gate(Ctx c, Trouble t) {
        return GateHooks.get(c.level(), t.cause.substring(5));
    }

    @Override
    public boolean begin(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null || !t.cause.startsWith("gate:")) return false;
        GateHooks.GateView g = gate(c, t).orElse(null);
        if (g == null) return false;
        t.place = g.pos().asLong();
        t.placeName = "the gate " + TroubleText.compass(g.pos().getX() - l.plan().ox, g.pos().getZ() - l.plan().oz);
        t.kin = Burning.elderOf(l);
        t.due = g.breaksOnPlayDay() + 1;
        t.put("breaks", g.breaksOnPlayDay());
        Villages.history(c.level().getServer(), l.key(), "A gate stood " + TroubleText.compass(g.pos().getX() - l.plan().ox, g.pos().getZ() - l.plan().oz)
                + " of the fields, due to break.");
        return true;
    }

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        GateHooks.GateView g = gate(c, t).orElse(null);
        String state = g == null ? "RELEASED" : g.state();
        if (state.equals("CLEARED") || state.equals("RELEASED")) {
            closed(c, t, l);
            return;
        }
        if (state.equals("BROKEN")) {
            broke(c, t, l);
            TroubleDirector.end(c, t);
            return;
        }
        // the elder at the green's edge, looking toward the gate
        if (t.kin >= 0) {
            Vec3 w = TroubleDirector.well(l);
            BlockPos gp = BlockPos.of(t.place);
            double a = Math.atan2(gp.getZ() - w.z, gp.getX() - w.x);
            Vec3 at = TroubleDirector.green(l, a, l.plan().greenR - 0.5);
            boolean someone = false;
            for (ServerPlayer p : c.level().players()) someone |= p.distanceToSqr(at) < 12 * 12 && !t.helped(p.getUUID());
            TroubleDirector.stage(l, t.kin, at, someone ? Float.NaN : TroubleDirector.yaw(at, Vec3.atCenterOf(gp)), someone ? "talk" : "arms_crossed", "cane");
        }
    }

    private void closed(Ctx c, Trouble t, VillageService.Live l) {
        // nothing happens: the best ending, and the village knows who to thank
        if (!t.helpers.isEmpty()) {
            List<ServerPlayer> paid = TroubleDirector.pay(c, t, TroubleDirector.well(l), 2000, 14, 15, 0.35, "The gate's closed. " + l.plan().name + "'s fields are safe", null);
            Villages.history(c.level().getServer(), l.key(), TroubleDirector.names(paid) + " closed " + t.placeName + " before it broke. The fields never knew.");
        } else Villages.history(c.level().getServer(), l.key(), "Someone closed " + t.placeName + " before it broke.");
        TroubleDirector.end(c, t);
    }

    /** It broke: the fields go grey and some are Hollowed (unless the bell a player hung keeps the blight off). */
    static void broke(Ctx c, Trouble t, VillageService.Live l) {
        TroubleState.Memory mem = c.st().memory(l.key());
        if (!mem.bellBy.isEmpty() && mem.bell == 0) {
            Villages.history(c.level().getServer(), l.key(), t.placeName + " broke. The blight came to the edge of the fields and the bell kept it there.");
            return;
        }
        grey(c.level(), l, true);
        mem.grey = true;
        // two or three grown-ups Hollowed: abed, not knowing their own
        int n = 2 + (int) Math.floorMod(t.id, 2L);
        List<Integer> hollow = TroubleDirector.crowd(l, n, new java.util.HashSet<>(mem.homeless), "farmer", "shepherd");
        for (int rec : hollow) {
            if (!mem.hollowed.contains(rec)) mem.hollowed.add(rec);
            l.settlement().away(rec, true);
        }
        Villages.history(c.level().getServer(), l.key(), t.placeName + " broke. Blight greyed the fields, and " + hollow.size() + " of us were Hollowed.");
        Villages.addMood(c.level().getServer(), l.key(), -20);
        Villages.addProsperity(c.level().getServer(), l.key(), -10);
        c.st().setDirty();
    }

    /** The fields grey (crops withered, the earth gone dead) or green again (tilled and sown). */
    static void grey(ServerLevel level, VillageService.Live l, boolean grey) {
        VillagePlan p = l.plan();
        for (VillagePlan.Field f : p.fields) {
            Block crop = BuiltInRegistries.BLOCK.getValue(Identifier.parse(f.crop().contains(":") ? f.crop() : "minecraft:" + f.crop()));
            for (int x = f.x0(); x <= f.x1(); x++) {
                for (int z = f.z0(); z <= f.z1(); z++) {
                    BlockPos soil = new BlockPos(p.ox + x, p.baseY + f.lift(), p.oz + z);
                    if (!level.isLoaded(soil)) continue;
                    BlockState s = level.getBlockState(soil), above = level.getBlockState(soil.above());
                    if (grey) {
                        if (s.is(Blocks.FARMLAND)) level.setBlock(soil, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                        if (above.getBlock() instanceof CropBlock || above.is(Blocks.SHORT_GRASS))
                            level.setBlock(soil.above(), ((x + z) & 3) == 0 ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    } else {
                        if (s.is(Blocks.COARSE_DIRT)) level.setBlock(soil, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
                        if ((above.isAir() || above.is(Blocks.DEAD_BUSH)) && crop instanceof CropBlock && level.getBlockState(soil).is(Blocks.FARMLAND))
                            level.setBlock(soil.above(), crop.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** An herb remedy to the healer or the elder: one of the Hollowed comes back; the last brings the fields back. */
    static boolean cure(Ctx c, VillageService.Live l, ServerPlayer p, int rec) {
        TroubleState.Memory mem = c.st().peek(l.key());
        if (mem == null || mem.hollowed.isEmpty()) return false;
        String job = l.settlement().job(rec);
        if (!job.equals("healer") && !job.equals("elder")) return false;
        ItemStack held = p.getMainHandItem();
        if (!held.is(TroubleContent.GREY_REMEDY.get())) {
            if (TroubleDirector.busy(l.key())) return false; // their trouble's asker speaks first
            l.settlement().say(p, rec, "Our Hollowed lie abed. An herb remedy would bring them back: sunleaf and moonmoss, steeped in a bottle.", 140);
            return true;
        }
        held.shrink(1);
        int back = mem.hollowed.removeFirst();
        l.settlement().away(back, false);
        String name = TroubleText.given(l.settlement().personName(back));
        l.settlement().say(p, rec, name + " opened their eyes and asked for their mother. Thank you.", 140);
        c.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        VillageService.standing(p, l, 6, "cured " + name);
        Villages.history(c.level().getServer(), l.key(), p.getName().getString() + " brought " + name + " back from the Hollowing.");
        if (mem.hollowed.isEmpty() && mem.grey) {
            grey(c.level(), l, false);
            mem.grey = false;
            Villages.history(c.level().getServer(), l.key(), "The last of the Hollowed woke. The fields were tilled and sown again.");
            p.sendOverlayMessage(Component.literal(l.plan().name + "'s fields are tilled and sown again"));
            Villages.addMood(c.level().getServer(), l.key(), 15);
        }
        c.st().setDirty();
        return true;
    }

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null || rec != t.kin && rec != Runner.of(t)) return false;
        long in = t.get("breaks", c.day()) - c.day();
        String when = in <= 0 ? "today" : in == 1 ? "tomorrow" : "in two days";
        if (t.helped(p.getUUID())) {
            l.settlement().say(p, rec, "It breaks " + when + ". " + TroubleText.line(c.level(), "village.trouble.grey.again", c.now() / 100), 100);
            return true;
        }
        l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.grey.ask", c.now() / 100 + p.getId())).replace("{when}", when), 140);
        TroubleDirector.card(p, t, "The Grey Fields · " + l.plan().name, t.placeName.substring(0, 1).toUpperCase() + t.placeName.substring(1) + " of " + l.plan().name
                + " breaks " + when + ". Close it before it spills onto their fields.");
        return true;
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l != null && t.kin >= 0) l.settlement().say(p, t.kin, TroubleText.line(c.level(), "village.trouble.grey.thanks", c.now()), 120);
    }

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        long in = t.get("breaks", c.day()) - c.day();
        String v = c.live() != null ? c.live().plan().name : "the village";
        return "Close " + t.placeName + " of " + v + " before it breaks (" + (in <= 0 ? "today" : in == 1 ? "tomorrow" : in + " days") + ").";
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        return BlockPos.of(t.place);
    }

    @Override
    public void miss(Ctx c, Trouble t) {
        // the gate's clock decides: broken, it's the blight; still standing (stabilised), nothing came of it
        VillageService.Live l = c.live();
        GateHooks.GateView g = gate(c, t).orElse(null);
        if (l != null && g != null && g.state().equals("BROKEN")) broke(c, t, l);
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        TroubleDirector.release(l, t.kin);
        Runner.release(c, t, l);
    }
}
