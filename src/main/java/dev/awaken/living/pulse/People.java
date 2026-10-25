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

/** People on the roads (SPEC §21.6): the Caravan, and the ambush that may meet it. */
final class People {
    private People() {}

    /**
     * A Caravan: a trader and their llamas walking 140 blocks of open road over six minutes, lanterns and smoke over them.
     * Trade with them on the way. Some days raiders hit it halfway: drive them off and the caravan pays.
     */
    static final class Caravan implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 70, 150, r, at -> Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 2);
            return c == null ? null : Behaviours.across(level, p, c, 70);
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
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
            Behaviours.walk(level, h, t, 0.06F, 0.55, 1);
            if (h.get("ambush") == 1 && h.stage == 0 && t > 0.45F) {
                h.stage = 1;
                Entity master = master(level, h);
                BlockPos at = Behaviours.near(level, Pulse.at(h.along(Math.min(1, t + 0.12F))), 3, level.getRandom());
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
            Behaviours.leave(level, h, 32);
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
