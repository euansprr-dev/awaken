package dev.awaken.powers;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One power: its identity, numbers and behaviour. Every power lives in {@link PowerRegistry}; the catalog with all
 * numbers is {@link PowerCatalog} (and docs/modules/powers.md).
 */
public final class PowerType {

    /** Power families. The family decides the icon colour, the scaling stat and the {@code power_uses/<family>} counter. */
    public enum Family {
        KINETIC("Kinetic", 0xE08F1F, Stat.STR),
        VELOCITY("Velocity", 0xFF4A3D, Stat.AGI),
        FLIGHT("Flight & Gravity", 0xA7CDE3, Stat.INT),
        ENERGY("Energy", 0x7FC3FF, Stat.INT),
        SHADOW("Shadow", 0x8F6BFF, Stat.SPI),
        MIND("Mind", 0xD088C2, Stat.PER),
        ARCHITECT("Architect", 0xD9BA80, Stat.INT),
        TECH("Tech", 0x47C6B0, Stat.INT),
        SPIRIT("Spirit", 0x86E0A0, Stat.SPI);

        public final String label;
        public final int colour;
        public final Stat stat;

        Family(String label, int colour, Stat stat) {
            this.label = label;
            this.colour = colour;
            this.stat = stat;
        }

        public String counter() {
            return "awaken:power_uses/" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Which key a power can sit on. MOVEMENT powers may also sit on the four active slots. */
    public enum Slot { ACTIVE, ULTIMATE, MOVEMENT, PASSIVE }

    /** INSTANT: one press. CHANNEL: hold the key (drains energy, ends on release). TOGGLE: press on, press off. */
    public enum Mode { INSTANT, CHANNEL, TOGGLE, PASSIVE }

    /**
     * A rolled variation of a power. Multipliers apply to damage, energy cost, cooldown, radius/range and duration.
     * Index 0 is always the standard form; behaviours read {@code c.variant} for special forms.
     */
    public record Variant(String name, String blurb, float dmg, float cost, float cooldown, float radius, float duration) {
        public static Variant standard() {
            return new Variant("Standard", "", 1, 1, 1, 1, 1);
        }

        public static Variant of(String name, String blurb) {
            return new Variant(name, blurb, 1, 1, 1, 1, 1);
        }

        public Variant dmg(float m) { return new Variant(name, blurb, m, cost, cooldown, radius, duration); }
        public Variant cost(float m) { return new Variant(name, blurb, dmg, m, cooldown, radius, duration); }
        public Variant cd(float m) { return new Variant(name, blurb, dmg, cost, m, radius, duration); }
        public Variant radius(float m) { return new Variant(name, blurb, dmg, cost, cooldown, m, duration); }
        public Variant dur(float m) { return new Variant(name, blurb, dmg, cost, cooldown, radius, m); }
    }

    /** Server behaviour of a cast. Return {@link Result#OK} to spend energy and start the cooldown. */
    public interface Behaviour {
        Result cast(Cast c);
    }

    /** Passive behaviour, run every {@link #PASSIVE_PERIOD} ticks for every owner. */
    public interface Passive {
        void tick(Cast c);
    }

    public static final int PASSIVE_PERIOD = 5;

    /**
     * Client-predicted movement: the motion a movement power gives. Common code (pure maths on the player's look),
     * used by the client to move instantly and by the server for players without prediction (fake players, commands).
     * {@code strafe} (+1 left, -1 right) and {@code forward} (+1 forward, -1 back) are the movement keys held at the press;
     * a power that goes the way you move (Dash) steers by them, the rest ignore them.
     */
    public interface Predictor {
        @Nullable Vec3 motion(Player player, int variant, int level, float strafe, float forward);
    }

    public record Result(boolean ok, String reason) {
        public static final Result OK = new Result(true, "");

        public static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    public final Identifier id;
    public final String path;
    public final String name;
    public final Family family;
    public final Set<Forces.Force> forces;
    public final Slot slot;
    public final Mode mode;
    public final float cost;
    /** Energy per second while channelled or toggled on. */
    public final float drain;
    public final int cooldown;
    /** Max channel/toggle length in ticks (0 = unlimited for toggles). */
    public final int maxDuration;
    public final float damage;
    public final Stat scaling;
    public final String description;
    public final Rank unlock;
    /** Hidden powers (path signatures, latents) never roll from Power Cores. */
    public final boolean hidden;
    public final List<Variant> variants;
    public final @Nullable Behaviour behaviour;
    public final @Nullable Passive passive;
    public final @Nullable Predictor predictor;
    /** Counts toward {@code awaken:dashes} and airborne dash chains. */
    public final boolean dashLike;
    /** Icon shape key used by the art spec (tools/art/specs/powers.py). */
    public final String icon;

    private PowerType(Builder b) {
        this.id = Awaken.id(b.path);
        this.path = b.path;
        this.name = b.name;
        this.family = b.family;
        this.forces = b.forces.isEmpty() ? Set.of() : Set.copyOf(b.forces);
        this.slot = b.slot;
        this.mode = b.slot == Slot.PASSIVE ? Mode.PASSIVE : b.mode;
        this.cost = b.cost;
        this.drain = b.drain;
        this.cooldown = b.cooldown;
        this.maxDuration = b.maxDuration;
        this.damage = b.damage;
        this.scaling = b.scaling == null ? b.family.stat : b.scaling;
        this.description = b.description;
        this.unlock = b.unlock;
        this.hidden = b.hidden;
        List<Variant> v = new ArrayList<>();
        v.add(Variant.standard());
        v.addAll(b.variants);
        this.variants = List.copyOf(v);
        this.behaviour = b.behaviour;
        this.passive = b.passive;
        this.predictor = b.predictor;
        this.dashLike = b.dashLike;
        this.icon = b.icon;
    }

    public Variant variant(int index) {
        return variants.get(Math.max(0, Math.min(variants.size() - 1, index)));
    }

    public boolean isPassive() {
        return slot == Slot.PASSIVE;
    }

    /** The primary force, used for this power's damage type (KINETIC damage when the power has none). */
    public Forces.@Nullable Force primaryForce() {
        return forces.isEmpty() ? null : forces.iterator().next();
    }

    /** Whether this power may sit on the given slot index (0-3 active, 4 ultimate, 5 movement). */
    public boolean fitsSlot(int index) {
        return switch (slot) {
            case ACTIVE -> index >= 0 && index < 4;
            case MOVEMENT -> index == 5 || (index >= 0 && index < 4);
            case ULTIMATE -> index == 4;
            case PASSIVE -> false;
        };
    }

    public static Builder builder(String path, String name, Family family, Slot slot) {
        return new Builder(path, name, family, slot);
    }

    public static final class Builder {
        private final String path;
        private final String name;
        private final Family family;
        private final Slot slot;
        private final EnumSet<Forces.Force> forces = EnumSet.noneOf(Forces.Force.class);
        private Mode mode = Mode.INSTANT;
        private float cost;
        private float drain;
        private int cooldown = 20;
        private int maxDuration;
        private float damage;
        private @Nullable Stat scaling;
        private String description = "";
        private Rank unlock = Rank.E;
        private boolean hidden;
        private final List<Variant> variants = new ArrayList<>();
        private @Nullable Behaviour behaviour;
        private @Nullable Passive passive;
        private @Nullable Predictor predictor;
        private boolean dashLike;
        private String icon = "star";

        private Builder(String path, String name, Family family, Slot slot) {
            this.path = path;
            this.name = name;
            this.family = family;
            this.slot = slot;
        }

        public Builder forces(Forces.Force... f) { forces.addAll(List.of(f)); return this; }
        public Builder channel(float drainPerSecond, int maxTicks) { mode = Mode.CHANNEL; drain = drainPerSecond; maxDuration = maxTicks; return this; }
        public Builder toggle(float drainPerSecond, int maxTicks) { mode = Mode.TOGGLE; drain = drainPerSecond; maxDuration = maxTicks; return this; }
        public Builder cost(float c) { cost = c; return this; }
        public Builder cooldown(int ticks) { cooldown = ticks; return this; }
        public Builder damage(float d) { damage = d; return this; }
        public Builder scaling(Stat s) { scaling = s; return this; }
        public Builder desc(String d) { description = d; return this; }
        public Builder unlock(Rank r) { unlock = r; return this; }
        public Builder hidden() { hidden = true; return this; }
        public Builder variant(Variant v) { variants.add(v); return this; }
        public Builder cast(Behaviour b) { behaviour = b; return this; }
        public Builder passive(Passive p) { passive = p; return this; }
        public Builder predict(Predictor p) { predictor = p; return this; }
        public Builder dash() { dashLike = true; return this; }
        public Builder icon(String i) { icon = i; return this; }

        public PowerType register() {
            PowerType t = new PowerType(this);
            PowerRegistry.register(t);
            return t;
        }
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
