package dev.awaken.arms.anvil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsData;
import dev.awaken.arms.ArmsItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A weapon in the making, kept on its War Anvil (SPEC §46.2, §46.7; schemaVersion 2): the weapon, the stage and the game
 * tick it began, the beats counted when this run of the stage began (a smith who walks away picks up there, counted in
 * again), what was taken in (given back if the anvil is broken), every judgement so far, when the blade went into the
 * trough, who is making it and the Forge it heats in (at E, by hand, the anvil itself). v2 adds whether it's forged or
 * made from memory, the bind's score and the helpers' strikes (a v1 save reads as forged, unbound, unhelped). Synced to
 * everyone near with the anvil, so they see it glow and draw out.
 */
public final class AnvilWork {
    public static final int SCHEMA = 2;
    /** Forged in full, or made from memory (one heat, one strike, the quench; always Fine). */
    public static final int FORGE = 0, MEMORY = 1;
    /** A Hunter blade forged alone has no style of its own. */
    public static final String BLADE = "blade";

    public final String weapon;
    public int stage;
    public long stageStart;
    public int strikeBase;
    public final List<ItemStack> materials;
    public float heat = -1F, quench = -1F;
    /** When the blade went into the trough (ticks from the quench's start; -1 not yet), and how long it was held there. */
    public float plunge = -1F, held = -1F;
    /** The draw out's strikes (at E the knap's flakes) and the hilt's taps (at E the bind's hold: its start, then each turn). */
    public final List<Float> scores, taps;
    public final UUID forger;
    public final BlockPos forge;
    public final int kind;
    /** The bind's score (-1 not yet), and the clean off-beats others struck while it was drawn out. */
    public float bind;
    public int help;
    /** The last helper's strike (game tick) and the last flake (ticks from the stage's start); not saved. */
    public long helpLast = Long.MIN_VALUE / 2;
    public float knapLast = -1F;
    /** Read once from the weapon's id: its style, tier (0 E to 5 S) and song. */
    public final String style;
    public final int tier;
    public final AnvilSong.Song song;

    public AnvilWork(String weapon, int stage, long stageStart, int strikeBase, List<ItemStack> materials, float heat, float quench, float plunge,
                     float held, List<Float> scores, List<Float> taps, UUID forger, BlockPos forge, int kind, float bind, int help) {
        this.weapon = weapon;
        this.stage = stage;
        this.stageStart = stageStart;
        this.strikeBase = strikeBase;
        this.materials = new ArrayList<>(materials);
        this.heat = heat;
        this.quench = quench;
        this.plunge = plunge;
        this.held = held;
        this.scores = new ArrayList<>(scores);
        this.taps = new ArrayList<>(taps);
        this.forger = forger;
        this.forge = forge;
        this.kind = kind;
        this.bind = bind;
        this.help = help;
        this.style = styleOf(weapon);
        this.tier = Math.max(0, tierOf(weapon));
        this.song = AnvilSong.song(style);
    }

    /** A fresh work: by hand at E (the knap first), else the Forge's heat. */
    public static AnvilWork fresh(String weapon, long now, List<ItemStack> materials, UUID forger, BlockPos forge, int kind) {
        int stage = tierOf(weapon) == 0 ? AnvilSong.KNAP : AnvilSong.HEAT;
        return new AnvilWork(weapon, stage, now, 0, materials, -1F, -1F, -1F, -1F, List.of(), List.of(), forger, forge, kind, -1F, 0);
    }

    /** A weapon's style ("daggers"), "blade" for a Hunter blade, read from its id ("beastbound_daggers"). */
    public static String styleOf(String id) {
        ArmsData.Weapon w = ArmsItems.data(id);
        if (w != null) return w.style();
        return id.endsWith("_" + BLADE) ? BLADE : id.substring(id.indexOf('_') + 1);
    }

    /** Its tier from its name's prefix (0 Wildhide to 5 Sovereign), or -1. */
    public static int tierOf(String id) {
        int us = id.indexOf('_');
        String prefix = us < 0 ? id : id.substring(0, us);
        for (int i = 0; i < ArmsArmoury.PREFIX.length; i++) if (ArmsArmoury.PREFIX[i].equals(prefix)) return i;
        return -1;
    }

    /** At E it's made by hand: knapped and bound, no Forge. */
    public boolean hand() {
        return tier == 0;
    }

    public boolean memory() {
        return kind == MEMORY;
    }

    /** The beats this stage strikes (the draw out's strikes or the knap's flakes, the hilt's taps). */
    public List<Float> beats() {
        return stage == AnvilSong.HILT || stage == AnvilSong.BIND ? taps : scores;
    }

    public int beatsNeeded() {
        return switch (stage) {
            case AnvilSong.HILT -> AnvilSong.TAPS;
            case AnvilSong.KNAP -> memory() ? 1 : AnvilSong.FLAKES;
            case AnvilSong.BIND -> AnvilSong.TURNS + 1;
            default -> memory() ? 1 : song.strikes();
        };
    }

    public AnvilSong.Rhythm rhythm() {
        return stage == AnvilSong.HILT ? AnvilSong.HILT_RHYTHM : song.rhythm();
    }

    /** Ticks from the stage's start that the next beat is due. */
    public float nextDue() {
        return rhythm().due(beats().size() - strikeBase);
    }

    public static float mean(List<Float> l) {
        if (l.isEmpty()) return 0F;
        float s = 0F;
        for (float f : l) s += f;
        return s / l.size();
    }

    public void begin(int stage, long now) {
        this.stage = stage;
        this.stageStart = now;
        if (stage == AnvilSong.BIND) taps.clear(); // a hold let go of, or left, is wound again from the start
        this.strikeBase = beats().size();
        this.knapLast = -1F;
        if (stage == AnvilSong.QUENCH) plunge = -1F;
    }

    /** v2's fields, kept beside the rest (one codec holds at most sixteen). */
    private record More(int kind, float bind, int help) {
        static final MapCodec<More> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("kind", FORGE).forGetter(More::kind),
                Codec.FLOAT.optionalFieldOf("bind", -1F).forGetter(More::bind),
                Codec.INT.optionalFieldOf("help", 0).forGetter(More::help)
        ).apply(i, More::new));
    }

    public static final Codec<AnvilWork> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(w -> SCHEMA),
            Codec.STRING.fieldOf("weapon").forGetter(w -> w.weapon),
            Codec.INT.fieldOf("stage").forGetter(w -> w.stage),
            Codec.LONG.fieldOf("stageStart").forGetter(w -> w.stageStart),
            Codec.INT.optionalFieldOf("strikeBase", 0).forGetter(w -> w.strikeBase),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(w -> w.materials),
            Codec.FLOAT.optionalFieldOf("heat", -1F).forGetter(w -> w.heat),
            Codec.FLOAT.optionalFieldOf("quench", -1F).forGetter(w -> w.quench),
            Codec.FLOAT.optionalFieldOf("plunge", -1F).forGetter(w -> w.plunge),
            Codec.FLOAT.optionalFieldOf("held", -1F).forGetter(w -> w.held),
            Codec.FLOAT.listOf().optionalFieldOf("scores", List.of()).forGetter(w -> w.scores),
            Codec.FLOAT.listOf().optionalFieldOf("taps", List.of()).forGetter(w -> w.taps),
            UUIDUtil.CODEC.fieldOf("forger").forGetter(w -> w.forger),
            BlockPos.CODEC.fieldOf("forge").forGetter(w -> w.forge),
            More.CODEC.forGetter(w -> new More(w.kind, w.bind, w.help))
    ).apply(i, (v, weapon, stage, start, base, mats, heat, quench, plunge, held, scores, taps, forger, forge, more) ->
            new AnvilWork(weapon, stage, start, base, mats, heat, quench, plunge, held, scores, taps, forger, forge, more.kind(), more.bind(), more.help())));
}
