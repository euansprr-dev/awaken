package dev.awaken.lucidium.dream;

import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamSign;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.world.DreamBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One dream while it runs: who is in it, the runtime oddities (the spinning clock, scrambling signs, the creature with
 * no shadow), the nightmare and the mentor, and rooms that change while nobody is looking. Memory only: after a
 * restart the dreamers are simply woken in their beds.
 */
public final class ActiveDream {
    public static final int UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private static final int SWAP_UNSEEN_TICKS = 120;
    private static final int SWAP_CELLS_PER_TICK = 384;

    public final DreamBlueprint bp;
    public final ServerLevel level;
    public final UUID host;
    public final long start;
    public final long end;
    public final Set<UUID> participants = new LinkedHashSet<>();
    final long[] lastSeen;
    private final Fragment[] inWorld;
    private final Deque<Fragment> spares = new ArrayDeque<>();
    private final RandomSource random;

    // nightmare: -1 none, 0 waiting, 1 fighting, 2 beaten
    int nightmareState = -1;
    int nightmareNode = -1;
    Nightmares.Family family;
    final List<UUID> nightmareMobs = new ArrayList<>();
    // mentor: -1 none, 0 waiting, 1 duelling, 2 done
    int mentorState = -1;
    int mentorNode = -1;
    UUID mentor;
    // signs
    UUID shadowless;
    private boolean shadowlessTried;
    private final List<BlockPos> signsPlaced = new ArrayList<>();
    private boolean signsTried;
    private final List<BlockPos> clockHand = new ArrayList<>();
    private int clockStep;
    final List<UUID> summons = new ArrayList<>();
    // swaps
    private int swapNode = -1;
    private Fragment swapTarget;
    private int swapCursor;
    private long lastSwap;

    ActiveDream(DreamBlueprint bp, ServerLevel level, UUID host, long now) {
        this.bp = bp;
        this.level = level;
        this.host = host;
        this.start = now;
        this.end = now + bp.plan.durationTicks();
        this.lastSeen = new long[bp.nodes.size()];
        java.util.Arrays.fill(lastSeen, now);
        this.inWorld = new Fragment[bp.nodes.size()];
        this.random = new XoroshiroRandomSource(bp.plan.seed() ^ 0xD8EA3L);
        this.lastSwap = now;
        for (DreamBlueprint.Node n : bp.nodes) {
            inWorld[n.index()] = n.fragment();
            if (DreamPlan.NIGHTMARE.equals(n.kind())) {
                nightmareNode = n.index();
                nightmareState = 0;
                family = Nightmares.Family.byId(n.ref()).orElse(Nightmares.Family.GENERIC);
            }
            if (DreamPlan.MENTOR.equals(n.kind())) {
                mentorNode = n.index();
                mentorState = 0;
            }
        }
        spares.addAll(bp.spares);
    }

    public boolean hasSign(DreamSign s) {
        return bp.signs.containsKey(s);
    }

    public int signMask() {
        int m = 0;
        for (DreamSign s : bp.signs.keySet()) m |= s.bit();
        return m;
    }

    // ---- world oddities ----------------------------------------------------------------------------------------------

    void tickWorld(long now, List<ServerPlayer> players) {
        if (now % 2 == 0) tickClock(players);
        if (now % 20 == 5) tickSigns(players);
        if (now % 2 == 1) tickShadowless();
        tickSwap(now, players);
    }

    private boolean anyoneNear(List<ServerPlayer> players, BlockPos pos, double dist) {
        for (ServerPlayer p : players) if (p.blockPosition().distSqr(pos) <= dist * dist) return true;
        return false;
    }

    private void tickClock(List<ServerPlayer> players) {
        DreamBlueprint.Sign clock = bp.signs.get(DreamSign.SPINNING_CLOCK);
        if (clock == null || clock.positions().isEmpty()) return;
        BlockPos face = clock.positions().getFirst();
        if (!level.isLoaded(face) || !anyoneNear(players, face, 64)) return;
        BlockState faceBlock = Blocks.QUARTZ_BLOCK.defaultBlockState();
        BlockState hand = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
        for (BlockPos p : clockHand) level.setBlock(p, faceBlock, UPDATE);
        clockHand.clear();
        clockStep = (clockStep + 1) % 12;
        double a = clockStep * Math.PI / 6;
        clockHand.add(face);
        for (int r = 1; r <= 2; r++) {
            int u = (int) Math.round(Math.sin(a) * r), v = (int) Math.round(Math.cos(a) * r);
            clockHand.add(face.offset(u * clock.axisX(), v, u * clock.axisZ()));
        }
        for (BlockPos p : clockHand) level.setBlock(p, hand, UPDATE);
    }

    private static final String[] PHRASES = {"WAKE UP", "YOU ARE HERE", "THIS WAY HOME", "NOT REAL", "LOOK AT YOUR HANDS",
            "THE DOOR IS OPEN", "IT IS LATE", "READ ME AGAIN", "WHO BUILT THIS", "YOU FELL ASLEEP"};

    private void tickSigns(List<ServerPlayer> players) {
        DreamBlueprint.Sign s = bp.signs.get(DreamSign.SCRAMBLED_SIGNS);
        if (s == null) return;
        if (!signsTried) {
            boolean allLoaded = true;
            for (BlockPos p : s.positions()) allLoaded &= level.isLoaded(p);
            if (!allLoaded) return;
            signsTried = true;
            for (BlockPos p : s.positions()) {
                BlockPos at = standingSpot(p);
                if (at == null) continue;
                int rot = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-(bp.center.getX() - at.getX()), bp.center.getZ() - at.getZ())) / 22.5) + 8, 16);
                level.setBlock(at, Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, rot), Block.UPDATE_ALL);
                signsPlaced.add(at);
            }
        }
        for (BlockPos p : signsPlaced) {
            if (!level.isLoaded(p) || !anyoneNear(players, p, 48)) continue;
            if (level.getBlockEntity(p) instanceof SignBlockEntity sign) {
                SignText.Mutable text = SignText.EMPTY.asMutable().setColor(DyeColor.PINK).setTextGlowing(true);
                String phrase = PHRASES[random.nextInt(PHRASES.length)];
                String[] words = scramble(phrase).split(" ");
                for (int line = 0; line < 4; line++) {
                    text.setLine(line, Component.literal(line < words.length ? words[line] : (random.nextInt(3) == 0 ? "..." : "")));
                }
                sign.setText(text.asImmutable(), SignTextSlot.FRONT);
                sign.setText(text.asImmutable(), SignTextSlot.BACK);
                sign.setWaxed(true);
            }
        }
    }

    private String scramble(String phrase) {
        StringBuilder out = new StringBuilder();
        for (String w : phrase.split(" ")) {
            char[] c = w.toCharArray();
            for (int i = 0; i < c.length; i++) {
                if (random.nextInt(3) == 0) {
                    int j = random.nextInt(c.length);
                    char t = c[i];
                    c[i] = c[j];
                    c[j] = t;
                }
                if (random.nextInt(9) == 0) c[i] = "#?*~%&"
                        .charAt(random.nextInt(6));
            }
            if (!out.isEmpty()) out.append(' ');
            out.append(c);
        }
        return out.toString();
    }

    private static final List<EntityType<? extends Mob>> SHADOWLESS = List.of(EntityTypes.SHEEP, EntityTypes.FOX,
            EntityTypes.RABBIT, EntityTypes.CAT, EntityTypes.PIG, EntityTypes.COW);

    private void tickShadowless() {
        DreamBlueprint.Sign s = bp.signs.get(DreamSign.NO_SHADOW);
        if (s == null || s.positions().isEmpty()) return;
        BlockPos p = s.positions().getFirst();
        if (shadowless == null && !shadowlessTried && level.isLoaded(p)) {
            shadowlessTried = true;
            BlockPos at = standingSpot(p);
            if (at == null) at = p;
            Mob mob = SHADOWLESS.get(random.nextInt(SHADOWLESS.size())).create(level, EntitySpawnReason.TRIGGERED);
            if (mob != null) {
                mob.snapTo(at.getX() + 0.5, at.getY() + 0.45, at.getZ() + 0.5, random.nextFloat() * 360, 0);
                mob.setNoAi(true);
                mob.setNoGravity(true);
                mob.setSilent(true);
                mob.setPermanentlyInvulnerable(true);
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
                shadowless = mob.getUUID();
            }
        }
        if (shadowless != null && level.getEntity(shadowless) instanceof Mob mob) {
            float yaw = mob.getYRot() + 3f;
            mob.setYRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
        }
    }

    public int shadowlessId() {
        if (shadowless == null) return -1;
        Entity e = level.getEntity(shadowless);
        return e == null ? -1 : e.getId();
    }

    public Vec3 shadowlessPos() {
        if (shadowless == null) return null;
        Entity e = level.getEntity(shadowless);
        return e == null ? null : e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public List<BlockPos> signsPlaced() {
        return signsPlaced;
    }

    /** A free spot with ground under it near {@code p} (searching a few blocks up and down). */
    BlockPos standingSpot(BlockPos p) {
        for (int r = 0; r <= 3; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = 3; dy >= -4; dy--) {
                        BlockPos at = p.offset(dx, dy, dz);
                        if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                                && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                            return at;
                        }
                    }
        return null;
    }

    // ---- rooms that change when nobody is looking ---------------------------------------------------------------------

    void markSeen(int node, long now) {
        lastSeen[node] = now;
    }

    private void tickSwap(long now, List<ServerPlayer> players) {
        if (swapNode >= 0) {
            writeSwap();
            if (swapCursor >= swapTarget.sx * swapTarget.sy * swapTarget.sz) {
                inWorld[swapNode] = swapTarget;
                swapNode = -1;
                swapTarget = null;
                lastSwap = now;
            }
            return;
        }
        if (now % 40 != 0 || now - lastSwap < 500 || now - start < 600) return;
        int n = bp.nodes.size();
        int offset = (int) Math.floorMod(now / 40, n);
        for (int k = 0; k < n; k++) {
            int i = (k + offset) % n;
            DreamBlueprint.Node node = bp.nodes.get(i);
            if (i == 0 || !(DreamPlan.MEMORY.equals(node.kind()) || DreamPlan.IMAGINED.equals(node.kind()))) continue;
            if (now - lastSeen[i] < SWAP_UNSEEN_TICKS) continue;
            if (anyoneNear(players, node.feet(), 26)) continue;
            if (!level.isLoaded(node.min()) || !level.isLoaded(node.min().offset(15, 0, 15))) continue;
            Fragment current = inWorld[i];
            Fragment next;
            if (!spares.isEmpty() && random.nextInt(3) != 0) {
                next = spares.pollFirst();
                spares.addLast(current);
            } else {
                next = current.rotated(1 + random.nextInt(3));
            }
            swapNode = i;
            swapTarget = next;
            swapCursor = 0;
            return;
        }
    }

    private void writeSwap() {
        DreamBlueprint.Node node = bp.nodes.get(swapNode);
        Fragment f = swapTarget;
        int total = f.sx * f.sy * f.sz;
        int stop = Math.min(total, swapCursor + SWAP_CELLS_PER_TICK);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int c = swapCursor; c < stop; c++) {
            int x = c % f.sx, z = (c / f.sx) % f.sz, y = c / (f.sx * f.sz);
            BlockState s = f.get(x, y, z);
            if (s == null) {
                if (y < 4) continue; // keep the ground
                s = air;
            }
            pos.set(node.min().getX() + x, node.min().getY() + y, node.min().getZ() + z);
            if (level.getBlockState(pos) != s) level.setBlock(pos, s, UPDATE);
        }
        swapCursor = stop;
    }

    // ---- cleanup -----------------------------------------------------------------------------------------------------

    void cleanup() {
        List<UUID> all = new ArrayList<>(nightmareMobs);
        all.addAll(summons);
        if (mentor != null) all.add(mentor);
        if (shadowless != null) all.add(shadowless);
        for (UUID id : all) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
    }

    public boolean isDreamEntity(Entity e) {
        UUID id = e.getUUID();
        return nightmareMobs.contains(id) || summons.contains(id) || id.equals(mentor) || id.equals(shadowless);
    }

    static boolean residue(Entity e) {
        return e.level().dimension() == LucidiumContent.LUCIDIUM;
    }
}
