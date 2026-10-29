package dev.awaken.kin;

import dev.awaken.hearth.Hearth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** What other parts of AWAKEN may ask of the kin (SPEC §12, §43.5-43.12). */
public final class KinApi {
    private KinApi() {}

    /** How many of a kind live at this player's home. */
    public static int livingWith(ServerLevel level, ServerPlayer p, KinKind k) {
        return Hearth.home(p).map(h -> (int) KinWorld.get(level.getServer()).at(h.anchor()).stream().filter(m -> m.kind() == k).count()).orElse(0);
    }

    /** The beasts of a kind living at a home that are loaded now. */
    public static List<KinBeast> loaded(ServerLevel level, BlockPos anchor, KinKind k) {
        List<KinBeast> out = new ArrayList<>();
        for (KinWorld.Homed m : KinWorld.get(level.getServer()).at(anchor))
            if (m.kind() == k && level.getEntity(m.id()) instanceof KinBeast b) out.add(b);
        return out;
    }

    /** The nearest chest or barrel to a spot, within eight blocks. */
    public static @Nullable Container chestNear(ServerLevel level, BlockPos at) {
        return KinHome.chest(level, at);
    }

    /** A woken bell's clapper, given to whoever woke it (SPEC §43.12): what a Chime Perch is built round. */
    public static void clapper(ServerPlayer p) {
        net.minecraft.world.item.ItemStack s = new net.minecraft.world.item.ItemStack(dev.awaken.kin.works.WorksContent.WOKEN_CLAPPER.get());
        if (!p.getInventory().add(s)) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    /** A bell this player rang or woke at {@code at}: wild Chimewrens within {@code reach} hear it (their help). */
    public static void bellHeard(ServerLevel level, ServerPlayer p, BlockPos at, int reach) {
        KinHelp.bellHeard(level, p, at, reach);
    }

    // ---- the Hundred Peal and the Kinsong (SPEC §43.14) ----------------------------------------------------------------

    /**
     * The Hundred Peal, for one who lives under it: every kind they've met comes a step closer at once (Wary to Curious,
     * Curious to Friendly, Friendly to Kin), and the Chimewren, which has been waiting for the song, straight to Friendly
     * at least; every fear of them is forgotten. Returns how many kinds came closer.
     */
    public static int hundredPeal(ServerPlayer p) {
        Kinship ks = Kinship.of(p);
        int n = 0;
        for (var e : ks.kinds.entrySet()) {
            Kinship.Kind s = e.getValue();
            if (!s.met) continue;
            s.fearUntil = 0;
            int to = s.points >= Kinship.FRIENDLY ? Kinship.KIN : s.points >= Kinship.CURIOUS ? Kinship.FRIENDLY : Kinship.CURIOUS;
            if (e.getKey() == KinKind.CHIMEWREN) to = Math.max(to, Kinship.FRIENDLY);
            if (s.points >= to) continue;
            s.points = to;
            n++;
        }
        Kinship.save(p, ks);
        return n;
    }

    /** The Hundred Peal heard in the wild: wild beasts within {@code reach} come to the tower to listen; any Gnawed one is let go. */
    public static int peal(ServerLevel level, BlockPos tower, int reach, long until) {
        int n = 0;
        for (KinBeast b : level.getEntitiesOfClass(KinBeast.class, new net.minecraft.world.phys.AABB(tower).inflate(reach), x -> x.home == null && x.isAlive())) {
            if (b.gnawed()) KinWatch.freed(b, null);
            b.hideUntil = 0;
            b.songAt = tower;
            b.songMode = KinBeast.SONG_GATHER;
            b.songUntil = until;
            n++;
        }
        return n;
    }

    /**
     * The Kinsong rung by {@code p}: their kinds' fear of them is forgotten, every wild beast within {@code reach} stops
     * running and turns to listen for {@code ticks}, and every Gnawed one there is let go (by {@code p}).
     * Returns how many beasts heard it.
     */
    public static int kinsong(ServerLevel level, ServerPlayer p, BlockPos at, int reach, int ticks) {
        Kinship ks = Kinship.of(p);
        for (Kinship.Kind s : ks.kinds.values()) s.fearUntil = 0;
        Kinship.save(p, ks);
        int n = 0;
        long until = level.getGameTime() + ticks;
        for (KinBeast b : level.getEntitiesOfClass(KinBeast.class, new net.minecraft.world.phys.AABB(at).inflate(reach), x -> x.home == null && x.isAlive())) {
            if (b.gnawed()) KinWatch.freed(b, p);
            b.hideUntil = 0;
            b.songAt = at;
            b.songMode = KinBeast.SONG_LISTEN;
            b.songUntil = until;
            n++;
        }
        return n;
    }

    /** Puts what fits into a container; the rest comes back. */
    public static net.minecraft.world.item.ItemStack put(Container c, net.minecraft.world.item.ItemStack s) {
        return KinHome.put(c, s);
    }
}
