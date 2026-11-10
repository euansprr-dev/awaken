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
        FindHeartBlockEntity.airHook = FindsClient::near;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FindsClient::onTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FindsClient::onFogColour);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(FindsClient::onFog);
        // a relic's tooltip says which of its five steps it is at (SPEC §62.7)
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) -> {
            if (e.getEntity() == null) return;
            var line = dev.awaken.finds.Relics.stepLine(e.getEntity(), e.getItemStack());
            if (line != null) e.getToolTip().add(line);
        });
    }

    // ---- a place's air (SPEC §59.4) -----------------------------------------------------------------------------------

    private static dev.awaken.finds.Air want, air;
    private static float wantS, shown;

    /** A heart with an air is loaded: how strongly should it be felt from where the camera is? The strongest this tick wins. */
    private static void near(BlockPos heart, Kind k) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var cam = mc.gameRenderer.mainCamera().position();
        double dx = cam.x - (heart.getX() + 0.5), dz = cam.z - (heart.getZ() + 0.5), d = Math.sqrt(dx * dx + dz * dz);
        float s = (float) Math.max(0, Math.min(1, (k.reach() + 14 - d) / 14.0));
        // a cave's air stays in the cave: only below the place's ground (the heart is two under it)
        if (k.air().below) s *= (float) Math.max(0, Math.min(1, (heart.getY() + 2 - cam.y) / 4.0));
        if (s > wantS) {
            wantS = s;
            want = k.air();
        }
    }

    private static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (wantS > 0 && (air == null || shown < 0.02F)) air = want;
        float to = want == air ? wantS : 0;
        shown += (to - shown) * 0.06F;
        if (Math.abs(to - shown) < 0.004F) shown = to;
        wantS = 0;
        if (air == null || shown <= 0.05F) return;
        var cam = mc.gameRenderer.mainCamera().position();
        var r = mc.level.getRandom();
        var mote = air.mote.get();
        if (mote != null) {
            float n = air.motes * shown;
            for (int i = 0; i < (int) n + (r.nextFloat() < n - (int) n ? 1 : 0); i++)
                mc.level.addParticle(mote, cam.x + (r.nextDouble() - 0.5) * 28, cam.y - 2 + r.nextDouble() * air.band, cam.z + (r.nextDouble() - 0.5) * 28,
                        (r.nextDouble() - 0.5) * 0.02, air.drift, (r.nextDouble() - 0.5) * 0.02);
        }
        long t = mc.level.getGameTime();
        for (int i = 0; i < air.loops.size(); i++) {
            var loop = air.loops.get(i);
            if ((t + i * 37L) % loop.every() != 0 || shown < 0.3F) continue;
            mc.level.playLocalSound(cam.x + (r.nextDouble() - 0.5) * 12, cam.y + r.nextDouble() * 3, cam.z + (r.nextDouble() - 0.5) * 12, loop.sound().get(),
                    net.minecraft.sounds.SoundSource.AMBIENT, loop.volume() * shown, loop.pitch() * (0.94F + r.nextFloat() * 0.12F), false);
        }
    }

    private static void onFogColour(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor e) {
        if (air == null || shown <= 0) return;
        // open-air places tint the distance, they don't bury it (the first tours were pea soup); a cave keeps its thick air
        float s = shown * (air.below ? 0.88F : 0.5F), fr = (air.fog >> 16 & 255) / 255F, fg = (air.fog >> 8 & 255) / 255F, fb = (air.fog & 255) / 255F;
        // keep some of the hour: the place's colour scaled by how bright the sky's own fog is now, so night stays night
        float lum = Math.max(0.18F, Math.min(1, (e.getRed() * 0.3F + e.getGreen() * 0.59F + e.getBlue() * 0.11F) * 1.6F));
        e.setRed(e.getRed() + (fr * lum - e.getRed()) * s);
        e.setGreen(e.getGreen() + (fg * lum - e.getGreen()) * s);
        e.setBlue(e.getBlue() + (fb * lum - e.getBlue()) * s);
    }

    private static void onFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog e) {
        if (air == null || shown <= 0) return;
        float far = e.getFarPlaneDistance(), to = Math.min(far, air.below ? air.far : Math.max(110, air.far * 2.4F));
        e.setFarPlaneDistance(far + (to - far) * shown);
        e.setNearPlaneDistance(e.getNearPlaneDistance() * (1 - (air.below ? 0.8F : 0.35F) * shown));
    }

    private static List<Catalogue.Slot> slots() {
        var mc = Minecraft.getInstance();
        FindsData d = mc.player == null ? FindsData.EMPTY : mc.player.getData(FindsContent.FINDS);
        // the Journal's Places are places: the small kinds are furnishing and are not listed
        List<Catalogue.Slot> out = new ArrayList<>(Kinds.monuments().size());
        for (Kind k : Kinds.monuments()) {
            int n = d.count(k.id());
            ItemStack icon = k.icon().get();
            String where = k.family() == null ? "" : k.family().display + ", " + k.tier() + " of 5.";
            if (n > 0) out.add(new Catalogue.Slot(k.name(), true, k.learned() + "\n\n" + where + " Found " + (n == 1 ? "once." : n + " times."), icon));
            else if (d.seen().contains(k.id())) out.add(new Catalogue.Slot(k.name(), true, k.see() + "\n\n" + where + " You have been here. There is something still to do.", icon));
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
        var r = level.getRandom();
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
