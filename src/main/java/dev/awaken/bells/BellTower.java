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
 * timber belfry goes up round it, at fifty a stone tower under the belfry, and with the Heart-clapper and a Sovereign's
 * relic the Great Belfry on top (§43.14; each built only into clear space; if something's in the way the System says what
 * room it needs and tries again at the next song). At dawn and dusk it rings your song as {@link SongPlan} lays it out,
 * missing bells silent, heard 256 blocks off; with no frame set down nothing rings. Your beasts take it by how many bells
 * there are. With all hundred in the Great Belfry, the next song is the Hundred Peal.
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
                : home != null && home.pealed >= 0 ? "Your song is whole, and the Kinsong is yours."
                : home != null && home.stage >= 3 ? (100 - n) + (100 - n == 1 ? " bell is" : " bells are") + " still missing. When the last one hangs, your bells ring the Hundred Peal."
                : "Only a Great Belfry holds all hundred. It needs " + (home != null && home.clapper ? "" : "the Heart-clapper, from the Lord of an S-rank gate")
                        + (home != null && (home.clapper || home.relic) ? "" : ", and ") + (home != null && home.relic ? "" : "a Sovereign's relic")
                        + ", set into this frame.";
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

    /** Blocks a belfry or a tower is made of: each stage may build over the last. */
    static boolean ours(BlockState s) {
        return s.is(Blocks.STRIPPED_DARK_OAK_LOG) || s.is(Blocks.DARK_OAK_PLANKS) || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.DEEPSLATE_TILE_SLAB)
                || s.is(Blocks.DEEPSLATE_TILE_STAIRS) || s.is(Blocks.BELL) || s.is(Blocks.DEEPSLATE_TILE_WALL) || s.is(Blocks.STONE_BRICKS)
                || s.is(Blocks.POLISHED_ANDESITE) || s.is(Blocks.SPRUCE_PLANKS) || s.is(Blocks.DEEPSLATE_BRICKS) || s.is(Blocks.POLISHED_DEEPSLATE)
                || s.is(Blocks.CHISELED_DEEPSLATE) || s.is(Blocks.LIGHTNING_ROD.weathering().unaffected()) || s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.LANTERN);
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

    /**
     * The Great Belfry (SPEC §43.14) on the stone tower: the tower's five courses, a shaft of five more with a window each
     * side, then the great belfry seven wide: deepslate piers, a ring of twenty-four bells under the beams round the Seed
     * Bell in the middle, lanterns at the corners, a tiled roof three courses high and a gilded spire. Twenty-three high.
     */
    static Map<BlockPos, BlockState> great(BlockPos f, Direction face) {
        Map<BlockPos, BlockState> out = new java.util.LinkedHashMap<>();
        // the tower's base, as it stands
        for (Map.Entry<BlockPos, BlockState> e : tower(f, face).entrySet()) if (e.getKey().getY() - f.getY() <= 4) out.put(e.getKey(), e.getValue());
        // the shaft: stone brick walls, deepslate corners, a window on each face
        for (int y = 5; y <= 9; y++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    BlockPos p = f.offset(dx, y, dz);
                    if (!edge) out.put(p, Blocks.AIR.defaultBlockState());
                    else if (corner) out.put(p, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    else if ((y == 7 || y == 8) && (dx == 0 || dz == 0)) out.put(p, Blocks.AIR.defaultBlockState());
                    else out.put(p, Blocks.STONE_BRICKS.defaultBlockState());
                }
        // the great belfry's floor, a step wider than the shaft
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                out.put(f.offset(dx, 10, dz), (rim ? Blocks.DEEPSLATE_BRICKS : Blocks.SPRUCE_PLANKS).defaultBlockState());
            }
        // piers at the corners and the middle of each side, five high
        for (int y = 11; y <= 15; y++)
            for (int dx = -3; dx <= 3; dx += 3)
                for (int dz = -3; dz <= 3; dz += 3)
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) out.put(f.offset(dx, y, dz), (y == 13 && dx != 0 && dz != 0 ? Blocks.CHISELED_DEEPSLATE : Blocks.DEEPSLATE_BRICKS).defaultBlockState());
        // the beams, the ring of bells under them and the Seed Bell in the middle
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                out.put(f.offset(dx, 16, dz), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring == 1 || ring == 2)
                    out.put(f.offset(dx, 15, dz), Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)
                            .setValue(BellBlock.FACING, Math.abs(dx) >= Math.abs(dz) ? Direction.EAST : Direction.NORTH));
            }
        out.put(f.offset(0, 15, 0), Blocks.DARK_OAK_PLANKS.defaultBlockState());
        out.put(f.offset(0, 14, 0), Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, face));
        for (int dx = -2; dx <= 2; dx += 4)
            for (int dz = -2; dz <= 2; dz += 4) out.put(f.offset(dx, 11, dz), Blocks.LANTERN.defaultBlockState());
        roof(out, f, 17, 3);
        roof(out, f, 18, 2);
        roof(out, f, 19, 1);
        out.put(f.offset(0, 20, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        out.put(f.offset(0, 21, 0), Blocks.DEEPSLATE_TILE_WALL.defaultBlockState());
        out.put(f.offset(0, 22, 0), Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
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

    /** Where a home's song rings from: its frame (or the belfry over it), or null with no frame or no bell (no song). */
    static @Nullable BlockPos ringsFrom(ServerLevel level, BellWorld.Home home) {
        BlockPos f = frame(level, home);
        if (f == null || home.hung.isEmpty()) return null;
        return home.stage == 3 ? f.above(14) : home.stage == 2 ? f.above(10) : home.stage == 1 ? f.above(5) : f;
    }

    /** Rings one home's song now. False when nothing rang: no frame set down (the bare fire never sings). */
    static boolean song(ServerLevel level, HearthRecord h, BellWorld.Home home) {
        hang(level.getServer(), h);
        // both pieces in and nobody to raise it: it goes up now, by the song
        if (GreatBelfry.ready(home) && home.raising == 0) GreatBelfry.build(level, h, home);
        BlockPos at = ringsFrom(level, home);
        if (at == null) return false;
        if (HundredPeal.due(home)) {
            HundredPeal.ring(level, h, home, at);
            return true;
        }
        long lo = 0, hi = 0;
        for (int no : home.hung) {
            if (no < 64) lo |= 1L << no;
            else hi |= 1L << (no - 64);
        }
        BellNet.Song s = new BellNet.Song(at, lo, hi);
        SongPlan.Plan plan = SongPlan.of(s::has);
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        boolean dusk = tod >= DUSK_SONG && tod < DAWN_SONG;
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at)) < (double) HEAR * HEAR) {
                PacketDistributor.sendToPlayer(p, s);
                // the first song a member hears says what it is, once
                if (h.belongs(p.getUUID()) && Bells.book(p).once("song")) {
                    Bells.save(p, Bells.book(p));
                    Bells.say(p, "[Your bells, ringing for " + (dusk ? "dusk" : "dawn") + ". They ring at dawn and dusk, and every bell you hang joins in.]");
                }
            }
        long until = level.getGameTime() + plan.length() + 40;
        KinHome.song(level, h.pos, at, home.hung.size(), until);
        Awaken.LOG.info("[bells] the song rang at {} ({} bells, {} s)", at.toShortString(), home.hung.size(), plan.length() / 20);
        return true;
    }

    /** The home a player belongs to's song, rung now (admin). */
    static boolean songNow(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) return false;
        BellWorld.Home home = BellWorld.get(p.level().getServer()).home(h.get().pos);
        return song(p.level().getServer().overworld(), h.get(), home);
    }
}
