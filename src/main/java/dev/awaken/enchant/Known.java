package dev.awaken.enchant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one player knows of enchanting (SPEC §45.2, attachment {@code awaken:enchant_known}, schemaVersion 1): every
 * enchantment learned (where and on which day), the marks in memory (traced cleanly once: "From memory" writes them in
 * a stroke), the gate kinds whose Lord already gave their page, whether the System's one line was said, how many
 * enchantments were written, whether a table was ever set down and whether one was used in your Study. Kept through
 * death; synced to its owner only.
 */
public final class Known {
    public static final int SCHEMA = 1;

    /** Where and when an enchantment was learned ("From the Ember Tyrant, the Lord of the Ember Mines"). */
    public record Learned(String where, long day) {
        static final Codec<Learned> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("where", "").forGetter(Learned::where),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Learned::day)
        ).apply(i, Learned::new));
    }

    final Map<String, Learned> learned;
    final List<String> memory;
    final List<String> gates;
    boolean told;
    int written;
    boolean table;
    boolean study;

    Known(Map<String, Learned> learned, List<String> memory, List<String> gates, boolean told, int written, boolean table, boolean study) {
        this.learned = new LinkedHashMap<>(learned);
        this.memory = new ArrayList<>(memory);
        this.gates = new ArrayList<>(gates);
        this.told = told;
        this.written = written;
        this.table = table;
        this.study = study;
    }

    static Known fresh() {
        return new Known(Map.of(), List.of(), List.of(), false, 0, false, false);
    }

    public static final MapCodec<Known> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(k -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Learned.CODEC).optionalFieldOf("learned", Map.of()).forGetter(k -> k.learned),
            Codec.STRING.listOf().optionalFieldOf("memory", List.of()).forGetter(k -> k.memory),
            Codec.STRING.listOf().optionalFieldOf("gates", List.of()).forGetter(k -> k.gates),
            Codec.BOOL.optionalFieldOf("told", false).forGetter(k -> k.told),
            Codec.INT.optionalFieldOf("written", 0).forGetter(k -> k.written),
            Codec.BOOL.optionalFieldOf("table", false).forGetter(k -> k.table),
            Codec.BOOL.optionalFieldOf("study", false).forGetter(k -> k.study)
    ).apply(i, (v, l, m, g, t, w, tb, st) -> new Known(l, m, g, t, w, tb, st)));

    public static final StreamCodec<RegistryFriendlyByteBuf, Known> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public static Known of(net.minecraft.world.entity.player.Player p) {
        return p.getData(EnchantContent.KNOWN);
    }

    public boolean knows(Identifier id) {
        return learned.containsKey(id.toString());
    }

    public int count() {
        return learned.size();
    }

    public Learned learned(Identifier id) {
        return learned.get(id.toString());
    }

    public boolean inMemory(Identifier id) {
        return memory.contains(id.toString());
    }

    public int written() {
        return written;
    }

    public boolean hadTable() {
        return table;
    }

    /** Whether you've used a table that stands in your Study. */
    public boolean studyTable() {
        return study;
    }

    /** Learns an enchantment; false when it was already known. */
    boolean learn(Identifier id, String where, long day) {
        return learned.putIfAbsent(id.toString(), new Learned(where, day)) == null;
    }

    /** Puts a mark in memory (its first clean trace); false when it was already there. */
    boolean remember(Identifier id) {
        if (memory.contains(id.toString())) return false;
        memory.add(id.toString());
        return true;
    }

    /** Whether this kind of gate's Lord already gave its page. */
    public boolean gatePaid(String theme) {
        return gates.contains(theme);
    }

    void payGate(String theme) {
        if (!gates.contains(theme)) gates.add(theme);
    }

    /** The System's one line about gates and pages: true the first time only. */
    boolean tell() {
        if (told) return false;
        told = true;
        return true;
    }

    static void save(ServerPlayer p, Known k) {
        p.setData(EnchantContent.KNOWN, k);
        p.syncData(EnchantContent.KNOWN);
    }
}
