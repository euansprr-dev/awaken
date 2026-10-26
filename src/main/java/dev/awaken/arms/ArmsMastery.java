package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Mastery (SPEC §25 "Mastery"): five steps for each fighting style, earned by the kills made with it. Each adds to the
 * same presses, never a key: I the chain's mastery strike (the 4th, or the 5th for the four-strike styles), II a
 * longer Slipcut, III a charged deflect (more posture to the attacker, more Resonance), IV Resonance filling faster,
 * V the Art at 80% of the meter. The client predicts I, II and V, so the steps are sent to it.
 */
public final class ArmsMastery {
    private ArmsMastery() {}

    /** Kills with a style for each step (I..V). */
    public static final int[] KILLS = {20, 60, 150, 350, 800};
    /** II: the Slipcut's window grows by this many ticks. */
    public static final int SLIPCUT_BONUS = 3;
    /** III: a deflect's posture and Resonance, times this. */
    public static final float DEFLECT_CHARGED = 1.5F;
    /** IV: every Resonance gain, times this. */
    public static final float RESONANCE_GAIN = 1.2F;
    /** V: the Art's meter. */
    public static final int ART_AT = 80;

    private static final String[] NUMERAL = {"", "I", "II", "III", "IV", "V"};
    private static final String[] GIVES = {"", "a mastery strike ends the chain", "a longer Slipcut", "a charged deflect",
            "Resonance fills faster", "the Art at 80% Resonance"};

    /** The step these kills reach (0..5). */
    public static int step(int kills) {
        int s = 0;
        while (s < KILLS.length && kills >= KILLS[s]) s++;
        return s;
    }

    /** Mastery strikes the chain gets at this step. */
    public static int strikes(int step) {
        return step >= 1 ? 1 : 0;
    }

    public static int slipcutBonus(int step) {
        return step >= 2 ? SLIPCUT_BONUS : 0;
    }

    public static float deflect(int step) {
        return step >= 3 ? DEFLECT_CHARGED : 1F;
    }

    public static float resonanceGain(int step) {
        return step >= 4 ? RESONANCE_GAIN : 1F;
    }

    public static int artAt(int step) {
        return step >= 5 ? ART_AT : ArmsRules.RESONANCE_MAX;
    }

    /** A fighter's step in a style (server). */
    public static int step(ServerPlayer p, Archetypes.Loaded style) {
        return step(p.getData(ArmsContent.MEMORY).kills().getOrDefault(style.id(), 0));
    }

    /** A kill with a style in hand counts for it; a new step is named by the System and sent to the client. */
    static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !(e.getEntity() instanceof LivingEntity dead)) return;
        if (!ArmsServer.foe(p, dead)) return;
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        if (style == null) return;
        count(p, style, 1);
        Marks.fire(p, Marks.Trigger.KILL, dead);
    }

    /** Adds kills to a style (also the dev command); returns the new step. */
    static int count(ServerPlayer p, Archetypes.Loaded style, int n) {
        ArmsWeaves.ArmsMemory m = p.getData(ArmsContent.MEMORY);
        int before = m.kills().getOrDefault(style.id(), 0);
        int after = before + n;
        m.kills().put(style.id(), after);
        p.setData(ArmsContent.MEMORY, m);
        int was = step(before), now = step(after);
        if (now > was) {
            Signal.toast(p, Component.literal("MASTERY · ").withStyle(Style.EMPTY.withColor(0xE2B450))
                    .append(Component.literal(style.def().name() + " " + NUMERAL[now]).withStyle(Style.EMPTY.withColor(0xFFE3A0)))
                    .append(Component.literal(" · " + GIVES[now]).withStyle(Style.EMPTY.withColor(0xC8C2B0))));
            sync(p);
        }
        return now;
    }

    /** Every style's step, by the style's index, to the fighter's client (on joining, after a new step). */
    static void sync(ServerPlayer p) {
        List<Integer> steps = new ArrayList<>(Archetypes.all().size());
        for (Archetypes.Loaded a : Archetypes.all()) steps.add(step(p, a));
        AwakenNet.send(p, new ArmsPayloads.Mastery(steps));
    }
}
