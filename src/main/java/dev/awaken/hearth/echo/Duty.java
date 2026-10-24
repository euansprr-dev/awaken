package dev.awaken.hearth.echo;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.HearthViews;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.room.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Echoes at home. Nothing is assigned by hand: every role pad takes the next Echo that isn't marching with you
 * (Guard pads first, strongest first), and the Echo walks onto it and starts working. Pads give the roster room, so
 * Echoes on duty never count against the field army.
 */
public final class Duty {
    private Duty() {}

    /** Ticks of Mine duty per ore delivery (abstracted: the Echo goes "below" and comes back with something). */
    public static final int MINE_TICKS = 20 * 120;
    public static final int TRAIN_TICKS = 20 * 60;

    public record Assignment(BlockPos pad, EchoRole role) {}

    private static final Map<UUID, BlockPos> DEFEND = new HashMap<>();
    private static final Map<UUID, String> INSTALLED = new HashMap<>();
    private static final Map<UUID, Integer> TRAINING = new HashMap<>();
    private record Containers(long at, List<BlockPos> list) {}
    private static final Map<UUID, Containers> CONTAINERS = new HashMap<>();

    /** Server stop: forget live Echo bookkeeping (entity ids belong to the world that stopped). */
    public static void clear() {
        DEFEND.clear();
        INSTALLED.clear();
        TRAINING.clear();
        CONTAINERS.clear();
    }

    // ---- assignment (pure) ------------------------------------------------------------------------------------------

    /**
     * Which Echo works which pad: Echoes not marching with you, strongest first, fill Guard pads first and then the
     * others in the order the pads were placed. Echoes left over rest at home.
     */
    public static Map<UUID, Assignment> assignments(HearthPlayer hp, HearthRecord h) {
        return assign(hp.echoes, h.pads);
    }

    public static Map<UUID, Assignment> assign(List<EchoRecord> roster, LinkedHashMap<BlockPos, EchoRole> pads) {
        List<EchoRecord> free = new ArrayList<>();
        for (EchoRecord e : roster) if (!e.field()) free.add(e);
        free.sort(Comparator.comparingInt(EchoRecord::level).reversed().thenComparingLong(EchoRecord::boundDay));
        Map<UUID, Assignment> out = new LinkedHashMap<>();
        int i = 0;
        for (var p : pads.entrySet()) {
            if (p.getValue() != EchoRole.GUARD || i >= free.size()) continue;
            out.put(free.get(i++).id(), new Assignment(p.getKey(), p.getValue()));
        }
        for (var p : pads.entrySet()) {
            if (p.getValue() == EchoRole.GUARD || i >= free.size()) continue;
            out.put(free.get(i++).id(), new Assignment(p.getKey(), p.getValue()));
        }
        return out;
    }

    /** Whether this live Echo carries cargo in its hand (haulers and farmers): drop it if the Echo falls. */
    public static boolean isCarrier(UUID entity) {
        String k = INSTALLED.get(entity);
        return k != null && (k.startsWith(EchoRole.HAUL.id + "@") || k.startsWith(EchoRole.FARM.id + "@"));
    }

    public static int guardCount(HearthRecord h) {
        int n = 0;
        for (EchoRole r : h.pads.values()) if (r == EchoRole.GUARD) n++;
        return n;
    }

    public static @Nullable String workerName(ServerLevel level, HearthRecord h, BlockPos pad) {
        Optional<ServerPlayer> owner = HearthService.owner(level.getServer(), h);
        if (owner.isEmpty()) return null;
        HearthPlayer hp = HearthService.player(owner.get());
        for (var e : assignments(hp, h).entrySet()) {
            if (e.getValue().pad().equals(pad)) return hp.echo(e.getKey()).map(EchoRecord::name).orElse(null);
        }
        return null;
    }

    // ---- upkeep -----------------------------------------------------------------------------------------------------

    /** Once a second while the owner is online and home is loaded: put Echoes on their pads and run timed jobs. */
    public static void upkeep(ServerPlayer owner, ServerLevel level, HearthRecord h, long now) {
        HearthPlayer hp = HearthService.player(owner);
        Map<UUID, Assignment> as = assignments(hp, h);
        boolean changed = false;
        // Echoes that lost their pad (or went to the field) stop working.
        for (EchoRecord e : hp.echoes) {
            if (as.containsKey(e.id())) continue;
            Mob m = Echoes.live(level.getServer(), e.id());
            if (m != null && Echoes.link(m).duty()) {
                Echoes.unsummon(level.getServer(), e.id());
                INSTALLED.remove(m.getUUID());
            }
        }
        for (var entry : as.entrySet()) {
            UUID id = entry.getKey();
            Assignment a = entry.getValue();
            Optional<EchoRecord> rec = hp.echo(id);
            if (rec.isEmpty() || rec.get().downUntil() > now) continue;
            Mob m = Echoes.live(level.getServer(), id);
            if (m == null) {
                if (!level.isPositionEntityTicking(a.pad())) continue;
                m = Echoes.spawn(level, rec.get(), owner.getUUID(), Vec3.atBottomCenterOf(a.pad()).add(0, 0.1, 0), true);
                if (m == null) continue;
                level.sendParticles(HearthContent.ECHO_WISP.get(), m.getX(), m.getY() + 0.6, m.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
                h.padEchoes.put(a.pad(), m.getUUID());
                changed = true;
            }
            String key = a.role().id + "@" + a.pad().asLong();
            if (!key.equals(INSTALLED.get(m.getUUID()))) {
                DutyGoals.install(m, a.role(), a.pad(), h);
                INSTALLED.put(m.getUUID(), key);
            }
            boolean barracks = h.roomAt(a.pad(), RoomType.BARRACKS).isPresent();
            if (m.getHealth() < m.getMaxHealth()) m.heal(barracks ? 1.0f : 0.25f);
            switch (a.role()) {
                case MINE -> mine(owner, level, h, a.pad(), m, rec.get());
                case TRAIN -> {
                    int t = TRAINING.merge(id, 20 * (barracks ? 2 : 1), Integer::sum);
                    if (t >= TRAIN_TICKS) {
                        TRAINING.put(id, 0);
                        Echoes.grantXp(owner, id, 1);
                    }
                }
                default -> {}
            }
        }
        if (changed) {
            HearthService.dirty(level.getServer());
            HearthViews.refresh(owner);
        }
    }

    private static void mine(ServerPlayer owner, ServerLevel level, HearthRecord h, BlockPos pad, Mob m, EchoRecord rec) {
        int prog = h.mineProgress.merge(pad, 20, Integer::sum);
        if (m.tickCount % 3 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), m.getX(), m.getY() + 0.1, m.getZ(),
                    4, 0.3, 0.05, 0.3, 0.05);
        }
        if (prog < MINE_TICKS) return;
        h.mineProgress.put(pad, 0);
        ItemStack ore = rollOre(level.getRandom().nextInt(1000), rec.level(), h.tier);
        h.oresBrought += ore.getCount();
        level.playSound(null, pad, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f);
        level.sendParticles(HearthContent.ECHO_WISP.get(), m.getX(), m.getY() + 0.5, m.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
        store(level, h, ore, pad);
        HearthService.dirty(level.getServer());
    }

    /**
     * What a Mine Echo brings up from below (pure). {@code roll} in [0,1000). Stronger Echoes and higher Anchor tiers
     * find better ore more often.
     */
    public static ItemStack rollOre(int roll, int echoLevel, int tier) {
        int bonus = Math.min(150, echoLevel * 2 + tier * 10);
        int r = Math.max(0, roll - bonus);
        if (roll >= 1000 - Math.min(60, 5 + echoLevel + tier * 3)) return new ItemStack(Items.DIAMOND);
        if (r < 280) return new ItemStack(Items.COAL, 3 + tier);
        if (r < 520) return new ItemStack(Items.RAW_IRON, 2 + tier / 2);
        if (r < 720) return new ItemStack(Items.RAW_COPPER, 3 + tier);
        if (r < 820) return new ItemStack(Items.REDSTONE, 4 + tier);
        if (r < 900) return new ItemStack(Items.LAPIS_LAZULI, 3 + tier);
        return new ItemStack(Items.RAW_GOLD, 1 + tier / 2);
    }

    /** Pads changed, an Echo was bound/released/moved: rework the assignment now. */
    public static void reassign(ServerPlayer owner, ServerLevel level, HearthRecord h) {
        if (level.isPositionEntityTicking(h.pos)) upkeep(owner, level, h, level.getGameTime());
    }

    public static void releasePad(ServerLevel level, HearthRecord h, BlockPos pad) {
        UUID u = h.padEchoes.remove(pad);
        if (u != null && level.getEntity(u) instanceof Mob m) {
            Echoes.forget(Echoes.link(m).echoId());
            INSTALLED.remove(u);
            m.discard();
        }
    }

    public static void releaseAll(ServerLevel level, HearthRecord h) {
        for (BlockPos p : List.copyOf(h.padEchoes.keySet())) releasePad(level, h, p);
        DEFEND.remove(h.owner);
        CONTAINERS.remove(h.owner);
    }

    /** A break nearby: Guards go and hold it ({@code null} = all clear, go home). */
    public static void defend(ServerLevel level, HearthRecord h, @Nullable BlockPos at) {
        if (at == null) DEFEND.remove(h.owner);
        else DEFEND.put(h.owner, at);
    }

    public static @Nullable BlockPos defendPos(UUID owner) {
        return DEFEND.get(owner);
    }

    // ---- storage ----------------------------------------------------------------------------------------------------

    /** Chests and barrels inside the Hearth (cached for 30 s). */
    static List<BlockPos> containers(ServerLevel level, HearthRecord h) {
        long now = level.getGameTime();
        Containers c = CONTAINERS.get(h.owner);
        if (c != null && now - c.at < 600) return c.list;
        List<BlockPos> out = new ArrayList<>();
        int r = h.radius();
        for (int cx = SectionPos.blockToSectionCoord(h.pos.getX() - r); cx <= SectionPos.blockToSectionCoord(h.pos.getX() + r); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(h.pos.getZ() - r); cz <= SectionPos.blockToSectionCoord(h.pos.getZ() + r); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && h.contains(be.getBlockPos())) out.add(be.getBlockPos());
                }
            }
        }
        CONTAINERS.put(h.owner, new Containers(now, out));
        return out;
    }

    /** Where a stack should go: a chest that already holds it, otherwise the nearest chest to {@code near}. */
    static @Nullable BlockPos chooseContainer(ServerLevel level, HearthRecord h, ItemStack stack, BlockPos near) {
        BlockPos matching = null, nearest = null;
        double dm = Double.MAX_VALUE, dn = Double.MAX_VALUE;
        for (BlockPos p : containers(level, h)) {
            if (!(level.getBlockEntity(p) instanceof Container c)) continue;
            double d = p.distSqr(near);
            boolean room = hasRoom(c, stack);
            if (!room) continue;
            if (holds(c, stack.getItem()) && d < dm) {
                dm = d;
                matching = p;
            }
            if (d < dn && d <= 16 * 16) {
                dn = d;
                nearest = p;
            }
        }
        return matching != null ? matching : nearest;
    }

    static boolean holds(Container c, Item item) {
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return true;
        return false;
    }

    static boolean hasRoom(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    /** Puts a stack away (sorting by what's already in the chests) and drops what doesn't fit at {@code near}. */
    public static void store(ServerLevel level, HearthRecord h, ItemStack stack, BlockPos near) {
        ItemStack left = stack;
        for (int tries = 0; tries < 3 && !left.isEmpty(); tries++) {
            BlockPos p = chooseContainer(level, h, left, near);
            if (p == null || !(level.getBlockEntity(p) instanceof Container c)) break;
            left = HopperBlockEntity.addItem(null, c, left, null);
            c.setChanged();
        }
        if (!left.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, near.getX() + 0.5, near.getY() + 0.5, near.getZ() + 0.5, left);
            drop.setPickUpDelay(40);
            level.addFreshEntity(drop);
        }
    }
}
