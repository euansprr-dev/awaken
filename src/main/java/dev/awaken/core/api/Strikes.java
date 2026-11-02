package dev.awaken.core.api;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * How a blow can be answered (SPEC §24, Arms): the class of an attack, the tell a body shows before it, and how a body
 * staggers when its posture breaks. Whoever delivers a blow names its class with {@link #deliver}; Arms reads it with
 * {@link #of} when the blow lands (guard, deflect, dodge). The mobs module installs the tells and the stagger of its
 * creatures; the defaults know vanilla's: an untold melee or projectile is LIGHT, a blast PERILOUS, the rest no attack.
 */
public final class Strikes {
    private Strikes() {}

    public enum Kind {
        /** A glint and a rising whoosh: deflect, block or dodge. */
        LIGHT,
        /** An amber flare: deflect or dodge; it breaks a guard. */
        HEAVY,
        /** A red flash: dodge only. */
        PERILOUS,
        /** A low lunge: dodge only. */
        GRAB,
        /** Not an attack anyone can answer (fire, falling, poison, the void). */
        NONE;

        public boolean deflectable() {
            return this == LIGHT || this == HEAVY;
        }

        public boolean blockable() {
            return this == LIGHT || this == HEAVY;
        }

        public boolean dodgeable() {
            return this != NONE;
        }

        /**
         * The class's colour (SPEC §46.2, one tell language): on a body's tell, a shot's core, a ring's edge, a wave's
         * crest and a lock's flash. White-gold: parry, block or dash; amber: parry or dash; red: dash or jump; violet: a
         * grab, dash only. NONE: white.
         */
        public int colour() {
            return switch (this) {
                case LIGHT -> 0xFFF2CC;
                case HEAVY -> 0xFFA63A;
                case PERILOUS -> 0xFF2E2E;
                case GRAB -> 0xC452FF;
                case NONE -> 0xFFFFFF;
            };
        }
    }

    // the blow being delivered right now: the server resolves one at a time on its thread, so a plain field holds it
    private static @Nullable Kind current;
    private static int currentBy = -1;

    /** Runs {@code blow} (a doHurtTarget, an ability's hit) as an attack of this class by {@code by}. */
    public static boolean deliver(Entity by, Kind kind, BooleanSupplier blow) {
        Kind prevKind = current;
        int prevBy = currentBy;
        current = kind;
        currentBy = by.getId();
        try {
            return blow.getAsBoolean();
        } finally {
            current = prevKind;
            currentBy = prevBy;
        }
    }

    /** The class of the blow being delivered right now (a bolt fired inside it carries it), or null. */
    public static @Nullable Kind delivering() {
        return current;
    }

    /** Classes a blow that lands after its ability ran (a bolt reaching you, fangs rising); null when it doesn't know. */
    public interface Classifier {
        @Nullable Kind of(DamageSource source);
    }

    private static Classifier classifier = s -> null;

    public static void installClassifier(Classifier c) {
        classifier = c;
    }

    /** The class of the blow behind this damage (server, while it lands). */
    public static Kind of(DamageSource source) {
        Entity by = source.getEntity();
        if (current != null && by != null && by.getId() == currentBy) return current;
        Kind late = classifier.of(source);
        if (late != null) return late;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_LIGHTNING)) {
            return Kind.NONE;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return by != null ? Kind.PERILOUS : Kind.NONE;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return Kind.LIGHT;
        if (by instanceof LivingEntity && source.getDirectEntity() == by) return Kind.LIGHT;
        return Kind.NONE;
    }

    // ---- tells (both sides: the client draws them, the server's rules may read them) --------------------------------

    /** A body winding up a blow: ticks left until it lands, and its class. */
    public record Tell(int ticks, Kind kind) {}

    public interface TellSource {
        @Nullable Tell tell(Entity e);
    }

    private static TellSource tells = e -> null;

    public static void installTells(TellSource source) {
        tells = source;
    }

    /** The blow this body is winding up, or null. */
    public static @Nullable Tell tell(Entity e) {
        return tells.tell(e);
    }

    // ---- stagger (server) -------------------------------------------------------------------------------------------

    public interface Staggers {
        /** Breaks the body's stance for {@code ticks}: its wind-up ends and it stands still. True when it took. */
        boolean stagger(LivingEntity e, int ticks);
    }

    private static Staggers staggers = (e, ticks) -> false;

    public static void installStaggers(Staggers s) {
        staggers = s;
    }

    public static boolean stagger(LivingEntity e, int ticks) {
        return staggers.stagger(e, ticks);
    }

    // ---- fliers brought down (server, SPEC §40) ---------------------------------------------------------------------

    public interface Grounder {
        /** Brings a flier out of the air for {@code ticks}: it drops, lands and stands open. True when it took. */
        boolean ground(LivingEntity e, int ticks);
    }

    private static Grounder grounder = (e, ticks) -> false;

    public static void installGrounder(Grounder g) {
        grounder = g;
    }

    /** A deflected dive, a broken stance: a flier that can be brought down comes down (others: nothing). */
    public static boolean ground(LivingEntity e, int ticks) {
        return grounder.ground(e, ticks);
    }

    // ---- beasts' bites (server, SPEC §46.2) -------------------------------------------------------------------------

    public interface Biters {
        /** A beast's melee goal: chases what it fights and bites it, told, while {@code when} holds. */
        Goal bite(PathfinderMob mob, double speed, BooleanSupplier when);
    }

    private static Biters biters = (mob, speed, when) -> new MeleeAttackGoal(mob, speed, true) {
        @Override
        public boolean canUse() {
            return when.getAsBoolean() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return when.getAsBoolean() && super.canContinueToUse();
        }
    };

    public static void installBiters(Biters b) {
        biters = b;
    }

    /**
     * The melee goal for a beast on a vanilla body (the Gnawed, an animal that fights back): it winds up a short told
     * lunge, a light blow, before each bite, and only two of a pack wind up on one body at once (the mobs module's).
     */
    public static Goal bite(PathfinderMob mob, double speed, BooleanSupplier when) {
        return biters.bite(mob, speed, when);
    }

    // ---- the duel (server, SPEC §46.3) ------------------------------------------------------------------------------

    /** What the mobs module answers when Arms turns a blow aside, and what Arms asks of a body. */
    public interface Duel {
        /** A melee blow of this body's was deflected. */
        default void deflected(LivingEntity attacker, Kind kind) {}

        /** Whether the body stands in an opening: it starts nothing, and blows on it break its stance faster. */
        default boolean opening(LivingEntity e) {
            return false;
        }

        /** Whether the body fights as a duel: braced outside its openings, its stance falls to deflects and punishes. */
        default boolean duelist(LivingEntity e) {
            return false;
        }

        /** A duel body's own stance size, or 0 for its role's. */
        default int stance(LivingEntity e) {
            return 0;
        }

        /** A shot deflected by {@code by}: true when it flies back at its maker (it is then kept, not broken). */
        default boolean returnShot(Entity shot, Player by) {
            return false;
        }
    }

    private static Duel duel = new Duel() {};

    public static void installDuel(Duel d) {
        duel = d;
    }

    public static void deflected(LivingEntity attacker, Kind kind) {
        duel.deflected(attacker, kind);
    }

    public static boolean opening(LivingEntity e) {
        return duel.opening(e);
    }

    public static boolean duelist(LivingEntity e) {
        return duel.duelist(e);
    }

    public static int stance(LivingEntity e) {
        return duel.stance(e);
    }

    public static boolean returnShot(Entity shot, Player by) {
        return duel.returnShot(shot, by);
    }

    public interface Postures {
        /** Fills a body's stance as a blow of {@code by}'s would (Arms: full, it breaks). */
        void add(LivingEntity target, int amount, Player by);
    }

    private static Postures postures = (target, amount, by) -> {};

    public static void installPostures(Postures p) {
        postures = p;
    }

    /** A blow of {@code by}'s that isn't a swing (a shot sent back) fills the body's stance. */
    public static void posture(LivingEntity target, int amount, Player by) {
        postures.add(target, amount, by);
    }
}
