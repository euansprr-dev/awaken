package dev.awaken.above.machine;

import dev.awaken.above.AboveBlocks;
import dev.awaken.above.AboveSounds;
import dev.awaken.above.MachineType;
import dev.awaken.above.power.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * State for every Precursor machine (and the Fabricator). Machines do almost nothing per tick: every 2 seconds they
 * check whether a reactor still powers them (cached, see {@link Power}) and light up or go dark. The Essence Reactor
 * keeps its essence here; the Clone Vat keeps whether its next clone has grown.
 */
public class MachineBlockEntity extends BlockEntity {
    public static final int REACTOR_CAPACITY = 20_000;
    /** Essence the Clone Vat draws to grow a clone. */
    public static final int CLONE_COST = 300;

    private @Nullable UUID owner;
    private String label = "";
    private int essence;
    private boolean cloneReady;
    public final Power.Cache power = new Power.Cache();

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(AboveBlocks.MACHINE_BE.get(), pos, state);
    }

    public @Nullable MachineType type() {
        return getBlockState().getBlock() instanceof MachineBlock mb ? mb.type() : null;
    }

    public boolean isReactor() {
        return type() == MachineType.REACTOR;
    }

    public boolean isFabricator() {
        return getBlockState().getBlock() instanceof MachineBlock mb && mb.type() == null;
    }

    // ---- owner / label --------------------------------------------------------------------------------------------
    public @Nullable UUID owner() { return owner; }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public String label() { return label; }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        setChanged();
    }

    // ---- reactor --------------------------------------------------------------------------------------------------
    public int essence() { return essence; }

    /** Adds essence; returns how much fitted. */
    public int addEssence(int amount) {
        int add = Math.max(0, Math.min(amount, REACTOR_CAPACITY - essence));
        if (add > 0) {
            essence += add;
            setChanged();
        }
        return add;
    }

    /** Removes up to {@code amount}; returns how much was taken. */
    public int takeEssence(int amount) {
        int take = Math.max(0, Math.min(amount, essence));
        if (take > 0) {
            essence -= take;
            setChanged();
        }
        return take;
    }

    // ---- clone vat ------------------------------------------------------------------------------------------------
    public boolean cloneReady() { return cloneReady; }

    public void setCloneReady(boolean ready) {
        this.cloneReady = ready;
        setChanged();
    }

    // ---- power ----------------------------------------------------------------------------------------------------
    public int poweredEssence() {
        return level instanceof ServerLevel sl ? Power.stored(sl, worldPosition, power) : 0;
    }

    public int reactorsInRange() {
        return level instanceof ServerLevel sl ? Power.reactorCount(sl, worldPosition, power) : 0;
    }

    public boolean draw(int amount) {
        return level instanceof ServerLevel sl && Power.draw(sl, worldPosition, dev.awaken.core.api.HearthHooks.machineCost(sl, worldPosition, amount), power);
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------------
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl && isReactor()) Power.add(sl, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl && isReactor()) Power.remove(sl, worldPosition);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel sl && isReactor()) Power.remove(sl, worldPosition);
        super.onChunkUnloaded();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel sl && type() == MachineType.WAYPOINT_PYLON) PylonNetwork.get(sl.getServer()).remove(sl, pos);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 40L) != 0 || !(level instanceof ServerLevel sl)) return;
        MachineType t = be.type();
        if (t == null) return;
        boolean lit;
        if (t == MachineType.REACTOR) {
            lit = be.essence > 0;
            if (lit && sl.getRandom().nextInt(4) == 0) {
                sl.playSound(null, pos, AboveSounds.REACTOR.get(), SoundSource.BLOCKS, 0.35f, 0.9f + sl.getRandom().nextFloat() * 0.2f);
            }
        } else {
            int stored = Power.stored(sl, pos, be.power);
            lit = stored > 0;
            if (t == MachineType.CLONE_VAT && !be.cloneReady && stored >= CLONE_COST && be.draw(CLONE_COST)) {
                be.setCloneReady(true);
                sl.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.02);
            }
        }
        if (state.getValue(MachineBlock.LIT) != lit) sl.setBlock(pos, state.setValue(MachineBlock.LIT, lit), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
        out.putString("label", label);
        out.putInt("essence", essence);
        out.putBoolean("clone_ready", cloneReady);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        label = in.getStringOr("label", "");
        essence = in.getIntOr("essence", 0);
        cloneReady = in.getBooleanOr("clone_ready", false);
    }
}
