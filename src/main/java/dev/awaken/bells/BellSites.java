package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.TheLull;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sleeping bells out in the land (SPEC §43.7): where a bell fell, the place fell asleep with it, grey and quiet. Each
 * 640-block cell past the city's ridge may hold one (rank by distance: E near, then D, then C), and every home gets one
 * of its own close by (the Wren Bell). A sleeping bell hums within 64 blocks (a Heard lead, crossed into an area the
 * second time from somewhere else), looks asleep within 24 (Seen), and wakes when struck: buried ones must be dug out,
 * sunk ones dived for, high ones climbed, and kept ones have something that gathered round them to deal with first.
 */
public final class BellSites {
    private BellSites() {}

    public enum Kind { BURIED, KEPT, SUNK, HIGH }

    static final int CELL = 640, HUM = 64, SEEN = 24, BUILD = 96, KEEPER_WAKES = 16, WAKE_REACH = 48, PAINT = 14;
    static final UUID BEATEN = new UUID(0L, 0L);
    /** Ticks between one player's hums from one bell. */
    static final int HUM_EVERY = 160;

    /** The land's sleeping bells by rank band, each with its kind of place. */
    static final Map<Rank, List<String>> POOL = Map.of(
            Rank.E, List.of("barrow", "mere", "wayside"),
            Rank.D, List.of("cairn", "spire", "reed"),
            Rank.C, List.of("thorn", "crag", "fen"));
    static final Map<String, Kind> KIND = Map.ofEntries(
            Map.entry("wren", Kind.BURIED), Map.entry("barrow", Kind.BURIED), Map.entry("mere", Kind.SUNK), Map.entry("wayside", Kind.KEPT),
            Map.entry("cairn", Kind.KEPT), Map.entry("spire", Kind.HIGH), Map.entry("reed", Kind.SUNK),
            Map.entry("thorn", Kind.KEPT), Map.entry("crag", Kind.HIGH), Map.entry("fen", Kind.SUNK));

    /** Rank band by distance from the world's centre (blocks): E from past the ridge, then D, then C. */
    static @Nullable Rank band(MinecraftServer server, int x, int z) {
        BlockPos c = TheLull.centre(server);
        double d = Math.hypot(x - c.getX(), z - c.getZ());
        if (d < TheLull.ridge(server) + 120) return null;
        if (d < 1300) return Rank.E;
        if (d < 2800) return Rank.D;
        if (d < 4800) return Rank.C;
        return null;
    }

    static String cellKey(int cx, int cz) {
        return "c" + cx + "," + cz;
    }

    /** The cell's own sleeping bell as the world seed lays it out (not yet built), or null. */
    static BellWorld.@Nullable Site planned(ServerLevel level, int cx, int cz) {
        RandomSource r = RandomSource.create(level.getSeed() ^ (cx * 341873128712L + cz * 132897987541L) ^ 0x5EEDBE11L);
        int x = cx * CELL + 96 + r.nextInt(CELL - 192), z = cz * CELL + 96 + r.nextInt(CELL - 192);
        Rank band = band(level.getServer(), x, z);
        if (band == null) return null;
        float chance = band == Rank.E ? 0.85F : band == Rank.D ? 0.7F : 0.6F;
        if (r.nextFloat() >= chance) return null;
        List<String> pool = POOL.get(band);
        String bell = pool.get(r.nextInt(pool.size()));
        return new BellWorld.Site(bell, KIND.get(bell), new BlockPos(x, 0, z));
    }

    /** Every sleeping place (saved or planned) whose column is within {@code r} of {@code at}, by key. */
    static Map<String, BellWorld.Site> near(ServerLevel level, BlockPos at, int r) {
        BellWorld w = BellWorld.get(level.getServer());
        Map<String, BellWorld.Site> out = new HashMap<>();
        for (int cx = Math.floorDiv(at.getX() - r, CELL); cx <= Math.floorDiv(at.getX() + r, CELL); cx++)
            for (int cz = Math.floorDiv(at.getZ() - r, CELL); cz <= Math.floorDiv(at.getZ() + r, CELL); cz++) {
                String key = cellKey(cx, cz);
                BellWorld.Site s = w.sites.get(key);
                if (s == null) s = planned(level, cx, cz);
                if (s != null && close(s.pos, at, r)) out.put(key, s);
            }
        for (Map.Entry<String, BellWorld.Site> e : w.sites.entrySet())
            if (!e.getKey().startsWith("c") && close(e.getValue().pos, at, r)) out.put(e.getKey(), e.getValue());
        return out;
    }

    private static boolean close(BlockPos a, BlockPos b, int r) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz <= (double) r * r;
    }

    // ---- the home's own (the Wren Bell) -------------------------------------------------------------------------------

    /** A home's near sleeping bell: 90 to 160 blocks out, away from the city. Chosen once, the first time it's asked for. */
    static void planHome(ServerLevel level, BlockPos anchor) {
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Home h = w.home(anchor);
        if (!h.wren.isEmpty()) return;
        RandomSource r = RandomSource.create(anchor.asLong() ^ level.getSeed() ^ 0x77E4L);
        BlockPos at = null;
        for (int i = 0; i < 12 && at == null; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 90 + r.nextInt(71);
            BlockPos p = anchor.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
            if (!CityHooks.inCity(level, p.atY(64))) at = p;
        }
        if (at == null) at = anchor.offset(120, 0, 0);
        String key = "h" + anchor.asLong();
        w.sites.put(key, new BellWorld.Site("wren", Kind.BURIED, new BlockPos(at.getX(), 0, at.getZ())));
        h.wren = key;
        w.setDirty();
    }

    // ---- each player, every 5 s ----------------------------------------------------------------------------------------

    /** When each player last heard each sleeping bell hum (game time), by player then key. Not saved. */
    private static final Map<UUID, Map<String, Long>> HEARD = new HashMap<>();

    static void tick(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || p.isSpectator()) return;
        BellWorld w = BellWorld.get(level.getServer());
        BellBook book = Bells.book(p);
        long now = level.getGameTime();
        for (Map.Entry<String, BellWorld.Site> e : near(level, p.blockPosition(), BUILD).entrySet()) {
            String key = e.getKey();
            BellWorld.Site s = e.getValue();
            if (s.state == BellWorld.Site.PLANNED) {
                if (!level.isLoaded(s.pos.atY(64))) continue;
                build(level, key, s);
                w.sites.put(key, s);
                w.setDirty();
            }
            if (s.state != BellWorld.Site.ASLEEP) {
                if (s.state == BellWorld.Site.AWAKE && !s.painted && level.isLoaded(s.pos)) paint(level, key, s);
                continue;
            }
            BellCatalog.Def def = BellCatalog.of(s.bell);
            if (def == null) continue;
            double d = Math.sqrt(p.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5));
            if (s.kind == Kind.KEPT && d < KEEPER_WAKES) keeper(level, key, s, p);
            if (book.held.containsKey(def.no()) || d > HUM) continue;
            Map<String, Long> mine = HEARD.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
            if (now - mine.getOrDefault(key, -10_000L) >= HUM_EVERY) {
                mine.put(key, now);
                p.connection.send(new ClientboundSoundPacket(BellContent.HUM, SoundSource.AMBIENT, s.pos.getX() + 0.5, s.pos.getY() + 0.5,
                        s.pos.getZ() + 0.5, 4F, BellContent.pitch(def.no()), now));
                Bells.heard(p, def, s.pos);
            }
            if (d <= SEEN) Bells.seen(p, def);
        }
    }

    static void forget(ServerPlayer p) {
        HEARD.remove(p.getUUID());
    }

    // ---- building a sleeping place --------------------------------------------------------------------------------------

    /** Builds the place round its column, all in one go (a few hundred blocks). Unbuildable ground makes it GONE. */
    static void build(ServerLevel level, String key, BellWorld.Site s) {
        int x = s.pos.getX(), z = s.pos.getZ();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos ground = new BlockPos(x, top, z);
        RandomSource r = RandomSource.create(ground.asLong() ^ 0xBE11L);
        if (CityHooks.inCity(level, ground) || HearthHooks.inside(level, ground) || top <= level.getMinY() + 4) {
            s.state = BellWorld.Site.GONE;
            Awaken.LOG.info("[bells] {} at {} can't lie there (city, a home or no ground)", s.bell, ground.toShortString());
            return;
        }
        boolean wet = !level.getFluidState(ground).isEmpty() || level.getBlockState(ground).is(Blocks.WATER);
        if (wet && s.kind != Kind.SUNK) {
            // a bell that fell into water lies on the bottom instead
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            if (top - floor > 12) {
                s.state = BellWorld.Site.GONE;
                return;
            }
        }
        BlockState bell = BellContent.SLEEPING_BELL.get().defaultBlockState();
        BlockPos at;
        if (wet) {
            int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            at = new BlockPos(x, floor, z);
            level.setBlock(at, bell, Block.UPDATE_CLIENTS);
        } else {
            dress(level, ground, 7, r);
            at = switch (s.kind) {
                case BURIED -> buried(level, ground, bell);
                case KEPT -> kept(level, ground, bell, r);
                case SUNK -> sunk(level, ground, bell);
                case HIGH -> high(level, ground, bell, r);
            };
        }
        s.pos = at;
        s.state = BellWorld.Site.ASLEEP;
        Awaken.LOG.info("[bells] built {} ({}) at {}", s.bell, s.kind, at.toShortString());
    }

    /** The asleep look: grey moss and coarse earth over the ground, dead bushes and grey carpet where plants were. */
    private static void dress(ServerLevel level, BlockPos c, int r, RandomSource rand) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r || rand.nextFloat() < (dx * dx + dz * dz) / (float) (r * r) * 0.6F) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX() + dx, c.getZ() + dz) - 1;
                BlockPos g = new BlockPos(c.getX() + dx, y, c.getZ() + dz);
                BlockState s = level.getBlockState(g);
                if (!(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.MYCELIUM))) continue;
                level.setBlock(g, (rand.nextFloat() < 0.62F ? Blocks.PALE_MOSS_BLOCK : Blocks.COARSE_DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
                BlockPos up = g.above();
                BlockState a = level.getBlockState(up);
                if (a.isAir() || a.canBeReplaced()) {
                    float f = rand.nextFloat();
                    BlockState put = f < 0.3F ? Blocks.PALE_MOSS_CARPET.defaultBlockState() : f < 0.4F ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    if (!a.isAir() || !put.isAir()) level.setBlock(up, put, Block.UPDATE_CLIENTS);
                }
            }
    }

    private static BlockPos buried(ServerLevel level, BlockPos g, BlockState bell) {
        BlockPos at = g.below();
        level.setBlock(at, bell, Block.UPDATE_CLIENTS);
        level.setBlock(g, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        // a low grey mound over it
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (Math.abs(dx) + Math.abs(dz) <= 1) level.setBlock(g.offset(dx, 1, dz), (dx == 0 && dz == 0 ? Blocks.COARSE_DIRT : Blocks.PALE_MOSS_BLOCK).defaultBlockState(), Block.UPDATE_CLIENTS);
        return at;
    }

    private static BlockPos kept(ServerLevel level, BlockPos g, BlockState bell, RandomSource r) {
        BlockPos at = g.above();
        level.setBlock(at, bell, Block.UPDATE_CLIENTS);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            BlockPos p = new BlockPos(g.getX() + (int) Math.round(Math.cos(a) * 4), 0, g.getZ() + (int) Math.round(Math.sin(a) * 4));
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
            int h = 1 + r.nextInt(2);
            for (int k = 0; k < h; k++)
                level.setBlock(p.atY(y + k), (r.nextBoolean() ? Blocks.MOSSY_COBBLESTONE : Blocks.CRACKED_STONE_BRICKS).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return at;
    }

    private static BlockPos sunk(ServerLevel level, BlockPos g, BlockState bell) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) continue;
                for (int dy = -3; dy <= 0; dy++) level.setBlock(g.offset(dx, dy, dz), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int dy = 1; dy <= 2; dy++) level.setBlock(g.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(g.offset(dx, -4, dz), Blocks.CLAY.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        BlockPos at = g.below(3);
        level.setBlock(at, bell, Block.UPDATE_CLIENTS);
        return at;
    }

    private static BlockPos high(ServerLevel level, BlockPos g, BlockState bell, RandomSource r) {
        int h = 12 + r.nextInt(5);
        for (int k = 0; k <= h; k++) {
            double rad = 2.6 * Math.pow(1 - k / (double) h, 0.7) + 0.6;
            int ri = (int) Math.ceil(rad);
            for (int dx = -ri; dx <= ri; dx++)
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > rad * rad) continue;
                    float f = r.nextFloat();
                    Block b = k >= h - 1 ? Blocks.PALE_MOSS_BLOCK : f < 0.45F ? Blocks.STONE : f < 0.75F ? Blocks.ANDESITE : f < 0.9F ? Blocks.COBBLESTONE : Blocks.MOSSY_COBBLESTONE;
                    level.setBlock(g.offset(dx, k + 1, dz), b.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            // a ledge every other block, round the spire: enough to climb
            if (k % 2 == 0 && k < h - 1) {
                double a = k * 0.9;
                int lx = (int) Math.round(Math.cos(a) * (rad + 1)), lz = (int) Math.round(Math.sin(a) * (rad + 1));
                level.setBlock(g.offset(lx, k + 1, lz), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos at = g.above(h + 2);
        level.setBlock(at, bell, Block.UPDATE_CLIENTS);
        return at;
    }

    // ---- keepers ----------------------------------------------------------------------------------------------------

    /** Something gathered round a kept bell: it comes out when anyone gets close, once, until it's beaten. */
    private static void keeper(ServerLevel level, String key, BellWorld.Site s, ServerPlayer near) {
        if (BEATEN.equals(s.keeper)) return;
        if (s.keeper != null && level.getEntity(s.keeper) instanceof LivingEntity e && e.isAlive()) return;
        if (s.keeper != null && !level.isLoaded(s.pos)) return;
        BellCatalog.Def def = BellCatalog.of(s.bell);
        Rank rank = def == null ? Rank.D : def.rank();
        String theme = dev.awaken.hearth.trial.Trials.themeFor(new UUID(s.pos.asLong(), 0x8E11L), rank);
        double a = Math.atan2(near.getZ() - s.pos.getZ(), near.getX() - s.pos.getX());
        BlockPos at = s.pos.offset((int) (Math.cos(a) * 3), 0, (int) (Math.sin(a) * 3));
        at = at.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
        LivingEntity e = MobHooks.spawnThemed(level, theme, MobRole.Role.ELITE, at, rank.contentLevel, Set.of(), "bell:" + key);
        BellWorld w = BellWorld.get(level.getServer());
        if (e == null) {
            // nothing comes (Peaceful, or no creature of that kind): the bell is free to strike
            s.keeper = BEATEN;
        } else {
            if (e instanceof Mob m) m.setPersistenceRequired();
            s.keeper = e.getUUID();
            Bells.say(near, "[Something was sleeping round the bell. It isn't any more.]");
        }
        w.setDirty();
    }

    /** A keeper died: its bell can be struck. */
    static void keeperDied(ServerLevel level, String key) {
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Site s = w.sites.get(key);
        if (s == null) return;
        s.keeper = BEATEN;
        w.setDirty();
    }

    // ---- striking and waking --------------------------------------------------------------------------------------------

    /** The site whose bell is at {@code pos}, or null. */
    static Map.@Nullable Entry<String, BellWorld.Site> at(ServerLevel level, BlockPos pos) {
        for (Map.Entry<String, BellWorld.Site> e : BellWorld.get(level.getServer()).sites.entrySet())
            if (e.getValue().pos.equals(pos)) return e;
        return null;
    }

    /** A player strikes a sleeping (or woken) bell. */
    static void strike(ServerLevel level, BlockPos pos, ServerPlayer p) {
        Map.Entry<String, BellWorld.Site> e = at(level, pos);
        if (e == null) {
            level.playSound(null, pos, BellContent.HANDBELL_RING.get(), SoundSource.BLOCKS, 0.6F, 0.6F);
            return;
        }
        BellWorld.Site s = e.getValue();
        BellCatalog.Def def = BellCatalog.of(s.bell);
        if (def == null) return;
        if (s.state == BellWorld.Site.AWAKE) {
            level.playSound(null, pos, BellContent.note(def.no()), SoundSource.BLOCKS, 3F, 1F);
            if (!Bells.book(p).held.containsKey(def.no())) Bells.ring(p, def, "where you woke it");
            return;
        }
        if (s.kind == Kind.KEPT && !BEATEN.equals(s.keeper)) {
            level.playSound(null, pos, BellContent.GAP.get(), SoundSource.BLOCKS, 2F, 0.7F);
            if (s.keeper == null || !(level.getEntity(s.keeper) instanceof LivingEntity k && k.isAlive())) keeper(level, e.getKey(), s, p);
            Bells.say(p, "[It won't ring while that thing is here.]");
            return;
        }
        wake(level, e.getKey(), s, p);
    }

    static void wake(ServerLevel level, String key, BellWorld.Site s, @Nullable ServerPlayer by) {
        BellCatalog.Def def = BellCatalog.of(s.bell);
        if (def == null) return;
        s.state = BellWorld.Site.AWAKE;
        BellWorld.get(level.getServer()).setDirty();
        BlockState st = level.getBlockState(s.pos);
        if (st.is(BellContent.SLEEPING_BELL.get())) level.setBlock(s.pos, st.setValue(SleepingBellBlock.AWAKE, true), Block.UPDATE_ALL);
        level.playSound(null, s.pos, BellContent.TOLL.get(), SoundSource.BLOCKS, 16F, BellContent.pitch(def.no()));
        level.playSound(null, s.pos, BellContent.WAKE.get(), SoundSource.AMBIENT, 4F, 1F);
        // the place comes back the way the world does after a Fault: on the mend
        dev.awaken.fault.Faults.mendNear(level, s.pos, WAKE_REACH);
        for (ServerPlayer q : level.players())
            if (q.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.pos)) < WAKE_REACH * WAKE_REACH && !Bells.book(q).held.containsKey(def.no()))
                Bells.ring(q, def, q == by ? "where you woke it" : "you were there when it woke");
        PAINTING.add(new Paint(key, s, 0));
        Awaken.LOG.info("[bells] {} woke at {}", def.id(), s.pos.toShortString());
    }

    /** The land round a woken bell turns back, ring by ring outward. */
    private record Paint(String key, BellWorld.Site s, int ring) {}

    private static final List<Paint> PAINTING = new ArrayList<>();

    /** Each server tick: two rings of each waking. */
    static void tickPaint(ServerLevel level) {
        if (PAINTING.isEmpty()) return;
        List<Paint> next = new ArrayList<>();
        for (Iterator<Paint> it = PAINTING.iterator(); it.hasNext(); ) {
            Paint p = it.next();
            if (!level.isLoaded(p.s.pos)) continue; // left as it is: painted whole when someone comes back
            int r = p.ring;
            for (int k = 0; k < 2 && r <= PAINT; k++, r++) ring(level, p.s.pos, r, level.getRandom());
            if (r > PAINT) {
                p.s.painted = true;
                BellWorld.get(level.getServer()).setDirty();
            } else next.add(new Paint(p.key, p.s, r));
        }
        PAINTING.clear();
        PAINTING.addAll(next);
    }

    private static void paint(ServerLevel level, String key, BellWorld.Site s) {
        for (Paint p : PAINTING) if (p.key.equals(key)) return;
        PAINTING.add(new Paint(key, s, 0));
    }

    private static void ring(ServerLevel level, BlockPos c, int r, RandomSource rand) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r || d2 <= (r - 1) * (r - 1) && r > 0) continue;
                for (int dy = -6; dy <= 18; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);
                    BlockState to = null;
                    if (s.is(Blocks.PALE_MOSS_BLOCK) || s.is(Blocks.COARSE_DIRT)) to = level.getBlockState(p.above()).isSolid() ? Blocks.DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                    else if (s.is(Blocks.PALE_MOSS_CARPET)) to = Blocks.SHORT_GRASS.defaultBlockState();
                    else if (s.is(Blocks.DEAD_BUSH)) {
                        float f = rand.nextFloat();
                        to = (f < 0.3F ? Blocks.POPPY : f < 0.55F ? Blocks.DANDELION : f < 0.8F ? Blocks.CORNFLOWER : Blocks.OXEYE_DAISY).defaultBlockState();
                    }
                    if (to != null) {
                        level.setBlock(p, to, Block.UPDATE_CLIENTS);
                        if (rand.nextFloat() < 0.15F)
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0);
                    }
                }
                if (rand.nextFloat() < 0.05F)
                    level.sendParticles(ParticleTypes.END_ROD, c.getX() + dx + 0.5, c.getY() + 1.5, c.getZ() + dz + 0.5, 1, 0.1, 0.4, 0.1, 0.02);
            }
    }
}
