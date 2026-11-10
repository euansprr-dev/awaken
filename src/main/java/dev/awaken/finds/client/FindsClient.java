package dev.awaken.finds.client;

import dev.awaken.finds.FindHeartBlockEntity;
import dev.awaken.finds.FindsContent;
import dev.awaken.finds.FindsData;
import dev.awaken.finds.Kind;
import dev.awaken.finds.Kinds;
import dev.awaken.progression.client.Catalogue;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;

import java.util.ArrayList;
import java.util.List;

/**
 * The finds on the client (SPEC §58.7): the Journal's Places shelf as numbered slots, one per kind in set order, and
 * the tells (a few particles and a quiet sound near a find that hasn't been done).
 */
public final class FindsClient {
    private FindsClient() {}

    public static void init(IEventBus modBus) {
        Catalogue.fixed(StatusSections.Shelf.PLACES, FindsClient::slots);
        FindHeartBlockEntity.drawer = FindsClient::tell;
    }

    private static List<Catalogue.Slot> slots() {
        var mc = Minecraft.getInstance();
        FindsData d = mc.player == null ? FindsData.EMPTY : mc.player.getData(FindsContent.FINDS);
        List<Catalogue.Slot> out = new ArrayList<>(Kinds.all().size());
        for (Kind k : Kinds.all()) {
            int n = d.count(k.id());
            ItemStack icon = k.icon().get();
            String where = k.set().display + ", " + k.number() + " of 8.";
            if (n > 0) out.add(new Catalogue.Slot(k.name(), true, k.learned() + "\n\n" + where + " Found " + (n == 1 ? "once." : n + " times."), icon));
            else if (d.seen().contains(k.id())) out.add(new Catalogue.Slot(k.name(), true, k.see() + "\n\n" + where + " Seen from a distance. You haven't been there yet.", icon));
            else out.add(Catalogue.Slot.unknown());
        }
        return out;
    }

    /** A find's tell, drawn near its heart while the camera is within forty blocks. Small on purpose. */
    private static void tell(Level level, BlockPos at, Kind.Tell tell, boolean done) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || done) return;
        double d = mc.player.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (d > 48 * 48) return;
        var r = level.random;
        double x = at.getX() + 0.5, y = at.getY() + 0.5, z = at.getZ() + 0.5;
        long t = level.getGameTime();
        boolean night = level.getOverworldClockTime() % 24000 > 12500;
        switch (tell) {
            case GLINT -> {
                if (t % 40 < 2) level.addParticle(ParticleTypes.WAX_OFF, x + (r.nextDouble() - 0.5) * 0.6, y + r.nextDouble() * 0.5, z + (r.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
            }
            case SMOKE -> {
                if (t % 6 == 0) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0, 0.05, 0);
            }
            case MOTHS -> {
                if (night && t % 4 == 0) {
                    double a = t * 0.11, rr = 0.7 + Math.sin(t * 0.03) * 0.3;
                    level.addParticle(ParticleTypes.WHITE_ASH, x + Math.cos(a) * rr, y + Math.sin(t * 0.07) * 0.4, z + Math.sin(a) * rr, 0, 0, 0);
                }
            }
            case FIREFLIES -> {
                if (night && r.nextInt(6) == 0) level.addParticle(ParticleTypes.FIREFLY, x + (r.nextDouble() - 0.5) * 3, y + r.nextDouble() * 1.5, z + (r.nextDouble() - 0.5) * 3, 0, 0, 0);
            }
            case CHIME -> {
                if (t % 260 == 0 && d < 28 * 28) level.playLocalSound(x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.5F, 0.6F + r.nextFloat() * 0.5F, false);
            }
            case HUM -> {
                if (t % 120 == 0 && d < 28 * 28) level.playLocalSound(x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.35F, 0.6F, false);
            }
            case DRIP -> {
                if (t % 14 == 0) level.addParticle(ParticleTypes.DRIPPING_WATER, x + (r.nextDouble() - 0.5), y, z + (r.nextDouble() - 0.5), 0, 0, 0);
            }
            case STEAM -> {
                if (t % 5 == 0) level.addParticle(ParticleTypes.CLOUD, x + (r.nextDouble() - 0.5), y, z + (r.nextDouble() - 0.5), 0, 0.04, 0);
            }
            default -> {
            }
        }
    }
}
