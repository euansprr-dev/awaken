package dev.awaken.living.pulse;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Herds on the move (SPEC §21.6): the Great Migration and the Stampede. */
final class Herds {
    private Herds() {}

    /** The herd of each grazing land, and who hunts it. */
    static final Map<String, String> HERD = Map.of("greenwild", "mossback", "frostwild", "frost_stag", "sunscorch", "dune_ray",
            "crag", "crag_ram", "blossom", "honey_bear");
    static final Map<String, String> HUNTERS = Map.of("greenwild", "gloomstalker", "frostwild", "wisp_hound", "crag", "wisp_hound",
            "blossom", "thornling", "sunscorch", "gloomstalker");

    static @Nullable String herdOf(ServerLevel level, BlockPos at) {
        return HERD.get(Behaviours.group(level, at));
    }

    /**
     * The Great Migration: at dawn a herd of 10-14 crosses a valley, 140 blocks in five minutes, a dust haze over it and
     * birds wheeling above. Twice on the way a few hunters come at the stragglers (hold the ford). At the far side it
     * moves on.
     */
    static final class Migration implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 90, 180, r,
                    at -> herdOf(level, at) != null && Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 3);
            return c == null ? null : Behaviours.across(level, p, c, 70);
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
            String herd = herdOf(level, h.from);
            if (herd == null) herd = "mossback";
            RandomSource r = level.getRandom();
            int n = 10 + r.nextInt(5);
            for (int i = 0; i < n; i++) Behaviours.bring(level, h, Behaviours.fauna(herd), Behaviours.near(level, h.from, 6, r));
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            float t = h.progress(now);
            Behaviours.walk(level, h, t, 0.12F, 1.0, 5);
            // the hunters come twice: a third and two thirds of the way
            int wave = t > 0.66F ? 2 : t > 0.33F ? 1 : 0;
            if (wave > h.stage) {
                h.stage = wave;
                String hunter = HUNTERS.getOrDefault(Behaviours.group(level, Pulse.at(h.centre)), "gloomstalker");
                BlockPos side = Behaviours.near(level, Pulse.at(h.centre).offset(14, 0, 0), 4, level.getRandom());
                for (int i = 0; i < 2 + level.getRandom().nextInt(2); i++) {
                    Entity e = Behaviours.bring(level, h, Behaviours.fauna(hunter), Behaviours.near(level, side, 3, level.getRandom()));
                    if (e instanceof Mob m) m.setTarget(straggler(level, h));
                }
            }
            if (now % 100 == 0) level.playSound(null, Pulse.at(h.centre), SoundEvents.CAMEL_STEP, SoundSource.AMBIENT, 4.0F, 0.6F);
        }

        @Override
        public void end(ServerLevel level, Happening h, HappeningDef def) {
            Behaviours.leave(level, h, 24);
        }
    }

    /** The last of the herd: who the hunters go for. */
    static @Nullable LivingEntity straggler(ServerLevel level, Happening h) {
        LivingEntity worst = null;
        double far = -1;
        Vec3 ahead = h.along(1);
        for (UUID id : h.participants) {
            Entity e = level.getEntity(id);
            if (!(e instanceof LivingEntity le) || !le.isAlive() || !HERD.containsValue(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())) continue;
            double d = le.position().distanceToSqr(ahead);
            if (d > far) {
                far = d;
                worst = le;
            }
        }
        return worst;
    }

    /**
     * A Stampede: a herd at a full run, 90 blocks in 40 seconds along a dust line. It hurts whoever stands in its way
     * (get clear, turn it with the Herdcaller Horn, or stop it with the Kinsong).
     */
    static final class Stampede implements Behaviour {
        private final Map<UUID, Long> hit = new HashMap<>();

        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 50, 110, r,
                    at -> herdOf(level, at) != null && Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 2);
            return c == null ? null : Behaviours.across(level, p, c, 45);
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
            String herd = herdOf(level, h.from);
            if (herd == null) herd = "mossback";
            RandomSource r = level.getRandom();
            for (int i = 0; i < 8 + r.nextInt(3); i++) Behaviours.bring(level, h, Behaviours.fauna(herd), Behaviours.near(level, h.from, 4, r));
            level.playSound(null, h.from, SoundEvents.HORSE_GALLOP, SoundSource.AMBIENT, 6.0F, 0.5F);
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            // the Kinsong stopped it (SPEC §43.14): the herd stands and grazes where it is
            if (h.data.containsKey("calmed")) return;
            Behaviours.walk(level, h, h.progress(now), 0.25F, 2.2, 3);
            for (UUID id : h.participants) {
                Entity e = level.getEntity(id);
                if (e == null || !e.isAlive()) continue;
                for (ServerPlayer pl : level.players()) {
                    if (pl.isSpectator() || pl.isCreative() || pl.distanceToSqr(e) > 2.6 * 2.6) continue;
                    Long last = hit.get(pl.getUUID());
                    if (last != null && now - last < 30) continue;
                    hit.put(pl.getUUID(), now);
                    pl.hurtServer(level, level.damageSources().mobAttack((LivingEntity) e), 4.0F);
                    Vec3 push = pl.position().subtract(e.position()).multiply(1, 0, 1).normalize().scale(1.1);
                    pl.push(push.x, 0.45, push.z);
                    pl.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(pl.getId(), pl.getDeltaMovement()));
                }
            }
            if (now % 40 == 0) level.playSound(null, Pulse.at(h.centre), SoundEvents.HORSE_GALLOP, SoundSource.AMBIENT, 5.0F, 0.55F);
        }

        @Override
        public void end(ServerLevel level, Happening h, HappeningDef def) {
            Behaviours.leave(level, h, 24);
        }
    }
}
