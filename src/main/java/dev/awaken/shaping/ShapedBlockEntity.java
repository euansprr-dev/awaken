package dev.awaken.shaping;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import org.jspecify.annotations.Nullable;

/** What a cut block really is and what it wears (SPEC §52.2). Saved at schema 1; both are synced so it can be drawn and mined. */
public final class ShapedBlockEntity extends BlockEntity {
    static final int SCHEMA = 1;
    public static final ModelProperty<BlockState> LOOK = new ModelProperty<>();

    private BlockState base = Blocks.STONE.defaultBlockState();
    private volatile @Nullable BlockState look;

    public ShapedBlockEntity(BlockPos pos, BlockState state) {
        super(ShapingContent.SHAPED_BE.get(), pos, state);
    }

    public BlockState base() {
        return this.base;
    }

    public @Nullable BlockState look() {
        return this.look;
    }

    void set(BlockState base, BlockState look) {
        this.base = base;
        this.look = look;
        setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schema", SCHEMA);
        out.store("base", BlockState.CODEC, this.base);
        BlockState l = this.look;
        if (l != null) out.store("look", BlockState.CODEC, l);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.base = in.read("base", BlockState.CODEC).orElse(Blocks.STONE.defaultBlockState());
        this.look = in.read("look", BlockState.CODEC).orElse(this.base);
    }

    /** A change sent to the client redraws at once. Not on chunk load (handleUpdateTag): that chunk hasn't been drawn yet. */
    @Override
    public void onDataPacket(Connection net, ValueInput in) {
        super.onDataPacket(net, in);
        requestModelDataUpdate();
        BlockState s = getBlockState();
        if (this.level != null) this.level.sendBlockUpdated(this.worldPosition, s, s, Block.UPDATE_IMMEDIATE);
    }

    @Override
    public ModelData getModelData() {
        BlockState l = this.look;
        return l == null ? ModelData.EMPTY : ModelData.of(LOOK, l);
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
