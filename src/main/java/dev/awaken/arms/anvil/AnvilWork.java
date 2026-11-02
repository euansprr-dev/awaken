package dev.awaken.arms.anvil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A weapon in the making, kept on its War Anvil (SPEC §46.2; schemaVersion 1): the weapon, the stage and the game tick it
 * began, the beats counted when this run of the stage began (a smith who walks away picks up there, counted in again),
 * what was taken in (given back if the anvil is broken), every judgement so far, when the blade went into the trough,
 * who is making it and the Forge it heats in. Synced to everyone near with the anvil, so they see it glow and draw out.
 */
public final class AnvilWork {
    public static final int SCHEMA = 1;

    public final String weapon;
    public int stage;
    public long stageStart;
    public int strikeBase;
    public final List<ItemStack> materials;
    public float heat = -1F, quench = -1F;
    /** When the blade went into the trough (ticks from the quench's start; -1 not yet), and how long it was held there. */
    public float plunge = -1F, held = -1F;
    public final List<Float> scores, taps;
    public final UUID forger;
    public final BlockPos forge;

    public AnvilWork(String weapon, int stage, long stageStart, int strikeBase, List<ItemStack> materials, float heat, float quench, float plunge,
                     float held, List<Float> scores, List<Float> taps, UUID forger, BlockPos forge) {
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
    }

    public static AnvilWork fresh(String weapon, long now, List<ItemStack> materials, UUID forger, BlockPos forge) {
        return new AnvilWork(weapon, AnvilSong.HEAT, now, 0, materials, -1F, -1F, -1F, -1F, List.of(), List.of(), forger, forge);
    }

    /** The beats this stage strikes (the draw out's strikes, the hilt's taps). */
    public List<Float> beats() {
        return stage == AnvilSong.HILT ? taps : scores;
    }

    public int beatsNeeded() {
        return stage == AnvilSong.HILT ? AnvilSong.TAPS : AnvilSong.STRIKES;
    }

    public AnvilSong.Rhythm rhythm() {
        return stage == AnvilSong.HILT ? AnvilSong.HILT_RHYTHM : AnvilSong.LONGSWORD;
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
        this.strikeBase = beats().size();
        if (stage == AnvilSong.QUENCH) plunge = -1F;
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
            BlockPos.CODEC.fieldOf("forge").forGetter(w -> w.forge)
    ).apply(i, (v, weapon, stage, start, base, mats, heat, quench, plunge, held, scores, taps, forger, forge) ->
            new AnvilWork(weapon, stage, start, base, mats, heat, quench, plunge, held, scores, taps, forger, forge)));
}
