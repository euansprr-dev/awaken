package dev.awaken.hearth.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

/**
 * The owner's client-side picture of their Hearth: drawn by the Status screen's HEARTH section, the Echo chooser
 * (hold B) and the Codex rumour board. Rebuilt on the server whenever something it shows changes.
 */
public record HearthView(boolean has, BlockPos pos, int tier, int radius, int forgeTier, List<String> rooms, List<String> town,
                         int trophies, int fieldCap, int pads, boolean fieldOut, List<EchoView> echoes,
                         List<HearthPlayer.Rumour> rumours, int records) {

    /** One roster entry as the client sees it. duty = role id of the pad it works, "" if none (resting or field). */
    public record EchoView(UUID id, String name, int level, boolean field, String duty, boolean down, boolean boss) {
        public static final Codec<EchoView> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(EchoView::id),
                Codec.STRING.fieldOf("name").forGetter(EchoView::name),
                Codec.INT.fieldOf("level").forGetter(EchoView::level),
                Codec.BOOL.fieldOf("field").forGetter(EchoView::field),
                Codec.STRING.fieldOf("duty").forGetter(EchoView::duty),
                Codec.BOOL.fieldOf("down").forGetter(EchoView::down),
                Codec.BOOL.fieldOf("boss").forGetter(EchoView::boss)
        ).apply(i, EchoView::new));
    }

    public static final HearthView EMPTY = new HearthView(false, BlockPos.ZERO, 0, 0, 0, List.of(), List.of(), 0, 3, 0,
            false, List.of(), List.of(), 0);

    public static final Codec<HearthView> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("has").forGetter(HearthView::has),
            BlockPos.CODEC.fieldOf("pos").forGetter(HearthView::pos),
            Codec.INT.fieldOf("tier").forGetter(HearthView::tier),
            Codec.INT.fieldOf("radius").forGetter(HearthView::radius),
            Codec.INT.fieldOf("forge").forGetter(HearthView::forgeTier),
            Codec.STRING.listOf().fieldOf("rooms").forGetter(HearthView::rooms),
            Codec.STRING.listOf().fieldOf("town").forGetter(HearthView::town),
            Codec.INT.fieldOf("trophies").forGetter(HearthView::trophies),
            Codec.INT.fieldOf("field_cap").forGetter(HearthView::fieldCap),
            Codec.INT.fieldOf("pads").forGetter(HearthView::pads),
            Codec.BOOL.fieldOf("field_out").forGetter(HearthView::fieldOut),
            EchoView.CODEC.listOf().fieldOf("echoes").forGetter(HearthView::echoes),
            HearthPlayer.Rumour.CODEC.listOf().fieldOf("rumours").forGetter(HearthView::rumours),
            Codec.INT.fieldOf("records").forGetter(HearthView::records)
    ).apply(i, HearthView::new));

    public static final StreamCodec<ByteBuf, HearthView> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
