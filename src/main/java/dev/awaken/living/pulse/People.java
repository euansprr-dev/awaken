package dev.awaken.living.pulse;

import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** People on the roads (SPEC §21.6, §28.9): the Caravan, and the ambush that may meet it. */
final class People {
    private People() {}

    /**
     * A Caravan: a trader and their llamas walking a stretch of a real road over six minutes (SPEC §28.9: the roads between
     * the settlements; open ground where no road is laid near), lanterns and smoke over them, bound for the settlement at
     * the road's end; where that's in reach they come in to it, and its trade and news arrive with them. Trade with them
     * on the way. Some days raiders hit it halfway: drive them off and the caravan pays, and the village it's bound for
     * hears who stood with it.
     */
    static final class Caravan implements Behaviour {
        /** The road a caravan walks, by happening (kept for the session; a caravan picked up after a restart walks straight). */
        static final java.util.Map<Long, dev.awaken.core.api.RoadHooks.Route> ROUTES = new java.util.concurrent.ConcurrentHashMap<>();
        /** The last route planned, until its happening starts. */
        private static dev.awaken.core.api.RoadHooks.@Nullable Route planned;

        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            var road = dev.awaken.core.api.RoadHooks.stretch(level, p.blockPosition(), 50, 160, 160, r.nextLong());
            if (road.isPresent() && road.get().points().size() >= 2) {
                planned = road.get();
                var pts = planned.points();
                return new Plan(BlockPos.containing(pts.getFirst()), BlockPos.containing(pts.getLast()));
            }
            planned = null;
            BlockPos c = Behaviours.spot(level, p, def, 70, 150, r, at -> Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 3);
            return c == null ? null : Behaviours.across(level, p, c, 70);
        }

        /** The point a fraction t of the way along the caravan's road (its straight line where it has none). */
        static net.minecraft.world.phys.Vec3 along(Happening h, float t) {
            var route = ROUTES.get(h.id);
            if (route == null) return h.along(t);
            var pts = route.points();
            double total = 0;
            for (int i = 1; i < pts.size(); i++) total += pts.get(i).distanceTo(pts.get(i - 1));
            double want = total * Math.clamp(t, 0f, 1f);
            for (int i = 1; i < pts.size(); i++) {
                double seg = pts.get(i).distanceTo(pts.get(i - 1));
                if (want <= seg) return pts.get(i - 1).lerp(pts.get(i), seg <= 0 ? 0 : want / seg);
                want -= seg;
            }
            return pts.getLast();
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
            var road = planned;
            planned = null;
            if (road != null && BlockPos.containing(road.points().getFirst()).equals(h.from)) ROUTES.put(h.id, road);
            RandomSource r = level.getRandom();
            Entity trader = Behaviours.bring(level, h, EntityTypes.WANDERING_TRADER, h.from);
            if (trader instanceof WanderingTrader w) {
                w.setDespawnDelay(def.durationS() * 20 + 2400);
                w.setCustomName(Component.literal("Caravan Master"));
            }
            for (int i = 0; i < 2 + r.nextInt(2); i++) {
                Entity llama = Behaviours.bring(level, h, EntityTypes.TRADER_LLAMA, Behaviours.near(level, h.from, 2, r));
                if (llama instanceof TraderLlama l && trader != null) l.setLeashedTo(trader, true);
            }
            h.put("ambush", r.nextFloat() < 0.4F ? 1 : 0);
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            float t = h.progress(now);
            walk(level, h, t, 0.06F, 0.55, 1);
            if (h.get("ambush") == 1 && h.stage == 0 && t > 0.45F) {
                h.stage = 1;
                Entity master = master(level, h);
                BlockPos at = Behaviours.near(level, Pulse.at(along(h, Math.min(1, t + 0.12F))), 3, level.getRandom());
                for (int i = 0; i < 3 + level.getRandom().nextInt(2); i++) {
                    Entity raider = Behaviours.bring(level, h, EntityTypes.PILLAGER, Behaviours.near(level, at, 3, level.getRandom()));
                    if (raider instanceof Mob m && master instanceof Mob target) m.setTarget(target);
                }
                level.playSound(null, at, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 5.0F, 1.1F);
            }
            // raiders gone and the master standing: the caravan pays whoever is with it
            if (h.stage == 1 && raiders(level, h) == 0) {
                h.stage = 2;
                if (master(level, h) != null) {
                    for (ServerPlayer pl : level.players()) {
                        if (pl.position().distanceToSqr(h.centre) > 40 * 40) continue;
                        Progression.worldXp(pl, "pulse", 0.15);
                        pl.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, 4), net.minecraft.util.Prediction.SERVER_ONLY);
                        dev.awaken.living.quest.QuestEngine.sayLine(pl, "Caravan Master: You've a good arm. Take this, and our thanks. We'll tell it at the next fire.");
                    }
                }
            }
        }

        @Override
        public void end(ServerLevel level, Happening h, HappeningDef def) {
            var route = ROUTES.remove(h.id);
            // (SPEC §28.9) it came in: its trade and news arrive with it, if its master made it
            if (route != null && route.arrives() && !route.toKey().isEmpty() && master(level, h) != null)
                dev.awaken.core.api.RoadHooks.arrived(level, route.toKey(), route.from(), h.get("ambush") == 1 && h.stage >= 2);
            Behaviours.leave(level, h, 32);
        }

        /** Its people walk the road: each heads for the point {@code lead} ahead along it, spread a little; the centre follows them. */
        static void walk(ServerLevel level, Happening h, float t, float lead, double speed, int spread) {
            if (!ROUTES.containsKey(h.id)) {
                Behaviours.walk(level, h, t, lead, speed, spread);
                return;
            }
            net.minecraft.world.phys.Vec3 target = along(h, Math.min(1, t + lead));
            double sx = 0, sy = 0, sz = 0;
            int n = 0, k = 0;
            for (UUID id : h.participants) {
                Entity e = level.getEntity(id);
                k++;
                if (!(e instanceof net.minecraft.world.entity.PathfinderMob m) || !m.isAlive()) continue;
                sx += m.getX();
                sy += m.getY();
                sz += m.getZ();
                n++;
                if (m.getTarget() != null) continue;
                double ox = ((k * 37) % (spread * 2 + 1)) - spread, oz = ((k * 53) % (spread * 2 + 1)) - spread;
                m.getNavigation().moveTo(target.x + ox, target.y, target.z + oz, speed);
            }
            h.centre = n > 0 ? new net.minecraft.world.phys.Vec3(sx / n, sy / n, sz / n) : along(h, t);
        }

        private static @Nullable Entity master(ServerLevel level, Happening h) {
            for (UUID id : h.participants) {
                Entity e = level.getEntity(id);
                if (e instanceof WanderingTrader && e.isAlive()) return e;
            }
            return null;
        }

        private static int raiders(ServerLevel level, Happening h) {
            int n = 0;
            for (UUID id : h.participants) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive() && e.getType() == EntityTypes.PILLAGER) n++;
            }
            return n;
        }
    }
}
