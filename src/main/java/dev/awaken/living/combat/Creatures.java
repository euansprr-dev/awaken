package dev.awaken.living.combat;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.living.LivingContent;
import dev.awaken.living.LivingModule;
import dev.awaken.living.api.Regions;
import dev.awaken.progression.Curve;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
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
import org.jspecify.annotations.Nullable;
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
        // a quieter land: animals are thinned by rank before they are anything (SPEC 21.11 decision 23)
        if (QuietLand.thins(level, mob, e.getSpawnType(), mob.getRandom())) {
            e.setSpawnCancelled(true);
            return;
        }
        tag(level, mob, true, e.getSpawnType());
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
        if (!tag.present() && !(living instanceof Npc) && living instanceof Mob mob) tag(level, mob, false, null);
        // a wild animal that fights back (WildKin) loses its goals with the chunk: put them back
        else if (tag.kind() == CreatureTag.Kind.NEUTRAL && living instanceof Mob mob && WildKin.isKin(mob)) WildKin.arm(mob, tag.level());
        // a creature from before forms, or that skipped finalizeSpawn, is dressed once
        if (living instanceof Mob mob && !mob.hasData(LivingContent.FORM.get())) {
            CreatureTag now = CreatureTag.of(mob);
            if (now.kind() == CreatureTag.Kind.MONSTER || (now.kind() == CreatureTag.Kind.NEUTRAL && WildKin.isKin(mob)))
                Forms.rollIfOn(level, mob, Regions.creatureLevel(level, mob.blockPosition(), Regions.Role.NEUTRAL), now.elite());
        }
        // a formed creature's moves are goals, and goals are not saved
        if (living instanceof Mob mob) {
            Moves.arm(mob);
            if (Forms.of(mob).rung() >= 2) FormShape.refresh(mob); // the saved form's true size
        }
    }

    /** Projectiles and blasts from a levelled vanilla monster hit as hard as its melee would (melee scales by attribute). */
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (src == null || src == e.getSource().getDirectEntity() || ours(src)) return;
        CreatureTag tag = CreatureTag.of(src);
        if (tag.kind() != CreatureTag.Kind.MONSTER) return;
        e.setAmount((float) (e.getAmount() * Curve.mobDamage(tag.level())));
    }

    private static void tag(ServerLevel level, Mob mob, boolean scale, @Nullable EntitySpawnReason reason) {
        if (mob instanceof Enemy) {
            int landLvl = Regions.creatureLevel(level, mob.blockPosition(), Regions.Role.NEUTRAL);
            boolean elite = scale && mob.getRandom().nextFloat() < LivingModule.eliteChance(landLvl);
            int lv = Regions.creatureLevel(level, mob.blockPosition(), elite ? Regions.Role.ELITE : Regions.Role.HUNTER);
            if (scale) applyScaling(mob, lv, elite);
            mob.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, CreatureTag.Kind.MONSTER, elite, ""));
            Forms.rollIfOn(level, mob, landLvl, elite);
        } else {
            // a wild animal in D land or higher fights back (SPEC §21.10): neutral, scaled, a little bigger, sometimes an alpha
            int land = Regions.creatureLevel(level, mob.blockPosition(), Regions.Role.NEUTRAL);
            boolean settled = Regions.landCap(level, mob.blockPosition()) != Integer.MAX_VALUE;
            // the ledger's herds (SPEC §41.2) are the wild's own, and a deep-wild herd's first body is its alpha
            boolean ledger = dev.awaken.living.population.Ledger.spawning();
            if (scale && reason != null && WildKin.fights(mob.getType(), WildKin.wild(reason) || ledger, mob.isBaby(), settled, land)) {
                boolean alpha = dev.awaken.living.population.Ledger.alphaWanted() || !ledger && mob.getRandom().nextFloat() < LivingModule.eliteChance(land);
                int lv = Regions.creatureLevel(level, mob.blockPosition(), alpha ? Regions.Role.ELITE : Regions.Role.NEUTRAL);
                applyScaling(mob, lv, alpha, WildKin.growth(dev.awaken.living.region.RegionMap.rankOf(land)));
                String title = alpha ? "Alpha of " + Regions.regionAt(level, mob.getBlockX(), mob.getBlockZ()).name() : "";
                mob.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, CreatureTag.Kind.NEUTRAL, alpha, title));
                Forms.rollIfOn(level, mob, land, alpha);
                return;
            }
            int lv = Regions.creatureLevel(level, mob.blockPosition(), Regions.Role.AMBIENT);
            mob.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, CreatureTag.Kind.PASSIVE, false, ""));
        }
    }

    /** SPEC §2's level scaling (health, damage, armour) and an elite's size and health, as permanent modifiers. */
    static void applyScaling(LivingEntity e, int lv, boolean elite) {
        applyScaling(e, lv, elite, 0);
    }

    /** As above, and {@code growth} bigger again (a wild animal in a harder land). */
    static void applyScaling(LivingEntity e, int lv, boolean elite, double growth) {
        set(e, Attributes.MAX_HEALTH, HEALTH, Curve.mobHealth(lv) * (elite ? ELITE_HEALTH : 1) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.ATTACK_DAMAGE, DAMAGE, Curve.mobDamage(lv) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.ARMOR, ARMOR, Curve.mobArmor(lv), AttributeModifier.Operation.ADD_VALUE);
        set(e, Attributes.SCALE, SCALE, (elite ? 0.12 : 0) + growth, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
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
