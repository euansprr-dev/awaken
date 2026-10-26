package dev.awaken.citizens.client;

import dev.awaken.citizens.CitizensContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The places in a village that sound (SPEC §28.6), from the emitters its settlement sends (outside Carillon, whose own
 * places are {@link CitySound}'s): the smithy's hammer through the working day, the mill wheel turning in its race, the
 * inn's voices of an evening. Under them lies the village's bed (the server's ambience slot). Checked every ten ticks;
 * at most one loop of a kind, the nearest.
 */
public final class VillageSound {
    private VillageSound() {}

    private static long[] seen;
    private static final List<double[]> FORGES = new ArrayList<>(), MILLS = new ArrayList<>(), INNS = new ArrayList<>();
    private static CitySound.Loop mill, inn;
    private static long nextAnvil;
    private static final RandomSource R = RandomSource.create();

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player p = mc.player;
        if (level == null || p == null || mc.isPaused()) return;
        if (level.getGameTime() % 10 != 3) return;
        long[] pos = CitizenFx.positions();
        if (pos != seen) {
            seen = pos;
            FORGES.clear();
            MILLS.clear();
            INNS.clear();
            byte[] kind = CitizenFx.kinds();
            for (int i = 0; i < pos.length && i < kind.length; i++) {
                int x = BlockPos.getX(pos[i]), y = BlockPos.getY(pos[i]), z = BlockPos.getZ(pos[i]);
                if (CityLife.inside(x, z, 24)) continue;
                double[] at = {x + 0.5, y, z + 0.5};
                switch (kind[i]) {
                    case 3 -> FORGES.add(new double[]{x + 0.5, y - 6, z + 0.5});
                    case 6 -> MILLS.add(at);
                    case 2 -> INNS.add(new double[]{x + 0.5, y - 5, z + 0.5});
                    default -> {
                    }
                }
            }
        }
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        double x = p.getX(), z = p.getZ();
        // the forge: a hammer on the anvil now and then through the working day, the bellows sometimes
        if (tod >= 900 && tod < 10800 && level.getGameTime() >= nextAnvil) {
            double[] f = nearest(FORGES, x, z, 34);
            if (f != null) {
                level.playLocalSound(f[0], f[1], f[2], CitizensContent.ANVIL.get(), SoundSource.AMBIENT, 0.7f, 0.92f + R.nextFloat() * 0.16f, false);
                if (R.nextInt(6) == 0)
                    level.playLocalSound(f[0], f[1], f[2], CitizensContent.BELLOWS.get(), SoundSource.AMBIENT, 0.5f, 1f, false);
                nextAnvil = level.getGameTime() + 30 + R.nextInt(70);
            }
        }
        mill = loop(mill, nearest(MILLS, x, z, 28), CitizensContent.MILL.get(), 0.8f);
        boolean evening = tod >= 11800 && tod < 18000;
        inn = loop(inn, evening ? nearest(INNS, x, z, 22) : null, CitizensContent.TAVERN.get(), 0.6f);
    }

    private static double[] nearest(List<double[]> pts, double x, double z, double within) {
        double best = within * within;
        double[] out = null;
        for (double[] q : pts) {
            double d = (q[0] - x) * (q[0] - x) + (q[2] - z) * (q[2] - z);
            if (d < best) {
                best = d;
                out = q;
            }
        }
        return out;
    }

    /** Keeps one loop at the nearest place (eased in), or eases it out. */
    private static CitySound.Loop loop(CitySound.Loop l, double[] at, SoundEvent ev, float level) {
        Minecraft mc = Minecraft.getInstance();
        if (l != null && (l.isStopped() || at == null || l.at != at)) {
            l.ending = true;
            if (l.isStopped() || !mc.getSoundManager().isActive(l)) l = null;
            else if (at == null) return l;
            else l = null;
        }
        if (at == null || l != null) return l;
        CitySound.Loop n = new CitySound.Loop(ev, at);
        n.target = level;
        mc.getSoundManager().play(n);
        return n;
    }

    public static void clear() {
        seen = null;
        FORGES.clear();
        MILLS.clear();
        INNS.clear();
        mill = null;
        inn = null;
    }
}
