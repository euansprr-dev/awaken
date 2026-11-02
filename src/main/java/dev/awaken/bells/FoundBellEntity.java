package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A bell where it was won (SPEC §43.16): which bell, what the Book says of where it was, who may lift it (nobody listed:
 * anyone), who has, and the home it belongs to if it's a home's (the Lampwing's old roost). Saved at schema 1; the number
 * is synced so it can be drawn.
 */
public final class FoundBellEntity extends BlockEntity {
    static final int SCHEMA = 1;

    int no = -1;
    String where = "";
    final List<UUID> claimants = new ArrayList<>();
    final Set<UUID> lifted = new HashSet<>();
    /** The Anchor of the home it belongs to, or 0. */
    long anchor;
    /** The gate it lies in, or "": if the gate closes first, the bell goes back to the pool (stage 4). */
    String gate = "";
    /** Already its claimants' (a Great Bell the Registry's wagon brought): lifting it puts it in their hands, nothing more. */
    boolean delivered;

    public FoundBellEntity(BlockPos pos, BlockState state) {
        super(BellContent.FOUND_BELL_BE.get(), pos, state);
    }

    public int no() {
        return no;
    }

    void set(int no, String where, List<UUID> claimants, long anchor) {
        this.no = no;
        this.where = where;
        this.claimants.clear();
        this.claimants.addAll(claimants);
        this.anchor = anchor;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Everyone it was left for has lifted theirs (or, left for anyone, someone has). */
    boolean done() {
        if (claimants.isEmpty()) return !lifted.isEmpty();
        return lifted.containsAll(claimants);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schema", SCHEMA);
        out.putInt("no", no);
        out.putString("where", where);
        out.store("claimants", UUIDUtil.STRING_CODEC.listOf(), new ArrayList<>(claimants));
        out.store("lifted", UUIDUtil.STRING_CODEC.listOf(), new ArrayList<>(lifted));
        out.putLong("anchor", anchor);
        out.putString("gate", gate);
        out.putBoolean("delivered", delivered);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        no = in.getIntOr("no", -1);
        where = in.getStringOr("where", "");
        claimants.clear();
        claimants.addAll(in.read("claimants", UUIDUtil.STRING_CODEC.listOf()).orElse(List.of()));
        lifted.clear();
        lifted.addAll(in.read("lifted", UUIDUtil.STRING_CODEC.listOf()).orElse(List.of()));
        anchor = in.getLongOr("anchor", 0L);
        gate = in.getStringOr("gate", "");
        delivered = in.getBooleanOr("delivered", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("no", no);
        return t;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
