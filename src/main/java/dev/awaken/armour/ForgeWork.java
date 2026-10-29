package dev.awaken.armour;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A piece in the making, kept on its Fitting Stand (SPEC §44.4; schemaVersion 2, the P3 and P4 fields optional): the set
 * and piece, the stage and the game tick it began, the strikes counted when this run of the stage began (a smith who
 * walks away picks up there, counted in again), what was taken in (given back if the stand is broken), every judgement so
 * far, and the rank's verbs: the tune's taps, the catalyst to inlay, the fold and the starfire. Every strike is one list:
 * the shaping's, then the fold's, then the starfire's. Synced to everyone near with the stand, so they see the bar glow
 * and the shape form.
 */
public final class ForgeWork {
    public static final int SCHEMA = 2;

    public final ArmourSet set;
    public final ArmorType piece;
    public int stage;
    public long stageStart;
    public int strikeBase;
    public final List<ItemStack> materials;
    public float heat = -1F, cut = -1F, quench = -1F;
    /** B's tune (-1 until done; 1 rang true) and the taps so far; C's inlay: the catalyst's Force + 1 (0 none) and how it seated. */
    public float tune = -1F, inlayScore = -1F;
    public int taps, inlay;
    /** A's fold and S's starfire: each the mean of its strikes (-1 until done). */
    public float fold = -1F, starfire = -1F;
    public final List<Float> scores;
    public int twin;
    public final UUID forger;
    public final boolean memory;
    /** Where the metal is worked (the anvil; by hand, the works table), and the Forge it heats in (none by hand). */
    public final BlockPos work;
    public final Optional<BlockPos> forge;

    public ForgeWork(ArmourSet set, ArmorType piece, int stage, long stageStart, int strikeBase, List<ItemStack> materials, float heat, float cut,
                     float quench, List<Float> scores, int twin, UUID forger, boolean memory, BlockPos work, Optional<BlockPos> forge) {
        this.set = set;
        this.piece = piece;
        this.stage = stage;
        this.stageStart = stageStart;
        this.strikeBase = strikeBase;
        this.materials = new ArrayList<>(materials);
        this.heat = heat;
        this.cut = cut;
        this.quench = quench;
        this.scores = new ArrayList<>(scores);
        this.twin = twin;
        this.forger = forger;
        this.memory = memory;
        this.work = work;
        this.forge = forge;
    }

    /** Strikes the shaping takes (one from memory). */
    public int shapeStrikes() {
        return memory ? 1 : ForgeSong.strikes(piece, ForgeSong.byHand(set));
    }

    /** Where this strike stage's strikes start in the list: the fold's after the shaping's, the starfire's after both. */
    public int stageFrom() {
        return switch (stage) {
            case ForgeSong.FOLD -> shapeStrikes();
            case ForgeSong.STARFIRE -> shapeStrikes() + (ForgeSong.folds(set) ? ForgeSong.FOLD_STRIKES : 0);
            default -> 0;
        };
    }

    /** The strikes counted when this strike stage is done (the list's length then). */
    public int strikesNeeded() {
        return switch (stage) {
            case ForgeSong.FOLD -> stageFrom() + ForgeSong.FOLD_STRIKES;
            case ForgeSong.STARFIRE -> stageFrom() + ForgeSong.STARFIRE_STRIKES;
            default -> shapeStrikes();
        };
    }

    /** The shaping's strikes' mean (what the quality weighs as the strikes). */
    public float strikeMean() {
        return mean(0, shapeStrikes());
    }

    /** The mean of the strikes from one place in the list to another (as many as there are). */
    public float mean(int from, int to) {
        to = Math.min(to, scores.size());
        if (to <= from) return 0F;
        float s = 0F;
        for (int i = from; i < to; i++) s += scores.get(i);
        return s / (to - from);
    }

    /** How far the metal has been shaped (0 the bar, 1 the piece). */
    public float shaped() {
        return Math.min(1F, scores.size() / (float) shapeStrikes());
    }

    /** Ticks from the stage's start that the next strike is due. */
    public float nextDue() {
        return ForgeSong.rhythm(set, stage).due(scores.size() - strikeBase);
    }

    public void begin(int stage, long now) {
        this.stage = stage;
        this.stageStart = now;
        this.strikeBase = scores.size();
    }

    private static final Codec<ArmorType> PIECE = Codec.INT.xmap(i -> ForgeSong.PIECES[Math.floorMod(i, 4)], ForgeSong::slot);
    private static final Codec<ArmourSet> SET = Codec.STRING.xmap(s -> {
        ArmourSet a = ArmourSet.byId(s);
        return a == null ? ArmourSet.WILDHIDE : a;
    }, a -> a.id);

    public static final Codec<ForgeWork> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(w -> SCHEMA),
            SET.fieldOf("set").forGetter(w -> w.set),
            PIECE.fieldOf("piece").forGetter(w -> w.piece),
            Codec.INT.fieldOf("stage").forGetter(w -> w.stage),
            Codec.LONG.fieldOf("stageStart").forGetter(w -> w.stageStart),
            Codec.INT.optionalFieldOf("strikeBase", 0).forGetter(w -> w.strikeBase),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("materials", List.of()).forGetter(w -> w.materials),
            Codec.FLOAT.listOf().optionalFieldOf("judged", List.of(-1F, -1F, -1F)).forGetter(w -> List.of(w.heat, w.cut, w.quench, w.tune, w.inlayScore, w.fold, w.starfire)),
            Codec.FLOAT.listOf().optionalFieldOf("scores", List.of()).forGetter(w -> w.scores),
            Codec.INT.optionalFieldOf("twin", 0).forGetter(w -> w.twin),
            UUIDUtil.CODEC.fieldOf("forger").forGetter(w -> w.forger),
            Codec.BOOL.optionalFieldOf("memory", false).forGetter(w -> w.memory),
            BlockPos.CODEC.fieldOf("work").forGetter(w -> w.work),
            BlockPos.CODEC.optionalFieldOf("forge").forGetter(w -> w.forge),
            Codec.INT.optionalFieldOf("taps", 0).forGetter(w -> w.taps),
            Codec.INT.optionalFieldOf("inlay", 0).forGetter(w -> w.inlay)
    ).apply(i, (v, set, piece, stage, start, base, mats, judged, scores, twin, forger, memory, work, forge, taps, inlay) -> {
        ForgeWork w = new ForgeWork(set, piece, stage, start, base, mats, judged(judged, 0), judged(judged, 1), judged(judged, 2), scores, twin, forger,
                memory, work, forge);
        w.tune = judged(judged, 3);
        w.inlayScore = judged(judged, 4);
        w.fold = judged(judged, 5);
        w.starfire = judged(judged, 6);
        w.taps = taps;
        w.inlay = inlay;
        return w;
    }));

    private static float judged(List<Float> l, int i) {
        return l.size() > i ? l.get(i) : -1F;
    }
}
