package dev.awaken.living.pulse;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.CreatureTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Hunts and fights (SPEC §21.6): the Wild Hunt and the Beast Duel. */
final class Hunts {
    private Hunts() {}

    static final String HUNT_TAG = "awaken_wild_hunt", LEADER_TAG = "awaken_huntmaster";

    /**
     * The Wild Hunt, on a full moon: a pale pack and its Huntmaster running across the land under a ghost-blue light,
     * horns calling. Hide, follow at a distance, or fight it for the best night of loot in the wilds.
     */
    static final class WildHunt implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 110, 210, r, at -> Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 4);
            return c == null ? null : Behaviours.across(level, p, c, 110);
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
            RandomSource r = level.getRandom();
            for (int i = 0; i < 5; i++) {
                Entity e = Behaviours.bring(level, h, Behaviours.fauna("wisp_hound"), Behaviours.near(level, h.from, 4, r));
                if (e != null) e.addTag(HUNT_TAG);
            }
            Entity lead = Behaviours.bring(level, h, Behaviours.fauna("wisp_hound"), Behaviours.near(level, h.from, 2, r));
            if (lead != null) {
                lead.addTag(HUNT_TAG);
                lead.addTag(LEADER_TAG);
                CreatureTag tag = CreatureTag.of(lead);
                lead.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(tag.level() + 4, tag.kind(), true, "Huntmaster of the Pale Moon"));
                lead.setCustomName(Component.literal("The Huntmaster"));
            }
            horn(level, h);
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            Behaviours.walk(level, h, h.progress(now), 0.1F, 1.3, 4);
            ServerPlayer prey = null;
            for (ServerPlayer pl : level.players()) {
                if (!pl.isSpectator() && !pl.isCreative() && pl.position().distanceToSqr(h.centre) < 18 * 18) prey = pl;
            }
            if (prey != null) {
                for (UUID id : h.participants) {
                    if (level.getEntity(id) instanceof Mob m && m.isAlive() && m.getTarget() == null) m.setTarget(prey);
                }
            }
            if (now % 300 == 0) horn(level, h);
        }

        @Override
        public void end(ServerLevel level, Happening h, HappeningDef def) {
            Behaviours.leave(level, h, 24);
        }

        private static void horn(ServerLevel level, Happening h) {
            var horns = SoundEvents.GOAT_HORN_SOUND_VARIANTS;
            level.playSound(null, Pulse.at(h.centre), horns.get(Math.min(2, horns.size() - 1)).value(), SoundSource.HOSTILE, 8.0F, 0.7F);
        }
    }

    /** The Hunt's loot: essence from every hound, more from the Huntmaster. */
    static void onDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (!dead.entityTags().contains(HUNT_TAG) || !(e.getSource().getEntity() instanceof ServerPlayer)) return;
        int shards = dead.entityTags().contains(LEADER_TAG) ? 6 : 2;
        e.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5, dead.getZ(), new ItemStack(CoreApi.ESSENCE_SHARD.get(), shards)));
    }

    /** Each grazing land's named beast, who walks there when two fight. */
    static final Map<String, String> BEAST = Map.of("greenwild", "briar_sow", "frostwild", "old_antler", "sunscorch", "dune_wyrm",
            "crag", "stonehorn", "canopy", "plumed_coil", "duskwood", "weaver_queen", "mire", "mire_mother");

    /**
     * A Beast Duel: two named beasts at a region's edge, roaring, trees falling. Watch, then finish the weakened
     * winner, or tip the fight. Both carry their parts.
     */
    static final class BeastDuel implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 80, 150, r,
                    at -> BEAST.containsKey(Behaviours.group(level, at)) && Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 3);
            return c == null ? null : new Plan(c, c);
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
            String home = BEAST.getOrDefault(Behaviours.group(level, h.from), "briar_sow");
            List<String> others = new ArrayList<>(BEAST.values());
            others.remove(home);
            String rival = others.get(level.getRandom().nextInt(others.size()));
            Behaviours.bring(level, h, Behaviours.fauna(home), Behaviours.near(level, h.from.offset(-6, 0, 0), 1, level.getRandom()));
            Behaviours.bring(level, h, Behaviours.fauna(rival), Behaviours.near(level, h.from.offset(6, 0, 0), 1, level.getRandom()));
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            List<Mob> fighters = new ArrayList<>(2);
            for (UUID id : h.participants) if (level.getEntity(id) instanceof Mob m && m.isAlive()) fighters.add(m);
            if (fighters.size() == 2) {
                Mob a = fighters.get(0), b = fighters.get(1);
                if (!(a.getTarget() instanceof ServerPlayer)) a.setTarget(b);
                if (!(b.getTarget() instanceof ServerPlayer)) b.setTarget(a);
                h.centre = a.position().add(b.position()).scale(0.5);
            } else if (fighters.size() == 1 && h.stage == 0) {
                // the winner, badly hurt: the moment to finish it
                h.stage = 1;
                Mob w = fighters.getFirst();
                w.setHealth(Math.max(1, w.getMaxHealth() * 0.45F));
                w.setTarget(null);
                h.centre = w.position();
            }
            if (now % 240 == 0 && !fighters.isEmpty()) level.playSound(null, Pulse.at(h.centre), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 6.0F, 0.6F);
        }

        @Override
        public void end(ServerLevel level, Happening h, HappeningDef def) {
            Behaviours.leave(level, h, 48);
        }
    }
}
