package dev.awaken.lucidium.client;

import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.net.LucidiumNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * What the client knows about the trial dream it's in (SPEC §9.3), from the server's payloads: the goal line, the rings
 * and true path to draw, the clock counted down between updates, a line to flash, the path's glint; and, awake, the bed
 * a dream waits in (a few pearl motes over it at night).
 */
public final class ClientTrial {
    private ClientTrial() {}

    static LucidiumNet.@Nullable TrialStart start;
    /** Where the end's light stands (a Room's goal moves, or hides until its rule shows it), null for nowhere. */
    static @Nullable Vec3 end;
    /** The places dreams showed you (SPEC §9.5), for the Atlas. */
    static List<LucidiumNet.DreamPlace> places = List.of();
    static LucidiumNet.TrialHud hud = new LucidiumNet.TrialHud(0, 0, 1, 0, "", false);
    static int left;
    static String flash = "";
    static int flashTicks;
    /** Ticks since arriving, and since the last phase change (lines fade in). */
    static int arrived, phaseTicks;
    static int glint;
    static Optional<BlockPos> bed = Optional.empty();
    private static final RandomSource RANDOM = RandomSource.create();

    static final int FLASH = 60, GLINT = 40;

    /** In a trial dream right now. */
    public static boolean active() {
        return start != null && ClientDream.dreaming() && ClientDream.hud.has(LucidiumNet.DreamHud.TRIAL);
    }

    static void begin(LucidiumNet.TrialStart s) {
        start = s;
        end = s.end();
        hud = new LucidiumNet.TrialHud(0, 0, 1, 0, "", false);
        left = 0;
        flash = "";
        flashTicks = 0;
        arrived = 0;
        phaseTicks = 0;
        glint = 0;
    }

    static void goal(LucidiumNet.TrialGoal g) {
        end = g.shown() ? g.end() : null;
    }

    /** The Atlas's pearl marks: each place a dream showed you, faded once you've been there. */
    static List<dev.awaken.core.api.MapMarks.Mark> marks(net.minecraft.world.entity.player.Player p) {
        if (places.isEmpty()) return List.of();
        List<dev.awaken.core.api.MapMarks.Mark> out = new java.util.ArrayList<>(places.size());
        for (LucidiumNet.DreamPlace d : places) out.add(new dev.awaken.core.api.MapMarks.Mark(d.pos(), d.found() ? 0xB8A8C8 : 0xE6D2FF, d.label(), d.found()));
        return out;
    }

    static void update(LucidiumNet.TrialHud h) {
        if (h.phase() != hud.phase()) phaseTicks = 0;
        hud = h;
        left = h.left();
        if (!h.flash().isEmpty()) {
            flash = h.flash();
            flashTicks = h.phase() == 2 ? 200 : FLASH;
        }
        if (h.glint()) glint = GLINT;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientDream.dreaming()) {
            beds(mc);
            return;
        }
        if (start == null) return;
        arrived++;
        phaseTicks++;
        if (hud.phase() == 1 && left > 0) left--;
        if (flashTicks > 0) flashTicks--;
        if (glint > 0) {
            glint--;
            if (glint % 2 == 0) glintMotes(mc);
        }
    }

    /** The true path, glinting: pearl motes along it near you. */
    private static void glintMotes(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || start == null) return;
        for (Vec3 v : start.path()) {
            if (v.distanceToSqr(p.position()) > 44 * 44 || v.z < p.getZ() - 4) continue;
            if (RANDOM.nextInt(3) != 0) continue;
            level.addParticle(LucidiumContent.PEARL_MOTE.get(), v.x + (RANDOM.nextDouble() - 0.5) * 0.6, v.y + 0.3 + RANDOM.nextDouble() * 0.4,
                    v.z + (RANDOM.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
    }

    /** Awake at night near your bed while a dream waits: a few pearl motes rise off it. */
    private static void beds(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (bed.isEmpty() || p == null || level == null || p.tickCount % 8 != 0) return;
        BlockPos b = bed.get();
        if (b.distSqr(p.blockPosition()) > 40 * 40) return;
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (tod < 12500 || tod > 23400) return;
        level.addParticle(LucidiumContent.PEARL_MOTE.get(), b.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 1.2, b.getY() + 0.7,
                b.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 1.2, 0, 0.015, 0);
    }

    static void clear() {
        start = null;
        end = null;
        places = List.of();
        bed = Optional.empty();
        flash = "";
        flashTicks = 0;
        glint = 0;
    }
}
