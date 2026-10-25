package dev.awaken.living.pulse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A kind of happening (SPEC §21.6), from {@code data/<ns>/awaken/happening/<id>.json}, schema 1. The behaviour is code
 * ({@link Behaviours}, by {@code behaviour}); the data says when and where it may run, how long, how it's told from afar,
 * what witnessing it is worth, and the rumours told about it.
 * <ul>
 * <li>{@code groups}: fauna biome groups it runs in ({@code greenwild}, {@code frostwild}, ...; empty: any land).</li>
 * <li>{@code bands}: the region bands it opens in (0..4).</li>
 * <li>{@code rumours}: what people say when it's on somewhere you haven't been ({@code {dir}} the direction).</li>
 * </ul>
 */
public record HappeningDef(int schemaVersion, String title, String family, String behaviour, When when, List<String> groups,
                           List<Integer> bands, int durationS, double weight, int witnessRadius, String tell, int tellRadius,
                           List<String> rumours) {

    /**
     * When it may run: {@code time} any of dawn, day, dusk, night (empty: any time); {@code moon} the moon phases
     * 0 (full) .. 7 it favours ({@code moon_weight} its weight on them), and with {@code moon_only} the only ones it runs
     * on; {@code weather} any, clear, rain or thunder.
     */
    public record When(List<String> time, List<Integer> moon, double moonWeight, boolean moonOnly, String weather) {
        public static final When ANY = new When(List.of(), List.of(), 1.0, false, "any");
        public static final Codec<When> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("time", List.of()).forGetter(When::time),
                Codec.INT.listOf().optionalFieldOf("moon", List.of()).forGetter(When::moon),
                Codec.DOUBLE.optionalFieldOf("moon_weight", 1.0).forGetter(When::moonWeight),
                Codec.BOOL.optionalFieldOf("moon_only", false).forGetter(When::moonOnly),
                Codec.STRING.optionalFieldOf("weather", "any").forGetter(When::weather)
        ).apply(i, When::new));
    }

    public static final Codec<HappeningDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(HappeningDef::schemaVersion),
            Codec.STRING.fieldOf("title").forGetter(HappeningDef::title),
            Codec.STRING.optionalFieldOf("family", "creature").forGetter(HappeningDef::family),
            Codec.STRING.fieldOf("behaviour").forGetter(HappeningDef::behaviour),
            When.CODEC.optionalFieldOf("when", When.ANY).forGetter(HappeningDef::when),
            Codec.STRING.listOf().optionalFieldOf("groups", List.of()).forGetter(HappeningDef::groups),
            Codec.INT.listOf().optionalFieldOf("bands", List.of(0, 1, 2, 3, 4)).forGetter(HappeningDef::bands),
            Codec.INT.optionalFieldOf("duration_s", 240).forGetter(HappeningDef::durationS),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(HappeningDef::weight),
            Codec.INT.optionalFieldOf("witness_radius", 48).forGetter(HappeningDef::witnessRadius),
            Codec.STRING.optionalFieldOf("tell", "haze").forGetter(HappeningDef::tell),
            Codec.INT.optionalFieldOf("tell_radius", 16).forGetter(HappeningDef::tellRadius),
            Codec.STRING.listOf().optionalFieldOf("rumours", List.of()).forGetter(HappeningDef::rumours)
    ).apply(i, HappeningDef::new));

    /** The tells the client knows how to draw. */
    public static final List<String> TELLS = List.of("haze", "dustline", "flock", "circle", "storm", "ghost", "fog", "lanterns", "roar");
}
