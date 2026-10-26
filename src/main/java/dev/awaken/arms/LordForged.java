package dev.awaken.arms;

import dev.awaken.core.Forces;
import dev.awaken.core.api.MobRole;
import dev.awaken.powers.Fx;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import dev.awaken.signal.Signal;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lord-forged weapons (SPEC §25 "Lord-forged"): one for each Gate Lord, forged from what it was, in a style that fits
 * it, carrying the Lord's signature move as the weapon's Art. On the kill it goes straight to the slayer's hand (a 35%
 * chance, certain by the third Lord without one), with the reveal: the Lord's light round the fighter, the System naming
 * it, the weapon in the empty hand. Its Art is the style's own and then the Lord's move as it lets go: a shape on the
 * effects engine, the Lord's Force, a reach and what it leaves on what it struck.
 */
public final class LordForged {
    private LordForged() {}

    /**
     * A Lord's move as an Art: the effects engine's shape and family look, its Force (null: a plain blow), how far, how
     * hard (× the fighter's attack), what it leaves (an effect for so many ticks, a fire) and a pull in (+) or throw out (-).
     */
    public record Signature(String name, FxKind shape, PowerType.Family look, Forces.@Nullable Force force, float radius, float power,
                            @Nullable Holder<MobEffect> effect, int effectTicks, int amplifier, boolean ignite, float pull) {}

    /** A Lord-forged weapon: the Lord's id (its mob id), the weapon's id (a row of ArmsData) and its Art. */
    public record Forged(String lord, String weapon, Signature art) {}

    private static Forged f(String lord, String weapon, Signature s) {
        return new Forged(lord, weapon, s);
    }

    /** The fifteen. Home ranks and styles in SPEC §25; the Arts are each Lord's own move from the bestiary. */
    public static final List<Forged> ALL = List.of(
            f("bell_warden", "bell_warden_maul", new Signature("Grand Peal", FxKind.RING, PowerType.Family.SPIRIT, Forces.Force.SPIRIT, 9F, 1.4F,
                    MobEffects.SLOWNESS, 60, 2, false, 0F)),
            f("mother_of_spores", "mother_of_spores_scythe", new Signature("Spore Rain", FxKind.NOVA, PowerType.Family.SPIRIT, null, 7F, 1.2F,
                    MobEffects.POISON, 100, 1, false, 0F)),
            f("ember_tyrant", "ember_tyrant_axe", new Signature("Eruption", FxKind.SLAM, PowerType.Family.KINETIC, Forces.Force.HEAT, 6F, 1.8F,
                    null, 0, 0, true, -1.2F)),
            f("hive_regent", "hive_regent_daggers", new Signature("Royal Dive", FxKind.CONE, PowerType.Family.VELOCITY, null, 8F, 1.5F,
                    MobEffects.POISON, 80, 1, false, 0F)),
            f("root_saint", "root_saint_warstaff", new Signature("Root Lance", FxKind.RING, PowerType.Family.ARCHITECT, Forces.Force.GRAVITY, 7F, 1.3F,
                    MobEffects.SLOWNESS, 70, 4, false, 0F)),
            f("drowned_archivist", "drowned_archivist_spear", new Signature("Tidal Page", FxKind.CONE, PowerType.Family.MIND, null, 9F, 1.4F,
                    MobEffects.BLINDNESS, 50, 0, false, -1.6F)),
            f("gearwright_colossus", "gearwright_colossus_gauntlets", new Signature("Piston Slam", FxKind.SLAM, PowerType.Family.TECH, Forces.Force.CHARGE, 6F, 2.0F,
                    MobEffects.SLOWNESS, 40, 3, false, 0F)),
            f("glass_serpent", "glass_serpent_rapier", new Signature("Glass Storm", FxKind.NOVA, PowerType.Family.ARCHITECT, null, 6F, 1.6F,
                    MobEffects.WEAKNESS, 80, 1, false, 0F)),
            f("lantern_broker", "lantern_broker_chainblade", new Signature("Last Light", FxKind.NOVA, PowerType.Family.ENERGY, Forces.Force.HEAT, 8F, 1.2F,
                    null, 0, 0, true, 1.6F)),
            f("abbot_of_frost", "abbot_of_frost_glaive", new Signature("Glacial Vow", FxKind.NOVA, PowerType.Family.FLIGHT, Forces.Force.FROST, 7F, 1.5F,
                    MobEffects.SLOWNESS, 80, 5, false, 0F)),
            f("mirror_knight", "mirror_knight_shieldblade", new Signature("Reflections", FxKind.RING, PowerType.Family.MIND, Forces.Force.SPIRIT, 6F, 1.7F,
                    MobEffects.GLOWING, 100, 0, false, 0F)),
            f("unit_zero", "unit_zero_precursor", new Signature("Zero Point", FxKind.VORTEX, PowerType.Family.TECH, Forces.Force.GRAVITY, 8F, 1.9F,
                    null, 0, 0, false, 2.2F)),
            f("the_sleeper", "the_sleeper_katana", new Signature("Waking", FxKind.RING, PowerType.Family.SHADOW, Forces.Force.SPIRIT, 8F, 1.4F,
                    MobEffects.SLOWNESS, 60, 6, false, 0F)),
            f("sky_leviathan", "sky_leviathan_twinblades", new Signature("Storm Eye", FxKind.NOVA, PowerType.Family.FLIGHT, Forces.Force.CHARGE, 8F, 1.8F,
                    MobEffects.LEVITATION, 30, 0, false, 0F)),
            f("hollow_herald", "hollow_herald_greatblade", new Signature("Event Horizon", FxKind.VORTEX, PowerType.Family.SHADOW, Forces.Force.GRAVITY, 9F, 2.1F,
                    MobEffects.DARKNESS, 60, 0, false, 2.6F))
    );

    /** Each Lord-forged weapon's Mark (Marks), something of its Lord in the hand that holds it. */
    static final Map<String, String> MARK = Map.ofEntries(
            Map.entry("bell_warden", "rebuke"), Map.entry("mother_of_spores", "feast"), Map.entry("ember_tyrant", "chain_reaction"),
            Map.entry("hive_regent", "windrunner"), Map.entry("root_saint", "steady_breath"), Map.entry("drowned_archivist", "echo_return"),
            Map.entry("gearwright_colossus", "earthsplitter"), Map.entry("glass_serpent", "counterspark"), Map.entry("lantern_broker", "harvest"),
            Map.entry("abbot_of_frost", "parry_ward"), Map.entry("mirror_knight", "mercy"), Map.entry("unit_zero", "overcharge"),
            Map.entry("the_sleeper", "phantom_cut"), Map.entry("sky_leviathan", "crescendo"), Map.entry("hollow_herald", "sunder"));

    /** A Lord-forged weapon as it falls: with its Lord's Mark. */
    static ItemStack stack(Forged f, Item item) {
        ItemStack s = new ItemStack(item);
        String mark = MARK.get(f.lord());
        if (mark != null) s.set(ArmsContent.INSTANCE.get(), Marks.Instance.EMPTY.withMark(mark));
        return s;
    }

    private static final Map<String, Forged> BY_WEAPON = ALL.stream().collect(Collectors.toUnmodifiableMap(Forged::weapon, Function.identity()));
    private static final Map<String, Forged> BY_BOSS = ALL.stream().collect(Collectors.toUnmodifiableMap(x -> "awaken:" + x.lord(), Function.identity()));

    public static @Nullable Forged byWeapon(String id) {
        return BY_WEAPON.get(id);
    }

    /** By the boss id a Lord carries in its MobRole ({@code awaken:<mob id>}). */
    public static @Nullable Forged byBoss(String bossId) {
        return BY_BOSS.get(bossId);
    }

    /** The Lord-forged weapon in the fighter's main hand, or null. */
    static @Nullable Forged held(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (stack.isEmpty()) return null;
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(dev.awaken.Awaken.MODID) ? BY_WEAPON.get(key.getPath()) : null;
    }

    // ---- the drop -----------------------------------------------------------------------------------------------------

    /** A Lord-forged weapon falls this often, and certainly on the third Lord in a row without one. */
    public static final float CHANCE = 0.35F;
    public static final int PITY = 3;

    /** Whether this Lord's fall gives its weapon: the roll, or the pity once {@code without} Lords have passed. */
    static boolean falls(int without, float roll) {
        return without + 1 >= PITY || roll < CHANCE;
    }

    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        MobRole role = MobRole.of(e.getEntity()); // a decoy's role is NONE
        if (role.role() != MobRole.Role.BOSS || role.bossId().isEmpty()) return;
        Forged forged = byBoss(role.bossId().get());
        if (forged == null) return;
        Item item = ArmsItems.item(forged.weapon());
        if (item == null) return;
        ArmsWeaves.ArmsMemory m = p.getData(ArmsContent.MEMORY);
        if (!falls(m.lordsWithout(), level.getRandom().nextFloat())) {
            p.setData(ArmsContent.MEMORY, m.withLordsWithout(m.lordsWithout() + 1));
            return;
        }
        p.setData(ArmsContent.MEMORY, m.withLordsWithout(0));
        // the reveal waits for the next tick: the death event fires inside die(), before the kill is counted (an
        // empty-handed kill counts as unarmed only while the hand is still empty when the Lord's death is tallied)
        PENDING.add(new Pending(p, stack(forged, item), forged));
    }

    private record Pending(ServerPlayer player, ItemStack stack, Forged forged) {}

    private static final List<Pending> PENDING = new java.util.ArrayList<>();

    /** The reveals the last tick's Lord deaths earned (ArmsServer, each server tick). */
    static void revealPending() {
        if (PENDING.isEmpty()) return;
        List<Pending> due = List.copyOf(PENDING);
        PENDING.clear();
        for (Pending r : due) {
            if (!r.player().isRemoved()) reveal(r.player(), r.stack(), r.forged());
            else r.player().level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(r.player().level(),
                    r.player().getX(), r.player().getY(), r.player().getZ(), r.stack())); // gone that tick: it waits where they stood
        }
    }

    /** The reveal: the Lord's light round the fighter, its name from the System, the weapon to the hand. */
    static void reveal(ServerPlayer p, ItemStack stack, Forged forged) {
        ServerLevel level = p.level();
        FxStyle look = FxStyle.of(forged.art().look());
        Fx.at(level, FxKind.PILLAR, look, p.position()).caster(p).scale(7.0).ticks(30).send();
        Fx.at(level, FxKind.RING, look, p.position().add(0, 0.1, 0)).caster(p).scale(5.0).ticks(16).send();
        Fx.at(level, FxKind.NOVA, look, p.position().add(0, 1.2, 0)).caster(p).scale(2.0).send();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.7F);
        Signal.toast(p, Component.literal("LORD-FORGED · ").withStyle(Style.EMPTY.withColor(0xF0B040))
                .append(stack.getHoverName().copy().withStyle(Style.EMPTY.withColor(forged.art().look().colour))));
        if (p.getMainHandItem().isEmpty()) p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        else if (!p.getInventory().add(stack)) p.drop(stack, false, Prediction.SERVER_ONLY);
        // a new weapon is a new capability: the Director's rhythm hears it (progression plan item 10)
        dev.awaken.progression.Progression.markNewThing(p, "lord-forged weapon");
    }

    // ---- its Art ------------------------------------------------------------------------------------------------------

    /** A Lord takes at most this share of its health from one Art (the Weaves' rule: a Lord is fought, not deleted). */
    static final float LORD_CAP = 0.05F;

    /** The Lord's move, as the Art lets go (ArmsServer: the first active tick of the Art). */
    static void unleash(ServerPlayer p, Forged forged) {
        Signature s = forged.art();
        ServerLevel level = p.level();
        Vec3 at = p.position();
        Vec3 look = p.getViewVector(1F);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        boolean cone = s.shape() == FxKind.CONE;
        float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * s.power();
        DamageSource src = s.force() == null ? p.damageSources().playerAttack(p) : level.damageSources().source(Forces.key(s.force()), p);
        float r2 = s.radius() * s.radius();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(s.radius(), 3, s.radius()),
                t -> t != p && t.isAlive() && ArmsServer.foe(p, t) && t.distanceToSqr(at) <= r2)) {
            Vec3 to = t.position().subtract(at);
            if (cone && new Vec3(to.x, 0, to.z).normalize().dot(flat) < 0.5) continue; // 60° each side
            boolean lord = t.getMaxHealth() >= 150; // as ArmsServer: a Lord is neither reeled in nor thrown
            t.damageCooldownTime = 0;
            t.hurtServer(level, src, lord ? Math.min(damage, t.getMaxHealth() * LORD_CAP) : damage);
            // each struck body flares in the Lord's light: the move reads where it lands, by day too
            Fx.at(level, FxKind.IMPACT, FxStyle.of(s.look()), t.position().add(0, t.getBbHeight() * 0.6, 0)).dir(to.normalize()).scale(1.6).send();
            if (s.effect() != null) t.addEffect(new MobEffectInstance(s.effect(), s.effectTicks(), s.amplifier()), p);
            if (s.ignite()) t.igniteForSeconds(4F);
            if (s.pull() != 0 && !lord) {
                double d = Math.max(1e-3, to.horizontalDistance());
                double k = s.pull() * 0.35 * (1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                t.setDeltaMovement(t.getDeltaMovement().add(-to.x / d * k, 0.15, -to.z / d * k));
                t.needsSync = true;
            }
        }
        var emit = Fx.at(level, s.shape(), FxStyle.of(s.look()), cone ? p.getEyePosition() : at.add(0, 0.2, 0)).caster(p).scale(s.radius());
        if (cone) emit.dir(flat).aux(60).ticks(10);
        else if (s.shape() == FxKind.VORTEX) emit.ticks(24);
        else if (s.shape() == FxKind.RING) emit.ticks(12);
        emit.send();
        Fx.at(level, FxKind.NOVA, FxStyle.of(s.look()), at.add(0, 1.0, 0)).caster(p).scale(2.2).send(); // the letting go
        p.sendOverlayMessage(Component.literal(s.name()).withStyle(Style.EMPTY.withColor(s.look().colour)));
    }
}
