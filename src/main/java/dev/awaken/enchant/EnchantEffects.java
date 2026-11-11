package dev.awaken.enchant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What our own enchantments do (SPEC §45.3). Wards: each worn piece takes 2.5% a level off one Force's damage, a whole
 * suit at most 30% off ordinary creatures' hits and 20% off a Lord's, Warden's or named beast's (agreed with the boss
 * fights, 2026-11-02); only hits typed as a Force count, so melee is never warded. Banes: +8% a level against one kind of
 * foe, applied after everything else, never against anything {@link #spared}. Clear Blood and Harvest. Insight is
 * vanilla's experience effects in our JSON.
 */
public final class EnchantEffects {
    private EnchantEffects() {}

    public static final float WARD_PER_LEVEL = 0.025F, WARD_CAP = 0.30F, WARD_CAP_LORDS = 0.20F, BANE_PER_LEVEL = 0.08F;

    private static final Codec<Forces.Force> FORCE = Codec.STRING.xmap(s -> Forces.Force.valueOf(s.toUpperCase(Locale.ROOT)),
            f -> f.name().toLowerCase(Locale.ROOT));

    /** {@code "awaken:ward": {"force": "heat"}}. */
    public record Ward(Forces.Force force) {
        public static final Codec<Ward> CODEC = RecordCodecBuilder.create(i -> i.group(FORCE.fieldOf("force").forGetter(Ward::force)).apply(i, Ward::new));
    }

    /** {@code "awaken:bane": {"kind": "dead"}}: dead, crawlers, beasts or deep. */
    public record Bane(String kind) {
        public static final Codec<Bane> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.STRING.fieldOf("kind").forGetter(Bane::kind)).apply(i, Bane::new));
    }

    /** An effect with nothing to say but that it's there ({@code "awaken:clear_blood": {}}). */
    public record Marker() {
        public static final Codec<Marker> CODEC = com.mojang.serialization.MapCodec.unitCodec(new Marker());
    }

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ---- who is spared ------------------------------------------------------------------------------------------------

    /**
     * What a bane never touches and a ward is held to 20% against: a gate's Lord or a Warden, a named beast (a title on
     * its plate: "Terror of Ashfen"), vanilla's great creatures, and other players.
     */
    public static boolean spared(@Nullable Entity e) {
        if (e == null) return false;
        if (e instanceof Player) return true;
        MobRole.Role role = MobRole.of(e).role();
        if (role == MobRole.Role.BOSS || role == MobRole.Role.WARDEN) return true;
        if (!CreatureTag.of(e).title().isEmpty()) return true;
        return e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Warden || e instanceof ElderGuardian;
    }

    // ---- wards --------------------------------------------------------------------------------------------------------

    /** The Force a hit carries for a ward: its own damage type, or fire for Heat, freezing for Frost, lightning for
     *  Charge and magic for Spirit. Null for anything else (melee, falls, arrows). */
    static Forces.@Nullable Force forceOf(DamageSource s) {
        Forces.Force f = Forces.forceOf(s);
        if (f != null) return f;
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return null;
        if (s.is(DamageTypeTags.IS_FIRE)) return Forces.Force.HEAT;
        if (s.is(DamageTypeTags.IS_FREEZING)) return Forces.Force.FROST;
        if (s.is(DamageTypeTags.IS_LIGHTNING)) return Forces.Force.CHARGE;
        if (s.is(DamageTypes.MAGIC) || s.is(DamageTypes.INDIRECT_MAGIC)) return Forces.Force.SPIRIT;
        return null;
    }

    /** How much of one Force the worn suit takes off (0..cap). */
    public static float ward(LivingEntity wearer, Forces.Force force, boolean againstLord) {
        int levels = 0;
        for (EquipmentSlot slot : ARMOUR) levels += wardLevels(wearer.getItemBySlot(slot), force);
        return Math.min(levels * WARD_PER_LEVEL, againstLord ? WARD_CAP_LORDS : WARD_CAP);
    }

    private static int wardLevels(ItemStack s, Forces.Force force) {
        if (s.isEmpty()) return 0;
        int n = 0;
        for (var e : s.getEnchantments().entrySet()) {
            Ward w = e.getKey().value().effects().get(EnchantContent.WARD.get());
            if (w != null && w.force() == force) n += e.getIntValue();
        }
        return n;
    }

    static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide()) return;
        DamageSource src = e.getSource();
        // a ward on the one hit
        Forces.Force force = forceOf(src);
        if (force != null) {
            float w = ward(target, force, spared(src.getEntity()));
            if (w > 0) {
                e.setAmount(e.getAmount() * (1 - w));
                if (target instanceof ServerPlayer sp) EnchantFx.warded(sp, force, w, src.getSourcePosition());
            }
        }
    }

    // ---- banes --------------------------------------------------------------------------------------------------------

    /** Last of all (after the Arms stack): a bane's share on the hit. */
    static void onIncomingLast(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide() || !(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p) return;
        float k = bane(p, p.getMainHandItem(), target);
        if (k > 0) {
            e.setAmount(e.getAmount() * (1 + k));
            EnchantFx.baned(p, target, baneNo(p, p.getMainHandItem(), target));
        }
    }

    /** The number of the bane that bit (its mark is the one shown). */
    static int baneNo(ServerPlayer attacker, ItemStack weapon, LivingEntity target) {
        for (var e : weapon.getEnchantments().entrySet()) {
            Bane b = e.getKey().value().effects().get(EnchantContent.BANE.get());
            Inscriptions.Entry en = Inscriptions.of(e.getKey());
            if (b != null && en != null && is(b.kind(), attacker, target)) return en.no();
        }
        return 0;
    }

    /** The bane a weapon carries against this target (0 when none applies). */
    public static float bane(ServerPlayer attacker, ItemStack weapon, LivingEntity target) {
        if (weapon.isEmpty() || spared(target)) return 0;
        float k = 0;
        for (var e : weapon.getEnchantments().entrySet()) {
            Bane b = e.getKey().value().effects().get(EnchantContent.BANE.get());
            if (b != null && is(b.kind(), attacker, target)) k = Math.max(k, e.getIntValue() * BANE_PER_LEVEL);
        }
        return k;
    }

    /** Whether a creature is of a bane's kind. */
    static boolean is(String kind, ServerPlayer attacker, LivingEntity t) {
        String theme = MobRole.of(t).theme();
        return switch (kind) {
            case "dead" -> t.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD) || theme.endsWith("bell_crypt");
            case "crawlers" -> t.getType().builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD) || theme.endsWith("hive");
            case "beasts" -> wildBeast(t);
            case "deep" -> theme.endsWith("hollows") || dev.awaken.core.api.DescentHooks.inDescent(attacker)
                    || t instanceof Enemy && t.getY() < 0 && t.level().dimension() == Level.OVERWORLD;
            default -> false;
        };
    }

    /** A wild herd animal, pack hunter or hunted quarry: never a tamed animal or your own beasts. */
    static boolean wildBeast(Entity t) {
        if (t instanceof OwnableEntity o && o.getOwnerReference() != null) return false;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(t.getType());
        if (key.getNamespace().equals("awaken") && key.getPath().startsWith("kin_")) return false;
        return t instanceof Animal || t.getType().getCategory().name().startsWith("AWAKEN_FAUNA");
    }

    // ---- Clear Blood --------------------------------------------------------------------------------------------------

    /** Every half second: poison, wither and spores lose half a second more (a second at II). */
    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        int level = 0;
        for (EquipmentSlot slot : ARMOUR) level = Math.max(level, levelOf(p.getItemBySlot(slot), EnchantContent.CLEAR_BLOOD.get()));
        if (level <= 0) return;
        List<MobEffectInstance> fading = null;
        for (MobEffectInstance i : p.getActiveEffects()) {
            if (!clears(i.getEffect()) || i.isInfiniteDuration()) continue;
            if (fading == null) fading = new ArrayList<>(2);
            fading.add(i);
        }
        if (fading == null) return;
        if (p.tickCount % 20 == 0) EnchantFx.cleared(p);
        for (MobEffectInstance i : fading) {
            int left = i.getDuration() - 10 * level;
            p.removeEffect(i.getEffect());
            if (left > 0) p.addEffect(new MobEffectInstance(i.getEffect(), left, i.getAmplifier(), i.isAmbient(), i.isVisible(), i.showIcon()));
        }
    }

    static boolean clears(Holder<MobEffect> h) {
        if (h.is(MobEffects.POISON) || h.is(MobEffects.WITHER)) return true;
        return h.unwrapKey().map(k -> k.identifier().getPath().contains("spore")).orElse(false);
    }

    /** The highest level of an enchantment carrying this effect on a stack. */
    static int levelOf(ItemStack s, DataComponentType<?> effect) {
        if (s.isEmpty()) return 0;
        int best = 0;
        for (var e : s.getEnchantments().entrySet()) {
            Enchantment en = e.getKey().value();
            if (en.effects().has(effect)) best = Math.max(best, e.getIntValue());
        }
        return best;
    }

    // ---- Harvest ------------------------------------------------------------------------------------------------------

    /** A wild beast killed with Harvest: one chance in two a level of one more of its hide, bone or pelt. */
    static void onDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer p) || !wildBeast(dead) || spared(dead)) return;
        int lv = levelOf(p.getMainHandItem(), EnchantContent.HARVEST.get());
        if (lv <= 0 || level.getRandom().nextFloat() >= Math.min(1F, 0.5F * lv)) return;
        for (ItemEntity drop : e.getDrops()) {
            ItemStack s = drop.getItem();
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (!(path.contains("hide") || path.contains("bone") || path.contains("pelt") || path.contains("leather")
                    || path.contains("fur") || path.contains("scale") || path.contains("rabbit_foot"))) continue;
            ItemEntity extra = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), s.copyWithCount(1));
            extra.setDefaultPickUpDelay();
            e.getDrops().add(extra);
            EnchantFx.spoils(level, extra);
            return;
        }
    }
}
