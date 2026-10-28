package dev.awaken.powers;

import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;

/** The runtime numbers whose power-level changes must also be shown before consuming a core. */
public final class PowerNumbers {
    private PowerNumbers() {}
    public static int effectTicks(Cast c) {
        int base = switch (c.type.path) {
            case "dominate", "third_eye", "bulwark", "twin_strike" -> 160;
            case "read_intent", "barrier", "nanite_skin" -> 200;
            case "decoy", "cloak" -> 120;
            case "foresight", "brace" -> 60;
            case "legion", "drone", "drone_swarm" -> 400;
            case "hover" -> 100;
            case "gravity_well", "void_maw", "afterimage" -> 80;
            case "turret" -> 600;
            case "overload" -> c.is(2) ? 300 : c.is(0) ? 160 : 0;
            default -> 0;
        };
        if (base == 0) return 0;
        int ticks = c.ticks(base);
        return c.type.path.equals("dominate") ? ticks + Progression.stat(c.player, Stat.PER) * 2 : ticks;
    }
    public static double dashSpeed(int variant, int level, boolean ground) {
        return (variant == 2 ? 1.45 : 1.9) * (1 + 0.04 * (level - 1)) * (ground ? 1.0 : 0.8);
    }
    public static double dashLift(int variant, int level, boolean ground, double aimY) {
        double speed = dashSpeed(variant, level, ground);
        return Math.min(0.9, ground ? 0.12 : Math.max(0.28, aimY * speed * 0.5));
    }
    public static double blinkRange(int level) { return 6 + level * 0.5; }
    public static float flightSpeed(Cast c) {
        return (float) (0.05 * (c.is(1) ? 1.4 : c.is(2) ? 0.85 : 1.0) * (1 + 0.04 * (c.powerLevel - 1)));
    }
    public static float shield(Cast c) {
        return (float) ((6 + 0.25 * Progression.stat(c.player, Stat.INT)) * c.v.dmg() * (1 + 0.15 * (c.powerLevel - 1)));
    }
    public static float mend(Cast c) {
        float missing = c.player.getMaxHealth() - c.player.getHealth();
        return (float) ((4 + 0.2 * Progression.stat(c.player, Stat.SPI) + 0.2 * missing) * c.v.dmg() * (1 + 0.12 * (c.powerLevel - 1)));
    }
    public static float chargedDamage(Cast c, double charge) {
        float bonus = switch (c.type.path) { case "haymaker" -> 9; case "railgun" -> 10; case "charged_shot" -> 14; default -> 0; };
        return c.dmg(c.type.damage + bonus * (float) charge);
    }
    public static double fallBonus(double fallen) { return Math.min(14, Math.max(0, fallen) * 0.6); }
    public static float fallDamage(Cast c, double bonus) { return c.dmg(c.type.damage + (float) bonus); }
    public static double flightPressure(Cast c) {
        var dim = c.level.dimension().identifier();
        if (!dim.getNamespace().equals("awaken")) return 1.0;
        if (dim.getPath().startsWith("stratum") || dim.getPath().equals("endless")) return 2.0;
        return dim.getPath().equals("gates") ? 1.5 : 1.0;
    }
    /** Named secondary hits share these values with the preview, rather than copying constants into tooltips. */
    public enum Hit { WELL_END, VOLATILE, WALL_FRAGMENT, CRUSH_PULSE, PRISM_FORK, BEAM_SIDE, THUNDER_FORK, LIGHTNING_FORK, THROW_SPLASH, WALL_RAM, PALM_ECHO }
    public static float secondary(float primary, Hit hit) {
        return primary * switch (hit) {
            case LIGHTNING_FORK, WALL_RAM -> 0.6f;
            case THROW_SPLASH -> 0.5f;
            case BEAM_SIDE -> 0.55f;
            case THUNDER_FORK -> 0.3f;
            default -> throw new IllegalArgumentException("Not a primary-damage derivative");
        };
    }
    public static float hit(Cast c, Hit hit) {
        return switch (hit) {
            case WELL_END -> c.dmg(8);
            case VOLATILE -> c.dmg(10);
            case WALL_FRAGMENT -> c.dmg(4);
            case CRUSH_PULSE -> c.dmg(1.5f);
            case PRISM_FORK -> c.dmg(3);
            case BEAM_SIDE -> secondary(c.dmg(), hit);
            case THUNDER_FORK -> secondary(c.dmg(), hit);
            case LIGHTNING_FORK -> secondary(c.dmg(), hit);
            case THROW_SPLASH -> secondary(c.dmg(), hit);
            case WALL_RAM -> secondary(c.dmg(), hit);
            case PALM_ECHO -> c.dmg() * (c.is(1) ? 1.0f : 0.5f);
        };
    }
    public static float palmFlurry(Cast c, boolean bare, boolean last) { return c.dmg() * (bare ? 1.3f : 1f) * (last ? 3f : 1f); }
    public static float stormStrike(Cast c, boolean rain) { return c.dmg() * (rain ? 1.5f : 1f); }
}
