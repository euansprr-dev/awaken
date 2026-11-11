package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.kin.KinWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bells that lie where they were won (SPEC §43.16): set one down ({@link #place}), lift one ({@link #lift}), a Lord's on
 * its arena's floor ({@link #keep}), any other won at a real place ({@link #leave}), the Registry's wagon, and the
 * Lampwing's old roost, the Nook Bell hanging under a branch near a new home's fire (§4.6 of the plan).
 */
final class FoundBells {
    private FoundBells() {}

    /** Where the first fire's Lampwing watches from, off the Anchor (kin.KinHome.lessonTick), and how far round it a tree is looked for. */
    static final int ROOST_DX = 12, ROOST_DZ = 9, ROOST_REACH = 24;

    /** Sets bell {@code no} down at {@code pos}. {@code claimants} empty: anyone may lift it. False if the spot isn't free. */
    static boolean place(ServerLevel level, BlockPos pos, int no, String where, List<UUID> claimants, long anchor, boolean hanging, boolean glow) {
        BlockState now = level.getBlockState(pos);
        if (!now.isAir() && !now.canBeReplaced()) return false;
        level.setBlock(pos, BellContent.FOUND_BELL.get().defaultBlockState().setValue(FoundBellBlock.HANGING, hanging).setValue(FoundBellBlock.GLOW, glow), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof FoundBellEntity be)) return false;
        be.set(no, where, claimants, anchor);
        return true;
    }

    /** A found bell used: it's yours if it was left for you (and it's awake), and it rings out clear. */
    static void lift(ServerLevel level, BlockPos pos, ServerPlayer p) {
        if (!(level.getBlockEntity(pos) instanceof FoundBellEntity be) || be.no < 0) return;
        BellCatalog.Def d = BellCatalog.of(be.no);
        if (be.anchor != 0) {
            Optional<HearthRecord> h = HearthService.data(level.getServer()).home(p.getUUID());
            if (h.isEmpty() || h.get().pos.asLong() != be.anchor) {
                Bells.say(p, "[It's hanging by someone else's fire. It's theirs.]");
                return;
            }
            if (asleep(level, be.anchor)) {
                Bells.say(p, "[Something's asleep in it.]");
                return;
            }
        }
        if (d.id().equals("shepherd") && VillageBells.kept(level, pos)) {
            Bells.say(p, "[A wolf's lying beside it and won't let you near. A bone might win her over.]");
            return;
        }
        if (!be.claimants.isEmpty() && !be.claimants.contains(p.getUUID())) {
            Bells.say(p, "[This one wasn't left for you. It's for the hunter who found it.]");
            return;
        }
        if (be.delivered && be.lifted.add(p.getUUID())) {
            Bells.giveBack(p, BellItem.of(d.no()));
            level.playSound(null, pos, BellContent.note(d.no()), SoundSource.BLOCKS, 1.5F, 1F);
            be.setChanged();
        } else if (be.lifted.add(p.getUUID())) {
            lifted(level, pos, p.getUUID());
            if (Bells.has(p, d.id())) Bells.say(p, "[You've already got " + d.name().replaceFirst("^The ", "the ") + ". This one stays for whoever it's for.]");
            else {
                Bells.ring(p, d, be.where);
                if (be.dulled()) dullCarried(p, d.no());
                level.playSound(null, pos, BellContent.note(d.no()), SoundSource.BLOCKS, 2F, 1F);
                level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 24, 0.35, 0.35, 0.35, 0.02);
            }
            be.setChanged();
        }
        if (be.done()) {
            BellWorld yards = BellWorld.get(level.getServer());
            for (BellWorld.Home home : yards.homes.values())
                if (home.yard.values().removeIf(pos::equals)) yards.setDirty();
            if (be.anchor != 0) {
                BellWorld w = BellWorld.get(level.getServer());
                BellWorld.Home home = w.peek(BlockPos.of(be.anchor));
                if (home != null && pos.equals(home.roost)) {
                    home.roost = null;
                    w.setDirty();
                }
            }
            level.removeBlock(pos, false);
        }
    }

    /** Nothing lives at that home yet: the Lampwing is still asleep in its old bell. */
    static boolean asleep(ServerLevel level, long anchor) {
        return KinWorld.get(level.getServer()).at(BlockPos.of(anchor)).isEmpty();
    }

    // ---- the bells gates' Lords kept (stage 4) ---------------------------------------------------------------------

    /** How far round where a Lord fell its bell may be set down, across. */
    static final int KEEP_REACH = 4;
    /** Heights tried for the floor, nearest the Lord's feet first. */
    private static final int[] KEEP_DY = {0, -1, 1, -2, 2, -3, -4, -5, -6};

    /**
     * Bell {@code no}, kept by a gate's Lord, set down on its arena's floor near where it fell for {@code claimants},
     * glowing a little so it's seen in the dark, and watched until they lift it or the gate closes. Null if no floor is
     * near enough (the caller puts it in their hands instead).
     */
    static @Nullable BlockPos keep(ServerLevel level, BlockPos fell, String gate, int no, String where, List<UUID> claimants) {
        BlockPos at = floor(level, fell);
        if (at == null || !place(level, at, no, where, claimants, 0L, false, true)) return null;
        if (level.getBlockEntity(at) instanceof FoundBellEntity be) {
            be.gate = gate;
            be.setChanged();
            // its clients draw it dulled while it lies in the gate
            level.sendBlockUpdated(at, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (!gate.isEmpty()) {
            BellWorld w = BellWorld.get(level.getServer());
            w.kept.add(new BellWorld.Kept(gate, at, no, claimants, ""));
            w.setDirty();
        }
        return at;
    }

    /** The free floor nearest {@code c}: air (or grass) with nothing liquid in it and a solid top under it. */
    static @Nullable BlockPos floor(ServerLevel level, BlockPos c) {
        for (int r = 0; r <= KEEP_REACH; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : KEEP_DY) {
                        BlockPos at = c.offset(dx, dy, dz);
                        if (!level.isLoaded(at)) continue;
                        BlockState s = level.getBlockState(at);
                        if (!(s.isAir() || s.canBeReplaced()) || !level.getFluidState(at).isEmpty()) continue;
                        if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) return at;
                    }
                }
        return null;
    }

    /** A hunter lifted their bell from a Lord's floor: they're off its list, and it's forgotten once nobody's left. */
    private static void lifted(ServerLevel level, BlockPos pos, UUID who) {
        BellWorld w = BellWorld.get(level.getServer());
        for (var it = w.kept.iterator(); it.hasNext(); ) {
            BellWorld.Kept k = it.next();
            if (!k.pos.equals(pos) || !k.left.remove(who)) continue;
            if (k.left.isEmpty()) it.remove();
            w.setDirty();
        }
    }

    /** Each 5 s: a Lord's bell whose gate has closed is lost to whoever hadn't lifted it, and goes back to the pool. */
    static void closed(net.minecraft.server.MinecraftServer server) {
        BellWorld w = BellWorld.get(server);
        if (w.kept.isEmpty()) return;
        ServerLevel ow = server.overworld();
        for (var it = w.kept.iterator(); it.hasNext(); ) {
            BellWorld.Kept k = it.next();
            if (k.gate.isEmpty()) continue;
            Optional<dev.awaken.core.api.GateHooks.GateView> g = dev.awaken.core.api.GateHooks.get(ow, k.gate);
            if (g.isPresent() && !"RELEASED".equals(g.get().state())) continue;
            for (UUID u : k.left) w.lost.computeIfAbsent(u, x -> new ArrayList<>()).add(k.no);
            it.remove();
            w.setDirty();
            Awaken.LOG.info("[bells] gate {} closed with bell {} on its floor, unlifted by {}", k.gate, k.no, k.left.size());
        }
    }

    /**
     * Each 5 s for a player: a bell a closed gate took back before they lifted it goes back to the pool. A swallowed one:
     * the next Lord they bring down keeps a bell for them; a gate world's own: its Lord keeps it again.
     */
    static void lost(ServerPlayer p) {
        BellWorld w = BellWorld.get(p.level().getServer());
        List<Integer> nos = w.lost.remove(p.getUUID());
        if (nos == null) return;
        w.setDirty();
        BellBook b = Bells.book(p);
        for (int no : nos) {
            BellCatalog.Def d = BellCatalog.of(no);
            if (d == null || b.held.containsKey(no)) continue;
            String name = d.name().replaceFirst("^The ", "the ");
            if (d.source() == BellCatalog.Source.GATE_WORLD)
                Bells.say(p, "[The gate closed with " + name + " still lying on its floor. That gate's Lord will have it again the next time you bring one down.]");
            else {
                b.lordsSince = Bells.LORDS_WITHOUT;
                Bells.say(p, "[The gate closed with " + name + " still lying on its floor. It's gone back to the gates: the next Lord you bring down will be keeping a bell for you.]");
            }
        }
        Bells.save(p, b);
    }

    /** The bell just lifted from a gate's floor, now in {@code p}'s bag: dulled by the Deep until it hangs (stage 6). */
    static void dullCarried(ServerPlayer p, int no) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            BellCatalog.Def d = BellItem.def(s);
            if (d != null && d.no() == no && !BellItem.isDulled(s)) {
                inv.setItem(i, BellItem.dulled(no));
                return;
            }
        }
    }

    // ---- bells set down where they were won (stage 5) -----------------------------------------------------------------

    /**
     * Bell {@code d}, won by {@code ps} at a real place, set down on the free floor nearest {@code near} (never where
     * someone's standing), glowing a little, for those of them who don't hold it and haven't one lying somewhere already,
     * and watched until each has lifted theirs. With no floor near, it goes into their hands instead. Each is told
     * {@code line}. True if it was set down.
     */
    static boolean leave(ServerLevel level, BlockPos near, BellCatalog.Def d, List<ServerPlayer> ps, String where, String line) {
        List<ServerPlayer> due = new ArrayList<>();
        for (ServerPlayer p : ps) if (!Bells.has(p, d.id()) && !waiting(level.getServer(), p.getUUID(), d.no()) && !due.contains(p)) due.add(p);
        if (due.isEmpty()) return false;
        BlockPos at = clear(level, near);
        List<UUID> ids = due.stream().map(ServerPlayer::getUUID).toList();
        if (at == null || !place(level, at, d.no(), where, ids, 0L, false, true)) {
            for (ServerPlayer p : due) Bells.ring(p, d, where);
            return false;
        }
        BellWorld w = BellWorld.get(level.getServer());
        w.kept.add(new BellWorld.Kept("", at, d.no(), ids, level.dimension().identifier().toString()));
        w.setDirty();
        level.playSound(null, at, BellContent.note(d.no()), SoundSource.BLOCKS, 1.2F, 0.9F);
        for (ServerPlayer p : due) Bells.say(p, line);
        Awaken.LOG.info("[bells] {} {} set down at {} in {} ({}) for {}", d.number(), d.id(), at.toShortString(), level.dimension().identifier(), where, ids.size());
        return true;
    }

    /** The same for one hunter, set down a few steps in front of them. */
    static boolean leave(ServerPlayer p, BellCatalog.Def d, String where, String line) {
        Vec3 ahead = p.getLookAngle().multiply(1, 0, 1);
        ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : ahead.normalize();
        BlockPos near = BlockPos.containing(p.getX() + ahead.x * 3, p.getY(), p.getZ() + ahead.z * 3);
        return leave(p.level(), near, d, List.of(p), where, line);
    }

    /** {@link #floor}, but never a block someone's standing in. */
    static @Nullable BlockPos clear(ServerLevel level, BlockPos c) {
        for (int r = 0; r <= KEEP_REACH; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : KEEP_DY) {
                        BlockPos at = c.offset(dx, dy, dz);
                        if (!level.isLoaded(at)) continue;
                        BlockState s = level.getBlockState(at);
                        if (!(s.isAir() || s.canBeReplaced()) || !level.getFluidState(at).isEmpty()) continue;
                        if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) continue;
                        if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, new AABB(at)).isEmpty()) continue;
                        return at;
                    }
                }
        return null;
    }

    /** Whether bell {@code no} is lying somewhere for this hunter to lift (a Lord's floor or anywhere else). */
    static boolean waiting(net.minecraft.server.MinecraftServer server, UUID who, int no) {
        for (BellWorld.Kept k : BellWorld.get(server).kept) if (k.no == no && k.left.contains(who)) return true;
        return false;
    }

    /**
     * Each 5 s: a bell set down outside a gate that's gone from its place (dug out, blown up, the ground gone) goes into
     * the hands of each hunter it was left for, once they're about. A bell is never lost.
     */
    static void watch(net.minecraft.server.MinecraftServer server) {
        BellWorld w = BellWorld.get(server);
        for (var it = w.kept.iterator(); it.hasNext(); ) {
            BellWorld.Kept k = it.next();
            if (!k.gate.isEmpty() || k.dim.isEmpty()) continue;
            Identifier dim = Identifier.tryParse(k.dim);
            ServerLevel level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
            if (level != null && !level.isLoaded(k.pos)) continue;
            if (level != null && level.getBlockEntity(k.pos) instanceof FoundBellEntity be && be.no == k.no) continue;
            BellCatalog.Def d = BellCatalog.of(k.no);
            for (var who = k.left.iterator(); who.hasNext(); ) {
                ServerPlayer p = server.getPlayerList().getPlayer(who.next());
                if (p == null) continue;
                who.remove();
                if (Bells.ring(p, d, "picked up for you where it lay")) Bells.say(p, "[" + d.name() + " was knocked out of where it lay. It's in your hands.]");
            }
            if (k.left.isEmpty()) it.remove();
            w.setDirty();
        }
    }

    // ---- the Registry's wagon (the Provings' Great Bells) -----------------------------------------------------------

    /**
     * Each 5 s for a member of a home: a Great Bell won before today that's on the Registry's wagon is left in the yard,
     * a few steps from the Anchor, for them to lift (once the yard's loaded and there's room to set it down).
     */
    static void wagon(ServerPlayer p, HearthRecord h) {
        BellBook b = Bells.book(p);
        if (b.wagon.isEmpty() || !(p.level() instanceof ServerLevel level) || !HearthService.OVERWORLD.equals(h.dimension) || level != level.getServer().overworld()) return;
        long today = dev.awaken.core.PlayDays.current(level.getServer());
        for (var it = b.wagon.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue() >= today) continue;
            BlockPos at = yard(level, h.pos);
            if (at == null) return;
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            if (!place(level, at, d.no(), "left in your yard by the Registry's wagon", List.of(p.getUUID()), 0L, false, false)) return;
            if (level.getBlockEntity(at) instanceof FoundBellEntity be) {
                be.delivered = true;
                be.setChanged();
            }
            it.remove();
            Bells.save(p, b);
            BellWorld w = BellWorld.get(level.getServer());
            w.home(h.pos).yard.put(d.no(), at);
            w.setDirty();
            Bells.say(p, "[The Registry's wagon left " + d.name().replaceFirst("^The ", "the ") + " in your yard."
                    + (d.source() == BellCatalog.Source.PROVING ? " It needs a Great Beam, 12 or more blocks above your fire.]" : " Hang it by your fire.]"));
            Awaken.LOG.info("[bells] the wagon left {} {} for {} at {}", d.number(), d.id(), p.getName().getString(), at.toShortString());
        }
    }

    /** A free spot on the ground two to five blocks from the Anchor, or null if the yard isn't loaded or has no room. */
    static @Nullable BlockPos yard(ServerLevel level, BlockPos anchor) {
        for (int r = 2; r <= 5; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = anchor.getX() + dx, z = anchor.getZ() + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) return null;
                    BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                    if (Math.abs(at.getY() - anchor.getY()) > 3 || !level.getBlockState(at).isAir()) continue;
                    if (!level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) continue;
                    return at;
                }
        return null;
    }

    // ---- the Lampwing's old roost (the Nook Bell) --------------------------------------------------------------------

    /**
     * Each 5 s for a home's owner nearby: the Lampwing's old bell is hung in the nearest tree once (not for a home whose
     * owner holds the Nook already), it glows while something sleeps in it, and the day a beast comes to live here it's
     * said to be the home's to take.
     */
    static void roost(ServerPlayer p, HearthRecord h) {
        if (!(p.level() instanceof ServerLevel level) || !HearthService.OVERWORLD.equals(h.dimension)) return;
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Home home = w.home(h.pos);
        BellCatalog.Def nook = BellCatalog.of("nook");
        if (!home.roosted) {
            if (!h.owner.equals(p.getUUID()) || Bells.has(p, "nook") || home.hung.contains(nook.no())
                    || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(h.pos)) > 32 * 32) return;
            BlockPos at = hangRoost(level, h.pos.offset(ROOST_DX, 0, ROOST_DZ), ROOST_REACH, h.pos.asLong());
            if (at == null) return;
            home.roost = at;
            home.roosted = true;
            w.setDirty();
            Awaken.LOG.info("[bells] the Lampwing's old bell hangs at {} for the home at {}", at.toShortString(), h.pos.toShortString());
            return;
        }
        if (home.roost == null || !level.isLoaded(home.roost)) return;
        BlockState s = level.getBlockState(home.roost);
        if (!s.is(BellContent.FOUND_BELL.get())) {
            home.roost = null;
            w.setDirty();
            return;
        }
        boolean glow = asleep(level, h.pos.asLong());
        if (s.getValue(FoundBellBlock.GLOW) != glow) level.setBlock(home.roost, s.setValue(FoundBellBlock.GLOW, glow), Block.UPDATE_ALL);
        if (!glow && h.belongs(p.getUUID()) && !Bells.has(p, "nook")) {
            BellBook b = Bells.book(p);
            if (b.once("roost_free")) {
                Bells.save(p, b);
                Bells.say(p, "[The Lampwing's left its old bell in the tree. It's yours.]");
            }
        }
    }

    /**
     * Hangs the Nook Bell under a branch of the tree nearest {@code centre} within {@code reach} (a leaves block with air
     * under it, three or more blocks up), or from a dead post at {@code centre} when there's no tree. Null if nothing
     * round it is loaded.
     */
    static @Nullable BlockPos hangRoost(ServerLevel level, BlockPos centre, int reach, long anchor) {
        BlockPos best = null;
        long bd = Long.MAX_VALUE;
        boolean any = false;
        for (int dx = -reach; dx <= reach; dx++)
            for (int dz = -reach; dz <= reach; dz++) {
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                any = true;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = top; y >= ground + 3; y--) {
                    BlockPos leaf = new BlockPos(x, y, z);
                    if (!level.getBlockState(leaf).is(BlockTags.LEAVES) || !level.getBlockState(leaf.below()).isAir()) continue;
                    long d = (long) dx * dx + (long) dz * dz;
                    if (d < bd) {
                        bd = d;
                        best = leaf.below();
                    }
                    break;
                }
            }
        BellCatalog.Def nook = BellCatalog.of("nook");
        String where = "in the tree by your fire, where the Lampwing slept in it";
        if (best != null) return place(level, best, nook.no(), where, List.of(), anchor, true, true) ? best : null;
        if (!any || !level.hasChunkAt(centre)) return null;
        // no tree near: an old dead post at the edge of where the trees would be, an arm out, the bell under it
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        BlockPos foot = new BlockPos(centre.getX(), ground, centre.getZ());
        BlockState post = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int y = 0; y < 4; y++) level.setBlock(foot.above(y), post, Block.UPDATE_ALL);
        level.setBlock(foot.above(3).east(), Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
        BlockPos at = foot.above(2).east();
        return place(level, at, nook.no(), where + ", on an old post", List.of(), anchor, true, true) ? at : null;
    }
}
