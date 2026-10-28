package dev.awaken.delve;

import dev.awaken.delve.sense.SenseModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * "The cave answers" (SPEC §34.5): {@value #DELAY} ticks after a heart breaks, a knock and a long low ring come back
 * from the rock {@value #AWAY} blocks off (a direction fixed by the lode), tuned to the ore, heard within 48.
 */
public final class CaveAnswers {
    private CaveAnswers() {}

    static final int DELAY = 30, AWAY = 18;
    static final float VOLUME = 3F;

    private record Answer(ServerLevel level, double x, double y, double z, float pitch, long at) {}

    private static final List<Answer> PENDING = new ArrayList<>();

    /** The ore's note: the ring's pitch (iron at 0.89; the deeper and rarer, the higher; Deepglass lowest, strange). */
    static float pitch(Ore ore) {
        return switch (ore) {
            case COAL -> 0.75F;
            case COPPER -> 0.84F;
            case IRON -> 0.89F;
            case REDSTONE -> 0.94F;
            case GOLD -> 1.0F;
            case LAPIS -> 1.06F;
            case EMERALD -> 1.12F;
            case DIAMOND -> 1.19F;
            case DEEPGLASS -> 0.67F;
        };
    }

    /** The heart of {@code l} broke: its answer comes in a moment. */
    static void heartBroke(ServerLevel level, Lode l) {
        BlockPos h = l.heartPos();
        double a = Math.floorMod(l.id() * 0x9E3779B97F4A7C15L >>> 7, 3600L) * Math.PI / 1800.0;
        PENDING.add(new Answer(level, h.getX() + 0.5 + Math.cos(a) * AWAY, h.getY() + 0.5, h.getZ() + 0.5 + Math.sin(a) * AWAY,
                pitch(l.ore()), level.getGameTime() + DELAY));
    }

    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Answer a = PENDING.get(i);
            if (a.level().getGameTime() < a.at()) continue;
            a.level().playSound(null, a.x(), a.y(), a.z(), SenseModule.CAVE_ANSWERS.get(), SoundSource.AMBIENT, VOLUME, a.pitch());
            PENDING.remove(i);
        }
    }

    static int pending() {
        return PENDING.size();
    }

    static void clear() {
        PENDING.clear();
    }
}
