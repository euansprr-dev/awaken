package dev.awaken.powers;

import dev.awaken.core.Forces;
import dev.awaken.core.api.FightMoments;
import dev.awaken.core.api.NoticeEvents;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.progression.Progression;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Arms Duets (SPEC §50, "Arms Duets"; Euan, 10 Nov 2026: "it really feels like you're actually doing combos between the
 * weapon and then the power"). One power and one weapon style make something neither does alone. Nothing is timed: a cast
 * opens a few seconds in which the blade's own moments (a hit, a finisher, a deflect, a perfect heavy, told through
 * {@code core.api.FightMoments}) answer it, or the power itself answers the weapon in your hand as it lands. They are found
 * by trying things; the System names each the first time, and the Journal keeps them.
 * <p>
 * All of it is the server's. Damage goes through {@link Combat} like any power's, under the same caps; nothing here touches a
 * player, and nothing reads the weapons' code: the style is the held item's own name.
 */
public final class ArmsDuets {
    private ArmsDuets() {}

    public enum Duet {
        BOTH_BARRELS("Both Barrels", "haymaker", "gauntlets", "The charge fills both fists, and the let-go is a one-two.",
                "Land a Haymaker with Gauntlets on. A second blow follows from the other fist for 60% of the first."),
        CHAIN_STEP("Chain Step", "shadow_step", "daggers", "A stab that kills or finishes makes your next Step free.",
                "Within 2 seconds of a Shadow Step, a Daggers kill or finisher brings Shadow Step straight back."),
        ICEBREAKER("Icebreaker", "frost_nova", "maul", "A heavy blow on frozen ground sends the ice out as a row of spikes.",
                "For 6 seconds after a Frost Nova, a Maul finisher or perfect heavy on its ground throws spikes 7 blocks ahead of you."),
        REAP("Reap", "gravity_well", "scythe", "A sweep through your Well drags everything in it along the blade.",
                "While your Gravity Well stands, a Scythe hit on anything inside it cuts everything else inside it too."),
        STILL_CUT("Still Cut", "flow", "katana", "Cuts you make in slowed time hang in the air and all land when time comes back.",
                "During Flow, every Katana hit leaves a cut on its target, six at most. They all land when Flow ends."),
        MIRROR_GUARD("Mirror Guard", "barrier", "shieldblade", "The plate sits on your shield. A clean block throws it back.",
                "While Barrier is up, a Shieldblade deflect or guard throws a blast at whatever struck you."),
        CROSSING("Crossing", "lightning_step", "twinblades", "Step through something with both blades out and it's cut on the way in and the way out.",
                "Lightning Step with Twin Blades drawn cuts everything on the line a second time as you arrive."),
        LONG_REACH("Long Reach", "spirit_palm", "warstaff", "The palm travels down the staff, so it lands a staff's length further away.",
                "Spirit Palm with a War-staff reaches 7.5 blocks in place of 4.5."),
        EDGE_OF_LIGHT("Edge of Light", "beam", "greatblade", "Hold the Beam and it runs up the blade. Your next heavy throws it as a crescent.",
                "Hold Beam for a second with a Greatblade or Longsword, then land a finisher or perfect heavy within 3 seconds."),
        THUNDER_JAVELIN("Thunder Javelin", "charged_shot", "spear", "Thrown off a spear, the shot is a javelin of lightning. It goes through everything in its line.",
                "Fire a Charged Shot of half charge or more with a Spear in hand. The bolt goes through three more enemies."),
        BATTER_UP("Batter Up", "terrain_rip", "maul", "You don't throw the rock. You hit it.",
                "Use Terrain Rip with a Maul or a War-staff in hand. The rock flies half again as fast and bursts wider.");

        public final String name, power, style, line, how;
        public final String key = name().toLowerCase(Locale.ROOT);

        Duet(String name, String power, String style, String line, String how) {
            this.name = name;
            this.power = power;
            this.style = style;
            this.line = line;
            this.how = how;
        }

        public String counter() {
            return "awaken:arms_duets/" + key;
        }
    }

    /** The styles a weapon's own name can end in (data/awaken/arms/archetypes.json), longest first so "twinblades" beats "blades". */
    private static final String[] STYLES = {"shieldblade", "greatblade", "twinblades", "chainblade", "longsword", "gauntlets", "precursor", "warstaff", "daggers",
            "katana", "rapier", "glaive", "scythe", "spear", "maul", "axe"};

    /** The style of what a player holds, by the item's own name (the same rule Arms uses for its rows), or null. */
    public static @Nullable String style(Player p) {
        var stack = p.getMainHandItem();
        if (stack.isEmpty()) return null;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        for (String s : STYLES) if (path.endsWith("_" + s) || path.equals(s)) return s;
        if (path.endsWith("_sword") || id.getNamespace().equals("awaken") && path.endsWith("_blade")) return "longsword";
        if (path.endsWith("_axe")) return "axe";
        if (path.equals("mace")) return "maul";
        return null;
    }

    private static boolean holds(Player p, Duet d) {
        String s = style(p);
        return s != null && (s.equals(d.style) || d == Duet.EDGE_OF_LIGHT && s.equals("longsword") || d == Duet.BATTER_UP && s.equals("warstaff"));
    }

    /** What a player's casts have left open for their blade. Never saved: a duet lives for seconds. */
    private static final class Open {
        long frostUntil, stepUntil, flowUntil, barrierUntil, wellUntil, edgeUntil, breakUntil;
        int breakTarget = -1;
        Vec3 frostAt = Vec3.ZERO, wellAt = Vec3.ZERO;
        double frostRadius, wellRadius;
        float frostDamage, wellDamage, edgeDamage, flowCut;
        final Map<Integer, Integer> cuts = new HashMap<>();
    }

    private static final Map<ServerPlayer, Open> OPEN = new WeakHashMap<>();

    private static Open of(ServerPlayer p) {
        return OPEN.computeIfAbsent(p, k -> new Open());
    }

    private static long now(ServerPlayer p) {
        return p.level().getGameTime();
    }

    static void init() {
        FightMoments.listen(ArmsDuets::moment);
    }

    // ---- the casts open them ----------------------------------------------------------------------------------------

    public static void frost(Cast c, double radius) {
        Open o = of(c.player);
        o.frostUntil = now(c.player) + 120;
        o.frostAt = c.pos();
        o.frostRadius = radius;
        o.frostDamage = c.dmg() * 0.6f;
    }

    public static void stepped(Cast c) {
        of(c.player).stepUntil = now(c.player) + 40;
    }

    public static void flow(Cast c, int ticks) {
        Open o = of(c.player);
        o.flowUntil = now(c.player) + ticks;
        o.cuts.clear();
        o.flowCut = 4f * (1f + 0.06f * (c.powerLevel - 1));
    }

    /** Flow ended: every cut left hanging lands at once. */
    public static void flowEnded(Cast c) {
        Open o = of(c.player);
        o.flowUntil = 0;
        if (o.cuts.isEmpty()) return;
        ServerLevel level = c.level;
        int landed = 0;
        for (Map.Entry<Integer, Integer> e : o.cuts.entrySet()) {
            if (!(level.getEntity(e.getKey()) instanceof LivingEntity t) || !t.isAlive()) continue;
            Vec3 at = t.getBoundingBox().getCenter();
            Combat.hurtFrom(c.player, null, t, o.flowCut * e.getValue(), null, true);
            Fx.of(c.player, "flow", FxKind.IMPACT, at).dir(c.look()).scale(0.8 + 0.2 * e.getValue()).target(t).send();
            landed += e.getValue();
        }
        o.cuts.clear();
        if (landed > 0) {
            sound(level, c.pos(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            sound(level, c.pos(), SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, 0.8f);
            found(c.player, Duet.STILL_CUT);
        }
    }

    public static void barrier(Cast c, int ticks) {
        of(c.player).barrierUntil = now(c.player) + ticks;
    }

    public static void barrierDown(ServerPlayer p) {
        Open o = OPEN.get(p);
        if (o != null) o.barrierUntil = 0;
    }

    public static void well(Cast c, Vec3 centre, double radius, int ticks) {
        Open o = of(c.player);
        o.wellUntil = now(c.player) + ticks;
        o.wellAt = centre;
        o.wellRadius = radius;
        o.wellDamage = Math.max(2f, c.dmg()) * 1.5f;
    }

    /** The Beam was let go after being held: a blade in the hand keeps its light for a few seconds. */
    public static void beamHeld(Cast c, int heldTicks) {
        if (heldTicks < 20 || !holds(c.player, Duet.EDGE_OF_LIGHT)) return;
        Open o = of(c.player);
        o.edgeUntil = now(c.player) + 60;
        o.edgeDamage = c.dmg() * 6f * Math.min(2f, heldTicks / 20f);
        Fx.of(c.player, "beam", FxKind.AURA, c.pos()).aux(FxKind.Aura.GENERIC.ordinal()).ticks(60).send();
    }

    // ---- the power answers the weapon in your hand -------------------------------------------------------------------

    /** A Haymaker landed with Gauntlets on: the other fist follows. */
    public static void haymaker(Cast c, LivingEntity target, float damage, double charge) {
        if (charge < 0.5 || !holds(c.player, Duet.BOTH_BARRELS)) return;
        Vec3 look = c.look();
        Scheduler.after(3, () -> {
            if (!target.isAlive() || !c.player.isAlive()) return;
            Vec3 at = target.getBoundingBox().getCenter();
            Combat.hurtFrom(c.player, null, target, damage * 0.6f, null, true);
            Combat.knockFrom(target, c.pos(), 0.7 + 0.6 * charge, 0.3);
            Fx.of(c.player, "haymaker", FxKind.IMPACT, at).dir(look).scale(0.9 + 0.9 * charge).target(target).send();
            sound(c.level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
            sound(c.level, at, SoundEvents.IRON_GOLEM_HURT, 0.8f, 0.7f);
            c.feel(target, 60, 3f);
            found(c.player, Duet.BOTH_BARRELS);
        });
    }

    /** Lightning Step with Twin Blades drawn: everything on the line is cut again as you arrive. */
    public static void crossing(Cast c, List<LivingEntity> onLine, float damage) {
        if (onLine.isEmpty() || !holds(c.player, Duet.CROSSING)) return;
        Scheduler.after(2, () -> {
            boolean any = false;
            for (LivingEntity e : onLine) {
                if (!e.isAlive()) continue;
                Combat.hurtFrom(c.player, null, e, damage * 0.5f, null, true);
                Vec3 at = e.getBoundingBox().getCenter();
                Fx.of(c.player, "lightning_step", FxKind.IMPACT, at).dir(c.look()).scale(0.8).target(e).send();
                any = true;
            }
            if (any) {
                sound(c.level, c.pos(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                sound(c.level, c.pos(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.9f);
                found(c.player, Duet.CROSSING);
            }
        });
    }

    /** Spirit Palm's reach: a War-staff in the hand carries it further. */
    /** Thunder Javelin: off a Spear, a Charged Shot of half charge or more goes through this many more bodies. */
    public static int javelin(Cast c, double charge) {
        if (charge < 0.5 || !holds(c.player, Duet.THUNDER_JAVELIN)) return 0;
        found(c.player, Duet.THUNDER_JAVELIN);
        return 3;
    }

    /** Batter Up: with a Maul or a War-staff in hand, Terrain Rip's rock is struck, not thrown (the caller makes it faster and wider). */
    public static boolean batter(Cast c) {
        if (!holds(c.player, Duet.BATTER_UP)) return false;
        sound(c.level, c.pos(), SoundEvents.MACE_SMASH_AIR, 0.9f, 0.8f);
        found(c.player, Duet.BATTER_UP);
        return true;
    }

    public static double reach(Cast c, double base) {
        return holds(c.player, Duet.LONG_REACH) ? base + 3.0 : base;
    }

    /** The palm landed: past a bare hand's reach, it was the staff that carried it. */
    public static void reached(Cast c, LivingEntity target, double base) {
        if (holds(c.player, Duet.LONG_REACH) && target.distanceToSqr(c.player) > base * base) found(c.player, Duet.LONG_REACH);
    }

    // ---- the blade's moments answer what a cast left open ------------------------------------------------------------

    /** How long after a stance breaks a cast into it is a finisher (ticks). Seconds, not a timing window. */
    static final int FINISH_WINDOW = 50;

    /**
     * A cast went out (Casting, after it succeeded): into a body whose stance this player has just broken, it is a Break
     * finisher. The power lands half again on it, the family's own ending plays, and the System names it the first time.
     */
    static void cast(Cast c) {
        Open o = OPEN.get(c.player);
        if (o == null || now(c.player) >= o.breakUntil) return;
        if (!(c.level.getEntity(o.breakTarget) instanceof LivingEntity t) || !t.isAlive() || t.distanceToSqr(c.player) > 16 * 16) return;
        o.breakUntil = 0;
        Vec3 at = t.getBoundingBox().getCenter();
        Combat.hurtFrom(c.player, null, t, c.dmg(Math.max(4f, c.type.damage)) * 0.5f, c.type.primaryForce(), true);
        Fx.at(c, FxKind.FINISHER, at).target(t).dir(c.look()).noAnim().send();
        c.feel(t, 140, 6f);
        String counter = "awaken:break_finishers/" + c.type.family.name().toLowerCase(Locale.ROOT);
        boolean first = Progression.profile(c.player).counter(counter) == 0;
        Progression.count(c.player, counter);
        NoticeEvents.tell(c.player, new NoticeEvents.Event("break_finisher/" + c.type.family.name().toLowerCase(Locale.ROOT), "Break finisher",
                c.type.family.label + " finisher", "Your power went into a body whose stance you had just broken.",
                List.of("Break an enemy's stance, then cast a " + c.type.family.label + " power at it within " + FINISH_WINDOW / 20.0 + " seconds.",
                        "The cast lands half again on it. There is nothing to time: the break gives you the seconds."), NoticeEvents.Kind.COMBAT,
                c.type.family.colour));
        if (first) Progression.markNewThing(c.player, "finisher");
    }

    private static void moment(ServerPlayer p, FightMoments.Moment m, @Nullable LivingEntity target) {
        if (m == FightMoments.Moment.BREAK && target != null) {
            Open broke = of(p);
            broke.breakTarget = target.getId();
            broke.breakUntil = now(p) + FINISH_WINDOW;
        }
        Open o = OPEN.get(p);
        if (o == null) return;
        long now = now(p);
        boolean heavy = m == FightMoments.Moment.FINISHER || m == FightMoments.Moment.PERFECT_HEAVY;
        // Still Cut: a Katana hit in slowed time hangs on its target
        if (m == FightMoments.Moment.HIT && target != null && now < o.flowUntil && holds(p, Duet.STILL_CUT)) {
            int n = o.cuts.merge(target.getId(), 1, Integer::sum);
            if (n > 6) o.cuts.put(target.getId(), 6);
            else Fx.of(p, "flow", FxKind.TRAIL, target.getBoundingBox().getCenter()).ticks(Math.max(8, (int) (o.flowUntil - now))).send();
        }
        // Chain Step: a Daggers kill or finisher straight after a Step brings the Step back
        if ((m == FightMoments.Moment.KILL || m == FightMoments.Moment.FINISHER) && now < o.stepUntil && holds(p, Duet.CHAIN_STEP)) {
            o.stepUntil = 0;
            Powers.loadout(p).cooldowns.remove("awaken:shadow_step");
            Powers.markDirty(p);
            Fx.of(p, "shadow_step", FxKind.RING, p.position()).scale(1.2).ticks(8).send();
            sound(p.level(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.5f);
            found(p, Duet.CHAIN_STEP);
        }
        // Icebreaker: a Maul's heavy on frozen ground
        if (heavy && now < o.frostUntil && holds(p, Duet.ICEBREAKER) && p.position().distanceToSqr(o.frostAt) <= (o.frostRadius + 1) * (o.frostRadius + 1)) {
            o.frostUntil = 0;
            icebreaker(p, o.frostDamage);
        }
        // Reap: a Scythe hit inside your own Well
        if (m == FightMoments.Moment.HIT && target != null && now < o.wellUntil && holds(p, Duet.REAP)
                && target.position().distanceToSqr(o.wellAt) <= o.wellRadius * o.wellRadius) {
            reap(p, o, target);
        }
        // Mirror Guard: a Shieldblade's clean block while Barrier is up
        if ((m == FightMoments.Moment.DEFLECT || m == FightMoments.Moment.GUARD) && target != null && now < o.barrierUntil && holds(p, Duet.MIRROR_GUARD)) {
            mirror(p, target);
        }
        // Edge of Light: the heavy that throws the held Beam
        if (heavy && now < o.edgeUntil && holds(p, Duet.EDGE_OF_LIGHT)) {
            o.edgeUntil = 0;
            edge(p, o.edgeDamage);
        }
    }

    private static void icebreaker(ServerPlayer p, float damage) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        Vec3 dir = flat.normalize();
        Vec3 from = p.position();
        ServerLevel level = p.level();
        // the spikes run out ahead of the blow, one a tick, and hit what they come up under
        for (int i = 1; i <= 7; i++) {
            int step = i;
            Scheduler.after(i, () -> {
                Vec3 at = from.add(dir.scale(step));
                Fx.of(p, "frost_nova", FxKind.PILLAR, at).scale(0.5 + step * 0.06).ticks(26).style(FxStyle.FROST).send();
                for (LivingEntity e : Combat.foesAround(p, at.add(0, 0.6, 0), 1.3)) {
                    Combat.hurtFrom(p, null, e, damage, Forces.Force.FROST, false);
                    Combat.knockFrom(e, at, 0.3, 0.55);
                }
                if (step % 2 == 1) sound(level, at, SoundEvents.GLASS_BREAK, 0.9f, 0.55f + step * 0.03f);
                sound(level, at, SoundEvents.PLAYER_HURT_FREEZE, 0.8f, 0.7f);
            });
        }
        sound(level, from, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.9f, 0.9f);
        found(p, Duet.ICEBREAKER);
    }

    private static void reap(ServerPlayer p, Open o, LivingEntity struck) {
        List<LivingEntity> in = Combat.foesAround(p, o.wellAt, o.wellRadius);
        if (in.size() < 2) return;
        Vec3 look = p.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0, look.x);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        for (LivingEntity e : in) {
            if (e == struck) continue;
            Combat.hurtFrom(p, null, e, o.wellDamage, Forces.Force.GRAVITY, true);
            Combat.push(e, side.scale(0.7).add(0, 0.15, 0));
            Fx.of(p, "gravity_well", FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(side).scale(0.8).target(e).send();
        }
        Fx.of(p, "gravity_well", FxKind.RING, o.wellAt.add(0, 0.6, 0)).scale(o.wellRadius).ticks(8).dir(new Vec3(0, 1, 0)).send();
        sound(p.level(), o.wellAt, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        sound(p.level(), o.wellAt, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 0.6f);
        o.wellUntil = 0; // one reaping a Well
        found(p, Duet.REAP);
    }

    private static void mirror(ServerPlayer p, LivingEntity struck) {
        PowerType barrier = PowerRegistry.get("awaken:barrier");
        if (barrier == null || !Powers.owns(p, "awaken:barrier")) return;
        Cast c = Cast.of(p, barrier);
        Vec3 at = struck.getBoundingBox().getCenter();
        Vec3 from = p.getEyePosition();
        Combat.hurtFrom(p, null, struck, Math.max(4f, c.dmg(6f)), Forces.Force.CHARGE, true);
        Combat.knockFrom(struck, p.position(), 1.1, 0.35);
        Fx.of(p, "barrier", FxKind.LIGHTNING, from.add(p.getLookAngle().scale(0.8))).to(at).ticks(6).send();
        Fx.of(p, "barrier", FxKind.NOVA, at).scale(2).style(FxStyle.CHARGE).send();
        sound(p.level(), at, SoundEvents.SHIELD_BLOCK.value(), 1f, 0.6f);
        sound(p.level(), at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 1.2f);
        found(p, Duet.MIRROR_GUARD);
    }

    private static void edge(ServerPlayer p, float damage) {
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        Vec3 look = p.getLookAngle();
        Vec3 to = from.add(look.scale(11));
        for (LivingEntity e : Combat.alongLine(p, from, to, 1.6)) {
            Combat.hurtFrom(p, null, e, damage, Forces.Force.HEAT, true);
            Fx.of(p, "beam", FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(look).scale(1.2).target(e).send();
        }
        // the crescent: three short beams fanned across the cut, and the light of it
        Vec3 side = new Vec3(-look.z, 0, look.x);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        for (int i = -1; i <= 1; i++) {
            Fx.of(p, "beam", FxKind.BEAM, from.add(side.scale(i * 0.5))).to(to.add(side.scale(i * 1.6))).ticks(5).scale(0.5).send();
        }
        Fx.of(p, "beam", FxKind.FLASH, p.position()).scale(0.25).send();
        sound(p.level(), from, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 0.8f);
        sound(p.level(), from, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        found(p, Duet.EDGE_OF_LIGHT);
    }

    // ---- found, named, kept ---------------------------------------------------------------------------------------

    private static void sound(net.minecraft.world.level.Level level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** A duet happened: counted every time, named by the System the first time, kept in the Journal. */
    private static void found(ServerPlayer p, Duet d) {
        boolean first = Progression.profile(p).counter(d.counter()) == 0;
        Progression.count(p, d.counter());
        if (first) {
            // named once, with how many you have found so far; the Journal keeps it, and never says how many there are
            NoticeEvents.Event e = event(d);
            List<String> details = new java.util.ArrayList<>(e.details());
            int n = found(p);
            details.add(n == 1 ? "This is the first Arms Duet you've found. There are more." : "You've found " + n + " Arms Duets. There are more.");
            NoticeEvents.tell(p, new NoticeEvents.Event(e.id(), e.category(), e.name(), e.summary(), details, e.kind(), e.colour()));
            Progression.markNewThing(p, "duet");
        }
    }

    public static NoticeEvents.Event event(Duet d) {
        PowerType t = PowerRegistry.get("awaken:" + d.power);
        return new NoticeEvents.Event("arms_duet/" + d.key, "Arms Duet", d.name, d.line,
                List.of(d.how, "It's a combo, not a new power: do the same again to repeat it."), NoticeEvents.Kind.COMBAT,
                t == null ? 0xFFFFFF : t.family.colour);
    }

    /** How many of the duets a player has found, and how many there are (the Journal counts N / ??). */
    public static int found(ServerPlayer p) {
        int n = 0;
        for (Duet d : Duet.values()) if (Progression.profile(p).counter(d.counter()) > 0) n++;
        return n;
    }
}
