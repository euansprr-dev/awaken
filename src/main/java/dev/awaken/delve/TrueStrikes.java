package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * True strikes (SPEC §34.3): on an ore or a lode block a thin bright seam shows on the face being struck, at a spot seeded
 * by the block, the face and how many true strikes it has taken. A bite landing within {@link #RADIUS} of the seam's
 * middle is true: the block's mining speed steps up ({@link #speed}), the seam moves, and a breaking blow that lands true
 * has a {@link #BONUS} chance of one more ore. Both sides run the same rule (the client predicts, the server decides with
 * its own ray from the eye); nothing is saved.
 */
public final class TrueStrikes {
    private TrueStrikes() {}

    /** How near the seam's middle a bite must land (blocks on the face). */
    public static final double RADIUS = 0.22, TEMPERED_RADIUS = 0.32;
    /** The server's extra allowance (its copy of the look is a tick behind). */
    static final double SLACK = 0.1;
    /** Half the seam's drawn length, along its angle. */
    public static final double HALF_LENGTH = 0.15;
    /** Each true strike adds this to the block's mining speed (times), up to {@link #MAX_SPEED}. */
    static final float STEP = 0.35F, MAX_SPEED = 2F;
    /** True strikes can't come faster than vanilla's bite. */
    static final int MIN_GAP = 3;
    /** The breaking blow landed true within this many ticks of the break: a chance of one more ore. */
    static final int BREAK_WINDOW = 6;
    public static final float BONUS = 0.25F;

    /** One player's strikes on one block. */
    static final class Strike {
        long pos = Long.MIN_VALUE;
        int trues;
        long lastTrue = Long.MIN_VALUE / 2;
    }

    // each side its own: in single player both run in one JVM under one UUID
    private static final Map<UUID, Strike> SERVER = new ConcurrentHashMap<>(), CLIENT = new ConcurrentHashMap<>();

    private static Map<UUID, Strike> side(boolean client) {
        return client ? CLIENT : SERVER;
    }

    /** Whether true strikes apply to this block (ores of the lode set and their hearts). */
    public static boolean applies(BlockState s) {
        return Ore.of(s) != null;
    }

    // ---- the seam ---------------------------------------------------------------------------------------------------

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /**
     * The seam on a face after {@code n} true strikes: {u, v, angle}, u and v across the face (0-1 along the face's two
     * other axes in x, y, z order), kept 0.22 from the edges so a bite there is never on a neighbour.
     */
    public static double[] seam(long pos, int face, int n) {
        long h = mix(pos * 31 + face * 0x9E3779B97F4A7C15L + n * 0xBF58476D1CE4E5B9L);
        double u = 0.22 + 0.56 * ((h & 0xFFFF) / 65535.0);
        double v = 0.22 + 0.56 * (((h >>> 16) & 0xFFFF) / 65535.0);
        double a = Math.PI * (((h >>> 32) & 0xFFFF) / 65535.0);
        return new double[]{u, v, a};
    }

    /** The world point of (u, v) on this face of the block. */
    public static Vec3 onFace(BlockPos pos, Direction face, double u, double v) {
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        return switch (face.getAxis()) {
            case X -> new Vec3(pos.getX() + plane, pos.getY() + u, pos.getZ() + v);
            case Y -> new Vec3(pos.getX() + u, pos.getY() + plane, pos.getZ() + v);
            case Z -> new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ() + plane);
        };
    }

    /** The seam's middle in the world. */
    public static Vec3 middle(BlockPos pos, Direction face, int n) {
        double[] s = seam(pos.asLong(), face.get3DDataValue(), n);
        return onFace(pos, face, s[0], s[1]);
    }

    /** Whether a bite at {@code hit} (a point on that face) lands within {@code radius} of the seam. */
    public static boolean near(BlockPos pos, Direction face, int n, Vec3 hit, double radius) {
        return middle(pos, face, n).distanceToSqr(hit) <= radius * radius;
    }

    /** How near a bite must land for this tool (the Delver's Pick's Tempered verse widens it, SPEC §34.4). */
    public static double radius(ItemStack tool) {
        return PickVerses.has(tool, PickVerses.Verse.TEMPERED) ? TEMPERED_RADIUS : RADIUS;
    }

    // ---- state ------------------------------------------------------------------------------------------------------

    /** How many true strikes this player has landed on the block at {@code pos} (0 for any other block). */
    public static int trues(Player p, BlockPos pos) {
        Strike s = side(p.level().isClientSide()).get(p.getUUID());
        return s != null && s.pos == pos.asLong() ? s.trues : 0;
    }

    /** The mining speed's multiplier from true strikes on this block. */
    public static float speed(int trues) {
        return Math.min(MAX_SPEED, 1F + STEP * trues);
    }

    private static Strike strike(Player p, BlockPos pos) {
        Strike s = side(p.level().isClientSide()).computeIfAbsent(p.getUUID(), u -> new Strike());
        if (s.pos != pos.asLong()) {
            s.pos = pos.asLong();
            s.trues = 0;
            s.lastTrue = Long.MIN_VALUE / 2;
        }
        return s;
    }

    /** The client's own prediction of a true strike (it then tells the server). */
    public static void predicted(Player p, BlockPos pos) {
        Strike s = strike(p, pos);
        s.trues++;
        s.lastTrue = p.level().getGameTime();
    }

    /** The server's count for a block, after it refused one: the client takes it. */
    public static void synced(Player p, BlockPos pos, int trues) {
        strike(p, pos).trues = trues;
    }

    /**
     * The server's decision on a bite the client says was true: an ore within reach, the {@code n}th true strike on this
     * block (as the server counts them), not sooner than a bite after the last, and the server's own ray from the eye
     * hitting that face within the seam's reach.
     */
    static boolean accept(ServerPlayer p, BlockPos pos, Direction face, int n) {
        ServerLevel level = p.level();
        if (!level.isLoaded(pos) || !applies(level.getBlockState(pos)) || !p.isWithinBlockInteractionRange(pos, 1.0)) return false;
        Strike s = strike(p, pos);
        long now = level.getGameTime();
        if (n != s.trues || now - s.lastTrue < MIN_GAP) return false;
        HitResult hr = p.pick(p.blockInteractionRange() + 1.0, 1F, false);
        if (!(hr instanceof BlockHitResult bh) || hr.getType() != HitResult.Type.BLOCK || !bh.getBlockPos().equals(pos) || bh.getDirection() != face) return false;
        if (!near(pos, face, n, bh.getLocation(), radius(p.getMainHandItem()) + SLACK)) return false;
        s.trues++;
        s.lastTrue = now;
        return true;
    }

    /** A true strike from the client: counted, or the server's count sent back so the two agree again. */
    static void onClaim(ServerPlayer p, DelveNet.TrueStrike m) {
        BlockPos pos = BlockPos.of(m.pos());
        Direction face = Direction.from3DDataValue(m.face());
        if (!accept(p, pos, face, m.n())) DelveNet.send(p, new DelveNet.TrueStrikeSync(m.pos(), trues(p, pos)));
    }

    /** Both sides: true strikes speed the block they landed on (after every other mod's say). */
    static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        if (e.getPosition().isEmpty() || !applies(e.getState())) return;
        int t = trues(e.getEntity(), e.getPosition().get());
        if (t > 0) e.setNewSpeed(e.getNewSpeed() * speed(t));
    }

    /** A breaking blow that landed true: a chance of one more of the block's first drop. */
    static void onDrops(BlockDropsEvent e) {
        if (!(e.getBreaker() instanceof ServerPlayer p) || e.getDrops().isEmpty()) return;
        Strike s = SERVER.get(p.getUUID());
        if (s == null || s.pos != e.getPos().asLong()) return;
        SERVER.remove(p.getUUID());
        if (!applies(e.getState()) || e.getLevel().getGameTime() - s.lastTrue > BREAK_WINDOW) return;
        if (e.getLevel().getRandom().nextFloat() >= BONUS) return;
        ItemEntity first = e.getDrops().getFirst();
        ItemStack more = first.getItem().copyWithCount(1);
        e.getDrops().add(new ItemEntity(e.getLevel(), first.getX(), first.getY(), first.getZ(), more));
    }

    /** Whether the last true strike on this block was within the break window (tests). */
    static boolean landedTrue(ServerPlayer p, BlockPos pos) {
        Strike s = SERVER.get(p.getUUID());
        return s != null && s.pos == pos.asLong() && p.level().getGameTime() - s.lastTrue <= BREAK_WINDOW;
    }

    static void forget(UUID id) {
        SERVER.remove(id);
        CLIENT.remove(id);
    }

    static void clear() {
        SERVER.clear();
        CLIENT.clear();
    }

    /** The client side's state on leaving a world. */
    public static void clientReset() {
        CLIENT.clear();
    }
}
