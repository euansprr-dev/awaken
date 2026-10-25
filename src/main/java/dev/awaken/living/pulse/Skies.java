package dev.awaken.living.pulse;

import dev.awaken.core.PlayDays;
import dev.awaken.mobs.MobTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What the sky does (SPEC §21.6): the Murmuration, the Carrion Circle, the Rolling Storm. */
final class Skies {
    private Skies() {}

    /** Who watched a murmuration, and on which play-day: that night their dreams start more lucid. */
    static final Map<UUID, Long> MURMURED = new ConcurrentHashMap<>();
    static final double MURMUR_LUCIDITY = 10;

    /** The perk source for it (Progression.addPerkSource): lucidity_start while the play-day lasts. */
    static double lucidity(Player p, String perk) {
        if (!perk.equals("awaken:lucidity_start") || !(p instanceof ServerPlayer sp)) return 0;
        Long day = MURMURED.get(p.getUUID());
        return day != null && day == PlayDays.current(sp.level().getServer()) ? MURMUR_LUCIDITY : 0;
    }

    /**
     * The Murmuration: at dusk, a living cloud of finches over water, drawn by the client from the tell alone (no
     * creatures). Watching it makes that night's dreams start more lucid.
     */
    static final class Murmuration implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 60, 140, r, at -> !level.getFluidState(at.below()).isEmpty());
            return c == null ? null : new Plan(c, c);
        }

        @Override
        public void witnessed(ServerPlayer p, Happening h, HappeningDef def) {
            MURMURED.put(p.getUUID(), PlayDays.current(p.level().getServer()));
            p.sendOverlayMessage(Component.literal("The flock turns as one. Something in you settles."));
        }
    }

    /**
     * The Carrion Circle: kites circling high over one spot, where something died: a beast's remains with what it
     * carried (a part, a fallen hunter's gear). The remains stay after the kites go.
     */
    static final class CarrionCircle implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 70, 160, r, at -> Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 3
                    && (level.getBlockState(at.below()).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                    || level.getBlockState(at.below()).is(BlockTags.DIRT) || level.getBlockState(at.below()).is(BlockTags.SAND)));
            return c == null ? null : new Plan(c, c);
        }

        @Override
        public void start(ServerLevel level, Happening h, HappeningDef def) {
            BlockPos at = h.from;
            if (!level.hasChunkAt(at) || !level.getBlockState(at).canBeReplaced()) return;
            RandomSource r = level.getRandom();
            level.setBlock(at, Blocks.BARREL.defaultBlockState(), 3);
            if (level.getBlockEntity(at) instanceof BarrelBlockEntity barrel) {
                List<ItemStack> loot = new ArrayList<>();
                loot.add(new ItemStack(Items.BONE, 3 + r.nextInt(5)));
                loot.add(new ItemStack(Items.LEATHER, 2 + r.nextInt(3)));
                if (r.nextFloat() < 0.5F) loot.add(new ItemStack(Items.ARROW, 4 + r.nextInt(8)));
                if (r.nextFloat() < 0.4F) {
                    var parts = new ArrayList<>(MobTypes.parts().values());
                    if (!parts.isEmpty()) loot.add(new ItemStack(parts.get(r.nextInt(parts.size())).get()));
                }
                if (r.nextFloat() < 0.25F) {
                    ItemStack blade = new ItemStack(Items.IRON_SWORD);
                    blade.setDamageValue(blade.getMaxDamage() / 2 + r.nextInt(Math.max(1, blade.getMaxDamage() / 3)));
                    loot.add(blade);
                }
                for (int i = 0; i < loot.size(); i++) barrel.setItem(r.nextInt(27), loot.get(i));
            }
            // the bones of whatever it was, scattered round it
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (r.nextFloat() < 0.6F) {
                    BlockPos b = Behaviours.surface(level, at.getX() + d.getStepX() * (1 + r.nextInt(2)), at.getZ() + d.getStepZ() * (1 + r.nextInt(2)));
                    if (level.getBlockState(b).canBeReplaced() && Behaviours.dry(level, b)) level.setBlock(b, Blocks.BONE_BLOCK.defaultBlockState(), 3);
                }
            }
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            if (now % 160 == 0) level.playSound(null, h.from.above(24), SoundEvents.PARROT_FLY, SoundSource.AMBIENT, 3.0F, 0.6F);
        }
    }

    /**
     * The Rolling Storm: a dark cell crossing the plains over a few minutes, lightning walking with it. A strike hurts
     * whoever stands under it and turns struck sand to glass.
     */
    static final class RollingStorm implements Behaviour {
        @Override
        public @Nullable Plan plan(ServerLevel level, ServerPlayer p, HappeningDef def, RandomSource r) {
            BlockPos c = Behaviours.spot(level, p, def, 110, 200, r, at -> Behaviours.dry(level, at) && Behaviours.rough(level, at) <= 4);
            return c == null ? null : Behaviours.across(level, p, c, 90);
        }

        @Override
        public void tick(ServerLevel level, Happening h, HappeningDef def, long now) {
            float t = h.progress(now);
            Vec3 c = h.along(t);
            int y = level.hasChunkAt(Pulse.at(c)) ? Behaviours.surface(level, (int) c.x, (int) c.z).getY() : (int) c.y;
            h.centre = new Vec3(c.x, y, c.z);
            RandomSource r = level.getRandom();
            if (r.nextFloat() > 0.35F) return;
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 20;
            int x = (int) (c.x + Math.cos(a) * d), z = (int) (c.z + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) return;
            BlockPos at = Behaviours.surface(level, x, z);
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setVisualOnly(true);
                bolt.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                level.addFreshEntity(bolt);
            }
            for (ServerPlayer pl : level.players()) {
                if (!pl.isSpectator() && !pl.isCreative() && pl.distanceToSqr(Vec3.atBottomCenterOf(at)) < 16) {
                    pl.hurtServer(level, level.damageSources().lightningBolt(), 3.0F);
                }
            }
            if (level.getBlockState(at.below()).is(BlockTags.SAND) && Behaviours.allowed(level, at)) {
                level.setBlock(at.below(), Blocks.GLASS.defaultBlockState(), 3);
            }
        }
    }
}
