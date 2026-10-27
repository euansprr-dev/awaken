package dev.awaken.powers.fx;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One effect, as sent from the server: the recipe, who cast it, where, which way, how big, and a seed. Every client
 * builds the same shape from the same event. ~50 bytes on the wire, replacing dozens of particle packets.
 *
 * @param caster entity id of the caster, or -1
 * @param target entity id of the target, or -1
 * @param colour ARGB override; 0 = the style's own colours
 * @param flags  low 16 bits: {@link #ULT}, {@link #FOLLOW}, ...; high 16 bits: the caster's {@link CastAnim}
 */
public record FxEvent(FxKind kind, FxStyle style, int caster, int target, double x, double y, double z,
                      float dx, float dy, float dz, int colour, float scale, float aux, int seed, int ticks, int flags) {

    /** An ultimate: bigger, longer, with a screen beat. */
    public static final int ULT = 1;
    /** Attached to the caster (moves with them; a beam follows the caster's own aim on their client). */
    public static final int FOLLOW = 2;
    /** Full charge / critical: the brightest version. */
    public static final int FULL = 4;
    /** Pushes outward instead of in (vortex). */
    public static final int REPULSE = 8;
    /** Low-key: no screen feel for anyone (buff refreshes, passives). */
    public static final int QUIET = 16;
    /**
     * This event shows the power's real hit volume: {@code scale} is the radius (or a cone's range) the damage code
     * used and {@code aux} a cone's half angle in degrees. GameTests compare it with where mobs actually get hit.
     */
    public static final int REACH = 128;
    /** Variant index (0-3) in bits 5-6, for recipes with per-variant looks. */
    public static final int VARIANT_SHIFT = 5;

    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    public Vec3 vec() {
        return new Vec3(dx, dy, dz);
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public CastAnim anim() {
        return CastAnim.byId(flags >>> 16);
    }

    /** High-bit values from here on are a cast profile and its beat, not a {@link CastAnim} ordinal. */
    public static final int PROFILE_BASE = 128;

    /** The cast profile this event belongs to (CastProfiles ids start at 1), or 0. */
    public int profile() {
        int hi = flags >>> 16;
        return hi >= PROFILE_BASE ? (hi - PROFILE_BASE) >> 2 : 0;
    }

    /** The beat of the profile ({@link CastProfiles#BEGIN}, RELEASE, END). */
    public int beat() {
        int hi = flags >>> 16;
        return hi >= PROFILE_BASE ? (hi - PROFILE_BASE) & 3 : 0;
    }

    public int variant() {
        return (flags >> VARIANT_SHIFT) & 3;
    }

    public static Builder of(FxKind kind, FxStyle style, Vec3 at) {
        return new Builder(kind, style, at);
    }

    /** Fluent builder for the server side; each call returns the same builder. */
    public static final class Builder {
        private final FxKind kind;
        private FxStyle style;
        private final double x, y, z;
        private int caster = -1, target = -1, colour, seed, ticks, flags;
        private float dx, dy, dz, scale = 1, aux;

        Builder(FxKind kind, FxStyle style, Vec3 at) {
            this.kind = kind;
            this.style = style;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        public Builder style(FxStyle s) {
            this.style = s;
            return this;
        }

        public Builder caster(@Nullable Entity e) {
            this.caster = e == null ? -1 : e.getId();
            return this;
        }

        public Builder target(@Nullable Entity e) {
            this.target = e == null ? -1 : e.getId();
            return this;
        }

        public Builder dir(Vec3 d) {
            this.dx = (float) d.x;
            this.dy = (float) d.y;
            this.dz = (float) d.z;
            return this;
        }

        /** Sets d to (to - origin): beams, lines, blinks. */
        public Builder to(Vec3 to) {
            this.dx = (float) (to.x - x);
            this.dy = (float) (to.y - y);
            this.dz = (float) (to.z - z);
            return this;
        }

        public Builder colour(int argb) {
            this.colour = argb;
            return this;
        }

        public Builder scale(double s) {
            this.scale = (float) s;
            return this;
        }

        public Builder aux(double a) {
            this.aux = (float) a;
            return this;
        }

        public Builder seed(int s) {
            this.seed = s;
            return this;
        }

        public Builder ticks(int t) {
            this.ticks = t;
            return this;
        }

        public Builder flag(int f) {
            this.flags |= f;
            return this;
        }

        public Builder flag(int f, boolean on) {
            if (on) this.flags |= f;
            return this;
        }

        public Builder variant(int v) {
            this.flags = (flags & ~(3 << VARIANT_SHIFT)) | ((Math.max(0, Math.min(3, v)) & 3) << VARIANT_SHIFT);
            return this;
        }

        public Builder anim(CastAnim a) {
            this.flags = (flags & 0xFFFF) | (a.ordinal() << 16);
            return this;
        }

        /** Marks the event as a beat of a cast profile (replaces any pose). */
        public Builder profile(int id, int beat) {
            this.flags = (flags & 0xFFFF) | ((PROFILE_BASE + (id << 2 | beat)) << 16);
            return this;
        }

        public FxEvent build() {
            return new FxEvent(kind, style, caster, target, x, y, z, dx, dy, dz, colour, scale, aux, seed, ticks, flags);
        }
    }
}
