package dev.awaken.hearth.guide;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Later;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.terrain.api.BiomeTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Oren's cottage: offered once per player after the Anchor takes root ("Raise it" / "I'll build my own"), free. On yes a
 * search finds open, flat-enough ground near the Anchor (never on anyone's build: only terrain, plants and whole natural
 * trees give way), fits the plinth to the terrain, and Oren builds {@link CottagePlan} over one day (SPEC §28.11):
 * foundation, frame, walls, roof, chimney, furniture, block by block with the blocks' own sounds and embers, walking the
 * site and swinging his hammer at each one. The build is saved on the Hearth ({@link CottageBuild}) and follows the time
 * that passed, so it catches up after a logout, a restart or the chunks unloading; a block never goes into someone
 * standing in its cell. Its room scans as a Sanctuary the moment it's done.
 */
public final class Cottage {
    private Cottage() {}

    public static final String ACTION = Guide.ACTION + ":cottage";
    public static final String TIP_OFFERED = "cottage_offered", TIP_DECLINED = "cottage_declined", TIP_BUILT = "cottage_built",
            TIP_NO_ROOM = "cottage_no_room";
    /** Candidate spots checked in full per tick. */
    static final int CHECKS_PER_TICK = 2;

    /** Players whose Oren is looking for a spot (runtime: a restart just leaves the offer open). */
    private static final Map<UUID, Search> JOBS = new HashMap<>();
    /** Where each owner's cottage went this session (for the camera commands). */
    private static final Map<UUID, Site> LAST = new HashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            JOBS.clear(); // a build in progress is saved on its Hearth and goes on after the restart
            LIVE_PLANS.clear();
        });
    }

    // ---- the offer --------------------------------------------------------------------------------------------------

    /** (Before SPEC §43.6) The Anchor took root: a few seconds later, offer the cottage if this player never had it. Not called now. */
    public static void afterAnchorPlaced(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        if (hp.tips.contains(TIP_OFFERED) || hp.tips.contains(TIP_BUILT)) return;
        MinecraftServer server = sp.level().getServer();
        UUID id = sp.getUUID();
        Later.run(server, 20 * 6, () -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) offer(p);
        });
    }

    /** Sends the choice card, once per player. Returns whether it was sent. */
    public static boolean offer(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        if (hp.tips.contains(TIP_OFFERED) || hp.tips.contains(TIP_BUILT) || HearthService.hearthOf(sp).isEmpty()) return false;
        hp.tips.add(TIP_OFFERED);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("A cottage from Oren?"))
                .body(Component.literal("Free: your first Sanctuary. Oren can raise a small cottage beside your Anchor: a bed and a "
                        + "Meditation Mat, your first Sanctuary."))
                .choice(ACTION, "Raise it", "I'll build my own").ttl(20 * 60 * 10).priority(4).build());
        return true;
    }

    static void answer(ServerPlayer sp, boolean yes) {
        if (yes) {
            start(sp);
            return;
        }
        HearthService.player(sp).tips.add(TIP_DECLINED);
        Signal.toast(sp, Component.literal("Changed your mind? Your Hearth can still ask Oren."));
        Guide.refresh(sp);
    }

    /** What the guide shows for the cottage (see {@link GuideData#cottage}). */
    public static int state(ServerPlayer sp, HearthRecord h) {
        if (JOBS.containsKey(sp.getUUID()) || h.cottageBuild != null) return GuideData.COTTAGE_BUILDING;
        if (h.cottage != null) return GuideData.COTTAGE_BUILT;
        // (SPEC §43.6) Oren lives in the city now: nobody offers one (a build already under way still finishes)
        return GuideData.COTTAGE_NONE;
    }

    /** Yes: find a spot and raise it. False when it can't start (built before, already building, no Hearth). */
    public static boolean start(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        var ho = HearthService.hearthOf(sp);
        if (ho.isEmpty() || hp.tips.contains(TIP_BUILT) || JOBS.containsKey(sp.getUUID()) || ho.get().cottageBuild != null) return false;
        hp.tips.add(TIP_OFFERED);
        Signal.withdraw(sp, ACTION);
        ServerLevel level = HearthService.levelOf(sp.level().getServer(), ho.get());
        JOBS.put(sp.getUUID(), new Search(level, ho.get(), sp.getUUID()));
        return true;
    }

    /** Looking for a spot, or building at the Hearth they live at. */
    public static boolean building(MinecraftServer server, UUID player) {
        if (JOBS.containsKey(player)) return true;
        return HearthData.get(server).home(player).map(h -> h.cottageBuild != null).orElse(false);
    }

    public static @Nullable Site lastSite(UUID owner) {
        return LAST.get(owner);
    }

    private static void tick(MinecraftServer server) {
        if (!JOBS.isEmpty()) JOBS.values().removeIf(j -> j.tick(server));
        tickBuilds(server);
    }

    // ---- the site ---------------------------------------------------------------------------------------------------

    /** A spot: the local origin, the turn that faces the door to the Anchor, the floor height and the ground under it. */
    public record Site(BlockPos origin, Rotation rot, int floorY, int[] ground, List<BlockPos> trees, CottagePlan.Palette palette) {
        int top(int x, int z) {
            return ground[(z - CottagePlan.MIN_Z) * WIDTH + (x - CottagePlan.MIN_X)];
        }
    }

    static final int WIDTH = CottagePlan.MAX_X - CottagePlan.MIN_X + 1, DEPTH = CottagePlan.MAX_Z - CottagePlan.MIN_Z + 1;
    /** No ground in a column (a hole deeper than the plinth reaches). */
    static final int VOID = -99;

    private record Candidate(BlockPos origin, Rotation rot, int floorY, int score) {}

    /** Candidate spots around the Anchor, cheapest first by the heightmap (flatness, few obstacles, close). */
    static List<Candidate> candidates(ServerLevel level, HearthRecord h) {
        List<Candidate> out = new ArrayList<>();
        BlockPos a = h.pos;
        int[] tops = new int[81];
        for (int d = 10; d <= 22; d += 2) {
            for (int k = 0; k < 16; k++) {
                double ang = (k + (d % 4 == 0 ? 0.5 : 0)) * Math.PI / 8;
                int cx = a.getX() + (int) Math.round(Math.cos(ang) * d), cz = a.getZ() + (int) Math.round(Math.sin(ang) * d);
                Direction front = Direction.getApproximateNearest(a.getX() - cx, 0, a.getZ() - cz);
                if (!front.getAxis().isHorizontal()) front = Direction.NORTH;
                Rotation rot = CottagePlan.facing(front);
                BlockPos origin = new BlockPos(cx, 0, cz).subtract(new BlockPos(4, 0, 4).rotate(rot));
                if (!loaded(level, origin, rot)) continue;
                int n = 0;
                for (int z = 0; z <= 8; z++) {
                    for (int x = 0; x <= 8; x++) {
                        BlockPos w = CottagePlan.at(origin, rot, x, 0, z);
                        tops[n++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.getX(), w.getZ()) - 1;
                    }
                }
                int[] sorted = tops.clone();
                java.util.Arrays.sort(sorted);
                int floor = sorted[40];
                int score = d, off = 0;
                for (int t : tops) {
                    int dy = Math.abs(t - floor);
                    score += Math.min(dy, 3);
                    if (dy > 2) off++;
                }
                if (off > 16 || Math.abs(floor - a.getY()) > 12) continue; // a cliff, a lake or a forest wall
                out.add(new Candidate(origin.atY(floor), rot, floor, score + off * 3));
            }
        }
        out.sort(Comparator.comparingInt(Candidate::score));
        return out;
    }

    private static boolean loaded(ServerLevel level, BlockPos origin, Rotation rot) {
        int[][] corners = {{CottagePlan.MIN_X, CottagePlan.MIN_Z}, {CottagePlan.MAX_X, CottagePlan.MIN_Z},
                {CottagePlan.MIN_X, CottagePlan.MAX_Z}, {CottagePlan.MAX_X, CottagePlan.MAX_Z}};
        for (int[] c : corners) if (!level.isLoaded(CottagePlan.at(origin, rot, c[0], 0, c[1]))) return false;
        return true;
    }

    /** The full check: every column of the box is terrain and plants (or a natural tree to fell). Null when not. */
    static @Nullable Site check(ServerLevel level, HearthRecord h, Candidate c) {
        int floor = c.floorY();
        int[] ground = new int[WIDTH * DEPTH];
        List<BlockPos> trees = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int z = CottagePlan.MIN_Z; z <= CottagePlan.MAX_Z; z++) {
            for (int x = CottagePlan.MIN_X; x <= CottagePlan.MAX_X; x++) {
                BlockPos w = CottagePlan.at(c.origin(), c.rot(), x, 0, z);
                minX = Math.min(minX, w.getX()); maxX = Math.max(maxX, w.getX());
                minZ = Math.min(minZ, w.getZ()); maxZ = Math.max(maxZ, w.getZ());
                boolean footprint = (x >= 0 && x <= 8 && z >= 0 && z <= 8) || (x == CottagePlan.CHIMNEY_X && z == CottagePlan.CHIMNEY_Z);
                int top = VOID;
                for (int y = floor + CottagePlan.TOP; y >= floor - 5; y--) {
                    BlockState s = level.getBlockState(m.set(w.getX(), y, w.getZ()));
                    if (clearable(s)) continue;
                    if (s.is(BlockTags.LOGS)) {
                        BlockPos base = naturalTreeBase(level, m.immutable());
                        if (base == null) return null; // logs that aren't a tree: somebody's build
                        if (!trees.contains(base)) trees.add(base);
                        if (trees.size() > 3) return null;
                        y = base.getY(); // past the trunk, on to the ground under it
                        continue;
                    }
                    if (!natural(s)) return null; // a build, water, a chest...
                    top = y;
                    break;
                }
                int rel = top == VOID ? VOID : top - floor;
                ground[(z - CottagePlan.MIN_Z) * WIDTH + (x - CottagePlan.MIN_X)] = rel;
                if (footprint) {
                    if (rel == VOID || rel < -4 || rel > 2) return null; // too deep to fill, too high to cut
                } else if (rel != VOID && rel > 1) {
                    return null; // a hill against the eaves or the shutters
                }
            }
        }
        // keep clear of the Anchor and the town Spire
        if (inside(h.pos, minX - 2, maxX + 2, minZ - 2, maxZ + 2)) return null;
        if (h.spire != null && inside(h.spire, minX - 3, maxX + 3, minZ - 3, maxZ + 3)) return null;
        // never build around a player standing there
        var box = new net.minecraft.world.phys.AABB(minX, floor - 1, minZ, maxX + 1, floor + CottagePlan.TOP + 1, maxZ + 1);
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, box).isEmpty()) return null;
        return new Site(c.origin(), c.rot(), floor, ground, trees, palette(level.getBiome(c.origin().atY(floor + 1))));
    }

    private static boolean inside(BlockPos p, int minX, int maxX, int minZ, int maxZ) {
        return p.getX() >= minX && p.getX() <= maxX && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    /** Air, plants, snow, natural leaves: what may simply go. */
    static boolean clearable(BlockState s) {
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty() || s.hasBlockEntity()) return false;
        if (s.is(BlockTags.LEAVES)) return s.hasProperty(BlockStateProperties.PERSISTENT) && !s.getValue(BlockStateProperties.PERSISTENT);
        if (s.getBlock() instanceof VegetationBlock || s.is(Blocks.SNOW) || s.is(Blocks.VINE)) return true;
        return s.is(BlockTags.REPLACEABLE) && !s.is(Blocks.WATER) && !s.is(Blocks.LAVA);
    }

    /** Terrain: what the plinth may stand on or the floor may replace. Never anything a player makes. */
    static boolean natural(BlockState s) {
        if (s.is(Blocks.POWDER_SNOW)) return false;
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SUBSTRATE_OVERWORLD)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.MUD)
                || s.is(Blocks.PACKED_ICE);
    }

    /** The lowest log of a natural tree (its top log touches natural leaves), or null for logs a player stacked. */
    static @Nullable BlockPos naturalTreeBase(ServerLevel level, BlockPos log) {
        BlockPos.MutableBlockPos m = log.mutable();
        int guard = 0;
        while (level.getBlockState(m.above()).is(BlockTags.LOGS) && guard++ < 40) m.move(Direction.UP);
        boolean leaves = false;
        for (int dx = -2; dx <= 2 && !leaves; dx++) {
            for (int dy = -1; dy <= 2 && !leaves; dy++) {
                for (int dz = -2; dz <= 2 && !leaves; dz++) {
                    BlockState s = level.getBlockState(m.offset(dx, dy, dz));
                    leaves = s.is(BlockTags.LEAVES) && s.hasProperty(BlockStateProperties.PERSISTENT) && !s.getValue(BlockStateProperties.PERSISTENT);
                }
            }
        }
        if (!leaves) return null;
        m.set(log);
        guard = 0;
        while (level.getBlockState(m.below()).is(BlockTags.LOGS) && guard++ < 40) m.move(Direction.DOWN);
        return m.immutable();
    }

    /** Each palette's biomes in the biome table: {@code #awaken:cottage/<palette>}, in {@link CottagePlan#PALETTES} order. */
    private static final List<TagKey<Biome>> PLANS = CottagePlan.PALETTES.stream().map(p -> BiomeTable.tag("cottage/" + p.id())).toList();

    /**
     * Wood and stone from the biome: its plan in the biome table (every vanilla and Awakened overworld biome has one);
     * another mod's biome by its vanilla and c: tags.
     */
    public static CottagePlan.Palette palette(Holder<Biome> b) {
        for (int i = 0; i < PLANS.size(); i++) if (b.is(PLANS.get(i))) return CottagePlan.PALETTES.get(i);
        if (b.is(BiomeTags.IS_BADLANDS)) return CottagePlan.BADLANDS;
        if (b.is(Tags.Biomes.IS_DESERT)) return CottagePlan.DESERT;
        if (b.is(BiomeTags.IS_SAVANNA)) return CottagePlan.SAVANNA;
        if (b.is(BiomeTags.IS_JUNGLE)) return CottagePlan.JUNGLE;
        if (b.is(Tags.Biomes.IS_SWAMP) || b.is(Tags.Biomes.IS_DARK_FOREST)) return CottagePlan.SWAMP;
        if (b.is(Tags.Biomes.IS_BIRCH_FOREST)) return CottagePlan.BIRCH;
        if (b.is(BiomeTags.IS_TAIGA) || b.is(Tags.Biomes.IS_SNOWY) || b.is(Tags.Biomes.IS_COLD_OVERWORLD)) return CottagePlan.TAIGA;
        return CottagePlan.MEADOW;
    }

    // ---- placing ----------------------------------------------------------------------------------------------------

    /** Places one block of the plan, turned. Leaves alone anything that isn't terrain, plants or the build's own. */
    static boolean put(ServerLevel level, BlockPos origin, Rotation rot, CottagePlan.Put p, boolean force) {
        BlockPos w = CottagePlan.at(origin, rot, p.x(), p.y(), p.z());
        BlockState here = level.getBlockState(w);
        if (!force && !clearable(here) && !natural(here)) return false; // someone built here meanwhile: never eat it
        BlockState s = p.state().rotate(rot);
        boolean door = s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        level.setBlock(w, s, door ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE : Block.UPDATE_ALL);
        return true;
    }

    /** Builds a whole cottage at once on flat ground (tests, the autopilot studio). Returns the inside cell. */
    public static BlockPos placeAll(ServerLevel level, BlockPos origin, Rotation rot, CottagePlan.Palette palette) {
        List<CottagePlan.Put> plan = CottagePlan.plan(palette, CottagePlan.Ground.FLAT, origin.asLong());
        for (CottagePlan.Put p : plan) put(level, origin, rot, p, true);
        refreshShapes(level, origin, rot, plan);
        return CottagePlan.at(origin, rot, CottagePlan.INSIDE.getX(), CottagePlan.INSIDE.getY(), CottagePlan.INSIDE.getZ());
    }

    /** Glass panes and stairs settle their connections once their neighbours stand. */
    static void refreshShapes(ServerLevel level, BlockPos origin, Rotation rot, List<CottagePlan.Put> plan) {
        for (CottagePlan.Put p : plan) {
            if (!p.state().is(Blocks.GLASS_PANE) && !p.state().is(BlockTags.STAIRS)) continue;
            BlockPos w = CottagePlan.at(origin, rot, p.x(), p.y(), p.z());
            BlockState cur = level.getBlockState(w);
            BlockState up = Block.updateFromNeighbourShapes(cur, level, w);
            if (up != cur) level.setBlock(w, up, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Fells a natural tree from its base: its logs and the natural leaves of its crown (never a neighbour's trunk far off). */
    static void fell(ServerLevel level, BlockPos base) {
        List<BlockPos> todo = new ArrayList<>(List.of(base));
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(todo);
        int removed = 0;
        while (!todo.isEmpty() && removed < 600) {
            BlockPos p = todo.remove(todo.size() - 1);
            BlockState s = level.getBlockState(p);
            boolean log = s.is(BlockTags.LOGS);
            boolean leaf = s.is(BlockTags.LEAVES) && s.hasProperty(BlockStateProperties.PERSISTENT) && !s.getValue(BlockStateProperties.PERSISTENT);
            if (!log && !leaf) continue;
            int reach = log ? 2 : 4; // trunks and branches near the base; leaves out to the crown's edge
            if (Math.abs(p.getX() - base.getX()) > reach + 2 || Math.abs(p.getZ() - base.getZ()) > reach + 2) continue;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), log ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
            removed++;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (n.getY() >= base.getY() && seen.add(n)) todo.add(n);
            }
        }
    }

    // ---- the search (runtime: a restart mid-search just leaves the offer open) ------------------------------------

    private static final class Search {
        final ServerLevel level;
        final HearthRecord hearth;
        final UUID by;
        List<Candidate> candidates;
        int next;

        Search(ServerLevel level, HearthRecord hearth, UUID by) {
            this.level = level;
            this.hearth = hearth;
            this.by = by;
        }

        /** Returns true when the search is over (a site found and the build begun, or none found). */
        boolean tick(MinecraftServer server) {
            if (hearth.cottageBuild != null) return true; // someone else in the household asked first
            if (!level.isLoaded(hearth.pos)) return false;
            if (candidates == null) {
                candidates = candidates(level, hearth);
                return false;
            }
            for (int i = 0; i < CHECKS_PER_TICK && next < candidates.size(); i++) {
                Site s = check(level, hearth, candidates.get(next++));
                if (s != null) {
                    begin(level, hearth, by, s);
                    return true;
                }
            }
            if (next < Math.min(candidates.size(), 40)) return false;
            ServerPlayer sp = server.getPlayerList().getPlayer(by);
            if (sp != null) {
                HearthService.player(sp).tips.add(TIP_NO_ROOM);
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("No room for a cottage"))
                        .body(Component.literal("Oren needs open ground. He looked for a flat, clear patch about 12 blocks across "
                                + "within 22 of your Anchor, away from your builds, and found none. Clear one, then ask again in your "
                                + "Hearth (use the Anchor)."))
                        .ttl(20 * 10).build());
                Guide.refresh(sp);
            }
            return true;
        }
    }

    // ---- the build: a day's work, saved on the Hearth (SPEC §28.11) --------------------------------------------------

    /** The whole build takes one day: a cottage started at noon stands by noon tomorrow. */
    public static final long DURATION = 24000;
    /** Each stage's share of the day, in percent: foundation, frame, walls, roof, chimney, furnishing. */
    private static final int[] SHARE = {14, 14, 24, 28, 5, 15};
    /** Blocks placed per tick at most while it catches up after time away (the whole plan in a dozen ticks). */
    static final int CATCH_UP = 64;
    /** More than this many due at once is catching up: no sound or embers per block. */
    private static final int LIVE = 3;

    /** The plan and each entry's due time, rebuilt from the saved build after a restart. Runtime only. */
    private record Live(CottageBuild build, List<CottagePlan.Put> plan, long[] due) {}

    private static final Map<UUID, Live> LIVE_PLANS = new HashMap<>();

    /** Clears the site (trees felled, plants and bumps gone) and starts the day's work. */
    static CottageBuild begin(ServerLevel level, HearthRecord h, UUID by, Site s) {
        LAST.put(h.owner, s);
        BlockPos origin = s.origin().atY(s.floorY());
        long[] grass = new long[(WIDTH * DEPTH + 63) / 64];
        for (int z = CottagePlan.MIN_Z; z <= CottagePlan.MAX_Z; z++) {
            for (int x = CottagePlan.MIN_X; x <= CottagePlan.MAX_X; x++) {
                BlockState g = level.getBlockState(CottagePlan.at(origin, s.rot(), x, 0, z));
                if (g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.DIRT) || g.is(Blocks.COARSE_DIRT) || g.is(Blocks.PODZOL)) {
                    int i = (z - CottagePlan.MIN_Z) * WIDTH + (x - CottagePlan.MIN_X);
                    grass[i / 64] |= 1L << (i % 64);
                }
            }
        }
        for (BlockPos tree : s.trees()) fell(level, tree);
        clearSite(level, origin, s.rot());
        CottageBuild b = new CottageBuild(by, origin, s.rot(), s.palette().id(), s.ground().clone(), grass, 0,
                level.getGameTime(), clock(level.getServer()), 0, List.of());
        h.cottageBuild = b;
        HearthService.dirty(level.getServer());
        // townsfolk wandering on the spot step out to the front first
        BlockPos a = CottagePlan.at(origin, s.rot(), CottagePlan.MIN_X, 0, CottagePlan.MIN_Z);
        BlockPos c = CottagePlan.at(origin, s.rot(), CottagePlan.MAX_X, CottagePlan.TOP, CottagePlan.MAX_Z);
        var box = new net.minecraft.world.phys.AABB(Math.min(a.getX(), c.getX()), s.floorY() - 1, Math.min(a.getZ(), c.getZ()),
                Math.max(a.getX(), c.getX()) + 1, s.floorY() + CottagePlan.TOP + 1, Math.max(a.getZ(), c.getZ()) + 1);
        Vec3 front = Vec3.atBottomCenterOf(CottagePlan.at(origin, s.rot(), 4, 1, -5));
        for (TownNpc npc : level.getEntitiesOfClass(TownNpc.class, box)) npc.teleportTo(front.x, front.y, front.z);
        TownNpc oren = oren(level, h);
        if (oren != null) {
            oren.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
            oren.gather(Vec3.atBottomCenterOf(CottagePlan.at(origin, s.rot(), 4, 1, -4)), 20 * 10);
        }
        ServerPlayer sp = level.getServer().getPlayerList().getPlayer(by);
        if (sp != null) {
            HearthService.player(sp).tips.remove(TIP_NO_ROOM);
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Oren is building your cottage"))
                    .body(Component.literal("He's starting on the foundation beside your Anchor. " + readyBy(level.getServer(), b)
                            + " He keeps working while you're away."))
                    .ttl(20 * 8).priority(4).build());
            Guide.refresh(sp);
        }
        return b;
    }

    /** The plants and the bumps the floor replaces, in one go. */
    private static void clearSite(ServerLevel level, BlockPos origin, Rotation rot) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int z = CottagePlan.MIN_Z; z <= CottagePlan.MAX_Z; z++) {
            for (int x = CottagePlan.MIN_X; x <= CottagePlan.MAX_X; x++) {
                boolean footprint = (x >= 0 && x <= 8 && z >= 0 && z <= 8) || (x == CottagePlan.CHIMNEY_X && z == CottagePlan.CHIMNEY_Z);
                BlockPos w = CottagePlan.at(origin, rot, x, 0, z);
                for (int y = 1; y <= CottagePlan.TOP; y++) {
                    BlockState s = level.getBlockState(m.set(w.getX(), origin.getY() + y, w.getZ()));
                    if (s.isAir()) continue;
                    if (clearable(s) || (footprint && natural(s))) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The overworld's clock: sleeping through the night moves it on, and the build with it. */
    private static long clock(MinecraftServer server) {
        return server.overworld().getOverworldClockTime();
    }

    private static Live live(HearthRecord h, CottageBuild b) {
        Live l = LIVE_PLANS.get(h.owner);
        if (l != null && l.build == b) return l;
        List<CottagePlan.Put> plan = CottagePlan.plan(CottagePlan.palette(b.palette), new CottagePlan.Ground() {
            @Override
            public int top(int x, int z) {
                int t = b.top(x, z);
                return t == VOID ? -5 : t;
            }

            @Override
            public boolean isGrass(int x, int z) {
                return b.grass(x, z);
            }
        }, b.origin.asLong());
        // each stage gets its share of the day, its blocks spread evenly across it
        int[] count = new int[CottagePlan.STAGES];
        for (CottagePlan.Put p : plan) count[p.stage()]++;
        long[] due = new long[plan.size()];
        int[] seen = new int[CottagePlan.STAGES];
        long[] start = new long[CottagePlan.STAGES];
        for (int st = 1; st < CottagePlan.STAGES; st++) start[st] = start[st - 1] + DURATION * SHARE[st - 1] / 100;
        for (int i = 0; i < plan.size(); i++) {
            int st = plan.get(i).stage();
            due[i] = start[st] + DURATION * SHARE[st] / 100 * ++seen[st] / count[st];
        }
        l = new Live(b, plan, due);
        LIVE_PLANS.put(h.owner, l);
        return l;
    }

    /** Every building Hearth, every tick: time worked, then the blocks that are due (or held for someone in the way). */
    private static void tickBuilds(MinecraftServer server) {
        for (HearthRecord h : HearthData.get(server).all()) {
            if (h.cottageBuild == null) continue;
            ServerLevel level = HearthService.levelOf(server, h);
            if (level != null) advance(level, h);
        }
    }

    /** One step of a Hearth's build. Package-private for the tests. */
    static void advance(ServerLevel level, HearthRecord h) {
        CottageBuild b = h.cottageBuild;
        MinecraftServer server = level.getServer();
        long game = level.getGameTime(), clock = clock(server);
        long worked = Math.max(game - b.lastGame, clock - b.lastClock); // a clock set back counts only the ticks
        b.worked = Math.min(DURATION, b.worked + Math.max(0, worked));
        b.lastGame = game;
        b.lastClock = clock;
        if (game % 20 == 0) HearthService.dirty(server);
        if (!loaded(level, b.origin, b.rot)) return; // the time still counts: he catches up when the chunks come back
        Live l = live(h, b);
        int due = 0;
        while (b.next + due < l.plan.size() && l.due[b.next + due] <= b.worked && due <= LIVE) due++;
        boolean quiet = due > LIVE;
        TownNpc oren = oren(level, h);
        boolean placed = false;
        for (int n = 0; n < CATCH_UP && b.next < l.plan.size() && l.due[b.next] <= b.worked; n++) {
            int i = b.next++;
            switch (place(level, b, l.plan.get(i))) {
                case PLACED -> {
                    placed = true;
                    if (!quiet) effects(level, b, l.plan.get(i), oren);
                }
                case BLOCKED -> b.held.add(i);
                case SKIPPED -> {}
            }
        }
        if (!b.held.isEmpty() && (game % 40 == 0 || b.next >= l.plan.size() && game % 10 == 0)) {
            Iterator<Integer> it = b.held.iterator();
            while (it.hasNext()) {
                int i = it.next();
                Placed r = place(level, b, l.plan.get(i));
                if (r == Placed.BLOCKED) continue;
                it.remove();
                if (r == Placed.PLACED) {
                    placed = true;
                    effects(level, b, l.plan.get(i), oren);
                }
            }
        }
        if (placed) HearthService.dirty(server);
        if (oren != null && game % 100 == 0 && b.next < l.plan.size()) oren.gather(workSpot(b, l.plan.get(b.next)), 20 * 6);
        if (b.next >= l.plan.size() && b.held.isEmpty()) finish(level, h, l);
    }

    enum Placed { PLACED, BLOCKED, SKIPPED }

    /** One block of the build: never into someone standing there, never half a door or a bed, never over a player's build. */
    static Placed place(ServerLevel level, CottageBuild b, CottagePlan.Put p) {
        BlockPos w = CottagePlan.at(b.origin, b.rot, p.x(), p.y(), p.z());
        BlockState s = p.state().rotate(b.rot);
        BlockPos partner = null;
        boolean second = false;
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            second = s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            partner = second ? w.below() : w.above();
        } else if (s.getBlock() instanceof BedBlock) {
            Direction toHead = s.getValue(BedBlock.FACING);
            second = s.getValue(BedBlock.PART) == BedPart.HEAD;
            partner = second ? w.relative(toHead.getOpposite()) : w.relative(toHead);
        }
        if (second) {
            if (!level.getBlockState(partner).is(s.getBlock())) return Placed.BLOCKED; // waits for its first half
        } else if (inTheWay(level, w, s) || (partner != null && inTheWay(level, partner, s))) {
            return Placed.BLOCKED;
        }
        return put(level, b.origin, b.rot, p, false) ? Placed.PLACED : Placed.SKIPPED;
    }

    /** Someone (a player, Oren, a sheep) stands in the cell. */
    private static boolean inTheWay(ServerLevel level, BlockPos w, BlockState s) {
        if (s.getCollisionShape(level, w).isEmpty()) return false;
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(w).deflate(0.02)).isEmpty();
    }

    /** The block's own sound, a few embers, and Oren swinging at it when he's near. */
    private static void effects(ServerLevel level, CottageBuild b, CottagePlan.Put p, @Nullable TownNpc oren) {
        BlockPos w = CottagePlan.at(b.origin, b.rot, p.x(), p.y(), p.z());
        var sound = p.state().getSoundType().getPlaceSound();
        level.playSound(null, w, sound, SoundSource.BLOCKS, 0.8f, 0.85f + level.getRandom().nextFloat() * 0.25f);
        level.sendParticles(HearthContent.HEARTH_EMBER.get(), w.getX() + 0.5, w.getY() + 0.6, w.getZ() + 0.5, 3, 0.3, 0.25, 0.3, 0.01);
        if (oren == null) return;
        if (oren.distanceToSqr(Vec3.atCenterOf(w)) < 8 * 8) {
            oren.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            oren.getLookControl().setLookAt(w.getX() + 0.5, w.getY() + 0.5, w.getZ() + 0.5);
        }
        oren.gather(workSpot(b, p), 20 * 6);
    }

    /** Where Oren stands for a block: inside the room for the furniture, else just outside the site on the nearer side. */
    static Vec3 workSpot(CottageBuild b, CottagePlan.Put p) {
        int x = p.x(), z = p.z();
        boolean room = x >= 1 && x <= 7 && z >= 1 && z <= 7;
        if (p.stage() == CottagePlan.FURNISH && room) return Vec3.atBottomCenterOf(CottagePlan.at(b.origin, b.rot, 4, 1, 5));
        int lx = CottagePlan.MIN_X - 1, hx = CottagePlan.MAX_X + 1, lz = CottagePlan.MIN_Z - 1, hz = CottagePlan.MAX_Z + 1;
        int dl = x - lx, dh = hx - x, fl = z - lz, fh = hz - z, best = Math.min(Math.min(dl, dh), Math.min(fl, fh));
        int sx = Math.max(lx, Math.min(hx, x)), sz = Math.max(lz, Math.min(hz, z));
        if (best == dl) sx = lx;
        else if (best == dh) sx = hx;
        else if (best == fl) sz = lz;
        else sz = hz;
        return Vec3.atBottomCenterOf(CottagePlan.at(b.origin, b.rot, sx, 1, sz));
    }

    private static @Nullable TownNpc oren(ServerLevel level, HearthRecord h) {
        UUID id = h.npcs.get("oren_blacksmith");
        return id != null && level.getEntity(id) instanceof TownNpc npc && npc.isAlive() ? npc : null;
    }

    private static void finish(ServerLevel level, HearthRecord h, Live l) {
        CottageBuild b = l.build;
        MinecraftServer server = level.getServer();
        refreshShapes(level, b.origin, b.rot, l.plan);
        h.cottage = CottagePlan.at(b.origin, b.rot, CottagePlan.INSIDE.getX(), CottagePlan.INSIDE.getY(), CottagePlan.INSIDE.getZ());
        h.cottageBuild = null;
        LIVE_PLANS.remove(h.owner);
        h.scheduleScan(level.getGameTime(), 1); // its room is a Sanctuary right away: the scan says so
        HearthService.dirty(server);
        TownNpc oren = oren(level, h);
        if (oren != null && oren.getItemInHand(InteractionHand.MAIN_HAND).is(Items.MACE)) oren.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos door = CottagePlan.at(b.origin, b.rot, 4, 1, 0);
        level.playSound(null, door, HearthContent.SND_ANCHOR.get(), SoundSource.BLOCKS, 0.9f, 1.25f);
        level.sendParticles(HearthContent.HEARTH_EMBER.get(), door.getX() + 0.5, door.getY() + 1.5, door.getZ() + 0.5, 30, 1.2, 1.0, 1.2, 0.02);
        java.util.Set<UUID> tell = new java.util.LinkedHashSet<>(h.everyone());
        tell.add(b.by);
        for (UUID id : tell) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp == null) continue; // an owner who was away gets the "once" marker at the next login (Guide)
            if (id.equals(b.by)) HearthService.player(sp).tips.add(TIP_BUILT);
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Oren's cottage is finished"))
                    .body(Component.literal("Your first Sanctuary is ready beside your Anchor. Sleep there to heal a Fracture. Oren "
                            + "built this one; the rest are yours to build."))
                    .ttl(20 * 8).priority(4).build());
            Guide.refresh(sp);
        }
    }

    /** For a test or an impatient admin: as if {@code ticks} more had passed (the next step catches up). */
    public static boolean hurry(HearthRecord h, long ticks) {
        CottageBuild b = h.cottageBuild;
        if (b == null) return false;
        b.lastGame -= ticks;
        b.lastClock -= ticks;
        return true;
    }

    // ---- what the guide says -----------------------------------------------------------------------------------------

    /** "Oren is building your cottage. He's on the roof now. It'll be ready by tomorrow evening." ("" when not building.) */
    public static String status(MinecraftServer server, HearthRecord h) {
        CottageBuild b = h.cottageBuild;
        if (b == null) return "";
        Live l = LIVE_PLANS.get(h.owner);
        int stage = CottagePlan.FOUNDATION;
        if (l != null && l.build == b && !l.plan.isEmpty()) stage = l.plan.get(Math.min(b.next, l.plan.size() - 1)).stage();
        String doing = switch (stage) {
            case CottagePlan.FOUNDATION -> "He's laying the foundation.";
            case CottagePlan.FRAME -> "The timber frame is going up.";
            case CottagePlan.WALLS -> "He's building the walls.";
            case CottagePlan.ROOF -> "He's on the roof now.";
            case CottagePlan.CHIMNEY -> "He's building the chimney.";
            default -> "He's moving the furniture in.";
        };
        String waiting = !b.held.isEmpty() && b.next >= (l == null ? Integer.MAX_VALUE : l.plan.size())
                ? " He's waiting for someone to step out of the way." : "";
        return "Oren is building your cottage. " + doing + " " + readyBy(server, b) + waiting;
    }

    /** When it'll stand, in the words you'd use: "It'll be ready by tomorrow evening." */
    static String readyBy(MinecraftServer server, CottageBuild b) {
        long left = DURATION - b.worked;
        if (left < 1200) return "It'll be ready in a minute or two.";
        long now = clock(server), end = now + left;
        // the clock's day starts at 6 am; people count days from midnight
        long dayNow = Math.floorDiv(now + 6000, 24000), dayEnd = Math.floorDiv(end + 6000, 24000);
        int hour = (int) (Math.floorMod(end + 6000, 24000) / 1000);
        long days = dayEnd - dayNow;
        if (hour < 5) days--; // after midnight still reads as that night
        String when;
        if (hour >= 5 && hour < 11) when = days == 0 ? "by later this morning" : days == 1 ? "by tomorrow morning" : null;
        else if (hour >= 11 && hour < 14) when = days == 0 ? "around midday" : days == 1 ? "around midday tomorrow" : null;
        else if (hour >= 14 && hour < 18) when = days == 0 ? "by this afternoon" : days == 1 ? "by tomorrow afternoon" : null;
        else if (hour >= 18 && hour < 22) when = days == 0 ? "by this evening" : days == 1 ? "by tomorrow evening" : null;
        else when = days <= 0 ? "by tonight" : days == 1 ? "by tomorrow night" : null;
        return when == null ? "It'll be ready in a couple of days." : "It'll be ready " + when + ".";
    }
}
