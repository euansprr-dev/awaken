package dev.awaken.living.hunt;

import dev.awaken.core.Later;
import dev.awaken.living.LivingContent;
import dev.awaken.living.population.Herds;
import dev.awaken.living.population.Spoor;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A quarry's body and the carve (SPEC §41.4). When a quarry someone fought dies, its body lies where it fell for a minute (it
 * falls in its death throes, then lies on its side). Every hunter who hurt it carves it three times (use it): hide by the
 * land's tier first for an alpha, then hide, its part, meat or sinew. Each hunter studies the kind for good (ten research).
 * When everyone has carved, or the minute is up, it's gone and its bones stay as a sign.
 */
public final class Carcass {
    private Carcass() {}

    /** How long a body lies (ticks), carves per hunter, ticks between one hunter's carves, research a hunt gives. */
    static final int LIES = 1200, CARVES = 3, CARVE_EVERY = 12, HUNT_RESEARCH = 10;
    /** A kill sign left by hunters (its species). */
    public static final String HUNTER = "hunter";

    /** One body on the ground: who may carve it and how often each has, and what it was. */
    static final class Body {
        final Set<UUID> hunters = new LinkedHashSet<>();
        final Map<UUID, Integer> carved = new HashMap<>();
        final Map<UUID, Long> last = new HashMap<>();
        String species = "", ledgerSpecies = "";
        boolean alpha;
        Quarry.Plan plan = Quarry.BEAST;
    }

    private static final Map<UUID, Body> BODIES = new HashMap<>();

    public static boolean is(Entity e) {
        return e.hasData(LivingContent.CARCASS) && e.getData(LivingContent.CARCASS) > 0;
    }

    // ---- the fall -------------------------------------------------------------------------------------------------------

    /** A quarry someone fought died: its hunters study it, and once it has fallen its body lies there. */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (e.isCanceled() || !(dead.level() instanceof ServerLevel level) || !Quarry.is(dead)) return;
        Quarry.State s = Quarry.state(dead);
        if (s.hunters.isEmpty()) return;
        Body b = new Body();
        b.hunters.addAll(s.hunters);
        b.species = Quarry.species(dead);
        b.ledgerSpecies = Quarry.ledgerSpecies(dead);
        b.alpha = dev.awaken.core.api.CreatureTag.of(dead).elite();
        b.plan = Quarry.plan(dead);
        String name = Herds.name(b.ledgerSpecies, false);
        for (UUID id : b.hunters) {
            if (!(level.getEntity(id) instanceof ServerPlayer p)) continue;
            HuntState before = Tracking.state(p);
            HuntState after = before.study(b.ledgerSpecies, HUNT_RESEARCH);
            p.setData(LivingContent.HUNT, after);
            String line = "The " + name + " is down. Its body will lie here for a minute: use it to carve it (three times).";
            if (after.level(b.ledgerSpecies) > before.level(b.ledgerSpecies)) line += " You know " + Herds.name(b.ledgerSpecies, true)
                    + " better now (" + Tracking.studyName(after.level(b.ledgerSpecies)) + ").";
            p.sendSystemMessage(Component.literal(line));
        }
        // the death throes play out first (the fall, the puff), then the body lies where it fell
        double x = dead.getX(), y = dead.getY(), z = dead.getZ();
        float yaw = dead.getYRot(), body = dead.yBodyRot;
        Mob made = make(level, dead);
        if (made == null) return;
        Later.run(level.getServer(), 20, () -> {
            made.snapTo(x, y, z, yaw, 0);
            made.setYBodyRot(body);
            made.setYHeadRot(body);
            made.setData(LivingContent.CARCASS, level.getGameTime() + LIES);
            if (level.addFreshEntity(made)) BODIES.put(made.getUUID(), b);
        });
    }

    /** A still copy of the dead: the same kind, size, age and colour; no mind, no voice, nothing can hurt it. */
    static @Nullable Mob make(ServerLevel level, LivingEntity dead) {
        if (!(dead.getType().create(level, EntitySpawnReason.TRIGGERED) instanceof Mob m)) return null;
        m.setNoAi(true);
        m.setPermanentlyInvulnerable(true);
        m.setSilent(true);
        m.setPersistenceRequired();
        m.setData(LivingContent.CARCASS, 1L); // a carcass from the start: nothing dresses it as a living creature
        if (dead instanceof AgeableMob a && m instanceof AgeableMob c) c.setAge(a.getAge());
        if (dead instanceof net.minecraft.world.entity.animal.sheep.Sheep a && m instanceof net.minecraft.world.entity.animal.sheep.Sheep c) c.setColor(a.getColor());
        var from = dead.getAttribute(Attributes.SCALE);
        var to = m.getAttribute(Attributes.SCALE);
        if (from != null && to != null) to.setBaseValue(from.getValue());
        return m;
    }

    /** A body loaded from disk: gone if its minute is up, else anyone may carve it (who fought it was not saved). */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !is(e.getEntity())) return;
        if (level.getGameTime() > e.getEntity().getData(LivingContent.CARCASS)) {
            e.setCanceled(true);
            return;
        }
        BODIES.computeIfAbsent(e.getEntity().getUUID(), k -> {
            Body b = new Body();
            b.species = Quarry.species(e.getEntity());
            b.ledgerSpecies = Quarry.ledgerSpecies(e.getEntity());
            b.plan = Quarry.plan(e.getEntity());
            return b;
        });
    }

    // ---- the carve ------------------------------------------------------------------------------------------------------

    /** Using a body carves it (both sides answer, so the hand moves at once). */
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!is(e.getTarget())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getEntity() instanceof ServerPlayer p && e.getTarget().level() instanceof ServerLevel level) carve(level, p, e.getTarget());
    }

    /** One carve: a hunter's own, three each; when every hunter has carved all theirs, the body is done. */
    public static boolean carve(ServerLevel level, ServerPlayer p, Entity body) {
        return carve(level, p, body, false);
    }

    public static boolean carve(ServerLevel level, ServerPlayer p, Entity body, boolean hurried) {
        Body b = BODIES.get(body.getUUID());
        if (b == null) return false;
        if (!b.hunters.isEmpty() && !b.hunters.contains(p.getUUID())) {
            p.sendSystemMessage(Component.literal("You didn't fight this one, so it isn't yours to carve."), true);
            return false;
        }
        int done = b.carved.getOrDefault(p.getUUID(), 0);
        if (done >= CARVES) {
            p.sendSystemMessage(Component.literal("You've carved all you can from it."), true);
            return false;
        }
        long now = level.getGameTime();
        if (!hurried && now - b.last.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2) < CARVE_EVERY) return false;
        b.last.put(p.getUUID(), now);
        b.carved.put(p.getUUID(), done + 1);
        ItemStack got = roll(level, b, body, done);
        String what = got.getHoverName().getString();
        if (!p.getInventory().add(got.copy())) p.drop(got.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
        var mid = body.position().add(0, body.getBbHeight() * 0.3, 0);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, got.getItem()), mid.x, mid.y, mid.z, 10, 0.25, 0.1, 0.25, 0.05);
        level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.6F, 0.6F + level.getRandom().nextFloat() * 0.2F);
        level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.5F, 0.8F);
        p.sendSystemMessage(Component.literal("Carved: " + what + " (" + (done + 1) + " of " + CARVES + ")"), true);
        if (allCarved(b, level)) gone(level, body, b);
        return true;
    }

    /** What a carve gives: an alpha's first is always the land's hide; then hide, its part, meat or sinew. */
    static ItemStack roll(ServerLevel level, Body b, Entity body, int done) {
        LivingEntity q = (LivingEntity) body;
        if (b.alpha && done == 0) return Quarry.drop(level, q, "hide", 1);
        int r = level.getRandom().nextInt(100);
        if (r < 40) return Quarry.drop(level, q, "hide", 1);
        if (r < 65) {
            Quarry.Part part = b.plan.parts().getFirst();
            return Quarry.drop(level, q, part.drop(), Math.max(1, part.count() / 2));
        }
        if (r < 90) return new ItemStack(meat(b.species), 2);
        return new ItemStack(Items.STRING, 2);
    }

    static net.minecraft.world.item.Item meat(String species) {
        return switch (species) {
            case "minecraft:cow" -> Items.BEEF;
            case "minecraft:pig" -> Items.PORKCHOP;
            case "minecraft:sheep" -> Items.MUTTON;
            case "minecraft:goat", "minecraft:horse", "minecraft:donkey" -> Items.LEATHER;
            default -> Items.BONE;
        };
    }

    private static boolean allCarved(Body b, ServerLevel level) {
        if (b.hunters.isEmpty()) return false; // anyone's: it lies its minute
        for (UUID id : b.hunters) {
            if (b.carved.getOrDefault(id, 0) < CARVES && level.getEntity(id) instanceof ServerPlayer) return false;
        }
        return true;
    }

    /** The body is done: a puff, and its bones stay as a sign that hunters killed it here. */
    static void gone(ServerLevel level, Entity body, @Nullable Body b) {
        level.sendParticles(ParticleTypes.POOF, body.getX(), body.getY() + 0.3, body.getZ(), 12, 0.4, 0.2, 0.4, 0.02);
        Spoor.of(level).lay(Spoor.Kind.KILL, body.getX(), body.getY(), body.getZ(), body.getYRot(), HUNTER, -1, 1, level.getGameTime(),
                b != null ? b.ledgerSpecies : Quarry.ledgerSpecies(body), 0);
        BODIES.remove(body.getUUID());
        body.discard();
    }

    /** Every second: bodies whose minute is up go. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 11 || BODIES.isEmpty()) return;
        ServerLevel level = server.overworld();
        var it = BODIES.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity body = level.getEntity(e.getKey());
            if (body == null) continue; // unloaded: it goes when it loads (onJoin)
            if (level.getGameTime() > body.getData(LivingContent.CARCASS)) {
                it.remove();
                gone(level, body, e.getValue());
            }
        }
    }

    /** Tests: a body for a quarry with these hunters, at once (no fall). */
    public static @Nullable Mob lay(ServerLevel level, LivingEntity dead, Set<UUID> hunters) {
        Mob made = make(level, dead);
        if (made == null) return null;
        made.snapTo(dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), 0);
        made.setData(LivingContent.CARCASS, level.getGameTime() + LIES);
        if (!level.addFreshEntity(made)) return null;
        Body b = new Body();
        b.hunters.addAll(hunters);
        b.species = Quarry.species(dead);
        b.ledgerSpecies = Quarry.ledgerSpecies(dead);
        b.alpha = dev.awaken.core.api.CreatureTag.of(dead).elite();
        b.plan = Quarry.plan(dead);
        BODIES.put(made.getUUID(), b);
        return made;
    }
}
