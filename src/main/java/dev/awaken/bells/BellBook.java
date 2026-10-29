package dev.awaken.bells;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's Bell Book (SPEC §43.7): the bells they hold (when and where each rang), their leads (how they learned of
 * each bell they don't hold yet, in the System's words, and where it seems to be), the lead they track, the peal their
 * handbell is set to, and what's been said once. Saved on the player (schema 1), kept through death.
 */
public final class BellBook {
    public static final int SCHEMA = 3;

    /** How a lead was learned (JOURNEY part 4). Sung comes with the Chimewren (stage 5), Found with the Stilled's letters. */
    public enum How { HEARD, SEEN, SUNG, FOUND, RUNG, REMEMBERED, DREAMT }

    /** A bell held: the play-day it rang and where (in words). */
    public record Held(long day, String where) {
        static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(Held::day),
                Codec.STRING.optionalFieldOf("where", "").forGetter(Held::where)
        ).apply(i, Held::new));
    }

    /** A lead on a bell not held yet. */
    public static final class Lead {
        static final Codec<Lead> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("hows").forGetter(l -> l.hows),
                Codec.STRING.listOf().optionalFieldOf("lines", List.of()).forGetter(l -> l.lines),
                Codec.INT.optionalFieldOf("bearings", 0).forGetter(l -> l.bearings),
                Codec.DOUBLE.optionalFieldOf("hx", 0D).forGetter(l -> l.hx),
                Codec.DOUBLE.optionalFieldOf("hz", 0D).forGetter(l -> l.hz),
                Codec.DOUBLE.optionalFieldOf("tx", 0D).forGetter(l -> l.tx),
                Codec.DOUBLE.optionalFieldOf("tz", 0D).forGetter(l -> l.tz),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(l -> l.since)
        ).apply(i, (hows, lines, bearings, hx, hz, tx, tz, since) -> {
            Lead l = new Lead();
            l.hows = hows;
            l.lines.addAll(lines);
            l.bearings = bearings;
            l.hx = hx;
            l.hz = hz;
            l.tx = tx;
            l.tz = tz;
            l.since = since;
            return l;
        }));

        /** {@link How} flags. */
        public int hows;
        public final List<String> lines = new ArrayList<>();
        /** 0: no bearing; 1: heard once, from (hx, hz); 2: two bearings crossed, the area's middle at (tx, tz). */
        public int bearings;
        public double hx, hz, tx, tz;
        /** Play-day it was first filed. */
        public long since;

        public boolean has(How h) {
            return (hows & (1 << h.ordinal())) != 0;
        }
    }

    static final MapCodec<BellBook> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(b -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Held.CODEC).optionalFieldOf("held", Map.of()).forGetter(b -> keys(b.held)),
            Codec.unboundedMap(Codec.STRING, Lead.CODEC).optionalFieldOf("leads", Map.of()).forGetter(b -> keys(b.leads)),
            Codec.INT.optionalFieldOf("tracked", -1).forGetter(b -> b.tracked),
            Codec.BOOL.optionalFieldOf("given", false).forGetter(b -> b.given),
            Codec.STRING.optionalFieldOf("peal", "").forGetter(b -> b.peal),
            Codec.LONG.optionalFieldOf("homeward_at", 0L).forGetter(b -> b.homewardAt),
            Codec.LONG.optionalFieldOf("remembered_day", -1L).forGetter(b -> b.rememberedDay),
            Codec.BOOL.optionalFieldOf("pealed", false).forGetter(b -> b.pealed),
            Codec.LONG.optionalFieldOf("kinsong_at", 0L).forGetter(b -> b.kinsongAt)
    ).apply(i, (schema, held, leads, tracked, given, peal, homewardAt, rememberedDay, pealed, kinsongAt) -> {
        BellBook b = new BellBook();
        held.forEach((k, v) -> b.held.put(Integer.parseInt(k), v));
        leads.forEach((k, v) -> b.leads.put(Integer.parseInt(k), v));
        b.tracked = tracked;
        b.given = given;
        b.peal = peal;
        b.homewardAt = homewardAt;
        b.rememberedDay = rememberedDay;
        b.pealed = pealed;
        b.kinsongAt = kinsongAt;
        return b;
    }));

    private static <V> Map<String, V> keys(Map<Integer, V> m) {
        Map<String, V> out = new HashMap<>();
        m.forEach((k, v) -> out.put(Integer.toString(k), v));
        return out;
    }

    public final Map<Integer, Held> held = new HashMap<>();
    public final Map<Integer, Lead> leads = new HashMap<>();
    /** The lead on the top-right card, or -1. */
    public int tracked = -1;
    /** The handbell and the bell frame were given with the first bell. */
    public boolean given;
    /** The peal the handbell rings ("" for a plain ring). */
    public String peal = "";
    /** Game time Homeward can next be rung. */
    public long homewardAt;
    /** The play-day someone in Carillon last remembered a home's bell aloud (schema 2), or -1. */
    public long rememberedDay = -1;
    /** This player rang a Hundred Peal, or was home for one (schema 3): the Kinsong is theirs and the Wake is open. */
    public boolean pealed;
    /** Game time the Deep's things may next come for this player's Kinsong (schema 3). */
    public long kinsongAt;

    public boolean has(String id) {
        BellCatalog.Def d = BellCatalog.of(id);
        return d != null && held.containsKey(d.no());
    }

    public int count() {
        return held.size();
    }
}
