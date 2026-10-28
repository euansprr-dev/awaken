package dev.awaken.powers;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/** Read-only server snapshots. Neither preview requests nor tooltip rendering run a power or consume a core. */
public final class PowerPreviews {
    private PowerPreviews() {}
    public record Value(String name, double value, String unit) {}
    public record Change(String name, double before, double after, String unit) {
        public boolean changed() { return Math.abs(before - after) > 0.000001; }
        public String line() {
            return name + ": " + number(before) + (changed() ? " → " + number(after) : " (unchanged)") + unit;
        }
    }
    /** Installed only by powers.client; common item code never references Minecraft's client classes. */
    public static BiFunction<PowerType, Integer, List<String>> client = (t, v) -> List.of("Waiting for server upgrade details…");

    public static List<Value> values(Cast c) {
        List<Value> out = new ArrayList<>();
        String id = c.type.path;
        if (!c.type.isPassive()) {
            value(out, "Energy per use", Casting.effectiveCost(c.player, c.type, c.variant), "");
            value(out, "Cooldown", Casting.cooldownTicks(c.player, c.type, c.variant, c.powerLevel) / 20.0, " s");
            if (c.type.drain > 0) {
                double drain = Casting.drainPerTick(c.player, c.type, c.variant) * 20;
                if (id.equals("flight")) drain += c.type.drain * c.v.cost() * (PowerNumbers.flightPressure(c) - 1);
                value(out, "Energy per second", drain, "");
            }
        }
        int duration = PowerNumbers.effectTicks(c);
        if (duration > 0) value(out, id.equals("overload") ? "Afterglow duration" : "Effect duration", duration / 20.0, " s");
        if (id.equals("dominate")) value(out, "Against elites", (duration / 2) / 20.0, " s");
        // A defined damage field is not necessarily used by every variant (Blink, Shade, Relay, Conduit).
        boolean primary = c.type.damage > 0 && switch (id) {
            case "dash" -> !c.is(1);
            case "afterimage", "echo_swap", "overload" -> !c.is(2);
            case "cloak" -> c.is(2);
            case "hundred_palms", "storm_call" -> false;
            default -> true;
        };
        if (primary) value(out, damageName(c), c.dmg(), " damage");
        switch (id) {
            case "dash" -> {
                if (c.is(1)) value(out, "Blink reach", PowerNumbers.blinkRange(c.powerLevel), " blocks");
                else {
                    value(out, "Ground dash speed", PowerNumbers.dashSpeed(c.variant, c.powerLevel, true), " blocks/tick");
                    value(out, "Air dash speed", PowerNumbers.dashSpeed(c.variant, c.powerLevel, false), " blocks/tick");
                    value(out, "Air lift with current aim", PowerNumbers.dashLift(c.variant, c.powerLevel, false, c.player.getLookAngle().y), " blocks/tick");
                }
            }
            case "flight" -> value(out, "Flight speed setting", PowerNumbers.flightSpeed(c), "");
            case "barrier" -> value(out, "Shield capacity", PowerNumbers.shield(c), " health");
            case "mend" -> {
                float heal = PowerNumbers.mend(c);
                value(out, "Healing available", heal, " health");
                value(out, "Self heal at current health", Math.min(heal, Math.max(0, c.player.getMaxHealth() - c.player.getHealth())), " health");
                if (!c.is(1)) value(out, "Healing per nearby ally", heal * (c.is(2) ? 1 : 0.5f), " health");
            }
            case "haymaker", "railgun" -> value(out, "Fully charged hit", PowerNumbers.chargedDamage(c, 1), " damage");
            case "charged_shot" -> {
                if (!c.is(2)) value(out, "Fully charged hit", PowerNumbers.chargedDamage(c, 1), " damage");
            }
            case "ground_slam", "meteor_dive" -> value(out, "Maximum falling hit", PowerNumbers.fallDamage(c, PowerNumbers.fallBonus(100)), " damage");
            case "gravity_well" -> {
                if (c.is(1)) hit(out, c, "Final implosion", PowerNumbers.Hit.WELL_END);
                if (!c.is(2)) {
                    hit(out, c, "Colliding volatile foes", PowerNumbers.Hit.VOLATILE);
                    hit(out, c, "Thrown wall fragment", PowerNumbers.Hit.WALL_FRAGMENT);
                }
            }
            case "telekinesis" -> {
                if (c.is(1)) hit(out, c, "Crush pulse each 0.5 s", PowerNumbers.Hit.CRUSH_PULSE);
                hit(out, c, "Throw splash", PowerNumbers.Hit.THROW_SPLASH);
                hit(out, c, "Thrown construct", PowerNumbers.Hit.WALL_RAM);
            }
            case "beam" -> { if (c.is(1)) hit(out, c, "Side beam each 0.2 s", PowerNumbers.Hit.BEAM_SIDE); }
            case "prism_lance" -> { if (!c.is(2)) hit(out, c, "Fork each 0.2 s", PowerNumbers.Hit.PRISM_FORK); }
            case "thunderclap" -> { if (c.is(2)) hit(out, c, "Forked bolt", PowerNumbers.Hit.THUNDER_FORK); }
            case "lightning_step" -> { if (c.is(1)) hit(out, c, "Forked bolt", PowerNumbers.Hit.LIGHTNING_FORK); }
            case "spirit_palm" -> { if (!c.is(2)) hit(out, c, "Echo after 1 s", PowerNumbers.Hit.PALM_ECHO); }
            case "hundred_palms" -> {
                boolean bare = c.player.getMainHandItem().isEmpty();
                value(out, bare ? "Bare-hand palm" : "Palm while holding an item", PowerNumbers.palmFlurry(c, bare, false), " damage");
                value(out, "Final palm", PowerNumbers.palmFlurry(c, bare, true), " damage");
            }
            case "storm_call" -> {
                value(out, "Strike on a dry target", PowerNumbers.stormStrike(c, false), " damage");
                value(out, "Strike on a target in rain", PowerNumbers.stormStrike(c, true), " damage");
            }
            default -> { }
        }
        if (!c.type.isPassive() && c.type.family == PowerType.Family.KINETIC)
            value(out, "If cast mid-slide: shockwave", c.dmg(MoveTwists.PUNCH_DAMAGE), " damage");
        return List.copyOf(out);
    }
    private static String damageName(Cast c) {
        return switch (c.type.path) {
            case "haymaker", "railgun", "charged_shot" -> c.type.path.equals("charged_shot") && c.is(2) ? "Each of three bolts" : "Hit before charge bonus";
            case "gravity_well", "void_maw" -> "Pulse each 0.5 s";
            case "beam", "prism_lance" -> "Main beam each 0.2 s";
            case "aura" -> "Pulse each 2 s";
            case "legion" -> "Each soldier's strike";
            case "drone", "turret", "drone_swarm" -> "Each shot";
            case "cloak" -> "Ambush burst";
            case "barrier" -> "Shield-break burst";
            case "brace" -> "Burst on expiry";
            case "afterimage", "decoy" -> "Decoy burst";
            case "terrain_rip", "collapse" -> "Each thrown block";
            case "ground_slam", "meteor_dive" -> "Hit before falling bonus";
            default -> "Hit";
        };
    }
    private static void hit(List<Value> out, Cast c, String name, PowerNumbers.Hit hit) { value(out, name, PowerNumbers.hit(c, hit), " damage"); }
    private static void value(List<Value> out, String name, double v, String unit) { out.add(new Value(name, v, unit)); }
    public static List<Change> changes(Cast before, Cast after) {
        List<Value> a = values(before), b = values(after);
        List<Change> out = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            Value x = a.get(i), y = b.get(i);
            if (!x.name().equals(y.name())) throw new IllegalArgumentException("Upgrade must preserve variant and context");
            out.add(new Change(x.name(), x.value(), y.value(), x.unit()));
        }
        return List.copyOf(out);
    }
    public static List<String> upgradeLines(ServerPlayer p, PowerType t, int variant, int from, int to) {
        Cast before = new Cast(p, t, variant, from, -1, false, p.getYRot(), p.getXRot());
        Cast after = new Cast(p, t, variant, to, -1, false, p.getYRot(), p.getXRot());
        List<Change> changes = changes(before, after);
        List<String> lines = new ArrayList<>();
        for (Change change : changes) if (change.changed()) lines.add(change.line());
        if (lines.isEmpty()) lines.add("No gameplay numbers change at this level.");
        for (Change change : changes) if (!change.changed() && (change.name().startsWith("Energy") || change.name().equals("Cooldown") || change.name().equals("Self heal at current health"))) lines.add(change.line());
        lines.add(t.isPassive() ? "This passive stays active automatically." : "Other ranges, target limits and effects stay unchanged.");
        if (changes.stream().anyMatch(v -> v.unit().equals(" damage"))) {
            lines.add("Damage shown is before target defences and on-hit effects.");
            lines.add("Direct power hits cap at 20% of boss max health before hit bonuses; reactions use their own rules.");
            PowerState state = PowerState.of(p);
            long now = Casting.now(p);
            double bonus = p.getHealth() > p.getMaxHealth() / 2 && Progression.profile(p).awakened()
                    ? Progression.perk(p, Perks.DAMAGE_ABOVE_HALF_HP) : 0;
            if (bonus > 0) lines.add("Current high-health bonus after that cap: +" + number(bonus * 100) + "%.");
            if (state.has(PowerState.Buffs.READ_INTENT, now)) lines.add("Read Intent currently adds 15% against hostile enemies after that cap.");
            if (state.has(PowerState.Buffs.BACKSTAB, now)) lines.add("Your primed next hit currently multiplies by " + number(state.buff(PowerState.Buffs.BACKSTAB, now)) + ".");
            if (state.has(PowerState.Buffs.CLOAK, now)) lines.add("Your first hit from the current cloak multiplies by 1.8.");
        }
        if (t.path.equals("dash") && variant != 1) lines.add("An aligned dash out of a slide multiplies horizontal speed by "
                + number(dev.awaken.core.api.MovementHooks.DASH_OUT_STRETCH) + "; terrain and drag affect distance.");
        if (t.path.equals("mend")) lines.add("Healing uses your missing health now; each recipient is capped at their own maximum health.");
        return List.copyOf(lines);
    }
    public static Reply preview(ServerPlayer p, PowerType t, int coreVariant) {
        Loadout.Owned owned = Powers.loadout(p).get(t.id.toString());
        if (owned == null) throw new IllegalArgumentException("Only owned powers need upgrade previews");
        int core = Math.max(0, Math.min(t.variants.size() - 1, coreVariant));
        List<String> lines = new ArrayList<>();
        if (owned.level() < Loadout.MAX_LEVEL) {
            lines.add("Use: level " + owned.level() + " → " + (owned.level() + 1) + " · " + t.variant(owned.variant()).name());
            lines.addAll(upgradeLines(p, t, owned.variant(), owned.level(), owned.level() + 1));
            if (core != owned.variant()) lines.add("Crouch-use instead: keep level " + owned.level() + " and swap to " + t.variant(core).name() + ".");
        } else if (core == owned.variant()) {
            lines.add("Maximum level. This core has no effect and is kept.");
        } else {
            lines.add("Maximum level. Use swaps to " + t.variant(core).name() + " at the same level.");
            lines.add(t.variant(core).blurb());
            lines.add("This is a behaviour change, not another level.");
        }
        return new Reply(t.id.toString(), core, owned.variant(), owned.level(), List.copyOf(lines));
    }
    static void request(ServerPlayer p, Request request) {
        PowerState state = PowerState.of(p);
        long now = Casting.now(p);
        if (state.previewAt != Long.MIN_VALUE && now >= state.previewAt && now - state.previewAt < 5) return;
        state.previewAt = now;
        PowerType t = PowerRegistry.get(request.power());
        if (t == null || !Powers.owns(p, t.id.toString()) || request.variant() < 0 || request.variant() >= t.variants.size()) return;
        AwakenNet.send(p, preview(p, t, request.variant()));
    }
    public static String number(double n) {
        return String.format(Locale.ROOT, "%.3f", n).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    public record Request(String power, int variant) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Awaken.id("power_preview_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(160), Request::power, ByteBufCodecs.VAR_INT, Request::variant, Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Reply(String power, int coreVariant, int ownedVariant, int level, List<String> lines) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(Awaken.id("power_preview"));
        public static final StreamCodec<ByteBuf, Reply> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(160), Reply::power, ByteBufCodecs.VAR_INT, Reply::coreVariant,
                ByteBufCodecs.VAR_INT, Reply::ownedVariant, ByteBufCodecs.VAR_INT, Reply::level,
                ByteBufCodecs.stringUtf8(320).apply(ByteBufCodecs.list(32)), Reply::lines, Reply::new);
        @Override public Type<Reply> type() { return TYPE; }
    }
}
