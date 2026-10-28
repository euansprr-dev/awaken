package dev.awaken.living.hunt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.living.LivingContent;
import dev.awaken.living.api.Regions;
import dev.awaken.living.combat.GameDrops;
import dev.awaken.living.population.Ledger;
import dev.awaken.living.population.Spoor;
import dev.awaken.living.region.RegionMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The quarry and its parts (SPEC §41.4). A quarry is a herd's or pack's alpha or a named beast. It has two to four parts by its
 * body (horns, antlers or tusks, its hide or pelt, its tail, a beast's claws); damage lands on the part where the blow struck,
 * and enough of it (a share of the quarry's health) breaks the part: it drops, the quarry staggers, everyone near hears it.
 * Hunters who know the kind well (research level 2) hit its weak spot a quarter harder. A herd's alpha beaten to a third of
 * its health limps to its bed, bleeding a trail, and rests there until it's found. Whoever hurt it is a hunter of it: they
 * carve its body ({@link Carcass}).
 */
public final class Quarry {
    private Quarry() {}

    public enum Zone { HEAD, BODY, REAR, LEGS }

    /**
     * A part: its id and name, where on the body, the share of the quarry's health it takes to break, what drops (an item id,
     * or "hide" / "core" for the land tier's hunter pool) and how many.
     */
    public record Part(String id, String name, Zone zone, float share, String drop, int count) {}

    /** A body plan: its parts and its weak spot (and what a hunter calls it). */
    public record Plan(List<Part> parts, Zone weak, String weakName) {}

    static final Part HIDE = new Part("hide", "hide", Zone.BODY, 0.3F, "hide", 1);
    static final Plan HORNED = new Plan(List.of(new Part("horns", "horns", Zone.HEAD, 0.15F, "awaken:crag_horn", 1), HIDE,
            new Part("tail", "tail", Zone.REAR, 0.1F, "minecraft:string", 2)), Zone.BODY, "the flank");
    static final Plan ANTLERED = new Plan(List.of(new Part("antlers", "antlers", Zone.HEAD, 0.15F, "awaken:antler_tine", 1), HIDE,
            new Part("tail", "tail", Zone.REAR, 0.1F, "minecraft:string", 2)), Zone.BODY, "the flank");
    static final Plan TUSKED = new Plan(List.of(new Part("tusks", "tusks", Zone.HEAD, 0.15F, "awaken:briar_tusk", 1),
            new Part("back", "bristled back", Zone.BODY, 0.3F, "hide", 1)), Zone.REAR, "the haunch");
    static final Plan MANED = new Plan(List.of(new Part("mane", "mane", Zone.HEAD, 0.12F, "minecraft:string", 3), HIDE,
            new Part("tail", "tail", Zone.REAR, 0.1F, "minecraft:string", 2)), Zone.HEAD, "the neck");
    static final Plan PACK = new Plan(List.of(new Part("fangs", "fangs", Zone.HEAD, 0.15F, "minecraft:bone", 3),
            new Part("pelt", "pelt", Zone.BODY, 0.3F, "hide", 1), new Part("tail", "tail", Zone.REAR, 0.12F, "minecraft:string", 2)), Zone.HEAD, "the head");
    static final Plan BEAST = new Plan(List.of(new Part("head", "head", Zone.HEAD, 0.15F, "core", 1),
            new Part("back", "back plates", Zone.BODY, 0.3F, "hide", 1), new Part("tail", "tail", Zone.REAR, 0.12F, "core", 1),
            new Part("claws", "claws", Zone.LEGS, 0.12F, "minecraft:bone", 3)), Zone.LEGS, "the legs");

    private static final Map<String, Plan> PLANS = Map.ofEntries(
            Map.entry("minecraft:cow", HORNED), Map.entry("minecraft:goat", HORNED), Map.entry("minecraft:sheep", HORNED),
            Map.entry("awaken:mossback", HORNED), Map.entry("awaken:crag_ram", HORNED), Map.entry("awaken:frost_stag", ANTLERED),
            Map.entry("minecraft:pig", TUSKED), Map.entry("minecraft:horse", MANED), Map.entry("minecraft:donkey", MANED),
            Map.entry("minecraft:wolf", PACK), Map.entry("awaken:gloomstalker", PACK), Map.entry("awaken:wisp_hound", PACK),
            Map.entry("awaken:canopy_prowler", PACK), Map.entry("awaken:thornling", PACK));

    /** A blow on the weak spot from a hunter who knows the kind (research 2+) deals this much more. */
    static final float WEAK = 1.25F;
    /** A herd's alpha limps to its bed at this share of its health, and rests there back up to half. */
    static final float LIMP_AT = 1 / 3F, REST_TO = 0.5F;
    /** A quarry under the limp share bleeds a sign every this many blocks it moves. */
    static final double BLEED_EVERY = 3;

    /** One quarry's fight, saved with it (schemaVersion 1): damage per part, the parts broken, whether it limped, its hunters. */
    public static final class State {
        static final int SCHEMA = 1;
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("hits", Map.of()).forGetter(s -> Map.copyOf(s.hits)),
                Codec.STRING.listOf().optionalFieldOf("broken", List.of()).forGetter(s -> List.copyOf(s.broken)),
                Codec.BOOL.optionalFieldOf("limped", false).forGetter(s -> s.limped),
                UUIDUtil.CODEC.listOf().optionalFieldOf("hunters", List.of()).forGetter(s -> List.copyOf(s.hunters))
        ).apply(i, (schema, hits, broken, limped, hunters) -> {
            State s = new State();
            s.hits.putAll(hits);
            s.broken.addAll(broken);
            s.limped = limped;
            s.hunters.addAll(hunters);
            return s;
        }));

        public final Map<String, Float> hits = new HashMap<>();
        public final Set<String> broken = new LinkedHashSet<>();
        public final Set<UUID> hunters = new LinkedHashSet<>();
        public boolean limped;
    }

    /** Whether a creature is a quarry: a ledger alpha or a named beast (never a carcass). */
    public static boolean is(LivingEntity e) {
        if (Carcass.is(e)) return false;
        CreatureTag tag = CreatureTag.of(e);
        return tag.kind() == CreatureTag.Kind.BEAST || tag.elite() && e.entityTags().contains(Ledger.TAG);
    }

    public static String species(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    /** The ledger's name for a kind: ours without the namespace ("frost_stag"), vanilla with it. */
    public static String ledgerSpecies(Entity e) {
        String id = species(e);
        return id.startsWith(Awaken.MODID + ":") ? id.substring(Awaken.MODID.length() + 1) : id;
    }

    public static Plan plan(Entity e) {
        return PLANS.getOrDefault(species(e), BEAST);
    }

    /** A kind's plan by the ledger's name for it ("frost_stag" or "minecraft:cow"). */
    public static Plan plan(String ledgerSpecies) {
        return PLANS.getOrDefault(ledgerSpecies.indexOf(':') < 0 ? Awaken.MODID + ":" + ledgerSpecies : ledgerSpecies, BEAST);
    }

    public static State state(LivingEntity e) {
        return e.getData(LivingContent.QUARRY);
    }

    // ---- where a blow lands ---------------------------------------------------------------------------------------------

    /** The zone of the body a blow struck: from the arrow, or along the striker's look into the body's box. */
    public static Zone zone(LivingEntity e, @Nullable Entity direct, @Nullable Entity striker) {
        Vec3 hit;
        AABB box = e.getBoundingBox();
        if (direct instanceof Projectile pr) hit = pr.position();
        else if (striker instanceof LivingEntity s) {
            Vec3 eye = s.getEyePosition(), to = eye.add(s.getViewVector(1F).scale(8));
            hit = box.inflate(0.3).clip(eye, to).orElseGet(() -> new Vec3(
                    Math.clamp(eye.x, box.minX, box.maxX), Math.clamp(eye.y, box.minY, box.maxY), Math.clamp(eye.z, box.minZ, box.maxZ)));
        } else return Zone.BODY;
        return zone(e, hit);
    }

    /** The zone of a point on a body: low is the legs, the front top the head, the back end the rear, the rest the body. */
    public static Zone zone(LivingEntity e, Vec3 hit) {
        double yaw = Math.toRadians(e.yBodyRot), fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double half = Math.max(0.3, e.getBbWidth() / 2), h = (hit.y - e.getY()) / Math.max(0.3, e.getBbHeight());
        double f = ((hit.x - e.getX()) * fx + (hit.z - e.getZ()) * fz) / half;
        if (h < 0.3) return Zone.LEGS;
        if (f > 0.35 && h > 0.45) return Zone.HEAD;
        if (f < -0.35) return Zone.REAR;
        return Zone.BODY;
    }

    private static @Nullable Part partAt(Plan plan, Zone z) {
        for (Part p : plan.parts()) if (p.zone() == z) return p;
        if (z == Zone.LEGS) for (Part p : plan.parts()) if (p.zone() == Zone.BODY) return p; // a plan with no legs part: the body takes it
        return null;
    }

    // ---- the fight ------------------------------------------------------------------------------------------------------

    /** Before a blow lands: its zone; a hunter who knows the kind well hits the weak spot harder; the part takes the damage. */
    public static void onDamage(LivingDamageEvent.Pre e) {
        LivingEntity q = e.getEntity();
        if (!(q.level() instanceof ServerLevel level) || !is(q)) return;
        Entity striker = e.getSource().getEntity();
        float amount = hit(level, q, zone(q, e.getSource().getDirectEntity(), striker), e.getNewDamage(), striker);
        if (amount != e.getNewDamage()) e.setNewDamage(amount);
    }

    /** A blow of {@code amount} on a zone of a quarry: the weak spot, its hunters, a limping one rising, the part's share. */
    public static float hit(ServerLevel level, LivingEntity q, Zone z, float amount, @Nullable Entity striker) {
        Plan plan = plan(q);
        if (striker instanceof ServerPlayer p && z == plan.weak() && Tracking.state(p).level(ledgerSpecies(q)) >= 2) amount *= WEAK;
        State s = state(q);
        if (striker instanceof ServerPlayer p) s.hunters.add(p.getUUID());
        if (q.entityTags().contains(Ledger.LIMPING) && striker != null) {
            // found where it lay: it gets up and fights
            q.removeTag(Ledger.LIMPING);
            if (q instanceof PathfinderMob m && striker instanceof LivingEntity le) m.setTarget(le);
        }
        Part part = partAt(plan, z);
        if (part == null || s.broken.contains(part.id())) return amount;
        float total = s.hits.merge(part.id(), amount, Float::sum);
        if (total >= part.share() * q.getMaxHealth()) breakPart(level, q, s, part, striker);
        return amount;
    }

    /** A part breaks: it drops where it was, the quarry staggers, the hunters near are told. */
    static void breakPart(ServerLevel level, LivingEntity q, State s, Part part, @Nullable Entity striker) {
        s.broken.add(part.id());
        Vec3 at = partPos(q, part.zone());
        ItemStack drop = drop(level, q, part.drop(), part.count());
        if (!drop.isEmpty()) {
            ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, drop);
            item.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.25, 0.3, (level.getRandom().nextDouble() - 0.5) * 0.25);
            item.setPickUpDelay(10);
            level.addFreshEntity(item);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, drop.getItem()), at.x, at.y, at.z, 18, 0.15, 0.15, 0.15, 0.12);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.8F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK.value(), SoundSource.HOSTILE, 1F, 0.5F);
        // the stagger: it stops, reels away from the blow and is slow for a breath
        if (q instanceof PathfinderMob m) m.getNavigation().stop();
        q.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3, false, false));
        if (striker != null) q.knockback(0.6, striker.getX() - q.getX(), striker.getZ() - q.getZ(), level.damageSources().generic(), 0F);
        String line = "You broke its " + part.name() + "!";
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(q) < 32 * 32) p.sendSystemMessage(Component.literal(line), true);
    }

    /** Where a zone is on the body, in the world. */
    static Vec3 partPos(LivingEntity e, Zone z) {
        double yaw = Math.toRadians(e.yBodyRot), fx = -Math.sin(yaw), fz = Math.cos(yaw), half = e.getBbWidth() / 2, h = e.getBbHeight();
        return switch (z) {
            case HEAD -> new Vec3(e.getX() + fx * half, e.getY() + h * 0.85, e.getZ() + fz * half);
            case REAR -> new Vec3(e.getX() - fx * half, e.getY() + h * 0.6, e.getZ() - fz * half);
            case LEGS -> new Vec3(e.getX(), e.getY() + h * 0.15, e.getZ());
            default -> new Vec3(e.getX(), e.getY() + h * 0.6, e.getZ());
        };
    }

    /** What a part or carve gives: an item by id (bone if it doesn't exist), or the land tier's hide or core. */
    static ItemStack drop(ServerLevel level, LivingEntity q, String what, int count) {
        if (what.equals("hide") || what.equals("core")) {
            String tier = GameDrops.hideTier(rank(level, q));
            TagKey<Item> key = TagKey.create(Registries.ITEM, Awaken.id("hunter_" + what + "_" + tier));
            List<Item> pool = new ArrayList<>();
            for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(key)) pool.add(h.value());
            if (pool.isEmpty()) return new ItemStack(what.equals("hide") ? Items.LEATHER : Items.BONE, count);
            return new ItemStack(pool.get(q.getRandom().nextInt(pool.size())), count);
        }
        Identifier id = Identifier.tryParse(what);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return new ItemStack(item == null || item == Items.AIR ? Items.BONE : item, count);
    }

    /** The rank of the land a quarry stands in, 0 (E) to 5 (S). */
    static int rank(ServerLevel level, LivingEntity q) {
        return RegionMap.rankOf(Regions.creatureLevel(level, q.blockPosition(), Regions.Role.NEUTRAL));
    }

    // ---- the limp and the blood trail -----------------------------------------------------------------------------------

    /** Wounded quarries this side of the limp share, and where each last bled. */
    private static final Map<UUID, double[]> BLEEDING = new HashMap<>();

    /** After a blow: a herd's alpha at a third of its health limps off to its bed (once a fight). */
    public static void afterDamage(LivingDamageEvent.Post e) {
        LivingEntity q = e.getEntity();
        if (q.level() instanceof ServerLevel level && is(q) && q.isAlive()) wounded(level, q);
    }

    /** A quarry at a third of its health bleeds; a herd's alpha limps off to its bed (once a fight). */
    public static void wounded(ServerLevel level, LivingEntity q) {
        if (q.getHealth() > q.getMaxHealth() * LIMP_AT) return;
        BLEEDING.putIfAbsent(q.getUUID(), new double[]{q.getX(), q.getZ()});
        State s = state(q);
        if (s.limped || !(q instanceof PathfinderMob m)) return;
        Ledger.Pop pop = Tracking.ledger(level.getServer()).popOfBody(q);
        if (pop == null || !pop.kind.equals("herd")) return; // a pack's alpha retreats to its den with its pack (PackBrain)
        s.limped = true;
        q.addTag(Ledger.LIMPING);
        m.setTarget(null);
        q.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 60 * 5, 1, false, false));
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(q) < 32 * 32) p.sendSystemMessage(Component.literal("It's limping away. Follow its blood."), true);
    }

    /** Every second: limping quarries walk on to their bed and rest there; every wounded one bleeds as it goes. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 3 && !BLEEDING.isEmpty()) step(server.overworld());
    }

    /** One second of the wounded. */
    public static void step(ServerLevel level) {
        MinecraftServer server = level.getServer();
        var it = BLEEDING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!(level.getEntity(e.getKey()) instanceof LivingEntity q) || !q.isAlive() || q.getHealth() > q.getMaxHealth() * REST_TO) {
                if (level.getEntity(e.getKey()) instanceof LivingEntity q2) q2.removeTag(Ledger.LIMPING);
                it.remove();
                continue;
            }
            double[] last = e.getValue();
            if (Math.hypot(q.getX() - last[0], q.getZ() - last[1]) >= BLEED_EVERY && q.onGround()) {
                Ledger.Pop pop = Tracking.ledger(server).popOfBody(q);
                float yaw = (float) Math.toDegrees(Math.atan2(-(q.getX() - last[0]), q.getZ() - last[1]));
                Spoor.of(level).lay(Spoor.Kind.BLOOD, q.getX(), q.getY(), q.getZ(), yaw, pop != null ? pop.species : ledgerSpecies(q),
                        pop != null ? pop.id : -1, 1, level.getGameTime(), "", 0);
                last[0] = q.getX();
                last[1] = q.getZ();
            }
            if (q.entityTags().contains(Ledger.LIMPING) && q instanceof PathfinderMob m) limp(level, m);
        }
    }

    /** One limping step: on toward tonight's bed (or, without a herd, away from whoever is near); there, rest and mend. */
    static void limp(ServerLevel level, PathfinderMob m) {
        Ledger l = Tracking.ledger(level.getServer());
        Ledger.Pop pop = l.popOfBody(m);
        double[] bed = pop == null ? null : l.where(pop, PlayDays.current(level.getServer()), 18000);
        if (bed == null) return;
        double d = Math.hypot(bed[0] - m.getX(), bed[1] - m.getZ());
        if (d < 4) {
            m.getNavigation().stop();
            if (level.getGameTime() % 40 < 20) m.heal(1F); // it rests and mends, slowly
            return;
        }
        if (m.getNavigation().isDone()) {
            double k = Math.min(1, 12 / d);
            m.getNavigation().moveTo(m.getX() + (bed[0] - m.getX()) * k, m.getY(), m.getZ() + (bed[1] - m.getZ()) * k, 0.9);
        }
    }

    /** Tests and dev: forget the wounded. */
    static void clear() {
        BLEEDING.clear();
    }
}
