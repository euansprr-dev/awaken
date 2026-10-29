package dev.awaken.world.stilled;

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

/**
 * One of the Stilled (SPEC §43.3): who they were (their spot's id, a name if anyone knows it), how they sat down (a
 * pose, a look, which way they face) and what they carry. Synced to clients for the renderer; what each player has
 * taken from them is kept in {@link Stilled}'s saved data, not here.
 */
public class StilledBlockEntity extends BlockEntity {
    /** The spot's id ("aldo", "north1", "g3_-2"). */
    String figure = "";
    /** Their name, when somebody knows it (Aldo), else "". */
    String name = "";
    int pose, look, look2;
    float yaw;
    /** What they carry: "letter", "key", "keepsake" or "". */
    String carry = "";
    /** Which letter or keepsake. */
    int n;
    /** A letter's or key's settlement (key and name), a keepsake's family and who the owner was to them. */
    String settlement = "", place = "", family = "", relation = "";
    int targetX, targetZ;

    public StilledBlockEntity(BlockPos pos, BlockState state) {
        super(StilledContent.STILLED_BE.get(), pos, state);
    }

    public StilledText.Pose pose() {
        return StilledText.Pose.values()[Math.floorMod(pose, StilledText.Pose.values().length)];
    }

    public StilledText.Look look() {
        return StilledText.Look.values()[Math.floorMod(look, StilledText.Look.values().length)];
    }

    public StilledText.Look look2() {
        return StilledText.Look.values()[Math.floorMod(look2, StilledText.Look.values().length)];
    }

    public float yaw() {
        return yaw;
    }

    public String figure() {
        return figure;
    }

    void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("figure", figure);
        out.putString("name", name);
        out.putInt("pose", pose);
        out.putInt("look", look);
        out.putInt("look2", look2);
        out.putFloat("yaw", yaw);
        out.putString("carry", carry);
        out.putInt("n", n);
        out.putString("settlement", settlement);
        out.putString("place", place);
        out.putString("family", family);
        out.putString("relation", relation);
        out.putInt("tx", targetX);
        out.putInt("tz", targetZ);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        figure = in.getStringOr("figure", "");
        name = in.getStringOr("name", "");
        pose = in.getIntOr("pose", 0);
        look = in.getIntOr("look", 0);
        look2 = in.getIntOr("look2", 1);
        yaw = in.getFloatOr("yaw", 0F);
        carry = in.getStringOr("carry", "");
        n = in.getIntOr("n", 0);
        settlement = in.getStringOr("settlement", "");
        place = in.getStringOr("place", "");
        family = in.getStringOr("family", "");
        relation = in.getStringOr("relation", "");
        targetX = in.getIntOr("tx", 0);
        targetZ = in.getIntOr("tz", 0);
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
