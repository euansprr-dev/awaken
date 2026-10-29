package dev.awaken.bells;

import dev.awaken.lucidium.Lucidium;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Lucidium's bells (SPEC §43.13): in a dream, the next dream bell you don't hold rings somewhere 24 to 40 blocks off,
 * bronze and awake, its note every five seconds to follow by ear. Strike it lucid enough for its rank (C 40, B 60, A 80)
 * and you hold it; wake and it's yours. Strike it too dim and your hands go through it; wake without it and the Book
 * keeps a Dreamt lead. One bell a dream.
 */
final class DreamBells {
    private DreamBells() {}

    /** The dream bells in the order dreams offer them. */
    static final List<String> ORDER = List.of("daydream", "lullaby", "falling", "nightmare", "lucid");
    static final int NEAR = 24, FAR = 40, NOTE_EVERY = 100;

    /** Tonight's bell for one dreamer: which, where, and whether they've taken hold of it. Not saved: a dream isn't. */
    private static final class Dream {
        final String bell;
        final BlockPos at;
        boolean held;

        Dream(String bell, BlockPos at) {
            this.bell = bell;
            this.at = at;
        }
    }

    private static final Map<UUID, Dream> DREAMS = new HashMap<>();

    static int need(Rank r) {
        return r.ordinal() <= Rank.C.ordinal() ? 40 : r == Rank.B ? 60 : 80;
    }

    static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((p.tickCount + p.getId()) % 20 != 7) continue;
            Dream d = DREAMS.get(p.getUUID());
            if (!Lucidium.isDreaming(p)) {
                if (d != null) woke(p, d);
                continue;
            }
            if (d == null) {
                d = start(p);
                if (d == null) continue;
                DREAMS.put(p.getUUID(), d);
            }
            if (d.held || !(p.level() instanceof ServerLevel level)) continue;
            long now = level.getGameTime();
            BellCatalog.Def def = BellCatalog.of(d.bell);
            // its note every five seconds, loud enough to follow from the far side of the dream
            if ((now + p.getId()) % NOTE_EVERY < 20 && def != null)
                level.playSound(null, d.at, BellContent.note(def.no()), SoundSource.AMBIENT, 6F, 1F);
            level.sendParticles(p, ParticleTypes.END_ROD, true, true, d.at.getX() + 0.5, d.at.getY() + 1.6, d.at.getZ() + 0.5, 3, 0.4, 0.5, 0.4, 0.01);
        }
    }

    /** A dream begins: the next bell not held, set down on the dream's ground somewhere off to one side. */
    private static @Nullable Dream start(ServerPlayer p) {
        String bell = null;
        for (String id : ORDER) if (!Bells.has(p, id)) {
            bell = id;
            break;
        }
        if (bell == null || !(p.level() instanceof ServerLevel level)) return null;
        var r = level.getRandom();
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = NEAR + r.nextInt(FAR - NEAR + 1);
            int x = (int) Math.floor(p.getX() + Math.cos(a) * dist), z = (int) Math.floor(p.getZ() + Math.sin(a) * dist);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() + 1) continue;
            BlockPos at = new BlockPos(x, y, z);
            level.setBlock(at, BellContent.SLEEPING_BELL.get().defaultBlockState().setValue(SleepingBellBlock.AWAKE, true), Block.UPDATE_CLIENTS);
            Bells.say(p, "[Somewhere in the dream, a bell is ringing.]");
            return new Dream(bell, at);
        }
        return null;
    }

    /** A strike in a dream: true if it was this dreamer's bell (held if they're lucid enough). */
    static boolean strike(ServerPlayer p, BlockPos pos) {
        Dream d = DREAMS.get(p.getUUID());
        if (d == null || d.held || !d.at.equals(pos)) return false;
        BellCatalog.Def def = BellCatalog.of(d.bell);
        if (def == null) return true;
        if (Lucidium.lucidity(p) < need(def.rank())) {
            Bells.say(p, "[Your hands go through it. You aren't lucid enough to hold it.]");
            return true;
        }
        d.held = true;
        p.level().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        p.level().playSound(null, pos, BellContent.note(def.no()), SoundSource.BLOCKS, 3F, 1F);
        Bells.say(p, "[You're holding it. Wake, and it's yours.]");
        return true;
    }

    /** Waking: a bell held comes out of the dream with you; one that wasn't stays a Dreamt lead. */
    private static void woke(ServerPlayer p, Dream d) {
        DREAMS.remove(p.getUUID());
        BellCatalog.Def def = BellCatalog.of(d.bell);
        if (def == null) return;
        if (d.held) {
            Bells.ring(p, def, "carried out of a dream");
            return;
        }
        Bells.dreamt(p, def, "A bell rang in a dream of yours. You weren't lucid enough to reach it and hold it. Be clearer in the next one.");
    }

    static void forget(UUID id) {
        DREAMS.remove(id);
    }
}
