package dev.awaken.core.api;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
}
