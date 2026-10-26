package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.core.api.EconomyHooks;
import dev.awaken.core.api.Notices;
import dev.awaken.signal.Signal;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Festival (SPEC §28.7, PLAN 6.3): every ten play-days or so a village keeps a festival (the harvest; the first snow
 * in the frost; the bell's day where a hunter hung its bell again). From dusk lantern strings hang over the green on
 * poles, a bonfire burns and a fiddle-and-drum jig is heard far off (note blocks, played by the musicians by the fire);
 * the village rings a dance round the fire, the children run, the market's shut. The elder judges the fishing and
 * cooking (hand over your best fish or dish); the rooftop race runs over the roofs round the green against the village's
 * best. Fireworks at the height of it. Prizes and standing. Festivals go on with {@code calm_villages} on.
 */
final class Festival implements Plot {
    static final int START = 11800, FIREWORKS_1 = 14500, FIREWORKS_2 = 16200, END = 18600;

    // ---- the calendar ---------------------------------------------------------------------------------------------------

    static void consider(Ctx c, VillageService.Live l) {
        TroubleState.Memory mem = c.st().memory(l.key());
        long h = Taken.mix(l.plan().seed, 0, 41);
        if (mem.festival < 0) {
            mem.festival = c.day() + 2 + Math.floorMod(h, 5L);
            c.st().setDirty();
        }
        if (c.day() < mem.festival || c.tod() < START || c.tod() > 13500 || c.st().festival(l.key()) != null) return;
        // a festival never runs over a trouble's worst night (a raid, the alarm): it waits a day
        Trouble busy = c.st().of(l.key());
        if (busy != null && (busy.kind.equals(TroubleKind.BURNING.id) || busy.kind.equals(TroubleKind.TAKEN.id) && busy.stage <= Taken.MORNING)) {
            mem.festival = c.day() + 1;
            return;
        }
        if (TroubleDirector.start(c.level(), l, TroubleKind.FESTIVAL, "calendar") != null) mem.festival = c.day() + 9 + Math.floorMod(h >>> 8, 3L);
    }

    static void newDay(ServerLevel level, TroubleState st, long day) {
        // (the calendar is read at dusk by consider)
    }

    static String name(TroubleState.Memory mem, VillagePlan p) {
        if (!mem.bellBy.isEmpty()) return "the Bell's Day";
        if (p.family == dev.awaken.city.VillageStyles.FROST) return "the First Snow";
        return "the Harvest Festival";
    }

    // ---- setting it up ----------------------------------------------------------------------------------------------------

    @Override
    public boolean begin(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return false;
        VillagePlan p = l.plan();
        TroubleState.Memory mem = c.st().memory(l.key());
        t.placeName = name(mem, p);
        t.place = BlockPos.containing(TroubleDirector.well(l)).asLong();
        t.due = c.day() + 1;
        // the bonfire on the green, across from the board
        double a = Math.atan2(p.notice[1] - p.well[1], p.notice[0] - p.well[0]) + Math.PI;
        Vec3 fire = TroubleDirector.green(l, a, Math.max(2.5, p.greenR * 0.55));
        BlockPos fp = BlockPos.containing(fire);
        if (c.level().getBlockState(fp).isAir() && c.level().getBlockState(fp.below()).isSolid())
            TroubleDirector.lay(c.level(), t, fp, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        t.put("fire", fp.asLong());
        // the village's best runner over the roofs: whose time the race is against
        int racer = -1;
        for (int i = 0; i < l.settlement().people() && racer < 0; i++) if (l.settlement().age(i) == 1 && !l.settlement().isAway(i)) racer = i;
        t.put("racer", racer);
        if (racer >= 0) t.text = TroubleText.given(l.settlement().personName(racer));
        // poles round the green, lanterns on top; the strings between them drawn by the clients
        List<BlockPos> tops = poles(c, t, l);
        long[] ends = ropes(tops, fire);
        t.put("ropes", ends.length);
        for (int i = 0; i < ends.length; i++) t.put("e" + i, ends[i]);
        TroubleNet.festoons(c.level(), l.key(), TroubleDirector.well(l), ends);
        Villages.history(c.level().getServer(), l.key(), l.plan().name + " kept " + t.placeName + ".");
        for (ServerPlayer pl : c.level().players()) {
            if (pl.distanceToSqr(TroubleDirector.well(l)) > 220 * 220) continue;
            Signal.toast(pl, Component.literal("Lanterns over " + l.plan().name + " · " + t.placeName.substring(0, 1).toUpperCase() + t.placeName.substring(1)),
                    Notices.Tier.AMBIENT, "village_festival");
        }
        Awaken.LOG.info("[trouble] {} keeps {}", l.plan().name, t.placeName);
        return true;
    }

    /** Six poles round the green (a fence post four high with a lantern), where the ground takes them. */
    private static List<BlockPos> poles(Ctx c, Trouble t, VillageService.Live l) {
        VillagePlan p = l.plan();
        List<BlockPos> tops = new ArrayList<>();
        int n = p.greenR >= 7 ? 6 : 5;
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n + 0.3;
            for (double r = p.greenR + 0.5; r >= p.greenR - 2.5 && tops.size() <= k; r -= 1) {
                Vec3 at = TroubleDirector.green(l, a, r);
                BlockPos base = BlockPos.containing(at);
                boolean clear = true;
                for (int dy = 0; dy < 6 && clear; dy++) clear = c.level().getBlockState(base.above(dy)).isAir();
                if (!clear || !c.level().getBlockState(base.below()).isSolid()) continue;
                for (int dy = 0; dy < 4; dy++) TroubleDirector.lay(c.level(), t, base.above(dy), Blocks.SPRUCE_FENCE.defaultBlockState());
                TroubleDirector.lay(c.level(), t, base.above(4), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
                tops.add(base.above(4));
            }
        }
        return tops;
    }

    /** The strings: round the ring of poles, and three across the green to the fire's side. */
    private static long[] ropes(List<BlockPos> tops, Vec3 fire) {
        List<Long> out = new ArrayList<>();
        int n = tops.size();
        for (int k = 0; k < n && n >= 2; k++) {
            out.add(tops.get(k).asLong());
            out.add(tops.get((k + 1) % n).asLong());
        }
        for (int k = 0; k + n / 2 < n && k < 3; k++) {
            out.add(tops.get(k).asLong());
            out.add(tops.get(k + n / 2).asLong());
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    // ---- the night ---------------------------------------------------------------------------------------------------------

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        if (c.tod() >= END || c.tod() < START - 400 || c.day() > t.day) {
            finish(c, t, l);
            return;
        }
        if (c.now() % 200 == 13) {
            long[] ends = new long[(int) t.get("ropes", 0)];
            for (int i = 0; i < ends.length; i++) ends[i] = t.get("e" + i, 0);
            TroubleNet.festoons(c.level(), l.key(), TroubleDirector.well(l), ends);
        }
        Vec3 fire = Vec3.atBottomCenterOf(BlockPos.of(t.get("fire", t.place)));
        Set<Integer> used = new HashSet<>();
        if (t.get("racer", -1) >= 0) used.add((int) t.get("racer", -1));
        // the musicians by the fire (the jig plays from them: music())
        List<Integer> band = TroubleDirector.crowd(l, 2, used, "tavernkeeper", "miller");
        for (int i = 0; i < band.size(); i++) {
            Vec3 at = TroubleDirector.stand(c.level(), l, fire.x + 3.4 + i * 1.1, fire.z + 2.4 - i * 0.6);
            TroubleDirector.stage(l, band.get(i), at, TroubleDirector.yaw(at, fire), i == 0 ? "talk" : "cheer", "");
            used.add(band.get(i));
        }
        t.put("band", band.isEmpty() ? -1 : band.getFirst());
        // the ring dance round the fire, turning
        List<Integer> dancers = TroubleDirector.crowd(l, 10, used, "farmer", "shepherd", "fisher", "woodcutter", "resident", "stallkeeper", "smith", "watch");
        double turn = (c.now() - t.since) / 20.0 * 0.11;
        for (int i = 0; i < dancers.size(); i++) {
            double a = turn + i * Math.PI * 2 / dancers.size();
            Vec3 at = TroubleDirector.stand(c.level(), l, fire.x + Math.cos(a) * 3.3, fire.z + Math.sin(a) * 3.3);
            // facing round the ring, a little in toward the fire
            Vec3 next = new Vec3(fire.x + Math.cos(a + 0.6) * 2.6, at.y, fire.z + Math.sin(a + 0.6) * 2.6);
            TroubleDirector.stage(l, dancers.get(i), at, TroubleDirector.yaw(at, next), "dance", "");
            used.add(dancers.get(i));
        }
        // the children run about the green's edge; the old watch from the benches
        int kids = 0, old = 0;
        for (int i = 0; i < l.settlement().people(); i++) {
            if (used.contains(i) || l.settlement().isAway(i)) continue;
            int age = l.settlement().age(i);
            if (age == 0 && kids < 6) {
                double a = -turn * 1.6 + kids * 1.1;
                Vec3 at = TroubleDirector.green(l, a, l.plan().greenR - 1.2);
                TroubleDirector.stage(l, i, at, TroubleDirector.yaw(at, fire), "play", "");
                kids++;
            } else if (age == 3 && old < 3) {
                Vec3 at = TroubleDirector.green(l, Math.PI + old * 0.5, l.plan().greenR - 0.6);
                TroubleDirector.stage(l, i, at, TroubleDirector.yaw(at, fire), "cheer", "");
                old++;
            }
        }
        // the race's runner at the well (the village's best over the roofs)
        int racer = (int) t.get("racer", -1);
        if (racer >= 0) {
            Vec3 w = TroubleDirector.well(l);
            Vec3 at = TroubleDirector.stand(c.level(), l, w.x + (w.x - fire.x) * 0.35 + 1.4, w.z + (w.z - fire.z) * 0.35);
            TroubleDirector.stage(l, racer, at, Float.NaN, "arms_crossed", "");
        }
        // the judge at the board
        int judge = Burning.elderOf(l);
        if (judge >= 0 && !used.contains(judge)) {
            VillagePlan p = l.plan();
            Vec3 board = new Vec3(p.ox + p.notice[0] + 0.5, fire.y, p.oz + p.notice[1] + 0.5);
            Vec3 at = TroubleDirector.stand(c.level(), l, board.x + 1.2, board.z + 1.2);
            TroubleDirector.stage(l, judge, at, Float.NaN, "arms_crossed", "cane");
            t.kin = judge;
        }
        if (c.tod() >= FIREWORKS_1 && t.get("fw1", 0) == 0) {
            fireworks(c, t, l, fire);
            t.put("fw1", 1);
        }
        if (c.tod() >= FIREWORKS_2 && t.get("fw2", 0) == 0) {
            fireworks(c, t, l, fire);
            t.put("fw2", 1);
        }
    }

    private void fireworks(Ctx c, Trouble t, VillageService.Live l, Vec3 fire) {
        java.util.Random r = new java.util.Random(Taken.mix(l.plan().seed, c.now(), 43));
        int[][] palettes = {{0xF2B233, 0xE85B2E}, {0x6CC5FF, 0xFFFFFF}, {0x8EE36B, 0xF2E24D}, {0xC86BFF, 0xFF8AD8}};
        for (int i = 0; i < 6; i++) {
            int[] pal = palettes[r.nextInt(palettes.length)];
            FireworkExplosion ex = new FireworkExplosion(r.nextInt(3) == 0 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.BURST,
                    IntArrayList.of(pal[0]), IntArrayList.of(pal[1]), true, r.nextBoolean());
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + r.nextInt(2), List.of(ex)));
            Vec3 at = TroubleDirector.green(l, r.nextDouble() * Math.PI * 2, 2 + r.nextDouble() * Math.max(1, l.plan().greenR - 2));
            FireworkRocketEntity e = new FireworkRocketEntity(c.level(), at.x, at.y + 0.5, at.z, rocket);
            c.level().addFreshEntity(e);
        }
    }

    // ---- the music: a jig on note blocks, from the musicians (played every tick from FestivalTick) ----------------------------

    /** The tune: (eighth-note step, flute note) with 0 a rest; note indices as a note block's (F#3 = 0 .. F#5 = 24). */
    static final int[] TUNE = {
            15, 12, 15, 20, 15, 12, 13, 10, 13, 17, 13, 10, 12, 8, 12, 15, 12, 8, 10, 12, 13, 15, 0, 0,
            20, 19, 17, 15, 17, 15, 13, 15, 13, 12, 10, 8, 10, 12, 13, 15, 17, 19, 20, 15, 12, 8, 0, 0};
    /** The bass under each bar (guitar), on its first and fourth eighth. */
    static final int[] BASS = {8, 13, 8, 15, 8, 13, 15, 8};
    static final int EIGHTH = 3;

    static void music(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TroubleState st = TroubleDirector.last;
        if (st == null) return;
        for (Trouble t : st.troubles) {
            if (!t.kind.equals(TroubleKind.FESTIVAL.id)) continue;
            VillageService.Live l = VillageService.live(t.village);
            int band = (int) t.get("band", -1);
            if (l == null || band < 0) continue;
            int tod = TroubleText.tod(level);
            if (tod < START || tod > END - 200) continue;
            long tick = level.getGameTime() - t.since;
            if (tick % EIGHTH != 0) continue;
            int step = (int) (tick / EIGHTH % TUNE.length);
            Vec3 at = l.settlement().where(band);
            if (at == null) at = Vec3.atCenterOf(BlockPos.of(t.get("fire", t.place)));
            BlockPos pos = BlockPos.containing(at);
            int note = TUNE[step];
            if (note > 0) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.RECORDS, 3.5f, pitch(note));
            int inBar = step % 6, bar = step / 6;
            if (inBar == 0 || inBar == 3) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_GUITAR.value(), SoundSource.RECORDS, 3.0f, pitch(BASS[bar % BASS.length]));
                level.playSound(null, pos, (inBar == 0 ? SoundEvents.NOTE_BLOCK_BASEDRUM : SoundEvents.NOTE_BLOCK_HAT).value(), SoundSource.RECORDS, 2.6f, 1f);
            }
        }
    }

    static float pitch(int note) {
        return (float) Math.pow(2.0, (note - 12) / 12.0);
    }

    // ---- the contests --------------------------------------------------------------------------------------------------------

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null) return false;
        if (rec == t.kin) return judge(c, t, l, p, rec);
        if (rec == (int) t.get("racer", -1)) {
            Race.offer(c, t, l, p, rec);
            return true;
        }
        if (rec == (int) t.get("band", -1)) {
            l.settlement().say(p, rec, TroubleText.line(c.level(), "village.trouble.festival.band", c.now() / 60), 90);
            return true;
        }
        return false;
    }

    /** The elder judges: your best fish or dish counts (the best you've handed over this festival). */
    private boolean judge(Ctx c, Trouble t, VillageService.Live l, ServerPlayer p, int rec) {
        ItemStack held = p.getMainHandItem();
        int score = score(held);
        String key = "c_" + p.getUUID().hashCode();
        if (score == 0) {
            int best = (int) t.get(key, 0);
            l.settlement().say(p, rec, best > 0 ? "Your entry stands at " + best + ". Something finer, and I'll taste it."
                    : "The fishing and the cooking! Bring me your best catch or your best dish, and we'll see who wins the ribbon.", 140);
            return true;
        }
        held.shrink(1);
        if (score > t.get(key, 0)) t.put(key, score);
        if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
        l.settlement().say(p, rec, TroubleText.line(c.level(), score >= 6 ? "village.trouble.festival.judge_good" : "village.trouble.festival.judge_ok", c.now()), 120);
        VillageService.standing(p, l, 2, "festival entry");
        c.st().setDirty();
        return true;
    }

    /** A festival entry's worth: fish by rarity, a cooked fish more, a craft meal most. */
    static int score(ItemStack s) {
        if (s.is(Items.TROPICAL_FISH) || s.is(Items.PUFFERFISH)) return 5;
        if (s.is(Items.COOKED_SALMON)) return 4;
        if (s.is(Items.COOKED_COD)) return 3;
        if (s.is(Items.SALMON)) return 2;
        if (s.is(Items.COD)) return 1;
        if (s.is(Items.PUMPKIN_PIE) || s.is(Items.CAKE) || s.is(Items.RABBIT_STEW)) return 5;
        if (s.is(Items.BEETROOT_SOUP) || s.is(Items.MUSHROOM_STEW) || s.is(Items.BREAD)) return 2;
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
        if (id.getNamespace().equals("awaken") && s.has(DataComponents.FOOD)) return 7; // a craft's meal
        return 0;
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {}

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        return (c.live() != null ? c.live().plan().name : "The village") + " keeps " + t.placeName + " tonight: the dance, the contests, the rooftop race.";
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        return null;
    }

    // ---- the end ----------------------------------------------------------------------------------------------------------------

    private void finish(Ctx c, Trouble t, VillageService.Live l) {
        // the contests' winners
        UUID best = null;
        int bestScore = 0;
        for (UUID id : t.helpers) {
            int s = (int) t.get("c_" + id.hashCode(), 0);
            if (s > bestScore) {
                bestScore = s;
                best = id;
            }
        }
        List<String> lines = new ArrayList<>();
        if (best != null) {
            ServerPlayer w = c.level().getServer().getPlayerList().getPlayer(best);
            if (w != null) {
                EconomyHooks.pay(w, 8, Awaken.id("village/festival/" + t.id + "/contest"));
                VillageService.standing(w, l, 8, "festival prize");
                ItemStack ribbon = Keepsake.make(l.plan().name + "'s Ribbon", "Won at " + t.placeName + ", for the best on the judge's table.");
                if (!w.getInventory().add(ribbon)) w.drop(ribbon, false, net.minecraft.util.Prediction.SERVER_ONLY);
                Signal.toast(w, Component.literal("You won the judging at " + l.plan().name + "'s " + t.placeName.replace("the ", "")), Notices.Tier.IMPORTANT,
                        "village_festival");
                lines.add(w.getName().getString() + " won the judging");
            }
        }
        String race = Race.winner(c, t, l);
        if (race != null) lines.add(race + " won the rooftop race");
        if (!lines.isEmpty()) Villages.history(c.level().getServer(), l.key(), t.placeName.substring(0, 1).toUpperCase() + t.placeName.substring(1) + ": "
                + String.join("; ", lines) + ".");
        Villages.addMood(c.level().getServer(), l.key(), 10);
        Villages.addProsperity(c.level().getServer(), l.key(), 2);
        TroubleNet.festoons(c.level(), l.key(), TroubleDirector.well(l), new long[0]);
        TroubleDirector.end(c, t);
    }

    @Override
    public void miss(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l != null) TroubleNet.festoons(c.level(), l.key(), TroubleDirector.well(l), new long[0]);
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        Race.stopAll(c, t);
    }
}
