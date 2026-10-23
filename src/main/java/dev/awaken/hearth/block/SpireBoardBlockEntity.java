package dev.awaken.hearth.block;

import dev.awaken.hearth.HearthContent;
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

import java.util.List;
import java.util.UUID;

/** What the Spire Board shows: the owner's name and rank and a few lines of records. Server writes, clients draw. */
public class SpireBoardBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private String name = "";
    private int rank;
    private int level;
    private List<String> lines = List.of();

    public SpireBoardBlockEntity(BlockPos pos, BlockState state) {
        super(HearthContent.SPIRE_BOARD_BE.get(), pos, state);
    }

    public @Nullable UUID owner() { return owner; }
    public String name() { return name; }
    public int rank() { return rank; }
    public int hunterLevel() { return level; }
    public List<String> lines() { return lines; }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Updates what the board shows; only sends to clients when something changed. */
    public void show(String name, int rank, int level, List<String> lines) {
        if (name.equals(this.name) && rank == this.rank && level == this.level && lines.equals(this.lines)) return;
        this.name = name;
        this.rank = rank;
        this.level = level;
        this.lines = List.copyOf(lines);
        setChanged();
        if (this.getLevel() != null) this.getLevel().sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (owner != null) out.store("owner", UUIDUtil.CODEC, owner);
        out.putString("name", name);
        out.putInt("rank", rank);
        out.putInt("level", level);
        out.store("lines", com.mojang.serialization.Codec.STRING.listOf(), lines);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        name = in.getStringOr("name", "");
        rank = in.getIntOr("rank", 0);
        level = in.getIntOr("level", 0);
        lines = in.read("lines", com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of());
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
