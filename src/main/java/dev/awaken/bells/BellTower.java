package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.kin.KinHome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Your bell tower (SPEC §43.7). The bell frame, set inside your own Hearth, is where your bells hang. At twenty bells a
 * timber belfry goes up round it, and at fifty a stone tower under the belfry (each built only into clear space; if
 * something's in the way the System says what room it needs and tries again at the next song). At dawn and dusk it rings
 * your song, every bell held by anyone who lives there in its place and a dull knock for each one missing, heard 256
 * blocks off; and your beasts take it by how many bells there are.
 */
final class BellTower {
    private BellTower() {}

    static final int DAWN_SONG = 23_600, DUSK_SONG = 11_600, HEAR = 256;
    static final int BELFRY = 20, TOWER = 50;

    // ---- the frame ----------------------------------------------------------------------------------------------------

    /** A frame was set down: kept if it's inside the placer's own Hearth and the home has no other, else it pops off. */
    static void placed(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
        Optional<HearthRecord> h = HearthService.data(level.getServer()).containing(pos, level.dimension().identifier().toString());
        if (by == null || h.isEmpty() || !h.get().belongs(by.getUUID())) {
            level.destroyBlock(pos, true);
            if (by != null) Bells.say(by, "[Your bells only hang inside your own Hearth. Set the frame down there.]");
            return;
        }
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Home home = w.home(h.get().pos);
        if (home.frame != null && !home.frame.equals(pos) && level.getBlockState(home.frame).is(BellContent.BELL_FRAME.get())) {
            level.destroyBlock(pos, true);
            Bells.say(by, "[Your bells already hang on the frame " + Bells.toward(home.frame.getX() - by.getX(), home.frame.getZ() - by.getZ())
                    + ". Take that one down first if you want them here.]");
            return;
        }
        home.frame = pos.immutable();
        home.stage = 0;
        w.setDirty();
        hang(level.getServer(), h.get());
        int n = home.hung.size();
        Bells.say(by, "[Your bells hang here now: " + n + (n == 1 ? " bell" : " bells") + ". They ring your song at dawn and dusk.]");
        level.playSound(null, pos, BellContent.TOLL.get(), SoundSource.BLOCKS, 2F, 1.4F);
    }

    static void used(ServerLevel level, BlockPos pos, ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(level.getServer()).containing(pos, level.dimension().identifier().toString());
        BellWorld.Home home = h.map(r -> BellWorld.get(level.getServer()).peek(r.pos)).orElse(null);
        int n = home == null ? 0 : home.hung.size();
        String next = n < BELFRY ? (BELFRY - n) + " more and a belfry goes up round it." : n < TOWER ? (TOWER - n) + " more and it becomes a tower."
                : "Every bell you find from here makes the song fuller.";
        Bells.say(p, "[" + n + " of 100 bells hang here. " + next + "]");
        if (home != null && !home.hung.isEmpty()) {
            int pick = new ArrayList<>(home.hung).get(level.getRandom().nextInt(home.hung.size()));
            level.playSound(null, pos, BellContent.note(pick), SoundSource.BLOCKS, 1.5F, 1F);
        }
    }

    /** Hangs every bell the household holds (those online now; the rest were hung when they rang) and grows the tower. */
    static void hang(MinecraftServer server, HearthRecord h) {
        BellWorld w = BellWorld.get(server);
        BellWorld.Home home = w.home(h.pos);
        int before = home.hung.size();
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (h.belongs(p.getUUID())) home.hung.addAll(Bells.book(p).held.keySet());
        if (home.hung.size() != before) w.setDirty();
        grow(server.overworld(), h, home);
    }

    private static @Nullable BlockPos frame(ServerLevel level, BellWorld.Home home) {
        return home.frame != null && level.isLoaded(home.frame) && level.getBlockState(home.frame).is(BellContent.BELL_FRAME.get()) ? home.frame : null;
    }

    // ---- growing ---------------------------------------------------------------------------------------------------

    /** When each home was last told its belfry has no room (play-day), so it's said once a day. Not saved. */
    private static final Map<Long, Long> TOLD = new HashMap<>();

    static void grow(ServerLevel level, HearthRecord h, BellWorld.Home home) {
        int want = home.hung.size() >= TOWER ? 2 : home.hung.size() >= BELFRY ? 1 : 0;
        BlockPos f = frame(level, home);
        if (want <= home.stage || f == null) return;
        Direction face = level.getBlockState(f).getValue(HorizontalDirectionalBlock.FACING);
        Map<BlockPos, BlockState> plan = want == 2 ? tower(f, face) : belfry(f, 0);
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockState now = level.getBlockState(e.getKey());
            if (!now.isAir() && !now.canBeReplaced() && !ours(now)) {
                long day = dev.awaken.core.PlayDays.current(level.getServer());
                if (TOLD.getOrDefault(h.pos.asLong(), -1L) != day) {
                    TOLD.put(h.pos.asLong(), day);
                    for (ServerPlayer p : level.players())
                        if (h.belongs(p.getUUID()))
                            Bells.say(p, "[Your " + (want == 2 ? "tower" : "belfry") + " is ready to go up, but it needs a clear space five wide and "
                                    + (want == 2 ? "fifteen" : "ten") + " high round the bell frame. Clear it and it goes up at the next song.]");
                }
                return;
            }
        }
        // a tower takes the belfry up with it: what's left of the old one below the new belfry comes down first
        if (want == 2 && home.stage == 1)
            for (BlockPos q : belfry(f, 0).keySet())
                if (!plan.containsKey(q) && ours(level.getBlockState(q))) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
        home.stage = want;
        BellWorld.get(level.getServer()).setDirty();
        level.playSound(null, f, BellContent.TOLL.get(), SoundSource.BLOCKS, 8F, 1F);
        for (ServerPlayer p : level.players())
            if (h.belongs(p.getUUID()))
                Bells.say(p, want == 2 ? "[Your bells have a tower now. It can be heard a long way off.]" : "[A belfry has gone up round your bells.]");
        Awaken.LOG.info("[bells] {} built at {} ({} bells)", want == 2 ? "tower" : "belfry", f.toShortString(), home.hung.size());
    }

    /** Blocks a belfry is made of: a tower may build over its own belfry. */
    private static boolean ours(BlockState s) {
        return s.is(Blocks.STRIPPED_DARK_OAK_LOG) || s.is(Blocks.DARK_OAK_PLANKS) || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.DEEPSLATE_TILE_SLAB)
                || s.is(Blocks.DEEPSLATE_TILE_STAIRS) || s.is(Blocks.BELL) || s.is(Blocks.DEEPSLATE_TILE_WALL);
    }

    /** The timber belfry, its floor {@code base} above the frame: posts, a beam ring, cross beams, bells under them, a tiled roof. */
    static Map<BlockPos, BlockState> belfry(BlockPos f, int base) {
        Map<BlockPos, BlockState> out = new java.util.LinkedHashMap<>();
        BlockState post = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        for (int y = 0; y <= 5; y++)
            for (int sx = -2; sx <= 2; sx += 4)
                for (int sz = -2; sz <= 2; sz += 4) out.put(f.offset(sx, base + y, sz), post);
        // the bells hang from cross beams: four in a belfry, nine once it's a tower's top
        boolean full = base > 0;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                boolean beam = edge || dx == 0 || dz == 0 || full;
                if (beam) out.put(f.offset(dx, base + 6, dz), plank);
                if (!edge && (full || (dx == 0) != (dz == 0)))
                    out.put(f.offset(dx, base + 5, dz), Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)
                            .setValue(BellBlock.FACING, dx != 0 ? Direction.EAST : Direction.NORTH));
            }
        roof(out, f, base + 7, 2);
        roof(out, f, base + 8, 1);
        out.put(f.offset(0, base + 9, 0), Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        return out;
    }

    private static void roof(Map<BlockPos, BlockState> out, BlockPos f, int y, int r) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                boolean ringX = Math.abs(dx) == r, ringZ = Math.abs(dz) == r;
                BlockState s;
                if (ringX && ringZ) s = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                else if (ringX || ringZ) {
                    // the tall back of each stair faces the middle, so the roof slopes outward
                    Direction in = ringX ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                    s = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, in);
                } else s = Blocks.DEEPSLATE_TILES.defaultBlockState();
                out.put(f.offset(dx, y, dz), s);
            }
    }

    /** The stone tower: five high, an arch on the frame's side, and the belfry (nine bells) on top. */
    static Map<BlockPos, BlockState> tower(BlockPos f, Direction face) {
        Map<BlockPos, BlockState> out = new java.util.LinkedHashMap<>();
        for (int y = 0; y <= 4; y++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    BlockPos p = f.offset(dx, y, dz);
                    if (!edge) {
                        if (y == 4) out.put(p, Blocks.SPRUCE_PLANKS.defaultBlockState());
                        else if (!(dx == 0 && dz == 0)) out.put(p, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    // the arch: three wide, three high, on the side the frame faces
                    int along = face.getAxis() == Direction.Axis.X ? dz : dx;
                    boolean front = face.getAxis() == Direction.Axis.X ? dx == 2 * face.getStepX() : dz == 2 * face.getStepZ();
                    if (front && Math.abs(along) <= 1 && y <= 2) {
                        out.put(p, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    out.put(p, (corner ? Blocks.POLISHED_ANDESITE : y == 3 && !corner && (dx == 0 || dz == 0) ? Blocks.AIR : Blocks.STONE_BRICKS).defaultBlockState());
                }
        out.putAll(belfry(f, 5));
        return out;
    }

    // ---- the song ---------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level.getGameTime() % 20 != 3) return;
        long time = level.getOverworldClockTime(), tod = Math.floorMod(time, 24000L);
        // dawn runs on past midnight's turn so a night slept through still wakes to it; the key names the morning
        int which = tod >= DAWN_SONG || tod < 400 ? 0 : tod >= DUSK_SONG && tod < DUSK_SONG + 800 ? 1 : -1;
        if (which < 0) return;
        long day = Math.floorDiv(time + 400, 24000L);
        BellWorld w = BellWorld.get(server);
        for (HearthRecord h : HearthService.data(server).snapshot()) {
            if (!HearthService.OVERWORLD.equals(h.dimension)) continue;
            BellWorld.Home home = w.peek(h.pos);
            if (home == null || home.hung.isEmpty() || home.sung == day * 2 + which) continue;
            home.sung = day * 2 + which;
            song(level, h, home);
        }
    }

    /** Rings one home's song now. */
    static void song(ServerLevel level, HearthRecord h, BellWorld.Home home) {
        hang(level.getServer(), h);
        BlockPos f = frame(level, home);
        BlockPos at = f != null ? (home.stage == 2 ? f.above(10) : home.stage == 1 ? f.above(5) : f) : h.pos;
        long lo = 0, hi = 0;
        for (int no : home.hung) {
            if (no < 64) lo |= 1L << no;
            else hi |= 1L << (no - 64);
        }
        BellNet.Song s = new BellNet.Song(at, lo, hi);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) < (double) HEAR * HEAR) PacketDistributor.sendToPlayer(p, s);
        long until = level.getGameTime() + 100L * BellCatalog.STEP + 40;
        KinHome.song(level, h.pos, at, home.hung.size(), until);
        Awaken.LOG.info("[bells] the song rang at {} ({} bells)", at.toShortString(), home.hung.size());
    }

    /** The home a player belongs to's song, rung now (admin). */
    static boolean songNow(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) return false;
        BellWorld.Home home = BellWorld.get(p.level().getServer()).home(h.get().pos);
        song(p.level().getServer().overworld(), h.get(), home);
        return true;
    }
}
