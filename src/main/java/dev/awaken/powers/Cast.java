package dev.awaken.powers;

import dev.awaken.core.Forces;
import dev.awaken.progression.Progression;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everything a power behaviour needs for one cast: who, where, which variant and level, and the scaled numbers.
 * Aim uses the rotation the client sent with the key press, so fast flicks land where the player was looking.
 */
public final class Cast {
    public final ServerPlayer player;
    public final ServerLevel level;
    public final PowerType type;
    public final int variant;
    public final int powerLevel;
    public final PowerType.Variant v;
    public final int slot;
    /** True when the client already applied this power's motion (movement prediction). */
    public final boolean predicted;
    /** An FX event carrying this cast's profile beat has been sent (Casting adds a pose beat if a power sent none). */
    public boolean poseSent;
    /** What the power's ladder adds at the level this cast started at (SPEC 34.3). */
    public final dev.awaken.powers.ladder.Kit kit;
    /** Rider bookkeeping (powers.ladder.Riders): the tick of the last throttled rider and chain hits this tick. */
    public long riderTick = Long.MIN_VALUE, chainTick = Long.MIN_VALUE;
    public int chainCount;
    /** A hit refund already paid this cast. */
    public boolean refunded;
    /** A second press this cast opened (SPEC 34.3, "press again"): what it does and for how many ticks. */
    @Nullable Runnable followUp;
    int followUpTicks;
    private final float yRot;
    private final float xRot;
    private final float strafe;
    private final float forward;

    /** A cast that reads the movement keys the player last sent (commands, tests, fake players). */
    public Cast(ServerPlayer player, PowerType type, int variant, int powerLevel, int slot, boolean predicted, float yRot, float xRot) {
        this(player, type, variant, powerLevel, slot, predicted, yRot, xRot, strafeOf(player.getLastClientInput()), forwardOf(player.getLastClientInput()));
    }

    public Cast(ServerPlayer player, PowerType type, int variant, int powerLevel, int slot, boolean predicted, float yRot, float xRot,
                float strafe, float forward) {
        this.player = player;
        this.level = player.level();
        this.type = type;
        this.variant = Math.max(0, Math.min(type.variants.size() - 1, variant));
        this.powerLevel = Math.max(1, powerLevel);
        this.v = type.variant(this.variant);
        this.kit = dev.awaken.powers.ladder.PowerLadder.kit(type, this.powerLevel);
        this.slot = slot;
        this.predicted = predicted;
        this.yRot = yRot;
        this.xRot = xRot;
        this.strafe = strafe;
        this.forward = forward;
    }

    /** +1 left, -1 right, 0 neither or both. */
    public static float strafeOf(net.minecraft.world.entity.player.Input in) {
        return in.left() == in.right() ? 0f : in.left() ? 1f : -1f;
    }

    /** +1 forward, -1 back, 0 neither or both. */
    public static float forwardOf(net.minecraft.world.entity.player.Input in) {
        return in.forward() == in.backward() ? 0f : in.forward() ? 1f : -1f;
    }

    /** A cast with the player's current rotation (passives, tests, commands). */
    public static Cast of(ServerPlayer player, PowerType type) {
        Loadout.Owned o = Powers.loadout(player).get(type.id.toString());
        return new Cast(player, type, o == null ? 0 : o.variant(), o == null ? 1 : o.level(), -1, false, player.getYRot(), player.getXRot());
    }

    // ---- aim --------------------------------------------------------------------------------------------------------

    public Vec3 eye() {
        return player.getEyePosition();
    }

    public Vec3 look() {
        float f = xRot * Mth.DEG_TO_RAD;
        float g = -yRot * Mth.DEG_TO_RAD;
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    /** The movement keys held when the power key went down: +1 left / -1 right. Dash steers by them. */
    public float strafe() {
        return strafe;
    }

    /** The movement keys held when the power key went down: +1 forward / -1 back. */
    public float forward() {
        return forward;
    }

    /** The look turned by the held keys: the way a dash goes (the look itself with none, or forward alone). */
    public Vec3 steered() {
        return Moves.steer(look(), strafe, forward);
    }

    public Vec3 flatLook() {
        Vec3 l = look();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        return flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    public float yRot() {
        return yRot;
    }

    public float xRot() {
        return xRot;
    }

    public Vec3 pos() {
        return player.position();
    }

    public long now() {
        return level.getServer().overworld().getGameTime();
    }

    // ---- numbers ----------------------------------------------------------------------------------------------------

    public int stat() {
        return Progression.stat(player, type.scaling);
    }

    /** Scaled damage: base x level x stat x power level x variant x temporary buffs. */
    public float dmg(float base) {
        float d = PowerMath.damage(base, Progression.profile(player).level(), stat(), powerLevel, v.dmg());
        return d * PowerState.of(player).damageBuff(now());
    }

    /** The power's own base damage, scaled. */
    public float dmg() {
        return dmg(type.damage);
    }

    public double radius(double base) {
        return base * v.radius() * kit.radius;
    }

    /** A reach or travel distance, grown by the ladder's extend steps. */
    public double range(double base) {
        return base * kit.range;
    }

    public int ticks(int base) {
        return Math.max(1, Math.round(base * v.duration() * (1 + 0.05f * (powerLevel - 1)) * kit.duration));
    }

    /** Ticks to a full charge, shortened by the ladder's charge steps. */
    public int chargeTicks(int base) {
        return Math.max(1, Math.round(base * kit.charge));
    }

    /**
     * Opens a second press: for {@code ticks} the power's key runs {@code then} instead of casting again, free, and the
     * cooldown starts when it is used or the window closes (Shadow Step's Return, Afterimage's swap, Titanfall's plunge).
     */
    public void followUp(int ticks, Runnable then) {
        this.followUp = then;
        this.followUpTicks = Math.max(1, ticks);
    }

    /** True from ladder level {@code n} up: a step's own code (capstones, steps no rider fits). */
    public boolean step(int n) {
        return powerLevel >= n;
    }

    /** A number of step {@code level}'s own code, from the ladder table; {@code def} below that level (so it reads as "off"). */
    public float num(int level, String key, float def) {
        return powerLevel >= level ? dev.awaken.powers.ladder.PowerLadder.num(type.path, level, key, def) : def;
    }

    public Forces.@Nullable Force force() {
        return type.primaryForce();
    }

    public boolean is(int variantIndex) {
        return variant == variantIndex;
    }

    // ---- effects ----------------------------------------------------------------------------------------------------

    public boolean hurt(LivingEntity target, float amount) {
        return Combat.hurt(this, target, amount, force());
    }

    public boolean hurt(LivingEntity target, float amount, Forces.@Nullable Force force) {
        return Combat.hurt(this, target, amount, force);
    }

    public void sound(Holder<SoundEvent> sound, float volume, float pitch) {
        sound(player.position(), sound, volume, pitch);
    }

    /**
     * A power with a cast profile plays its sounds on its release frame (an FX event, played by each client); the rest play now,
     * on the server, as before.
     */
    public void sound(Vec3 at, Holder<SoundEvent> sound, float volume, float pitch) {
        if (dev.awaken.powers.fx.CastProfiles.byPower(type.path) != null) Fx.sound(this, at, sound, volume, pitch);
        else level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public <T extends ParticleOptions> void particles(T p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** Hit-stop and camera kick for the caster (client respects the camera shake setting). */
    public void feel(@Nullable LivingEntity hit, int hitstopMs, float shake) {
        Fx.feel(player, hit, hitstopMs, shake);
    }
}
