package dev.awaken.mobs.def;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import org.jspecify.annotations.Nullable;

/**
 * One telegraphed attack. Every ability winds up first (particles at the place it will land, a sound, the mob's glow
 * brightening) so deaths stay readable, then executes once. Numbers are relative to the mob's ATTACK_DAMAGE so SPEC
 * scaling carries through.
 */
public record Ability(Kind kind, String name, int windup, int cooldown, float range, float power, float radius, int count,
                      @Nullable BoltKind bolt, @Nullable Holder<MobEffect> effect, int effectTicks, int effectAmp,
                      @Nullable String summon, float inner, boolean roots, boolean heavy) {

    public enum Kind {
        /** Ground pound around itself. */
        SLAM,
        /** Jumps at the target and pounds where it lands. */
        LEAP,
        /** Dashes along a locked line. */
        CHARGE,
        /** Fires {@code count} bolts at the target in a fan. */
        BOLT,
        /** Fires {@code count} arrows in a fan (bow users). */
        ARROWS,
        /** A ring of {@code count} bolts outward. */
        NOVA,
        /** Damage in a ring between {@code inner} and {@code radius} around itself (step inside to dodge). */
        BURST,
        /** Marks {@code count} circles (under players first, then random spots in the arena) and strikes them. */
        RAIN,
        /** Locks on, then a line strike. */
        BEAM,
        /** Teleports beside the target and strikes. */
        BLINK,
        /** Calls {@code count} of {@code summon}. */
        SUMMON,
        /** A lingering cloud at the target. */
        CLOUD,
        /** A cone in front of it (breath, spray). */
        CONE,
        /** Drags everyone in {@code radius} towards it. */
        PULL,
        /** Blasts everyone in {@code radius} away. */
        GUST,
        /** Heals allies in {@code radius} by {@code power} of their max health. */
        HEAL,
        /** Roots whoever is still standing in the marked circle. */
        ROOT,
        /** A line of evoker fangs towards the target. */
        FANGS,
        /** A ring of evoker fangs around itself. */
        FANGS_RING,
        /** Short-lived copies of itself that hit for nothing and die in one blow. */
        DECOY,
        /** Raises a guard: blocks most melee damage and ripostes. */
        GUARD,
        /** Lifts everyone in the marked circle (gravity), then lets go. */
        LIFT,
        /** Darkness on everyone in {@code radius}. */
        DARKNESS,
        /** A pulsing aura for a few seconds. */
        AURA,
        /** Speeds and strengthens itself. */
        FRENZY
    }

    public static Builder of(Kind kind, String name) {
        return new Builder(kind, name);
    }

    public static final class Builder {
        private final Kind kind;
        private final String name;
        private int windup = 20, cooldown = 100, count = 1, effectTicks, effectAmp;
        private float range = 6, power = 1.0F, radius = 3.0F, inner;
        private BoltKind bolt;
        private Holder<MobEffect> effect;
        private String summon;
        private Boolean roots;
        private boolean heavy;

        private Builder(Kind kind, String name) {
            this.kind = kind;
            this.name = name;
            switch (kind) {
                case BOLT, ARROWS -> { windup = 14; cooldown = 50; range = 18; }
                case NOVA -> { windup = 24; cooldown = 140; range = 12; count = 10; }
                case RAIN -> { windup = 34; cooldown = 180; range = 24; radius = 2.5F; count = 3; }
                case BEAM -> { windup = 30; cooldown = 140; range = 18; power = 1.6F; }
                case SUMMON -> { windup = 30; cooldown = 400; range = 24; count = 2; }
                case CHARGE -> { windup = 18; cooldown = 120; range = 14; power = 1.4F; }
                case LEAP -> { windup = 16; cooldown = 120; range = 12; power = 1.3F; radius = 2.5F; }
                case SLAM -> { windup = 22; cooldown = 110; range = 3.5F; power = 1.5F; radius = 3.5F; }
                case BURST -> { windup = 40; cooldown = 260; range = 14; power = 2.2F; radius = 12; inner = 3.5F; }
                case CLOUD -> { windup = 18; cooldown = 200; range = 12; radius = 2.5F; }
                case CONE -> { windup = 18; cooldown = 120; range = 5; power = 1.0F; radius = 5; }
                case PULL, GUST -> { windup = 24; cooldown = 200; range = 10; power = 0.5F; radius = 10; }
                case HEAL -> { windup = 30; cooldown = 260; range = 24; power = 0.25F; radius = 10; }
                case ROOT -> { windup = 26; cooldown = 180; range = 14; radius = 2.0F; }
                case FANGS -> { windup = 20; cooldown = 160; range = 14; count = 10; power = 1.2F; }
                case FANGS_RING -> { windup = 20; cooldown = 200; range = 5; count = 8; power = 1.2F; }
                case DECOY -> { windup = 24; cooldown = 360; range = 16; count = 2; }
                case GUARD -> { windup = 8; cooldown = 160; range = 6; }
                case LIFT -> { windup = 30; cooldown = 220; range = 16; radius = 3.0F; }
                case DARKNESS -> { windup = 26; cooldown = 260; range = 16; radius = 16; }
                case AURA -> { windup = 36; cooldown = 320; range = 8; power = 0.5F; radius = 6; count = 5; }
                case FRENZY -> { windup = 20; cooldown = 400; range = 16; }
                case BLINK -> { windup = 14; cooldown = 120; range = 16; power = 1.2F; }
            }
        }

        public Builder wind(int ticks) { this.windup = ticks; return this; }
        public Builder cd(int ticks) { this.cooldown = ticks; return this; }
        public Builder range(float r) { this.range = r; return this; }
        public Builder power(float p) { this.power = p; return this; }
        public Builder radius(float r) { this.radius = r; return this; }
        public Builder inner(float r) { this.inner = r; return this; }
        public Builder n(int count) { this.count = count; return this; }
        public Builder bolt(BoltKind k) { this.bolt = k; return this; }
        public Builder fx(Holder<MobEffect> e, int ticks, int amp) { this.effect = e; this.effectTicks = ticks; this.effectAmp = amp; return this; }
        public Builder summon(String id) { this.summon = id; return this; }
        public Builder moving() { this.roots = false; return this; }
        public Builder rooted() { this.roots = true; return this; }
        /** Big tell: the heavy telegraph sound and a longer, brighter wind-up. */
        public Builder heavy() { this.heavy = true; return this; }

        public Ability build() {
            boolean r = roots != null ? roots : kind != Kind.BOLT && kind != Kind.ARROWS && kind != Kind.HEAL && kind != Kind.FRENZY;
            return new Ability(kind, name, windup, cooldown, range, power, radius, count, bolt, effect, effectTicks, effectAmp, summon, inner, r, heavy);
        }
    }
}
