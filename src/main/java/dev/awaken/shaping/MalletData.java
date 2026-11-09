package dev.awaken.shaping;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** What a Mallet is set to: what right click does, and the look it carries. */
public record MalletData(Mode mode, Optional<Identifier> look) {
    public enum Mode implements StringRepresentable {
        KNOCK("knock"), LOOK("look");

        static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        static final Mode[] VALUES = values();
        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        public String key() {
            return "awaken.shaping.mode." + this.id;
        }
    }

    public static final MalletData DEFAULT = new MalletData(Mode.KNOCK, Optional.empty());
    public static final Codec<MalletData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Mode.CODEC.fieldOf("mode").forGetter(MalletData::mode),
            Identifier.CODEC.optionalFieldOf("look").forGetter(MalletData::look)).apply(i, MalletData::new));
    public static final StreamCodec<ByteBuf, MalletData> STREAM = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(n -> Mode.VALUES[Math.floorMod(n, Mode.VALUES.length)], Mode::ordinal), MalletData::mode,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), MalletData::look, MalletData::new);

    public static MalletData of(ItemStack stack) {
        return stack.getOrDefault(ShapingContent.MALLET_DATA.get(), DEFAULT);
    }

    MalletData turned(int by) {
        return new MalletData(Mode.VALUES[Math.floorMod(this.mode.ordinal() + by, Mode.VALUES.length)], this.look);
    }

    MalletData carrying(Identifier id) {
        return new MalletData(this.mode, Optional.of(id));
    }

    MalletData in(Mode m) {
        return new MalletData(m, this.look);
    }
}
