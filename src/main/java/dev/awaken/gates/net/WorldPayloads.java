package dev.awaken.gates.net;

import dev.awaken.Awaken;
import dev.awaken.gates.world.FloorAir;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Gate Worlds payloads (SPEC §38). Server → client through {@link dev.awaken.core.AwakenNet#send}. */
public final class WorldPayloads {
    private WorldPayloads() {}

    /**
     * You arrived on a floor: which world and mood it is (the title card and the Atlas), whether it's a first-ever find,
     * and its Gate Air data with the world position of its floor-local origin. {@code world} "" = a rooms floor (clears).
     * {@code where} is the card's floor line ("Floor 3", "Second gate · Floor 2", "Floor 5 · the Lord waits"); {@code sound}
     * the world's sound bed ("" for none). {@code card}: show the title card (false when it's only telling the client again,
     * say after the Flood or on leaving a place that's a world of its own).
     */
    public record Arrival(int gateId, int area, int floor, String world, String name, String mood, String moodName, String rarity,
                          String tier, String line, String where, String sound, boolean first, boolean card, BlockPos origin, FloorAir air)
            implements CustomPacketPayload {
        public static final Type<Arrival> TYPE = new Type<>(Awaken.id("gate_world_arrival"));
        public static final StreamCodec<ByteBuf, Arrival> STREAM_CODEC = StreamCodec.of((buf, a) -> {
            ByteBufCodecs.VAR_INT.encode(buf, a.gateId);
            ByteBufCodecs.VAR_INT.encode(buf, a.area);
            ByteBufCodecs.VAR_INT.encode(buf, a.floor);
            for (String s : new String[]{a.world, a.name, a.mood, a.moodName, a.rarity, a.tier, a.line, a.where, a.sound}) ByteBufCodecs.STRING_UTF8.encode(buf, s);
            ByteBufCodecs.BOOL.encode(buf, a.first);
            ByteBufCodecs.BOOL.encode(buf, a.card);
            BlockPos.STREAM_CODEC.encode(buf, a.origin);
            FloorAir.STREAM_CODEC.encode(buf, a.air);
        }, buf -> new Arrival(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf), BlockPos.STREAM_CODEC.decode(buf), FloorAir.STREAM_CODEC.decode(buf)));

        public static Arrival none(int gateId, int area, int floor) {
            return new Arrival(gateId, area, floor, "", "", "", "", "", "", "", "", "", false, false, BlockPos.ZERO, FloorAir.NONE);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A world's sound bed drains away for {@code ticks} and comes back (the Field of Bells' Silence, SPEC §38.8). */
    public record Hush(int ticks) implements CustomPacketPayload {
        public static final Type<Hush> TYPE = new Type<>(Awaken.id("gate_world_hush"));
        public static final StreamCodec<ByteBuf, Hush> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Hush::ticks, Hush::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * You broke the Seal and are dropping to a world floor (sent just before the teleport): the screen goes dark until the
     * floor below has loaded, with {@code sound} (the world's sound bed, "" for none) fading in and {@code glow} (the
     * world's light colour, RGB) rising from below.
     */
    public record Journey(String world, String sound, int glow) implements CustomPacketPayload {
        public static final Type<Journey> TYPE = new Type<>(Awaken.id("gate_world_journey"));
        public static final StreamCodec<ByteBuf, Journey> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Journey::world,
                ByteBufCodecs.STRING_UTF8, Journey::sound,
                ByteBufCodecs.INT, Journey::glow,
                Journey::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One world's card in the Atlas. Unfound: only its id, theme, tier and rarity (the dark outline); its name, line and
     * what you've found in it stay empty. {@code met} is the world's signature creature's name once you've met it.
     */
    public record Card(String world, String theme, String tier, String rarity, boolean found, String name, String line,
                       List<String> moods, int moodsAll, List<String> secrets, int secretsAll, String met, int visits,
                       boolean keepsake) {
        public static final StreamCodec<ByteBuf, Card> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            for (String s : new String[]{c.world, c.theme, c.tier, c.rarity}) ByteBufCodecs.STRING_UTF8.encode(buf, s);
            ByteBufCodecs.BOOL.encode(buf, c.found);
            for (String s : new String[]{c.name, c.line}) ByteBufCodecs.STRING_UTF8.encode(buf, s);
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, c.moods);
            ByteBufCodecs.VAR_INT.encode(buf, c.moodsAll);
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, c.secrets);
            ByteBufCodecs.VAR_INT.encode(buf, c.secretsAll);
            ByteBufCodecs.STRING_UTF8.encode(buf, c.met);
            ByteBufCodecs.VAR_INT.encode(buf, c.visits);
            ByteBufCodecs.BOOL.encode(buf, c.keepsake);
        }, buf -> new Card(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf)));
    }

    /** Your Atlas of Gates for the Codex: every world's card in catalogue order, and how many of them you've found. */
    public record AtlasView(int found, int total, List<Card> cards) implements CustomPacketPayload {
        public static final Type<AtlasView> TYPE = new Type<>(Awaken.id("gate_atlas_view"));
        public static final StreamCodec<ByteBuf, AtlasView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AtlasView::found,
                ByteBufCodecs.VAR_INT, AtlasView::total,
                Card.STREAM_CODEC.apply(ByteBufCodecs.list()), AtlasView::cards,
                AtlasView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
