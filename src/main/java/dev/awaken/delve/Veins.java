package dev.awaken.delve;

import dev.awaken.delve.api.Lodes;
import dev.awaken.progression.Progression;
import dev.awaken.progression.XpBudget;
import dev.awaken.signal.Finds;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lodes in play (PLAN 7.3-7.4, SPEC §28.3): where the nearest is, what happens when a heart breaks ("the vein gives":
 * the System names it, a lump of XP, the lode's light and cracks on the client, and for 30 s its blocks fall in two
 * blows each for whoever broke it), the cave-in it may bring down, and keeping each player's client told of the lodes
 * round them (for the glints, Vein Sight and the fast window).
 */
public final class Veins {
    private Veins() {}

    /** The vein gives: how long its blocks break in two blows, and the blows' length in ticks. */
    public static final int WINDOW = 30 * 20, BLOW_TICKS = 4, BLOWS = 2;
    /** A heart's lump of XP (a share of a level) before the ore's weight and the taper by depth. */
    public static final double LUMP = 0.08;
    /** How far round the player the client is told of lodes. */
    static final int NEAR = 64;

    // ---- queries (Lodes API) ----------------------------------------------------------------------------------------

    public static Optional<Lodes.View> nearest(ServerLevel level, BlockPos pos, Ore ore, int radius) {
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        long[] at = new long[1];
        Lode best = null;
        double bestD = (double) radius * radius;
        long bestAt = 0;
        for (Lode l : LodePlans.touching(level.getSeed(), pos.getX() - radius, pos.getZ() - radius, pos.getX() + radius, pos.getZ() + radius, true)) {
            if (l.ore() != ore) continue;
            double d = l.nearest(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, at);
            if (d > bestD || !present(level, l)) continue;
            best = l;
            bestD = d;
            bestAt = at[0];
        }
        return best == null ? Optional.empty() : Optional.of(view(level, best, bestAt, Math.sqrt(bestD)));
    }

    public static Optional<Lodes.View> heartAt(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        long p = pos.asLong();
        for (Lode l : LodePlans.touching(level.getSeed(), pos.getX(), pos.getZ(), pos.getX(), pos.getZ(), true)) {
            if (l.heart() == p) return Optional.of(view(level, l, p, 0));
        }
        return Optional.empty();
    }

    private static Lodes.View view(ServerLevel level, Lode l, long nearest, double d) {
        return new Lodes.View(l.id(), l.ore(), l.heartPos(), l.size(), BlockPos.of(nearest), d, DelveData.get(level).heartBroken(l.id()));
    }

    /**
     * Whether a planned lode is really in the rock: where its blocks are loaded, enough of them must be its ore (an old
     * world's terrain from before lodes has none; a mined-out lode has few). Nothing of it loaded: the plan stands.
     */
    static boolean present(ServerLevel level, Lode l) {
        if (LodePlans.EXTRA.contains(l)) return true;
        int loaded = 0, ore = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long c : l.cells()) {
            m.set(c);
            if (!level.isLoaded(m)) continue;
            loaded++;
            if (l.ore().matches(level.getBlockState(m))) ore++;
        }
        return loaded == 0 || ore >= Math.max(2, loaded / 8);
    }

    static Lode lodeAt(ServerLevel level, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD ? LodePlans.at(level.getSeed(), pos.asLong()) : null;
    }

    // ---- the vein gives -----------------------------------------------------------------------------------------------

    /** A player's window on one opened lode: its blocks left, until when. */
    record Window(long lode, LongOpenHashSet cells, long until) {}

    private static final Map<UUID, Window> WINDOWS = new HashMap<>();

    /** After the break went through (LOW, not cancelled): a planned heart breaking opens its lode, once for the world. */
    static void onBreak(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getState();
        if (!(s.getBlock() instanceof LodeHeartBlock)) return;
        Lode l = lodeAt(level, e.getPos());
        if (l == null || l.heart() != e.getPos().asLong()) return;
        if (!DelveData.get(level).breakHeart(l.id())) return;
        give(level, p, l);
    }

    /** The heart of {@code l} has just broken under {@code p}. */
    static void give(ServerLevel level, ServerPlayer p, Lode l) {
        LongArrayList left = new LongArrayList();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long c : l.cells()) {
            if (c == l.heart()) continue;
            m.set(c);
            BlockState s = level.getBlockState(m);
            if (s.getBlock() != l.ore().heart() && l.ore().matches(s)) left.add(c);
        }
        long now = level.getGameTime();
        WINDOWS.put(p.getUUID(), new Window(l.id(), new LongOpenHashSet(left), now + WINDOW));
        int count = left.size() + 1;
        // the lump, by the ore's weight and its depth (out-levelled content tapers as every world XP does)
        double lump = LUMP * weight(l.ore()) * XpBudget.taper(Progression.profile(p).level(), contentLevel(l.ore(), l.heartPos().getY()));
        if (Progression.profile(p).awakened()) Progression.grantLevels(p, lump, Progression.XpSource.HOME);
        Progression.count(p, "awaken:lodes_opened");
        PickVerses.deed(p, PickVerses.Verse.KINDLE);
        Progression.markNewThing(p, "lode/" + l.ore().id());
        String line = "LODE · " + l.ore().word() + " · " + count;
        boolean first = Progression.profile(p).counter("awaken:lodes_opened") <= 1;
        Progression.discover(p, "lode/" + l.id(), 0);
        Finds.Find f = Finds.Find.of("lode", line, line, hint(l.ore()), l.ore().tint, false);
        Finds.tell(p, first ? f.big("Lode") : f);
        level.playSound(null, l.heartPos(), DelveContent.VEIN_GIVES.get(), SoundSource.BLOCKS, 1.6F, 1.0F);
        long[] cells = left.toLongArray();
        DelveNet.send(p, new DelveNet.VeinOpen(l.id(), l.ore().ordinal(), l.heart(), cells, WINDOW, true));
        BlockPos h = l.heartPos();
        DelveNet.near(level, p, h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5, 40,
                new DelveNet.VeinOpen(l.id(), l.ore().ordinal(), l.heart(), cells, WINDOW, false));
        PickGifts.call(level, p, l, left);
        CaveIns.consider(level, l, p);
    }

    /** The heart's lump by ore: the same weights as an ore's XP, square-rooted (a lode is a lot of ore at once). */
    static double weight(Ore o) {
        return Math.sqrt(switch (o) {
            case COAL -> 0.5;
            case COPPER -> 0.7;
            case IRON -> 1.0;
            case GOLD, REDSTONE, LAPIS -> 1.5;
            case EMERALD -> 4.0;
            case DIAMOND, DEEPGLASS -> 6.0;
        });
    }

    /** Ores deep down are content of a higher level (they taper later): under y 0 level 15, diamond and Deepglass 25. */
    static int contentLevel(Ore o, int y) {
        if (o == Ore.DIAMOND || o == Ore.DEEPGLASS) return 25;
        return y < 0 ? 15 : 0;
    }

    static String hint(Ore o) {
        return switch (o) {
            case COAL -> "A seam of coal: fuel for the Forge";
            case COPPER -> "A seam of copper: bell bronze, the Fork and the lamp";
            case IRON -> "A seam of iron: D-rank gear is bound in it";
            case GOLD -> "A seam of gold: the power's metal";
            case REDSTONE -> "A seam of redstone: the Precursors' circuits";
            case LAPIS -> "A seam of lapis: the ink of Blueprints";
            case EMERALD -> "A seam of emerald: coin for a long road";
            case DIAMOND -> "A seam of diamond: C-rank gear is cut from it";
            case DEEPGLASS -> "A seam of Deepglass: B-rank's resonance";
        };
    }

    /** The two-blow speed (both sides read it: the server through this, the client through its own copy of the cells). */
    static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        Player pl = e.getEntity();
        if (pl.level().isClientSide()) return;
        Window w = WINDOWS.get(pl.getUUID());
        if (w == null || e.getPosition().isEmpty()) return;
        if (pl.level().getGameTime() > w.until()) {
            WINDOWS.remove(pl.getUUID());
            return;
        }
        if (!w.cells().contains(e.getPosition().get().asLong())) return;
        e.setNewSpeed(Math.max(e.getNewSpeed(), twoBlows(e.getState().getDestroySpeed(pl.level(), e.getPosition().get()))));
    }

    /** The mining speed that takes a block of this hardness in {@link #BLOWS} blows (vanilla: progress = speed / hardness / 30). */
    public static float twoBlows(float hardness) {
        return hardness * 30F / (BLOWS * BLOW_TICKS);
    }

    /** Whether this player's blows take this block in two right now (tests). */
    static boolean inWindow(ServerPlayer p, BlockPos pos) {
        Window w = WINDOWS.get(p.getUUID());
        return w != null && p.level().getGameTime() <= w.until() && w.cells().contains(pos.asLong());
    }

    static void forget(UUID player) {
        WINDOWS.remove(player);
        SENT.remove(player);
    }

    static void clear() {
        WINDOWS.clear();
        SENT.clear();
    }

    // ---- telling the client what's near -------------------------------------------------------------------------------

    /** The lode ids last sent to each player. */
    private static final Map<UUID, LongOpenHashSet> SENT = new HashMap<>();

    static void tickPlayer(ServerPlayer p) {
        if ((p.tickCount + p.getId()) % 40 != 0) return;
        ServerLevel level = p.level();
        if (level.dimension() != Level.OVERWORLD) {
            if (SENT.remove(p.getUUID()) != null) DelveNet.send(p, new DelveNet.LodesNear(List.of()));
            return;
        }
        BlockPos at = p.blockPosition();
        List<Lode> near = LodePlans.touching(level.getSeed(), at.getX() - NEAR, at.getZ() - NEAR, at.getX() + NEAR, at.getZ() + NEAR, true);
        LongOpenHashSet ids = new LongOpenHashSet();
        List<DelveNet.Near> out = new ArrayList<>();
        for (Lode l : near) {
            if (Math.abs(l.heartPos().getY() - at.getY()) > NEAR || out.size() >= 16) continue;
            ids.add(l.id());
            out.add(new DelveNet.Near(l.id(), l.ore().ordinal(), l.cells()));
        }
        LongOpenHashSet last = SENT.get(p.getUUID());
        if (last != null && last.equals(ids)) return;
        SENT.put(p.getUUID(), ids);
        DelveNet.send(p, new DelveNet.LodesNear(out));
    }
}
