package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The bells hanging on one hook block (SPEC §43.16): the Bell Frame's three hooks, a Bell Beam's four, a Great Beam's one.
 * Each hook holds a bell's number (or -1) and who hung it. Synced, so the renderer draws each bell where it hangs. When the
 * block goes, its bells drop as items and leave the home's song. Saved at schema 1.
 */
public final class HooksEntity extends BlockEntity {
    static final int SCHEMA = 1;
    /** The block event that swings a hook's bell on every client near (b1: the hook). */
    static final int SWING = 1;
    private static final UUID NOBODY = new UUID(0, 0);

    int[] bells;
    UUID[] by;
    /**
     * Bell spots of an old self-built belfry or tower, or beams the Thatchbacks set in a Belfry or Bell Tower (SPEC §43.16,
     * stage 3): they count wherever they are, however high the fire.
     */
    boolean old;
    /** Client only: the game time each hook's bell last swung (hung, or rung by hand). */
    public final long[] swung;

    public HooksEntity(BlockPos pos, BlockState state) {
        super(BellContent.HOOKS_BE.get(), pos, state);
        int n = Hanging.hooks(state);
        bells = new int[n];
        Arrays.fill(bells, -1);
        by = new UUID[n];
        Arrays.fill(by, NOBODY);
        swung = new long[n];
        Arrays.fill(swung, Long.MIN_VALUE / 2);
    }

    public int hooks() {
        return bells.length;
    }

    /** Bell number on hook {@code i}, or -1. */
    public int bell(int i) {
        return i >= 0 && i < bells.length ? bells[i] : -1;
    }

    @Nullable UUID hungBy(int i) {
        return i >= 0 && i < by.length && !NOBODY.equals(by[i]) ? by[i] : null;
    }

    /** The hook bell {@code no} hangs on here, or -1. */
    int hookOf(int no) {
        for (int i = 0; i < bells.length; i++) if (bells[i] == no) return i;
        return -1;
    }

    void set(int i, int no, @Nullable UUID who) {
        bells[i] = no;
        by[i] = who == null ? NOBODY : who;
        changed();
    }

    boolean any() {
        for (int b : bells) if (b >= 0) return true;
        return false;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean triggerEvent(int b0, int b1) {
        if (b0 != SWING) return super.triggerEvent(b0, b1);
        if (level != null && level.isClientSide() && b1 >= 0 && b1 < swung.length) swung[b1] = level.getGameTime();
        return true;
    }

    /** The block is going: its bells fall as items, and they leave the song. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel sl) {
            for (int i = 0; i < bells.length; i++) {
                if (bells[i] < 0) continue;
                Containers.dropItemStack(sl, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, BellItem.of(bells[i]));
                Hanging.unindex(sl, pos, bells[i]);
                bells[i] = -1;
            }
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schema", SCHEMA);
        out.putIntArray("bells", bells);
        List<UUID> who = new ArrayList<>(Arrays.asList(by));
        out.store("by", UUIDUtil.STRING_CODEC.listOf(), who);
        out.putBoolean("old", old);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int[] saved = in.getIntArray("bells").orElse(new int[0]);
        List<UUID> who = in.read("by", UUIDUtil.STRING_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < bells.length; i++) {
            bells[i] = i < saved.length ? saved[i] : -1;
            by[i] = i < who.size() ? who.get(i) : NOBODY;
        }
        old = in.getBooleanOr("old", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
