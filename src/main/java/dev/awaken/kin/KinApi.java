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

    /** Puts what fits into a container; the rest comes back. */
    public static net.minecraft.world.item.ItemStack put(Container c, net.minecraft.world.item.ItemStack s) {
        return KinHome.put(c, s);
    }
}
