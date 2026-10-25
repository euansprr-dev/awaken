package dev.awaken.descent.well;

import dev.awaken.Awaken;
import dev.awaken.descent.DescentContent;
import dev.awaken.descent.DescentModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Well: the Descent's mouth, 400-700 blocks from world spawn on solid ground, built once per world at runtime
 * (SavedData) because landmark structures can't be placed relative to spawn (docs/modules/structures.md, "The Well").
 *
 * <p>A sunken court of Precursor stone round a shaft fifteen blocks wide, a glyph circle on its floor and four lantern
 * pylons at its corners; terraces climb out of it to a ring of twelve great pillars (some fallen, three pairs still
 * joined by lintels) whose glyph bands and crowns glow at night. Looking down the shaft you see rings of glyph light
 * and the lights in the pool far below; jumping in (or the lift on the court) takes you to stratum 1.
 */
public final class Well {
    private Well() {}

    public static final int MIN_DISTANCE = 400, MAX_DISTANCE = 700;
    /** The shaft's radius, and the whole landmark's. */
    public static final int RING_INNER = 7, RING_OUTER = 23;
    public static final int DEPTH = 110;
    /** The look built (WellData.style): wells built in an older look are rebuilt in place when the server starts. */
    public static final int STYLE = 2;
    private static final int SALT = 77031;
    /** Floor heights above y0 by radius: the sunken court, two terraces, steps back down to the land. */
    static final double COURT = 12.5, TERRACE = 15.5, CROWN = 20.5, STEP = 22.0;
    /** The great pillars: how many, how far out. */
    static final int PILLARS = 12;
    static final double PILLAR_RADIUS = 18.0;

    /** Candidate sites around spawn, all within [MIN_DISTANCE + margin, MAX_DISTANCE - margin]. Pure. */
    public static List<BlockPos> candidates(long seed, int spawnX, int spawnZ, int count) {
        XoroshiroRandomSource r = new XoroshiroRandomSource(seed ^ SALT);
        double base = r.nextDouble() * Math.PI * 2;
        List<BlockPos> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = base + i * 2.399963229728653; // golden angle: spread the tries around the ring
            double dist = MIN_DISTANCE + RING_OUTER + 16 + r.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE - 2 * (RING_OUTER + 16));
            out.add(new BlockPos(spawnX + (int) Math.round(Math.cos(angle) * dist), 0, spawnZ + (int) Math.round(Math.sin(angle) * dist)));
        }
        return out;
    }

    private static boolean building;

    /** Called at server start (and when the feature flag is on): chooses and builds the Well if it doesn't exist. */
    public static void ensure(MinecraftServer server) {
        WellData data = WellData.get(server);
        if (building || !DescentModule.enabled()) return;
        if (data.built()) {
            if (data.style < STYLE) rebuild(server, data);
            return;
        }
        ServerLevel overworld = server.overworld();
        if (data.state == WellData.CHOSEN) {
            build(server, data);
            return;
        }
        BlockPos spawn = overworld.getRespawnData().pos();
        List<BlockPos> sites = candidates(overworld.getSeed(), spawn.getX(), spawn.getZ(), 32);
        ChunkGenerator gen = overworld.getChunkSource().getGenerator();
        RandomState rs = overworld.getChunkSource().randomState();
        building = true;
        CompletableFuture.supplyAsync(() -> pick(sites, gen, rs, overworld), Util.backgroundExecutor())
                .thenAcceptAsync(site -> {
                    data.state = WellData.CHOSEN;
                    data.spawnX = spawn.getX();
                    data.spawnZ = spawn.getZ();
                    data.x = site.getX();
                    data.z = site.getZ();
                    data.y = site.getY();
                    data.setDirty();
                    Awaken.LOG.info("AWAKEN: the Well will stand at {} {}", site.getX(), site.getZ());
                    build(server, data);
                }, server)
                .exceptionally(t -> {
                    Awaken.LOG.error("AWAKEN: could not place the Well", t);
                    building = false;
                    return null;
                });
    }

    // Settlements that could start near a site: the Well only overwrites replaceable blocks, so a village growing
    // there kept its houses inside the ring. Six chunks clears the Well's terraces and a village's spread.
    private static final List<ResourceKey<StructureSet>> KEEP_CLEAR =
            List.of(BuiltinStructureSets.VILLAGES, BuiltinStructureSets.PILLAGER_OUTPOSTS, BuiltinStructureSets.WOODLAND_MANSIONS);
    static final int CLEAR_CHUNKS = 6;

    /** Whether a settlement may start within CLEAR_CHUNKS of the site (a structure placement check, no chunk loads). */
    static boolean nearSettlement(ServerLevel level, BlockPos site) {
        var state = level.getChunkSource().getGeneratorState();
        var sets = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        int cx = site.getX() >> 4, cz = site.getZ() >> 4;
        for (ResourceKey<StructureSet> key : KEEP_CLEAR) {
            var set = sets.get(key);
            if (set.isPresent() && state.hasStructureChunkInRange(set.get(), cx, cz, CLEAR_CHUNKS)) return true;
        }
        return false;
    }

    /** First candidate on dry, fairly flat land with no settlement near (estimated from the generator, no chunk loads). */
    static boolean inCity(BlockPos s) {
        // the whole ring's circle, not points on its edge (they miss the reserve between them)
        return dev.awaken.terrain.api.CityBasin.reservedWithin(s.getX(), s.getZ(), RING_OUTER);
    }

    static BlockPos pick(List<BlockPos> sites, ChunkGenerator gen, RandomState rs, ServerLevel level) {
        int sea = gen.getSeaLevel();
        BlockPos fallback = null, first = null;
        for (BlockPos s : sites) {
            // never inside Carillon's reserve, the Well's own ring included (SPEC §16.2)
            if (inCity(s)) continue;
            int[] h = new int[9];
            boolean dry = true;
            int k = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = s.getX() + dx * 16, z = s.getZ() + dz * 16;
                    int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                    int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                    if (surface != floor || surface <= sea + 1) dry = false;
                    h[k++] = surface;
                }
            }
            Arrays.sort(h);
            BlockPos site = new BlockPos(s.getX(), h[4] - 1, s.getZ());
            boolean clear = !nearSettlement(level, s);
            if (first == null) first = site;
            if (fallback == null && clear) fallback = site;
            if (dry && h[8] - h[0] <= 8 && clear) return site;
        }
        return fallback != null ? fallback : first;
    }

    private static void build(MinecraftServer server, WellData data) {
        building = true;
        ServerLevel level = server.overworld();
        ChunkPos center = ChunkPos.containing(new BlockPos(data.x, 0, data.z));
        level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, center, 2)
                .thenRunAsync(() -> startBuild(level, data), server);
    }

    /** A Well built in an older look is rebuilt where it stands (same centre, same ground level, same shaft). */
    private static void rebuild(MinecraftServer server, WellData data) {
        building = true;
        ServerLevel level = server.overworld();
        ChunkPos center = ChunkPos.containing(new BlockPos(data.x, 0, data.z));
        Awaken.LOG.info("AWAKEN: rebuilding the Well at {} {} {} in its new look", data.x, data.y, data.z);
        level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, center, 2)
                .thenRunAsync(() -> JOB = new Job(level, data), server);
    }

    private static void startBuild(ServerLevel level, WellData data) {
        // Ground level from the real terrain: median of the court's edge.
        int[] h = new int[16];
        for (int i = 0; i < h.length; i++) {
            double a = i * Math.PI * 2 / h.length;
            h[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, data.x + (int) Math.round(Math.cos(a) * 12),
                    data.z + (int) Math.round(Math.sin(a) * 12)) - 1;
        }
        Arrays.sort(h);
        int y0 = Math.max(level.getSeaLevel() + 1, h[h.length / 2]);
        data.y = y0;
        data.bottom = Math.max(level.getMinY() + 6, y0 - DEPTH);
        data.setDirty();
        JOB = new Job(level, data);
    }

    private static Job JOB;

    /** Server tick: advances the build job, a bounded number of columns per tick. */
    public static void tick(MinecraftServer server) {
        Job j = JOB;
        if (j == null) return;
        if (j.step(10)) { // ~10 columns per tick keeps the build under the 3 ms/tick budget
            JOB = null;
            building = false;
            j.data.state = WellData.BUILT;
            j.data.style = STYLE;
            j.data.setDirty();
            DescentModule.onWellBuilt(server);
            Awaken.LOG.info("AWAKEN: the Well is built at {} {} {}", j.data.x, j.data.y, j.data.z);
        }
    }

    public static void reset() {
        JOB = null;
        building = false;
    }

    /** Tests: build the whole Well now (for the data's centre), in one go. */
    static void buildNow(ServerLevel level, WellData data) {
        Job j = new Job(level, data);
        while (!j.step(1000)) {
            // all columns
        }
    }

    // ---- the blueprint ----------------------------------------------------------------------------------------------

    /** Floor height above y0 at radius r: the sunken court, the terraces, the steps back down to the land. */
    static int floor(double r) {
        if (r <= COURT) return 0;
        if (r <= TERRACE) return 1;
        if (r <= CROWN) return 2;
        if (r <= STEP) return 1;
        return 0;
    }

    /** Pillar k's height above its terrace (some have fallen), from the position only. */
    static int pillarHeight(int k, long salt) {
        if (k == 2 || k == 7 || k == 10) return 4 + (int) (hash(salt, k, 1) * 4);
        return 15 + (int) (hash(salt, k, 2) * 7);
    }

    static double pillarAngle(int k) {
        return Math.PI * 2 * (k + 0.5) / PILLARS;
    }

    /** A stable 0..1 value. */
    static double hash(long salt, int a, int b) {
        long h = salt * 0x9E3779B97F4A7C15L + a * 0x632BE59BD9B4E019L + b * 0x85157AF5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /** Terrain, plants and the Well's own stone: what the build may replace (never somebody's build). */
    static boolean replaceable(BlockState s) {
        if (s.isAir() || s.canBeReplaced()) return true;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SNOW) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(BlockTags.CORAL_BLOCKS)) return true;
        return s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.WATER) || s.is(Blocks.LAVA)
                || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD) || s.is(Blocks.COBBLESTONE)
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBBLED_DEEPSLATE) || s.is(Blocks.OBSIDIAN)
                || s.is(Blocks.DEEPSLATE_TILES) || s.is(Blocks.CRACKED_DEEPSLATE_TILES) || s.is(Blocks.POLISHED_DEEPSLATE)
                || s.is(Blocks.CHISELED_DEEPSLATE) || s.is(Blocks.IRON_BARS) || s.is(Blocks.MOSS_CARPET)
                || s.is(DescentContent.WELL_STONE.get()) || s.is(DescentContent.WELL_GLYPH.get()) || s.is(DescentContent.WELL_LIGHT.get())
                || s.is(DescentContent.WELL_LIFT.get());
    }

    private static final class Job {
        final ServerLevel level;
        final WellData data;
        final long salt;
        int column;
        static final int SPAN = RING_OUTER + 1;
        static final int SIDE = SPAN * 2 + 1;

        Job(ServerLevel level, WellData data) {
            this.level = level;
            this.data = data;
            this.salt = (long) data.x * 341873128712L + data.z * 132897987541L;
        }

        /** Returns true when finished. */
        boolean step(int columns) {
            for (int n = 0; n < columns; n++) {
                if (column >= SIDE * SIDE) return true;
                int dx = column % SIDE - SPAN, dz = column / SIDE - SPAN;
                column++;
                buildColumn(dx, dz);
            }
            return column >= SIDE * SIDE;
        }

        private void set(BlockPos.MutableBlockPos p, BlockState s) {
            level.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }

        /** Only over terrain, plants and the Well's own blocks: a build next to the Well stays as it is. */
        private void put(BlockPos.MutableBlockPos p, BlockState s) {
            if (replaceable(level.getBlockState(p))) set(p, s);
        }

        private double n(int x, int y, int z) {
            return hash(salt + y * 31L, x, z);
        }

        /** The Precursors' brass (the Above's trim), by id so the Descent needs no Above classes. */
        private BlockState brass() {
            return BuiltInRegistries.BLOCK.getOptional(Awaken.id("precursor_trim")).orElse(Blocks.CHISELED_DEEPSLATE).defaultBlockState();
        }

        private void buildColumn(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > RING_OUTER + 0.5) return;
            int x = data.x + dx, z = data.z + dz, y0 = data.y, bottom = data.bottom;
            BlockState stone = DescentContent.WELL_STONE.get().defaultBlockState();
            BlockState glyph = DescentContent.WELL_GLYPH.get().defaultBlockState();
            BlockState light = DescentContent.WELL_LIGHT.get().defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            double a = Math.atan2(dz, dx);
            // the air above, up past the pillar crowns (terrain and plants only)
            for (int y = y0 + 1; y <= y0 + 28; y++) {
                p.set(x, y, z);
                BlockState here = level.getBlockState(p);
                if (!here.isAir() && replaceable(here)) set(p, air);
            }
            if (r <= RING_INNER) {
                // the shaft, open to the pool; a band of light just under the rim so the mouth glows at night
                for (int y = bottom + 2; y <= y0; y++) set(p.set(x, y, z), air);
                set(p.set(x, bottom + 1, z), Blocks.WATER.defaultBlockState());
                set(p.set(x, bottom, z), Blocks.WATER.defaultBlockState());
                set(p.set(x, bottom - 1, z), ((dx + dz) & 1) == 0 ? light : glyph);
                set(p.set(x, bottom - 2, z), stone);
                return;
            }
            if (r <= RING_INNER + 1.6) {
                // the lining: watertight, a glyph ring every 12 blocks so you see the depth when you look down
                for (int y = bottom - 2; y <= y0 - 1; y++) {
                    boolean mark = (y0 - y) % 12 == 6 && Math.floorMod((int) Math.round(a * 4 / Math.PI), 2) == 0;
                    boolean mouth = y == y0 - 3;
                    set(p.set(x, y, z), mouth ? (Math.floorMod((int) Math.round(a * 8 / Math.PI), 2) == 0 ? light : glyph) : mark ? glyph : stone);
                }
                set(p.set(x, y0, z), DescentContent.WELL_STONE.get().defaultBlockState());
                // a knee-high parapet you can step over to jump in, broken by eight gaps
                set(p.set(x, y0 + 1, z), Math.floorMod((int) Math.round(a * 8 / Math.PI), 4) == 0 ? air
                        : r > RING_INNER + 1.0 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState() : stone);
                return;
            }
            int f = floor(r);
            // floor and foundations
            BlockState top = floorBlock(dx, dz, r, a);
            for (int y = y0 - 1; y >= y0 - 14; y--) {
                p.set(x, y, z);
                BlockState here = level.getBlockState(p);
                if (y < y0 - 3 && !here.canBeReplaced() && here.getFluidState().isEmpty()) break;
                put(p, stone);
            }
            for (int y = y0; y < y0 + f; y++) put(p.set(x, y, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            put(p.set(x, y0 + f, z), top);
            // the lantern pylons at the court's corners: a cage of light at y0+7 (where the landmark glow is drawn)
            int ox = Math.abs(dx) - 8, oz = Math.abs(dz) - 8;
            if (Math.abs(ox) <= 1 && Math.abs(oz) <= 1) {
                pylon(p, x, z, ox, oz, y0);
                return;
            }
            // the ring of great pillars, and lintels over three pairs
            if (r > TERRACE && r <= CROWN + 0.5) {
                int k = Math.floorMod((int) Math.round(a / (Math.PI * 2 / PILLARS) - 0.5), PILLARS);
                double pa = pillarAngle(k);
                double px = Math.cos(pa) * PILLAR_RADIUS, pz = Math.sin(pa) * PILLAR_RADIUS;
                double pd = Math.sqrt((dx - px) * (dx - px) + (dz - pz) * (dz - pz));
                if (pd <= 1.65) {
                    pillar(p, x, z, k, pd, y0 + f, dx - px, dz - pz);
                    return;
                }
                lintel(p, x, z, dx, dz, y0 + f);
                rubble(p, x, z, dx, dz, y0 + f);
            }
            // moss at the outer edge, where the land takes it back
            if (r > CROWN && n(dx, 3, dz) < 0.25) put(p.set(x, y0 + f + 1, z), Blocks.MOSS_CARPET.defaultBlockState());
            if (dx == RING_INNER + 3 && dz == 0) set(p.set(x, y0 + 1, z), DescentContent.WELL_LIFT.get().defaultBlockState());
        }

        /** The floor: the court's glyph circle and spokes in Well stone, deepslate tile terraces, worn at the edges. */
        private BlockState floorBlock(int dx, int dz, double r, double a) {
            BlockState stone = DescentContent.WELL_STONE.get().defaultBlockState();
            if (r <= COURT) {
                if (Math.abs(r - 10.5) < 0.55) return Math.floorMod((int) Math.round(a * 16 / Math.PI), 2) == 0
                        ? DescentContent.WELL_GLYPH.get().defaultBlockState() : Blocks.CHISELED_DEEPSLATE.defaultBlockState();
                boolean spoke = Math.abs(Math.sin(4 * a)) * r < 0.6 && r > 9;
                if (spoke) return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
                return (int) Math.floor(r) % 2 == 0 ? stone : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            }
            double edge = Math.min(Math.abs(r - COURT), Math.min(Math.abs(r - TERRACE), Math.abs(r - CROWN)));
            if (edge < 0.6) return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            double w = n(dx, 1, dz);
            if (r > CROWN) return w < 0.3 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : w < 0.5 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                    : w < 0.62 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
            return w < 0.18 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState();
        }

        private void pylon(BlockPos.MutableBlockPos p, int x, int z, int ox, int oz, int y0) {
            boolean corner = ox != 0 && oz != 0, centre = ox == 0 && oz == 0;
            BlockState stone = DescentContent.WELL_STONE.get().defaultBlockState();
            for (int y = y0 + 1; y <= y0 + 9; y++) {
                int k = y - y0;
                BlockState s;
                if (k == 6) s = brass();
                else if (k <= 6) s = k == 1 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : corner ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                        : k == 4 && !centre ? DescentContent.WELL_GLYPH.get().defaultBlockState() : stone;
                else if (k == 7) s = centre ? DescentContent.WELL_LIGHT.get().defaultBlockState() : corner ? stone : Blocks.IRON_BARS.defaultBlockState();
                else if (k == 8) s = centre ? DescentContent.WELL_GLYPH.get().defaultBlockState() : Blocks.CHISELED_DEEPSLATE.defaultBlockState();
                else s = centre ? stone : Blocks.AIR.defaultBlockState();
                put(p.set(x, y, z), s);
            }
        }

        /** A great pillar: a plinth, a fluted body ringed by glyph bands, a crown of light; or a broken stump. */
        private void pillar(BlockPos.MutableBlockPos p, int x, int z, int k, double pd, int base, double ox, double oz) {
            int h = pillarHeight(k, salt);
            boolean broken = h < 10;
            boolean core = pd <= 0.75;
            BlockState stone = DescentContent.WELL_STONE.get().defaultBlockState();
            for (int i = 1; i <= h; i++) {
                if (broken && i == h && !core && n(x, i, z) < 0.5) break; // a jagged break
                BlockState s;
                if (i <= 2) s = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                else if (i == h - 1 && !broken) s = brass();
                else if (i == h && !broken) s = core ? DescentContent.WELL_LIGHT.get().defaultBlockState() : Blocks.CHISELED_DEEPSLATE.defaultBlockState();
                else if (i % 5 == 0) s = DescentContent.WELL_GLYPH.get().defaultBlockState();
                else if (pd > 1.2) s = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                else s = n(x, i, z) < 0.08 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : stone;
                put(p.set(x, base + i, z), s);
            }
            if (!broken && core) put(p.set(x, base + h + 1, z), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        }

        /** Lintels join pillars 0-1, 4-5 and 8-9 at the lower one's top. */
        private void lintel(BlockPos.MutableBlockPos p, int x, int z, int dx, int dz, int base) {
            for (int k : new int[]{0, 4, 8}) {
                double a0 = pillarAngle(k), a1 = pillarAngle(k + 1);
                double x0 = Math.cos(a0) * PILLAR_RADIUS, z0 = Math.sin(a0) * PILLAR_RADIUS;
                double x1 = Math.cos(a1) * PILLAR_RADIUS, z1 = Math.sin(a1) * PILLAR_RADIUS;
                double vx = x1 - x0, vz = z1 - z0, len2 = vx * vx + vz * vz;
                double t = ((dx - x0) * vx + (dz - z0) * vz) / len2;
                if (t < 0 || t > 1) continue;
                double ex = dx - (x0 + vx * t), ez = dz - (z0 + vz * t);
                if (ex * ex + ez * ez > 0.55) continue;
                int top = Math.min(pillarHeight(k, salt), pillarHeight(k + 1, salt));
                put(p.set(x, base + top, z), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
                put(p.set(x, base + top - 1, z), DescentContent.WELL_STONE.get().defaultBlockState());
            }
        }

        /** The fallen pillars lie where they fell, outward. */
        private void rubble(BlockPos.MutableBlockPos p, int x, int z, int dx, int dz, int base) {
            for (int k : new int[]{2, 7, 10}) {
                double pa = pillarAngle(k);
                double cx = Math.cos(pa), cz = Math.sin(pa);
                double along = dx * cx + dz * cz - PILLAR_RADIUS, across = -dx * cz + dz * cx;
                if (along > 1.5 && along < 6.5 && Math.abs(across - 0.6 * Math.sin(along)) < 0.9 && n(dx, 5, dz) < 0.85) {
                    put(p.set(x, base + 1, z), n(dx, 6, dz) < 0.3 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                            : DescentContent.WELL_STONE.get().defaultBlockState());
                }
            }
        }
    }

    /** True when a player at this position has jumped into the shaft. */
    public static boolean inShaft(WellData data, double x, double y, double z) {
        if (!data.built()) return false;
        double dx = x - (data.x + 0.5), dz = z - (data.z + 0.5);
        return dx * dx + dz * dz < (RING_INNER + 0.4) * (RING_INNER + 0.4) && y < data.y - 6 && y > data.bottom - 4;
    }
}
