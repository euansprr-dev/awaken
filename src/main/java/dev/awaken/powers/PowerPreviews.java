package dev.awaken.powers;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.powers.ladder.PowerLadder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * What a known Power Core will do before you use it (SPEC 26.3c, 36.1): the next ladder step in its own words
 * ({@link PowerLadder#text}) and the numbers every level moves, worked out on the server by the same code a cast uses
 * ({@link Cast#dmg()}, {@link Casting#cooldownTicks}, {@link Casting#effectiveCost}). A step's own changes are told by its
 * text, never re-modelled here. Read-only: a preview never casts, spends or levels anything.
 */
public final class PowerPreviews {
    private PowerPreviews() {}
    public record Value(String name, double value, String unit) {}
    public record Change(String name, double before, double after, String unit) {
        public boolean changed() { return Math.abs(before - after) > 0.000001; }
        public String line() { return name + ": " + number(before) + unit + " → " + number(after) + unit; }
    }
    /** Installed only by powers.client; common item code never references Minecraft's client classes. */
    public static BiFunction<PowerType, Integer, List<String>> client = (t, v) -> List.of("Reading your next level…");

    /** The numbers a level moves, as a cast at {@code c}'s level would use them right now. */
    public static List<Value> values(Cast c) {
        List<Value> out = new ArrayList<>();
        String id = c.type.path;
        if (!c.type.isPassive()) {
            out.add(new Value("Energy", Casting.effectiveCost(c.player, c.type, c.variant), ""));
            out.add(new Value("Cooldown", Casting.cooldownTicks(c.player, c.type, c.variant, c.powerLevel) / 20.0, " s"));
        }
        // a defined damage field is not used by every variant (Blink, Shade, Relay, Conduit)
        boolean primary = c.type.damage > 0 && switch (id) {
            case "dash" -> !c.is(1);
            case "afterimage", "echo_swap", "overload" -> !c.is(2);
            case "cloak" -> c.is(2);
            case "hundred_palms", "storm_call" -> false;
            default -> true;
        };
        if (primary) out.add(new Value(damageName(c), c.dmg(), ""));
        return List.copyOf(out);
    }

    private static String damageName(Cast c) {
        return switch (c.type.path) {
            case "haymaker", "railgun" -> "Damage before the charge";
            case "charged_shot" -> c.is(2) ? "Damage of each bolt" : "Damage before the charge";
            case "gravity_well", "void_maw" -> "Damage each half second";
            case "beam", "prism_lance" -> "Beam damage every 0.2 s";
            case "aura" -> "Damage every 2 s";
            case "legion" -> "Damage of each soldier's strike";
            case "drone", "turret", "drone_swarm" -> "Damage of each shot";
            case "cloak" -> "Ambush damage";
            case "barrier" -> "Damage when the shield breaks";
            case "brace" -> "Damage when it ends";
            case "afterimage", "decoy" -> "Decoy burst damage";
            case "terrain_rip", "collapse" -> "Damage of each thrown block";
            case "ground_slam", "meteor_dive" -> "Damage before the fall";
            default -> "Damage";
        };
    }

    public static List<Change> changes(Cast before, Cast after) {
        List<Value> a = values(before), b = values(after);
        List<Change> out = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) out.add(new Change(a.get(i).name(), a.get(i).value(), b.get(i).value(), a.get(i).unit()));
        return out;
    }

    /** The numbers that move from one level to the next, one plain line each (nothing for a passive). */
    public static List<String> numberLines(ServerPlayer p, PowerType t, int variant, int from, int to) {
        List<String> lines = new ArrayList<>();
        if (t.isPassive()) return lines;
        Cast before = new Cast(p, t, variant, from, -1, false, p.getYRot(), p.getXRot());
        Cast after = new Cast(p, t, variant, to, -1, false, p.getYRot(), p.getXRot());
        for (Change c : changes(before, after)) if (c.changed()) lines.add(c.line());
        return lines;
    }

    /** The same numbers said as one sentence, for the Journal: "Its energy cost goes from 12 to 11 and cooldown from 8 to 7.6 seconds." */
    public static String numberSentence(ServerPlayer p, PowerType t, int variant, int from, int to) {
        if (t.isPassive()) return "";
        Cast before = new Cast(p, t, variant, from, -1, false, p.getYRot(), p.getXRot());
        Cast after = new Cast(p, t, variant, to, -1, false, p.getYRot(), p.getXRot());
        List<String> parts = new ArrayList<>();
        for (Change c : changes(before, after)) {
            if (!c.changed()) continue;
            String name = c.name().equals("Energy") ? "energy cost" : Character.toLowerCase(c.name().charAt(0)) + c.name().substring(1);
            String unit = c.unit().equals(" s") ? " seconds" : c.unit();
            parts.add(name + (parts.isEmpty() ? " goes" : "") + " from " + number(c.before()) + " to " + number(c.after()) + unit);
        }
        if (parts.isEmpty()) return "";
        String last = parts.removeLast();
        return "Its " + (parts.isEmpty() ? last : String.join(", ", parts) + " and " + last) + ".";
    }

    public static Reply preview(ServerPlayer p, PowerType t, int coreVariant) {
        Loadout.Owned owned = Powers.loadout(p).get(t.id.toString());
        if (owned == null) throw new IllegalArgumentException("Only owned powers need upgrade previews");
        int core = Math.max(0, Math.min(t.variants.size() - 1, coreVariant));
        int level = owned.level();
        List<String> lines = new ArrayList<>();
        if (level < Loadout.MAX_LEVEL) {
            lines.add("Use: level " + level + " → " + (level + 1) + " (" + t.variant(owned.variant()).name() + ")");
            if (PowerLadder.step(t.path, level + 1) != null) lines.add(PowerLadder.text(t.path, level + 1).getString());
            lines.addAll(numberLines(p, t, owned.variant(), level, level + 1));
            if (core != owned.variant()) lines.add("Sneak and use instead to change it to " + t.variant(core).name() + " and keep level " + level + ".");
        } else if (core == owned.variant()) {
            lines.add("It's at its top level. Using this core does nothing, and you keep it.");
        } else {
            lines.add("Use: change it to " + t.variant(core).name() + " and keep level " + level + ".");
            lines.add(t.variant(core).blurb());
        }
        return new Reply(t.id.toString(), core, owned.variant(), level, List.copyOf(lines));
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

    /** One decimal at most: 10.6, 5.8, 12. */
    public static String number(double n) {
        return String.format(Locale.ROOT, "%.1f", n).replaceAll("\\.0$", "");
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
