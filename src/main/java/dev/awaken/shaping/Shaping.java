package dev.awaken.shaping;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.Throttle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the Mallet does on the server (SPEC §52): knocks, looks, runs of looks and undo. */
public final class Shaping {
    private Shaping() {}

    public static final int UNDO_MAX = 32, RUN_MAX = 64;
    private static final double REACH_BUFFER = 2.0;

    /** A block as it stood: enough to tell if it has been changed since, and to put it back. */
    record Snapshot(BlockState state, @Nullable BlockState base, @Nullable BlockState look) {
        static Snapshot of(Level level, BlockPos pos) {
            BlockState s = level.getBlockState(pos);
            if (level.getBlockEntity(pos) instanceof ShapedBlockEntity be) return new Snapshot(s, be.base(), be.look());
            return new Snapshot(s, null, null);
        }

        boolean matches(Level level, BlockPos pos) {
            Snapshot now = of(level, pos);
            return now.state == this.state && now.base == this.base && now.look == this.look;
        }

        void restore(ServerLevel level, BlockPos pos) {
            level.setBlock(pos, this.state, Block.UPDATE_ALL);
            if (this.base != null && this.look != null && level.getBlockEntity(pos) instanceof ShapedBlockEntity be) be.set(this.base, this.look);
        }
    }

    private record Change(BlockPos pos, Snapshot before, Snapshot after) {}

    private record Undo(ResourceKey<Level> dimension, List<Change> changes) {}

    private static final class Ripple {
        final UUID player;
        final ResourceKey<Level> dimension;
        final Block from;
        final BlockState look;
        final List<List<BlockPos>> layers;
        final Undo undo;
        int next;

        Ripple(UUID player, ResourceKey<Level> dimension, Block from, BlockState look, List<List<BlockPos>> layers, Undo undo) {
            this.player = player;
            this.dimension = dimension;
            this.from = from;
            this.look = look;
            this.layers = layers;
            this.undo = undo;
        }
    }

    private static final Map<UUID, ArrayDeque<Undo>> UNDO = new HashMap<>();
    private static final Map<UUID, Ripple> RIPPLES = new HashMap<>();

    // ---- right click ------------------------------------------------------------------------------------------------------

    static void useOn(ServerPlayer sp, ServerLevel level, BlockPos pos, Direction face, Vec3 hit, ItemStack stack) {
        if (!ShapingConfig.mallet()) return;
        if (!ShapingRules.mayChange(sp, level, pos)) {
            // you can't cut what's kept whole, but you can always learn from it
            BlockState look = Looks.lookAt(level, pos);
            if (look != null && Looks.offerable(look) && LookBook.learn(sp, look.getBlock())) learned(sp, look);
            else refuse(sp, "awaken.shaping.refuse.kept");
            return;
        }
        MalletData data = MalletData.of(stack);
        if (data.mode() == MalletData.Mode.KNOCK) {
            knock(sp, level, pos, face, hit);
            return;
        }
        BlockState look = carried(sp, data);
        if (look == null) {
            refuse(sp, "awaken.shaping.refuse.no_look");
            return;
        }
        Change c = apply(sp, level, pos, look, face);
        if (c != null) remember(sp, new Undo(level.dimension(), new ArrayList<>(List.of(c))));
    }

    private static @Nullable BlockState carried(ServerPlayer sp, MalletData data) {
        if (data.look().isEmpty()) return null;
        Block b = BuiltInRegistries.BLOCK.getValue(data.look().get());
        return Looks.offerable(b.defaultBlockState()) && LookBook.knows(sp, b) ? b.defaultBlockState() : null;
    }

    /** A knock: what this part of this block becomes next. True if it changed. */
    static boolean knock(ServerPlayer sp, ServerLevel level, BlockPos pos, Direction face, Vec3 hit) {
        BlockState state = level.getBlockState(pos);
        RealShapes.Parts p = RealShapes.read(level, pos, state);
        if (p == null) {
            String why = RealShapes.refusal(state);
            if (why != null) refuse(sp, why);
            return false;
        }
        Snapshot before = Snapshot.of(level, pos);
        ShapingRules.Aim aim = ShapingRules.aim(p.shape(), p.facing(), face, hit.x - pos.getX(), hit.y - pos.getY(), hit.z - pos.getZ());
        ShapingRules.Cut cut = ShapingRules.next(p.shape(), p.facing(), aim);
        RealShapes.write(level, pos, new RealShapes.Parts(p.base(), p.look(), cut.shape(), cut.facing()));
        remember(sp, new Undo(level.dimension(), new ArrayList<>(List.of(new Change(pos.immutable(), before, Snapshot.of(level, pos))))));
        fx(level, pos, p.look(), ShapingPayloads.FX_KNOCK, face, sp);
        return true;
    }

    /** Puts a look on one block. A whole block taking a look from its own family becomes that real block; otherwise it's worn. */
    private static @Nullable Change apply(ServerPlayer sp, ServerLevel level, BlockPos pos, BlockState look, Direction face) {
        BlockState state = level.getBlockState(pos);
        RealShapes.Parts p = RealShapes.read(level, pos, state);
        if (p == null) {
            String why = RealShapes.refusal(state);
            if (why != null) refuse(sp, why);
            return null;
        }
        if (p.look().is(look.getBlock())) return null;
        BlockState worn = Looks.fit(look, p.look());
        Snapshot before = Snapshot.of(level, pos);
        boolean real = p.shape() == Shape.FULL && p.base() == p.look() && Looks.sameFamily(p.base().getBlock(), look.getBlock());
        RealShapes.write(level, pos, real ? new RealShapes.Parts(worn, worn, Shape.FULL, p.facing()) : new RealShapes.Parts(p.base(), worn, p.shape(), p.facing()));
        fx(level, pos, worn, ShapingPayloads.FX_LOOK, face, sp);
        return new Change(pos.immutable(), before, Snapshot.of(level, pos));
    }

    // ---- from the ring and the mouse --------------------------------------------------------------------------------------

    static void onSetLook(ServerPlayer sp, ShapingPayloads.SetLook msg) {
        if (!ShapingConfig.mallet() || !holding(sp) || !(sp.level() instanceof ServerLevel level) || !sp.isWithinBlockInteractionRange(msg.pos(), REACH_BUFFER)) return;
        Block b = BuiltInRegistries.BLOCK.getValue(msg.look());
        BlockState look = b.defaultBlockState();
        if (!Looks.offerable(look) || !LookBook.knows(sp, b)) return;
        if (!ShapingRules.mayChange(sp, level, msg.pos())) {
            refuse(sp, "awaken.shaping.refuse.kept");
            return;
        }
        sp.getMainHandItem().set(ShapingContent.MALLET_DATA.get(), MalletData.of(sp.getMainHandItem()).carrying(msg.look()));
        if (!msg.run()) {
            Change c = apply(sp, level, msg.pos(), look, seen(sp, msg.pos()));
            if (c != null) remember(sp, new Undo(level.dimension(), new ArrayList<>(List.of(c))));
            return;
        }
        startRun(sp, level, msg.pos(), look);
    }

    /** Sets a run going: the look spreads out from the block you chose, a ring each tick. */
    static void startRun(ServerPlayer sp, ServerLevel level, BlockPos pos, BlockState look) {
        BlockState from = Looks.lookAt(level, pos);
        if (from == null) return;
        Undo undo = new Undo(level.dimension(), new ArrayList<>());
        remember(sp, undo);
        RIPPLES.put(sp.getUUID(), new Ripple(sp.getUUID(), level.dimension(), from.getBlock(), look, Looks.run(level, pos, RUN_MAX), undo));
    }

    static void onPick(ServerPlayer sp, BlockPos pos) {
        if (!holding(sp) || !(sp.level() instanceof ServerLevel level) || !sp.isWithinBlockInteractionRange(pos, REACH_BUFFER)) return;
        BlockState look = Looks.lookAt(level, pos);
        if (look == null || !Looks.offerable(look)) {
            refuse(sp, "awaken.shaping.refuse.not_a_look");
            return;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(look.getBlock());
        ItemStack stack = sp.getMainHandItem();
        stack.set(ShapingContent.MALLET_DATA.get(), MalletData.of(stack).carrying(id).in(MalletData.Mode.LOOK));
        if (LookBook.learn(sp, look.getBlock())) learned(sp, look);
    }

    static void onTurnMode(ServerPlayer sp, int by) {
        if (!holding(sp)) return;
        ItemStack stack = sp.getMainHandItem();
        stack.set(ShapingContent.MALLET_DATA.get(), MalletData.of(stack).turned(by));
    }

    private static boolean holding(ServerPlayer sp) {
        return sp.getMainHandItem().is(ShapingContent.MALLET.get());
    }

    // ---- undo -------------------------------------------------------------------------------------------------------------

    private static void remember(ServerPlayer sp, Undo undo) {
        ArrayDeque<Undo> list = UNDO.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
        list.addLast(undo);
        while (list.size() > UNDO_MAX) list.pollFirst();
    }

    /** Takes back the last thing you did. Anything changed since is left alone. Returns how many blocks went back. */
    static int undo(ServerPlayer sp) {
        RIPPLES.remove(sp.getUUID());
        ArrayDeque<Undo> list = UNDO.get(sp.getUUID());
        Undo undo = list == null ? null : list.pollLast();
        if (undo == null || !(sp.level() instanceof ServerLevel level) || !level.dimension().equals(undo.dimension())) {
            refuse(sp, "awaken.shaping.refuse.nothing_to_undo");
            return 0;
        }
        int back = 0;
        for (int i = undo.changes().size() - 1; i >= 0; i--) {
            Change c = undo.changes().get(i);
            if (!level.isLoaded(c.pos()) || !c.after().matches(level, c.pos()) || !ShapingRules.mayChange(sp, level, c.pos())) continue;
            c.before().restore(level, c.pos());
            back++;
            RealShapes.Parts p = RealShapes.read(level, c.pos(), level.getBlockState(c.pos()));
            if (p != null) fx(level, c.pos(), p.look(), ShapingPayloads.FX_UNDO, seen(sp, c.pos()), sp);
        }
        return back;
    }

    // ---- each tick --------------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (RIPPLES.isEmpty()) return;
        Iterator<Ripple> it = RIPPLES.values().iterator();
        while (it.hasNext()) {
            Ripple r = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(r.player);
            if (sp == null || !(sp.level() instanceof ServerLevel level) || !level.dimension().equals(r.dimension) || r.next >= r.layers.size()) {
                it.remove();
                continue;
            }
            for (BlockPos p : r.layers.get(r.next)) {
                BlockState now = Looks.lookAt(level, p);
                if (now == null || !now.is(r.from) || !ShapingRules.mayChange(sp, level, p)) continue;
                Change c = apply(sp, level, p, r.look, seen(sp, p));
                if (c != null) r.undo.changes().add(c);
            }
            r.next++;
        }
    }

    static void forget(UUID player) {
        UNDO.remove(player);
        RIPPLES.remove(player);
    }

    // ---- telling people ---------------------------------------------------------------------------------------------------

    /** The side of this block you're looking at it from. */
    private static Direction seen(ServerPlayer sp, BlockPos pos) {
        Vec3 d = sp.getEyePosition().subtract(Vec3.atCenterOf(pos));
        return Direction.getApproximateNearest((float) d.x, (float) d.y, (float) d.z);
    }

    private static void fx(ServerLevel level, BlockPos pos, BlockState look, int kind, Direction face, ServerPlayer actor) {
        ShapingPayloads.Fx msg = new ShapingPayloads.Fx(pos.immutable(), Block.getId(look), kind, face.ordinal(), actor.getId());
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 32.0 * 32.0) AwakenNet.send(near, msg);
        }
    }

    private static void refuse(ServerPlayer sp, String key) {
        if (Throttle.allow(sp, "shaping_refuse", 10)) sp.sendOverlayMessage(Component.translatable(key));
    }

    private static void learned(ServerPlayer sp, BlockState look) {
        sp.sendOverlayMessage(Component.translatable("awaken.shaping.learned", look.getBlock().getName()));
    }
}
