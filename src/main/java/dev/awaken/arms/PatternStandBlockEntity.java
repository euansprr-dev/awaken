package dev.awaken.arms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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

/** What a Pattern Stand holds: the style of its pattern and the tier of the weapon hung above it. Synced so the renderer can show it. */
public class PatternStandBlockEntity extends BlockEntity {
    private String style = "";
    private int tier;
    /** The structure's seed while the style is still to be drawn (a stand placed during world generation, where the land's level can't be read). */
    private long seed;

    public PatternStandBlockEntity(BlockPos pos, BlockState state) {
        super(ArmsContent.STAND_BE.get(), pos, state);
    }

    public String style() {
        return style;
    }

    public int tier() {
        return tier;
    }

    boolean pending() {
        return style.isEmpty() && seed != 0;
    }

    long seed() {
        return seed;
    }

    void pend(long seed) {
        this.seed = seed == 0 ? 1 : seed;
        setChanged();
    }

    void set(String style, int tier) {
        this.style = style;
        this.tier = tier;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) ArmsStands.loaded(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) ArmsStands.unloaded(level, worldPosition);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("style", style);
        out.putInt("tier", tier);
        out.putLong("seed", seed);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        style = in.getStringOr("style", "");
        tier = in.getIntOr("tier", 0);
        seed = in.getLongOr("seed", 0L);
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
