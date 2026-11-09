package dev.awaken.shaping;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.AwakenNet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/** The looks a player has learned (SPEC §52.4), kept through death and shown on the ring after their own family. Schema 1. */
public record LookBook(int schema, List<Identifier> learned) {
    public static final int SCHEMA = 1;
    public static final Codec<LookBook> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(LookBook::schema),
            Identifier.CODEC.listOf().fieldOf("learned").forGetter(LookBook::learned)).apply(i, LookBook::new));

    static LookBook fresh() {
        return new LookBook(SCHEMA, List.of());
    }

    static boolean knows(ServerPlayer sp, Block b) {
        return Looks.ordinary(b) || sp.getData(ShapingContent.LOOK_BOOK).learned().contains(BuiltInRegistries.BLOCK.getKey(b));
    }

    /** True if this was new to them. Ordinary looks are already on offer, so there's nothing to learn. */
    static boolean learn(ServerPlayer sp, Block b) {
        if (Looks.ordinary(b)) return false;
        LookBook book = sp.getData(ShapingContent.LOOK_BOOK);
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);
        if (book.learned().contains(id)) return false;
        List<Identifier> more = new ArrayList<>(book.learned());
        more.add(id);
        sp.setData(ShapingContent.LOOK_BOOK, new LookBook(SCHEMA, List.copyOf(more)));
        send(sp);
        return true;
    }

    static void send(ServerPlayer sp) {
        AwakenNet.send(sp, new ShapingPayloads.Known(sp.getData(ShapingContent.LOOK_BOOK).learned()));
    }
}
