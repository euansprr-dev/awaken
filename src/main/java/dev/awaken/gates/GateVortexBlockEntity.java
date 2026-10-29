package dev.awaken.gates;

import dev.awaken.core.PlayDays;
import dev.awaken.gates.content.Twist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The anchor core's block entity (bottom centre of a surface gate's opening). It carries what the vortex renderer and
 * the hum need, synced to clients: rank, axis, look (open, about to break, broken, calm, spent, forming), Red Gate, how
 * near the break is, when it formed and a seed for the shards and motes. On the server it re-reads its gate every two
 * seconds and sends an update only when the look changes. Gate id -1 is a showcase gate (a dev command): fixed look.
 */
public final class GateVortexBlockEntity extends BlockEntity {
    public static final int SCHEMA = 1;

    /** Client side: every vortex loaded, for the hum (the renderer gets its own from the dispatcher). */
    public static final Set<GateVortexBlockEntity> CLIENT_LOADED = ConcurrentHashMap.newKeySet();

    private int gateId = -1;
    private int rank;
    private int axis;
    private int mode = GateVortex.OPEN;
    private boolean red;
    /** 0..100: how near the break is (WARNING). */
    private int warn;
    private long formedAt;
    private long seed;

    public GateVortexBlockEntity(BlockPos pos, BlockState state) {
        super(GatesRegistry.VORTEX_BE.get(), pos, state);
        this.seed = pos.asLong() * 0x9E3779B97F4A7C15L;
    }

    // ---- accessors (client renderer, sound, tests) ------------------------------------------------------------------

    public int gateId() {
        return gateId;
    }

    public int rank() {
        return rank;
    }

    public int axis() {
        return axis;
    }

    public int mode() {
        return mode;
    }

    public boolean red() {
        return red;
    }

    public float warn() {
        return warn / 100F;
    }

    public long formedAt() {
        return formedAt;
    }

    public long seed() {
        return seed;
    }

    /** The vortex centre in world coordinates. */
    public double centreX() {
        return worldPosition.getX() + 0.5;
    }

    public double centreY() {
        return worldPosition.getY() - 1 + GateVortex.size(rank).centreY();
    }

    public double centreZ() {
        return worldPosition.getZ() + 0.5;
    }

    // ---- server ---------------------------------------------------------------------------------------------------------

    /** Called right after the structure is placed: binds this vortex to its gate. */
    public void configure(GateRecord g, long now) {
        boolean fresh = gateId != g.id;
        gateId = g.id;
        rank = GateVortex.clampRank(g.plan.rank().ordinal());
        axis = g.axis;
        seed = g.plan.seed() ^ (g.pos.asLong() * 31L);
        if (fresh) formedAt = now;
        if (level instanceof ServerLevel sl) {
            look(sl.getServer(), g);
            sync();
        }
    }

    /** A showcase gate: fixed rank, axis and look, no gate behind it. */
    public void showcase(int rank, int axis, int mode, boolean red, float warn, long now) {
        this.gateId = -1;
        this.rank = GateVortex.clampRank(rank);
        this.axis = axis;
        this.mode = mode;
        this.red = red;
        this.warn = Math.round(Math.max(0F, Math.min(1F, warn)) * 100F);
        this.formedAt = now;
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GateVortexBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || be.gateId < 0) return;
        if (Math.floorMod(sl.getGameTime() + pos.asLong(), 40L) != 0) return;
        GateRecord g = GateRegistry.get(sl.getServer()).gate(be.gateId);
        if (g == null) return;
        if (be.look(sl.getServer(), g)) be.sync();
    }

    /** Reads the gate's look; true when it changed. */
    boolean look(MinecraftServer server, GateRecord g) {
        int m;
        int w = 0;
        switch (g.state) {
            case BUILDING -> m = GateVortex.FORMING;
            case BROKEN -> m = GateVortex.BROKEN;
            case CLEARED, RELEASED -> m = GateVortex.SPENT;
            default -> {
                if (dev.awaken.core.api.StoryHooks.gatesCalm(server, g.owner)) m = GateVortex.CALM;
                else if (g.counting(server) && g.breaksIn(PlayDays.current(server)) <= 1) {
                    m = GateVortex.WARNING;
                    w = g.breaksIn(PlayDays.current(server)) == 0 ? 100 : 55;
                } else m = GateVortex.OPEN;
            }
        }
        boolean r = g.plan.is(Twist.RED_GATE);
        if (m == mode && w == warn && r == red) return false;
        mode = m;
        warn = w;
        red = r;
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- save and sync --------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schema", SCHEMA);
        out.putInt("gate", gateId);
        out.putInt("rank", rank);
        out.putInt("axis", axis);
        out.putInt("mode", mode);
        out.putBoolean("red", red);
        out.putInt("warn", warn);
        out.putLong("formed", formedAt);
        out.putLong("seed", seed);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        gateId = in.getIntOr("gate", -1);
        rank = GateVortex.clampRank(in.getIntOr("rank", 0));
        axis = in.getIntOr("axis", 0) & 1;
        mode = Math.max(0, Math.min(GateVortex.MODE_NAMES.length - 1, in.getIntOr("mode", GateVortex.OPEN)));
        red = in.getBooleanOr("red", false);
        warn = Math.max(0, Math.min(100, in.getIntOr("warn", 0)));
        formedAt = in.getLongOr("formed", 0L);
        seed = in.getLongOr("seed", seed);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CLIENT_LOADED.remove(this);
    }
}
