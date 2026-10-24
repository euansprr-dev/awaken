package dev.awaken.hearth.guide;

import dev.awaken.hearth.HearthContent;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
 * job finds open, flat-enough ground near the Anchor (never on anyone's build: only terrain, plants and whole natural
 * trees give way), fits the plinth to the terrain and raises {@link CottagePlan} over a few seconds, row by row with the
 * blocks' own sounds and embers, Oren swinging his hammer at the door. Its room scans as a Sanctuary the moment it's done.
 * Budgeted: a few candidate spots and a few blocks per tick.
 */
public final class Cottage {
    private Cottage() {}

    public static final String ACTION = Guide.ACTION + ":cottage";
    public static final String TIP_OFFERED = "cottage_offered", TIP_DECLINED = "cottage_declined", TIP_BUILT = "cottage_built",
            TIP_NO_ROOM = "cottage_no_room";
    /** Blocks placed per tick: about six seconds for the whole cottage. */
    static final int PER_TICK = 4;
    /** Candidate spots checked in full per tick. */
    static final int CHECKS_PER_TICK = 2;

    private static final Map<UUID, Job> JOBS = new HashMap<>();
    /** Where each owner's cottage went this session (for the camera commands). */
    private static final Map<UUID, Site> LAST = new HashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            for (Job j : JOBS.values()) j.finishNow(); // never leave half a cottage behind
            JOBS.clear();
        });
    }

    // ---- the offer --------------------------------------------------------------------------------------------------

    /** The Anchor took root: a few seconds later (after the welcome), offer the cottage if this player never had it. */
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
    static int state(ServerPlayer sp, HearthRecord h) {
        HearthPlayer hp = HearthService.player(sp);
        if (JOBS.containsKey(sp.getUUID())) return GuideData.COTTAGE_BUILDING;
        if (h.cottage != null) return GuideData.COTTAGE_BUILT;
        if (hp.tips.contains(TIP_BUILT)) return GuideData.COTTAGE_NONE; // free once: it stands at an old home
        if (hp.tips.contains(TIP_NO_ROOM)) return GuideData.COTTAGE_NO_ROOM;
        return hp.tips.contains(TIP_DECLINED) ? GuideData.COTTAGE_DECLINED : GuideData.COTTAGE_OFFER;
    }

    /** Yes: find a spot and raise it. False when it can't start (built before, already building, no Hearth). */
    public static boolean start(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        var ho = HearthService.hearthOf(sp);
        if (ho.isEmpty() || hp.tips.contains(TIP_BUILT) || JOBS.containsKey(sp.getUUID())) return false;
        hp.tips.add(TIP_OFFERED);
        Signal.withdraw(sp, ACTION);
        ServerLevel level = HearthService.levelOf(sp.level().getServer(), ho.get());
        JOBS.put(sp.getUUID(), new Job(level, ho.get(), sp.getUUID()));
        return true;
    }

    public static boolean building(UUID owner) {
        return JOBS.containsKey(owner);
    }

    public static @Nullable Site lastSite(UUID owner) {
        return LAST.get(owner);
    }

    private static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Iterator<Job> it = JOBS.values().iterator();
        while (it.hasNext()) {
            Job j = it.next();
            if (j.tick(server)) it.remove();
        }
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

    /** The plan for a site: the plinth follows its ground; the path only crosses grass and dirt. */
    static List<CottagePlan.Put> planFor(ServerLevel level, Site s) {
        return CottagePlan.plan(s.palette(), new CottagePlan.Ground() {
            @Override
            public int top(int x, int z) {
                int t = s.top(x, z);
                return t == VOID ? -5 : t;
            }

            @Override
            public boolean isGrass(int x, int z) {
                BlockState g = level.getBlockState(CottagePlan.at(s.origin().atY(s.floorY()), s.rot(), x, 0, z));
                return g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.DIRT) || g.is(Blocks.COARSE_DIRT) || g.is(Blocks.PODZOL);
            }
        }, s.origin().asLong());
    }

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

    // ---- the job ----------------------------------------------------------------------------------------------------

    private static final class Job {
        private enum Phase { SEARCH, CLEAR, BUILD, DONE }

        final ServerLevel level;
        final HearthRecord hearth;
        final UUID owner;
        Phase phase = Phase.SEARCH;
        List<Candidate> candidates;
        int next;
        Site site;
        List<CottagePlan.Put> plan;
        int index, treeIndex;
        int lastRowY = Integer.MIN_VALUE, lastRowZ = Integer.MIN_VALUE, lastStage = -1;
        long lastSound;
        ItemStack orensHand = ItemStack.EMPTY;
        TownNpc oren;

        Job(ServerLevel level, HearthRecord hearth, UUID owner) {
            this.level = level;
            this.hearth = hearth;
            this.owner = owner;
        }

        /** Returns true when the job is over. */
        boolean tick(MinecraftServer server) {
            switch (phase) {
                case SEARCH -> search(server);
                case CLEAR -> clear();
                case BUILD -> build(server);
                default -> {}
            }
            return phase == Phase.DONE;
        }

        private void search(MinecraftServer server) {
            if (!level.isLoaded(hearth.pos)) return;
            if (candidates == null) {
                candidates = candidates(level, hearth);
                return;
            }
            for (int i = 0; i < CHECKS_PER_TICK && next < candidates.size(); i++) {
                Site s = check(level, hearth, candidates.get(next++));
                if (s != null) {
                    begin(server, s);
                    return;
                }
            }
            if (next >= Math.min(candidates.size(), 40)) {
                phase = Phase.DONE;
                ServerPlayer sp = server.getPlayerList().getPlayer(owner);
                if (sp != null) {
                    HearthService.player(sp).tips.add(TIP_NO_ROOM);
                    Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("No room for a cottage"))
                            .body(Component.literal("Oren needs open ground. He looked for a flat, clear patch about 12 blocks across "
                                    + "within 22 of your Anchor, away from your builds, and found none. Clear one, then ask again in your "
                                    + "Hearth (use the Anchor)."))
                            .ttl(20 * 10).build());
                    Guide.refresh(sp);
                }
            }
        }

        private void begin(MinecraftServer server, Site s) {
            site = s;
            LAST.put(owner, s);
            plan = planFor(level, s);
            phase = Phase.CLEAR;
            ServerPlayer sp = server.getPlayerList().getPlayer(owner);
            if (sp != null) {
                HearthService.player(sp).tips.remove(TIP_NO_ROOM);
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Oren gets to work"))
                        .body(Component.literal("Watch: a cottage, beside your Anchor.")).ttl(20 * 5).build());
            }
            // townsfolk wandering on the spot step out to the front first
            BlockPos a = CottagePlan.at(s.origin(), s.rot(), CottagePlan.MIN_X, 0, CottagePlan.MIN_Z);
            BlockPos b = CottagePlan.at(s.origin(), s.rot(), CottagePlan.MAX_X, CottagePlan.TOP, CottagePlan.MAX_Z);
            var box = new net.minecraft.world.phys.AABB(Math.min(a.getX(), b.getX()), s.floorY() - 1, Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()) + 1, s.floorY() + CottagePlan.TOP + 1, Math.max(a.getZ(), b.getZ()) + 1);
            Vec3 front = Vec3.atBottomCenterOf(CottagePlan.at(s.origin(), s.rot(), 4, 1, -5));
            for (TownNpc npc : level.getEntitiesOfClass(TownNpc.class, box)) npc.teleportTo(front.x, front.y, front.z);
            UUID id = hearth.npcs.get("oren_blacksmith");
            if (id != null && level.getEntity(id) instanceof TownNpc npc) {
                oren = npc;
                orensHand = npc.getItemInHand(InteractionHand.MAIN_HAND).copy();
                npc.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
                Vec3 door = Vec3.atBottomCenterOf(CottagePlan.at(s.origin(), s.rot(), 4, 1, -4));
                npc.gather(door, 20 * 14);
            }
        }

        /** One tree a tick, then the plants and the bumps the floor replaces, in one go. */
        private void clear() {
            if (treeIndex < site.trees().size()) {
                fell(level, site.trees().get(treeIndex++));
                return;
            }
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int z = CottagePlan.MIN_Z; z <= CottagePlan.MAX_Z; z++) {
                for (int x = CottagePlan.MIN_X; x <= CottagePlan.MAX_X; x++) {
                    boolean footprint = (x >= 0 && x <= 8 && z >= 0 && z <= 8) || (x == CottagePlan.CHIMNEY_X && z == CottagePlan.CHIMNEY_Z);
                    BlockPos w = CottagePlan.at(site.origin(), site.rot(), x, 0, z);
                    for (int y = 1; y <= CottagePlan.TOP; y++) {
                        BlockState s = level.getBlockState(m.set(w.getX(), site.floorY() + y, w.getZ()));
                        if (s.isAir()) continue;
                        if (clearable(s) || (footprint && natural(s))) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            phase = Phase.BUILD;
        }

        private void build(MinecraftServer server) {
            BlockPos origin = site.origin().atY(site.floorY());
            if (!level.isLoaded(CottagePlan.at(origin, site.rot(), 4, 0, 4))) return; // wait for the chunk to come back
            for (int n = 0; n < PER_TICK && index < plan.size(); n++) {
                CottagePlan.Put p = plan.get(index++);
                if (!put(level, origin, site.rot(), p, false)) continue;
                BlockPos w = CottagePlan.at(origin, site.rot(), p.x(), p.y(), p.z());
                boolean newRow = p.y() != lastRowY || p.z() != lastRowZ || p.stage() != lastStage;
                if (newRow) {
                    lastRowY = p.y();
                    lastRowZ = p.z();
                    lastStage = p.stage();
                    if (level.getGameTime() - lastSound >= 3) { // a building rhythm, not a din
                        lastSound = level.getGameTime();
                        var sound = p.state().getSoundType().getPlaceSound();
                        level.playSound(null, w, sound, SoundSource.BLOCKS, 0.7f, 0.85f + level.getRandom().nextFloat() * 0.25f);
                    }
                    if (oren != null && oren.isAlive()) {
                        oren.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                        oren.getLookControl().setLookAt(w.getX() + 0.5, w.getY() + 0.5, w.getZ() + 0.5);
                    }
                }
                if (!p.state().isAir()) {
                    level.sendParticles(HearthContent.HEARTH_EMBER.get(), w.getX() + 0.5, w.getY() + 0.6, w.getZ() + 0.5, 1, 0.25, 0.2, 0.25, 0.005);
                }
            }
            if (index >= plan.size()) finish(server);
        }

        private void finish(MinecraftServer server) {
            BlockPos origin = site.origin().atY(site.floorY());
            refreshShapes(level, origin, site.rot(), plan);
            BlockPos inside = CottagePlan.at(origin, site.rot(), CottagePlan.INSIDE.getX(), CottagePlan.INSIDE.getY(), CottagePlan.INSIDE.getZ());
            hearth.cottage = inside;
            hearth.scheduleScan(level.getGameTime(), 1); // its room is a Sanctuary right away: the scan says so
            HearthService.dirty(server);
            if (oren != null && oren.isAlive()) oren.setItemInHand(InteractionHand.MAIN_HAND, orensHand);
            BlockPos door = CottagePlan.at(origin, site.rot(), 4, 1, 0);
            level.playSound(null, door, HearthContent.SND_ANCHOR.get(), SoundSource.BLOCKS, 0.9f, 1.25f);
            level.sendParticles(HearthContent.HEARTH_EMBER.get(), door.getX() + 0.5, door.getY() + 1.5, door.getZ() + 0.5, 30, 1.2, 1.0, 1.2, 0.02);
            ServerPlayer sp = server.getPlayerList().getPlayer(owner);
            if (sp != null) {
                HearthService.player(sp).tips.add(TIP_BUILT);
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Oren's cottage"))
                        .body(Component.literal("Your first Sanctuary is up. Sleep there to heal a Fracture. Oren built this one; "
                                + "build the rest yourself."))
                        .ttl(20 * 8).priority(4).build());
                Guide.refresh(sp);
            } // an owner who left mid-build gets the "once" marker at the next login (Guide)
            phase = Phase.DONE;
        }

        /** The server is stopping: place what's left now. */
        void finishNow() {
            if (phase == Phase.SEARCH || phase == Phase.DONE || site == null) return;
            while (treeIndex < site.trees().size()) fell(level, site.trees().get(treeIndex++));
            if (phase == Phase.CLEAR) clear();
            BlockPos origin = site.origin().atY(site.floorY());
            while (index < plan.size()) put(level, origin, site.rot(), plan.get(index++), false);
            refreshShapes(level, origin, site.rot(), plan);
            hearth.cottage = CottagePlan.at(origin, site.rot(), CottagePlan.INSIDE.getX(), CottagePlan.INSIDE.getY(), CottagePlan.INSIDE.getZ());
            HearthService.dirty(level.getServer());
            phase = Phase.DONE;
        }
    }
}
