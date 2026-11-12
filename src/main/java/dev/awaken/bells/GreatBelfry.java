package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.kin.KinHome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Great Belfry (SPEC §43.14, §43.16): the S-rank work round your bell frame that leads the Hundred Peal. It needs
 * fifty bells in your song, the Heart-clapper (from an S-rank gate's Lord) and a Sovereign's relic, each set into your
 * bell frame with it in hand; then every beast that lives with you comes to the frame and raises it in half a minute
 * (with none, it goes up at the next song). The Seed Bell, the song's first note, is cast round the Heart-clapper and
 * hangs in its middle; your beasts carry your Great Bells up into it, and any bell on a hook where it stands moves into it.
 */
final class GreatBelfry {
    private GreatBelfry() {}

    static final int RAISE_TICKS = 20 * 30;

    /** The frame used with a Heart-clapper or a relic in hand. True when it was one of them (handled either way). */
    static boolean offer(ServerLevel level, BlockPos pos, ServerPlayer p, ItemStack held) {
        boolean clapper = held.is(BellContent.HEART_CLAPPER.get()), relic = held.is(BellContent.SOVEREIGN_RELIC.get());
        if (!clapper && !relic) return false;
        Optional<HearthRecord> h = HearthService.data(level.getServer()).containing(pos, level.dimension().identifier().toString());
        if (h.isEmpty() || !h.get().belongs(p.getUUID())) {
            Bells.say(p, "[This isn't your bell frame. Set it into the frame in your own Hearth.]");
            return true;
        }
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Home home = w.home(h.get().pos);
        if (home.stage >= 3) {
            Bells.say(p, "[Your Great Belfry already stands. It doesn't need another.]");
            return true;
        }
        if (home.hung.size() < BellTower.GREAT) {
            Bells.say(p, "[A Great Belfry needs fifty bells in your song first. You have " + home.hung.size() + ".]");
            return true;
        }
        if (clapper ? home.clapper : home.relic) {
            Bells.say(p, clapper ? "[The Heart-clapper is already set in the frame.]" : "[A Sovereign's relic is already set in the frame. One is all it needs.]");
            return true;
        }
        if (!p.isCreative()) held.shrink(1);
        if (clapper) home.clapper = true;
        else home.relic = true;
        w.setDirty();
        level.playSound(null, pos, BellContent.TOLL.get(), SoundSource.BLOCKS, 2F, clapper ? 0.5F : 0.8F);
        level.sendParticles(clapper ? ParticleTypes.LAVA : ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        if (!(home.clapper && home.relic)) {
            Bells.say(p, clapper ? "[You set the Heart-clapper into the frame. The other bells hum against it. The Great Belfry still needs a Sovereign's relic.]"
                    : "[You set the relic into the frame. It's cold, and the bells round it go quiet. The Great Belfry still needs the Heart-clapper, from the Lord of an S-rank gate.]");
            return true;
        }
        begin(level, h.get(), home, pos);
        return true;
    }

    /** Both pieces are in: every beast that lives here comes to raise it. */
    private static void begin(ServerLevel level, HearthRecord h, BellWorld.Home home, BlockPos frame) {
        int n = KinHome.gather(level, h.pos, frame, RAISE_TICKS + 40);
        if (n == 0) {
            members(level, h, "[Both pieces are in. Nobody lives with you to raise the Great Belfry, so it'll go up slowly: by the next time your bells ring.]");
            return;
        }
        home.raising = level.getGameTime() + RAISE_TICKS;
        members(level, h, n == 1 ? "[Both pieces are in. Your beast has come to the tower to raise the Great Belfry.]"
                : "[Both pieces are in. All " + n + " of your beasts have come to the tower to raise the Great Belfry.]");
    }

    /** Raising under way: the work's dust and knocks, and the belfry when the time is up. */
    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % 10 != 4) return;
        BellWorld w = BellWorld.get(server);
        for (HearthRecord h : HearthService.data(server).snapshot()) {
            BellWorld.Home home = w.peek(h.pos);
            if (home == null || home.raising == 0 || home.frame == null || !dev.awaken.core.api.Ready.loaded(level, home.frame)) continue;
            if (now < home.raising) {
                BlockPos f = home.frame;
                var r = level.getRandom();
                level.sendParticles(ParticleTypes.CLOUD, f.getX() + 0.5 + r.nextInt(5) - 2, f.getY() + 5 + r.nextInt(6), f.getZ() + 0.5 + r.nextInt(5) - 2, 3, 0.4, 0.4, 0.4, 0.01);
                if (now % 40 == 4) level.playSound(null, f.above(6), r.nextBoolean() ? SoundEvents.STONE_PLACE : SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.5F, 0.8F + r.nextFloat() * 0.3F);
                continue;
            }
            home.raising = 0;
            build(level, h, home);
        }
    }

    /** Whether this home's frame holds both pieces, with fifty bells in its song and no Great Belfry yet. */
    static boolean ready(BellWorld.Home home) {
        return home.clapper && home.relic && home.stage < 3 && home.hung.size() >= BellTower.GREAT;
    }

    /** Puts the Great Belfry up over the tower (clear space allowing) and casts the Seed Bell for everyone who lives here. */
    static boolean build(ServerLevel level, HearthRecord h, BellWorld.Home home) {
        BlockPos f = home.frame;
        if (f == null || !level.getBlockState(f).is(BellContent.BELL_FRAME.get()) || !ready(home)) return false;
        Direction face = level.getBlockState(f).getValue(HorizontalDirectionalBlock.FACING);
        Map<BlockPos, BlockState> plan = BellTower.great(f, face);
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockState now = level.getBlockState(e.getKey());
            if (!now.isAir() && !now.canBeReplaced() && !BellTower.ours(now)) {
                members(level, h, "[The Great Belfry needs a clear space seven wide and twenty-three high round the bell frame. Clear it and it goes up at the next song.]");
                return false;
            }
        }
        // the bells on any hook where it goes up come down first, to be carried into it, never dropped
        List<Carried> carried = new java.util.ArrayList<>();
        java.util.Set<BlockPos> clear = new java.util.HashSet<>(plan.keySet());
        for (BlockPos q : BellTower.tower(f, face).keySet()) if (!plan.containsKey(q) && BellTower.ours(level.getBlockState(q))) clear.add(q);
        for (BlockPos q : clear) lift(level, q, carried);
        // and the Great Bells hung anywhere else at home
        for (Map.Entry<Integer, BlockPos> e : List.copyOf(home.placed.entrySet()))
            if (Hanging.great(BellCatalog.of(e.getKey())) && dev.awaken.core.api.Ready.loaded(level, e.getValue())) lift(level, e.getValue(), carried);
        // an old tower's belfry comes down under the new one
        for (BlockPos q : BellTower.tower(f, face).keySet())
            if (!plan.containsKey(q) && BellTower.ours(level.getBlockState(q))) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
        // the Seed in the middle, the Great Bells round it, the rest on the beams round those
        BlockPos middle = f.above(14);
        if (level.getBlockEntity(middle) instanceof HooksEntity seed) {
            seed.set(0, BellCatalog.of("seed").no(), h.owner);
            Hanging.index(level, h, middle, BellCatalog.of("seed").no());
        }
        for (Carried c : carried) hangCarried(level, h, plan, c);
        home.stage = 3;
        BellWorld.get(level.getServer()).setDirty();
        level.playSound(null, f.above(14), BellContent.TOLL.get(), SoundSource.BLOCKS, 12F, 0.6F);
        level.sendParticles(ParticleTypes.END_ROD, f.getX() + 0.5, f.getY() + 15, f.getZ() + 0.5, 60, 2.5, 3, 2.5, 0.05);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
            if (h.belongs(p.getUUID())) seed(p);
        Awaken.LOG.info("[bells] the Great Belfry built at {} ({} bells)", f.toShortString(), home.hung.size());
        return true;
    }

    /** A bell being carried into the Great Belfry, and who hung it. */
    private record Carried(int no, java.util.@org.jspecify.annotations.Nullable UUID by) {}

    /** Every bell on the hook block at {@code q} taken off it (out of the song for now), to be carried into the belfry. */
    private static void lift(ServerLevel level, BlockPos q, List<Carried> carried) {
        if (!(level.getBlockEntity(q) instanceof HooksEntity be)) return;
        for (int i = 0; i < be.hooks(); i++) {
            int no = be.bell(i);
            if (no < 0) continue;
            carried.add(new Carried(no, be.hungBy(i)));
            be.set(i, -1, null);
            Hanging.unindex(level, q, no);
        }
    }

    /** A carried bell hung on the first free hook of the Great Belfry that takes it, else set down by the fire. */
    private static void hangCarried(ServerLevel level, HearthRecord h, Map<BlockPos, BlockState> plan, Carried c) {
        int no = c.no();
        java.util.UUID by = c.by();
        boolean great = Hanging.great(BellCatalog.of(no));
        for (BlockPos q : plan.keySet()) {
            if (!(level.getBlockEntity(q) instanceof HooksEntity be) || level.getBlockState(q).is(BellContent.GREAT_BEAM.get()) != great) continue;
            for (int i = 0; i < be.hooks(); i++)
                if (be.bell(i) < 0) {
                    be.set(i, no, by);
                    Hanging.index(level, h, q, no);
                    return;
                }
        }
        net.minecraft.world.Containers.dropItemStack(level, h.pos.getX() + 0.5, h.pos.getY() + 1, h.pos.getZ() + 0.5, BellItem.of(no));
    }

    /** The Seed Bell, for one who lives under a Great Belfry (now, or when they're next about). */
    static void seed(ServerPlayer p) {
        if (Bells.ring(p, "seed", "cast round the Heart-clapper the day your Great Belfry went up"))
            Bells.say(p, "[The Great Belfry stands. In the middle of it hangs the Seed Bell, cast round the Heart-clapper: the first note of the song.]");
    }

    /** The Hearth page's Great Belfry line (SPEC §43.14): "" until A rank (the page keeps it dim), then what's still needed. */
    static String line(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        BellWorld.Home home = h.map(r -> BellWorld.get(p.level().getServer()).peek(r.pos)).orElse(null);
        if (home != null && home.stage >= 3)
            return home.pealed >= 0 ? "Your Great Belfry stands, and all hundred bells have rung in it together."
                    : "Your Great Belfry stands. When all hundred bells hang in it, ring them.";
        if (dev.awaken.progression.Progression.rank(p).ordinal() < dev.awaken.progression.Rank.A.ordinal()) return "";
        List<String> needs = new java.util.ArrayList<>();
        if (home == null || home.hung.size() < BellTower.GREAT) needs.add("fifty bells in your song");
        if (home == null || !home.clapper) needs.add("the Heart-clapper, from the Lord of an S-rank gate");
        if (home == null || !home.relic) needs.add("a Sovereign's relic");
        if (needs.isEmpty()) return "The Great Belfry. Both pieces are in the frame; it goes up when your beasts raise it, or at the next song.";
        return "Only a Great Belfry can lead the Hundred Peal. It needs " + String.join(", ", needs.subList(0, needs.size() - 1))
                + (needs.size() > 1 ? " and " : "") + needs.getLast() + ", set into your bell frame.";
    }

    private static void members(ServerLevel level, HearthRecord h, String line) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers())
            if (h.belongs(p.getUUID())) Bells.say(p, line);
    }
}
