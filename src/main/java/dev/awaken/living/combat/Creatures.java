package dev.awaken.living.combat;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.living.LivingModule;
import dev.awaken.living.api.Regions;
import dev.awaken.progression.Curve;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Levels for the overworld's creatures that aren't AWAKEN's own (SPEC §21.2): a vanilla monster takes its place's level
 * (as a hunter, +1; one in twenty an elite, +2 and ×2.5 health) with SPEC §2's scaling as permanent modifiers; animals
 * and the like get a level for the band and for XP but keep their vanilla bodies. AWAKEN's creatures tag themselves
 * when the mobs module configures them; named beasts get their title here, from the region of their lair.
 */
public final class Creatures {
    private Creatures() {}

    static final Identifier HEALTH = Awaken.id("living_level_health");
    static final Identifier DAMAGE = Awaken.id("living_level_damage");
    static final Identifier ARMOR = Awaken.id("living_level_armor");
    static final Identifier SCALE = Awaken.id("living_elite_scale");
    static final double ELITE_HEALTH = 2.5;

    /** Before a vanilla mob's own finalizeSpawn: its level, scaling and tag. */
    public static void onFinalizeSpawn(FinalizeSpawnEvent e) {
        Mob mob = e.getEntity();
        if (!(e.getLevel().getLevel() instanceof ServerLevel level) || !Regions.on(level) || ours(mob) || mob instanceof Npc) return;
        tag(level, mob, true);
    }

    /** Creatures from before the Living Wilds (or that skipped finalizeSpawn) get a level when they join. */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !Regions.on(level)) return;
        if (!(e.getEntity() instanceof LivingEntity living) || living instanceof Player) return;
        CreatureTag tag = CreatureTag.of(living);
        if (ours(living)) {
            // a named beast is the terror of the region its lair is in
            if (tag.kind() == CreatureTag.Kind.BEAST && tag.title().isEmpty()) {
                String region = Regions.regionAt(level, living.getBlockX(), living.getBlockZ()).name();
                living.setData(CoreApi.CREATURE_TAG.get(), tag.withTitle("Terror of " + region));
            }
            return;
        }
        if (!tag.present() && !(living instanceof Npc) && living instanceof Mob mob) tag(level, mob, false);
    }

    /** Projectiles and blasts from a levelled vanilla monster hit as hard as its melee would (melee scales by attribute). */
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (src == null || src == e.getSource().getDirectEntity() || ours(src)) return;
        CreatureTag tag = CreatureTag.of(src);
        if (tag.kind() != CreatureTag.Kind.MONSTER) return;
        e.setAmount((float) (e.getAmount() * Curve.mobDamage(tag.level())));
    }

    private static void tag(ServerLevel level, Mob mob, boolean scale) {
        if (mob instanceof Enemy) {
            boolean elite = scale && mob.getRandom().nextFloat() < LivingModule.eliteChance(Regions.creatureLevel(level, mob.blockPosition(), Regions.Role.NEUTRAL));
            int lv = Regions.creatureLevel(level, mob.blockPosition(), elite ? Regions.Role.ELITE : Regions.Role.HUNTER);
            if (scale) applyScaling(mob, lv, elite);
            mob.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, CreatureTag.Kind.MONSTER, elite, ""));
        } else {
            int lv = Regions.creatureLevel(level, mob.blockPosition(), Regions.Role.AMBIENT);
            mob.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, CreatureTag.Kind.PASSIVE, false, ""));
        }
    }

    /** SPEC §2's level scaling (health, damage, armour) and an elite's size and health, as permanent modifiers. */
    static void applyScaling(LivingEntity e, int lv, boolean elite) {
        set(e, Attributes.MAX_HEALTH, HEALTH, Curve.mobHealth(lv) * (elite ? ELITE_HEALTH : 1) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.ATTACK_DAMAGE, DAMAGE, Curve.mobDamage(lv) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.ARMOR, ARMOR, Curve.mobArmor(lv), AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.SCALE, SCALE, elite ? 0.12 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        e.setHealth(e.getMaxHealth());
    }

    private static void set(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        if (Math.abs(amount) < 1e-9) inst.removeModifier(id);
        else inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
    }

    /** AWAKEN's own creatures level and tag themselves. */
    static boolean ours(Entity e) {
        return Awaken.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }
}
