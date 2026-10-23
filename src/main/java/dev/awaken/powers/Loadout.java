package dev.awaken.powers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's powers: what they own (variant + level), what sits on the six keys, cooldowns and energy. Saved on the
 * player, kept on death, synced to its owner only. The network form also carries transient fields the HUD needs to
 * extrapolate energy between syncs (max, regen, pause) and the powers currently running.
 *
 * <p>Slots: 0-3 = Z X C V, 4 = Ultimate (R), 5 = Movement (Left Alt). Mutate through {@link Powers}.
 */
public final class Loadout {
    public static final int SCHEMA = 1;
    public static final int SLOTS = 6;
    public static final int ULTIMATE = 4;
    public static final int MOVEMENT = 5;
    public static final int MAX_LEVEL = 5;

    public record Owned(String id, int variant, int level) {
        public static final Codec<Owned> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Owned::id),
                Codec.INT.optionalFieldOf("variant", 0).forGetter(Owned::variant),
                Codec.INT.optionalFieldOf("level", 1).forGetter(Owned::level)
        ).apply(i, Owned::new));
        public static final StreamCodec<ByteBuf, Owned> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Owned::id, ByteBufCodecs.VAR_INT, Owned::variant, ByteBufCodecs.VAR_INT, Owned::level, Owned::new);
    }

    /** end = game time the cooldown ends; total = its full length (for the HUD sweep). */
    public record Cooldown(long end, int total) {
        public static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("end").forGetter(Cooldown::end),
                Codec.INT.fieldOf("total").forGetter(Cooldown::total)
        ).apply(i, Cooldown::new));
        public static final StreamCodec<ByteBuf, Cooldown> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Cooldown::end, ByteBufCodecs.VAR_INT, Cooldown::total, Cooldown::new);
    }

    /** A power currently running (channel, toggle or timed effect). end = -1 when open-ended. */
    public record Active(String id, long end) {
        public static final StreamCodec<ByteBuf, Active> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Active::id, ByteBufCodecs.VAR_LONG, Active::end, Active::new);
    }

    int schema = SCHEMA;
    final LinkedHashMap<String, Owned> owned = new LinkedHashMap<>();
    final String[] slots = {"", "", "", "", "", ""};
    final Map<String, Cooldown> cooldowns = new HashMap<>();
    /** Negative until first initialised to the full pool. */
    float energy = -1;

    // transient, network only
    float maxEnergy = 125;
    float regenPerTick = 0.22f;
    long pauseUntil;
    long syncTime;
    final List<Active> active = new ArrayList<>();

    public Loadout() {}

    // ---- read API ---------------------------------------------------------------------------------------------------
    public Map<String, Owned> owned() { return owned; }
    public boolean owns(String id) { return owned.containsKey(id); }
    public @Nullable Owned get(String id) { return owned.get(id); }
    public String slot(int i) { return i >= 0 && i < SLOTS ? slots[i] : ""; }
    public float energy() { return Math.max(0, energy); }
    public float maxEnergy() { return maxEnergy; }
    public float regenPerTick() { return regenPerTick; }
    public long pauseUntil() { return pauseUntil; }
    public long syncTime() { return syncTime; }
    public List<Active> active() { return active; }

    public @Nullable Cooldown cooldown(String id) { return cooldowns.get(id); }

    public long cooldownLeft(String id, long now) {
        Cooldown c = cooldowns.get(id);
        return c == null ? 0 : Math.max(0, c.end() - now);
    }

    public boolean isActive(String id) {
        for (Active a : active) if (a.id().equals(id)) return true;
        return false;
    }

    /** Energy extrapolated to {@code now} the same way the server regenerates it (client HUD). */
    public float energyAt(long now) {
        float e = energy();
        long from = Math.max(syncTime, pauseUntil);
        if (now > from) e += regenPerTick * (now - from);
        return Math.min(maxEnergy, e);
    }

    public int slotOf(String id) {
        for (int i = 0; i < SLOTS; i++) if (slots[i].equals(id)) return i;
        return -1;
    }

    // ---- codecs -----------------------------------------------------------------------------------------------------
    public static final MapCodec<Loadout> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(l -> l.schema),
            Owned.CODEC.listOf().fieldOf("owned").forGetter(l -> List.copyOf(l.owned.values())),
            Codec.STRING.listOf().fieldOf("slots").forGetter(l -> Arrays.asList(l.slots)),
            Codec.unboundedMap(Codec.STRING, Cooldown.CODEC).optionalFieldOf("cooldowns", Map.of()).forGetter(l -> Map.copyOf(l.cooldowns)),
            Codec.FLOAT.optionalFieldOf("energy", -1f).forGetter(l -> l.energy)
    ).apply(i, Loadout::fromParts));

    public static final Codec<Loadout> CODEC = MAP_CODEC.codec();

    private static Loadout fromParts(int schema, List<Owned> owned, List<String> slots, Map<String, Cooldown> cds, float energy) {
        Loadout l = new Loadout();
        l.schema = schema;
        for (Owned o : owned) l.owned.put(o.id(), o);
        for (int s = 0; s < SLOTS && s < slots.size(); s++) l.slots[s] = slots.get(s);
        l.cooldowns.putAll(cds);
        l.energy = energy;
        return l;
    }

    private static final StreamCodec<ByteBuf, List<Owned>> OWNED_LIST = Owned.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, Map<String, Cooldown>> COOLDOWN_MAP =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Cooldown.STREAM_CODEC);
    private static final StreamCodec<ByteBuf, List<Active>> ACTIVE_LIST = Active.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, Loadout> STREAM_CODEC = StreamCodec.of((buf, l) -> {
        OWNED_LIST.encode(buf, List.copyOf(l.owned.values()));
        STRING_LIST.encode(buf, Arrays.asList(l.slots));
        COOLDOWN_MAP.encode(buf, l.cooldowns);
        buf.writeFloat(l.energy);
        buf.writeFloat(l.maxEnergy);
        buf.writeFloat(l.regenPerTick);
        buf.writeLong(l.pauseUntil);
        buf.writeLong(l.syncTime);
        ACTIVE_LIST.encode(buf, l.active);
    }, buf -> {
        Loadout l = fromParts(SCHEMA, OWNED_LIST.decode(buf), STRING_LIST.decode(buf), COOLDOWN_MAP.decode(buf), buf.readFloat());
        l.maxEnergy = buf.readFloat();
        l.regenPerTick = buf.readFloat();
        l.pauseUntil = buf.readLong();
        l.syncTime = buf.readLong();
        l.active.addAll(ACTIVE_LIST.decode(buf));
        return l;
    });
}
