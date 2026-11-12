package dev.awaken.kin;

import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helping (SPEC §43.5, step 3): each kind's own small act, never a fetch list, worked out from its want. A warm light
 * where a Lampwing can see it; soft ground tilled near a Burrowbun; the branches over a Mossback cut back, or water
 * brought to it; a shiny stone tossed by a Pebblepup's pile. The right act gets its tell, the band's chime and a line;
 * close but wrong gets a nudge, and after a few misses the nudge says plainly what to do. Once you've met a kind, its
 * act is said plainly everywhere ({@link #howTo}; Euan 2026-11-12: "I never know what I have to do with them"). Three
 * helps of one beast and it trusts you. Stage 6's four (SPEC §43.13): flowers planted round a Bumblepuff's bare nest, lots of them; a
 * branch of a Loomworm's own tree brought down to it; a lost Cloudcalf led home to its pod by someone up at its height;
 * and a Boulderback waited for in the dark, light put away, until it comes to you.
 */
public final class KinHelp {
    private KinHelp() {}

    /** How near the act must be to the beast, and how long one beast takes before it can be helped again. */
    static final int NEAR = 10, GAP = 20 * 20, LESSON_GAP = 20 * 4;

    private record Pending(ServerPlayer player, BlockPos pos, int what, long at) {}

    private static final int TILL = 0, WATER = 1, CLEARED = 2, FIRE = 3, BLOOM = 4;
    /** Flowers round a Bumblepuff's nest (within six) before it counts them as enough; a Boulderback's wait (ticks). */
    static final int FLOWERS = 3, NEST_REACH = 6, DARK_WAIT = 20 * 30;
    private static final List<Pending> PENDING = new ArrayList<>();
    /** Dull stones a Pebblepup has already pushed off (item entity ids), so each nudges once. */
    private static final Set<Integer> PUSHED = new HashSet<>();

    static void serverStopped() {
        PENDING.clear();
        PUSHED.clear();
    }

    // ---- the acts ---------------------------------------------------------------------------------------------------

    static void placed(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getPlacedBlock();
        BlockPos pos = e.getPos();
        KinHome.placed(level, p, pos, s);
        boolean flower = s.is(BlockTags.FLOWERS);
        if (flower || s.is(BlockTags.SAPLINGS) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.AZALEA)) {
            KinBeast b = nestNear(level, pos);
            if (b != null) {
                if (flower) bloom(level, b, p, pos);
                else nudge(b, p, "[It buzzed round the leaves and lost interest. It wants flowers.]");
                return;
            }
        }
        for (KinBeast b : wild(level, pos, KinKind.EMBERKIT)) {
            if (cold(s)) nudge(b, p, "[It shrank away from the cold blue flame.]");
            else if (warm(s)) {
                if (b.blockPosition().distManhattan(pos) > 5) nudge(b, p, "[It lifted its head toward the warmth. It's too far off to feel it.]");
                else help(b, p);
            } else if (s.getLightEmission(level, pos) >= 10) nudge(b, p, "[It edged toward the light, then curled up again. Too small to warm it.]");
            else break;
            return;
        }
        if (s.getLightEmission(level, pos) < 10) return;
        for (KinBeast b : wild(level, pos, KinKind.LAMPWING)) {
            if (cold(s)) nudge(b, p, "[It flinched from the cold blue light and went back into the trees.]");
            else if (!b.hasLineOfSight(p) && !KinHome.lesson(b)) nudge(b, p, "[It can't see that light from where it is.]");
            else help(b, p);
            return;
        }
    }

    /** Something an Emberkit can lie by: a lit campfire, a magma block, a vent stone. */
    static boolean warm(BlockState s) {
        return (s.is(Blocks.CAMPFIRE) && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(dev.awaken.kin.works.WorksContent.VENT_STONE.get()) || s.is(BlockTags.FIRE);
    }

    static boolean cold(BlockState s) {
        return s.is(Blocks.SOUL_LANTERN) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.SOUL_FIRE);
    }

    /** A hoe on the ground, a bucket of water poured, bone meal: checked a tick later, once the world has changed. */
    static void used(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
        ItemStack held = e.getItemStack();
        BlockPos pos = e.getPos();
        if (held.is(ItemTags.HOES)) PENDING.add(new Pending(p, pos, TILL, level.getGameTime() + 1));
        else if (held.is(Items.WATER_BUCKET)) PENDING.add(new Pending(p, pos.relative(e.getFace() == null ? net.minecraft.core.Direction.UP : e.getFace()), WATER,
                level.getGameTime() + 1));
        else if (held.is(Items.BONE_MEAL)) {
            for (KinBeast b : wild(level, pos, KinKind.MOSSBACK)) {
                nudge(b, p, "[The garden on its back perked up for a moment. That isn't what it's after.]");
                break;
            }
            // bone meal on the grass by a Bumblepuff's nest brings flowers up: counted once they're there
            if (nestNear(level, pos) != null) PENDING.add(new Pending(p, pos, BLOOM, level.getGameTime() + 2));
        }
        if (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE))
            PENDING.add(new Pending(p, pos.relative(e.getFace() == null ? net.minecraft.core.Direction.UP : e.getFace()), FIRE, level.getGameTime() + 1));
        if (held.is(Items.WATER_BUCKET)) for (KinBeast b : wild(level, pos, KinKind.PONDLING)) {
            nudge(b, p, "[More water won't help. Something at the bottom is souring it.]");
            break;
        }
        BlockState at = level.getBlockState(pos);
        if (at.is(Blocks.NOTE_BLOCK) || at.is(Blocks.BELL)) for (KinBeast b : wild(level, pos, KinKind.CHIMEWREN)) {
            nudge(b, p, at.is(Blocks.BELL) ? "[They know that bell's song already. Every bird for miles does.]" : "[It cocked its head at the note and copied it. It's heard that one.]");
            break;
        }
    }

    static void broken(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getState();
        BlockPos pos = e.getPos();
        if (s.is(dev.awaken.kin.works.WorksContent.POND_ROT.get())) {
            for (KinBeast b : wild(level, pos, KinKind.PONDLING)) {
                BlockPos pond = b.mark == null ? b.origin : b.mark;
                // the block's still there this tick: the last of it is this one
                if (rotNear(level, pond) > 1) KinWatch.say(p, "[The water's a little clearer. There's still some down there.]", 6);
                else help(b, p);
                return;
            }
            return;
        }
        if (diggable(s)) for (KinBeast b : wild(level, pos, KinKind.SNUFFLET)) {
            if (b.mark != null && pos.equals(b.mark)) {
                unearth(level, b, p, pos);
                help(b, p);
            } else if (b.mark != null && pos.getX() == b.mark.getX() && pos.getZ() == b.mark.getZ() && pos.getY() > b.mark.getY())
                KinWatch.say(p, "[Deeper. It's right under there.]", 5);
            else nudge(b, p, "[It squealed and nosed at a spot a few steps over.]");
            return;
        }
        if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
            for (KinBeast b : wild(level, pos, KinKind.MOSSBACK)) {
                BlockPos at = b.blockPosition();
                if (Math.abs(pos.getX() - at.getX()) <= 3 && Math.abs(pos.getZ() - at.getZ()) <= 3 && pos.getY() > at.getY()) {
                    PENDING.add(new Pending(p, at, CLEARED, level.getGameTime() + 1));
                    return;
                }
            }
        } else if ((s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT)) && p.getMainHandItem().is(ItemTags.SHOVELS)) {
            for (KinBeast b : wild(level, pos, KinKind.BURROWBUN)) {
                nudge(b, p, "[It sniffed the hole and turned away. It wants the soil broken up, not dug out.]");
                return;
            }
        }
    }

    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending q = PENDING.get(i);
            if (!(q.player().level() instanceof ServerLevel level) || q.player().isRemoved()) {
                PENDING.remove(i);
                continue;
            }
            if (level.getGameTime() < q.at()) continue;
            PENDING.remove(i);
            switch (q.what()) {
                case TILL -> {
                    if (!level.getBlockState(q.pos()).is(Blocks.FARMLAND)) break;
                    for (KinBeast b : wild(level, q.pos(), KinKind.BURROWBUN)) {
                        help(b, q.player());
                        break;
                    }
                }
                case WATER -> {
                    if (!level.getFluidState(q.pos()).is(net.minecraft.tags.FluidTags.WATER)) break;
                    for (KinBeast b : wild(level, q.pos(), KinKind.MOSSBACK)) {
                        if (b.blockPosition().distManhattan(q.pos()) > 5) {
                            nudge(b, q.player(), "[It turned toward the water, but it's too far for it to sit in.]");
                        } else if (!level.canSeeSky(b.blockPosition().above())) {
                            nudge(b, q.player(), "[It likes the water. It's still in the shade.]");
                        } else help(b, q.player());
                        break;
                    }
                }
                case CLEARED -> {
                    for (KinBeast b : wild(level, q.pos(), KinKind.MOSSBACK)) {
                        if (b.blockPosition().distManhattan(q.pos()) > 2) continue;
                        if (level.canSeeSky(b.blockPosition().above())) help(b, q.player());
                        else nudge(b, q.player(), "[A little more light got through. Not enough yet.]");
                        break;
                    }
                }
                case BLOOM -> {
                    KinBeast b = nestNear(level, q.pos());
                    if (b != null && b.mark != null && flowersNear(level, b.mark) >= FLOWERS) help(b, q.player());
                }
                case FIRE -> {
                    BlockState f = level.getBlockState(q.pos());
                    if (!f.is(BlockTags.FIRE) && !warm(f)) break;
                    for (KinBeast b : wild(level, q.pos(), KinKind.EMBERKIT)) {
                        if (b.blockPosition().distManhattan(q.pos()) > 5) nudge(b, q.player(), "[It lifted its head toward the warmth. It's too far off to feel it.]");
                        else help(b, q.player());
                        break;
                    }
                }
                default -> {}
            }
        }
    }

    /** Wild beasts, every half second: things tossed to them, a lost flock and its leader, the rot in a pond. */
    static void beastTick(ServerLevel level, KinBeast b) {
        if (b.home != null) return;
        switch (b.kind()) {
            case PEBBLEPUP -> tossed(level, b, KinHelp::shiny, KinHelp::dull, "[It pushed the dull one off its pile.]");
            case TOTTER -> tossed(level, b, KinHelp::tidy, s -> !tidy(s), "[It nudged that aside. It isn't one of its things.]");
            case THATCHBACK -> tossed(level, b, s -> s.is(ItemTags.LOGS), s -> s.is(ItemTags.PLANKS), "[It sniffed the planks and left them. It wants whole logs.]");
            case DUMPLIN -> flock(level, b);
            case PONDLING -> {
                if (b.mark != null && b.tickCount % 2400 == 0) sour(level, b.mark);
            }
            case LOOMWORM -> {
                Tree t = Tree.of(level, b.origin);
                tossed(level, b, t::branch, s -> s.is(ItemTags.LEAVES) || s.is(ItemTags.SAPLINGS), "[It sniffed the leaves and turned its head away. They're off the wrong tree.]");
            }
            case CLOUDCALF -> calf(level, b);
            case BOULDERBACK -> dark(level, b);
            default -> {}
        }
    }

    // ---- Bumblepuffs: the bare meadow --------------------------------------------------------------------------------------

    /** The wild Bumblepuff whose nest is within six of a spot, or null. */
    private static @org.jspecify.annotations.Nullable KinBeast nestNear(ServerLevel level, BlockPos pos) {
        for (KinBeast b : level.getEntitiesOfClass(KinBeast.class, new AABB(pos).inflate(24), x -> x.isAlive() && x.kind() == KinKind.BUMBLEPUFF && x.home == null
                && x.mark != null && x.mark.closerThan(pos, NEST_REACH + 1))) return b;
        return null;
    }

    /** Flowers round a nest: within six across and two up or down. */
    static int flowersNear(ServerLevel level, BlockPos nest) {
        int n = 0;
        for (BlockPos at : BlockPos.betweenClosed(nest.offset(-NEST_REACH, -3, -NEST_REACH), nest.offset(NEST_REACH, 2, NEST_REACH)))
            if (level.getBlockState(at).is(BlockTags.FLOWERS)) n++;
        return n;
    }

    /** A flower planted by its nest: with lots round it now, it's helped; with one or two, it wants more. */
    private static void bloom(ServerLevel level, KinBeast b, ServerPlayer p, BlockPos pos) {
        if (b.mark == null) return;
        int n = flowersNear(level, b.mark) + (level.getBlockState(pos).is(BlockTags.FLOWERS) ? 0 : 1);
        if (n >= FLOWERS) help(b, p);
        else KinWatch.say(p, "[It bumbled down to the flower and back up again. One or two isn't enough. It wants lots.]", 6);
    }

    // ---- Loomworms: the right tree -------------------------------------------------------------------------------------------

    /** The tree a Loomworm lives on, from its land: the log it lies on, and the branch it eats (leaves, or a sapling). */
    enum Tree {
        OAK(Blocks.OAK_LOG, Items.OAK_LEAVES, Items.OAK_SAPLING),
        BIRCH(Blocks.BIRCH_LOG, Items.BIRCH_LEAVES, Items.BIRCH_SAPLING),
        SPRUCE(Blocks.SPRUCE_LOG, Items.SPRUCE_LEAVES, Items.SPRUCE_SAPLING),
        DARK_OAK(Blocks.DARK_OAK_LOG, Items.DARK_OAK_LEAVES, Items.DARK_OAK_SAPLING);

        final Block log;
        final Item leaves, sapling;

        Tree(Block log, Item leaves, Item sapling) {
            this.log = log;
            this.leaves = leaves;
            this.sapling = sapling;
        }

        static Tree of(ServerLevel level, BlockPos at) {
            Holder<Biome> b = level.getBiome(at);
            if (b.is(Biomes.DARK_FOREST)) return DARK_OAK;
            if (b.is(Tags.Biomes.IS_BIRCH_FOREST)) return BIRCH;
            if (b.is(BiomeTags.IS_TAIGA)) return SPRUCE;
            return OAK;
        }

        boolean branch(ItemStack s) {
            return s.is(leaves) || s.is(sapling);
        }
    }

    // ---- Cloudcalves: the pod ------------------------------------------------------------------------------------------------

    /** A point in the sky 48 to 72 blocks off {@code from}: where a pod calls from, or where its calf is lost next. */
    static BlockPos pod(ServerLevel level, BlockPos from, long seed) {
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(from.asLong() ^ seed * 0x9E3779B97F4A7C15L);
        double a = r.nextDouble() * Math.PI * 2;
        int d = 48 + r.nextInt(25);
        int x = from.getX() + (int) Math.round(Math.cos(a) * d), z = from.getZ() + (int) Math.round(Math.sin(a) * d);
        // no chunk is loaded for it: an unloaded column takes the height it was lost at
        int y = dev.awaken.core.api.Ready.at(level, new BlockPos(x, 0, z)) ? Math.max(from.getY() - KinSpawner.CLOUD_UP, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z))
                : from.getY() - KinSpawner.CLOUD_UP + 6;
        return new BlockPos(x, y + KinSpawner.CLOUD_UP, z);
    }

    /**
     * A lost Cloudcalf circles where it is, its pod calling now and then from a way off. Someone up at its height (no more
     * than four blocks under it) within 16 blocks, and it follows them; led within 12 blocks of its pod (across the map),
     * it's home: helped, and whoever led it hears the Cloud bell. A while after it drifts off, lost somewhere new.
     */
    private static void calf(ServerLevel level, KinBeast b) {
        if (b.mark == null) return;
        long now = level.getGameTime();
        boolean home = b.origin.equals(b.mark);
        if (!home && b.tickCount % 200 == 0) {
            var call = KinContent.CALL.get(KinKind.CLOUDCALF);
            if (call != null) level.playSound(null, b.mark, call.get(), SoundSource.NEUTRAL, 4F, 0.7F);
            level.sendParticles(ParticleTypes.CLOUD, b.mark.getX() + 0.5, b.mark.getY() + 0.5, b.mark.getZ() + 0.5, 16, 2.5, 0.8, 2.5, 0.01);
        }
        ServerPlayer leader = b.leader != null && level.getServer().getPlayerList().getPlayer(b.leader) instanceof ServerPlayer sp && sp.level() == level
                && sp.distanceToSqr(b) < 32 * 32 ? sp : null;
        if (leader == null) {
            b.leader = null;
            if (home) {
                if (now - b.lastHelp > 2400) b.wildAt(pod(level, b.mark, now / 2400));
                return;
            }
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, b.getBoundingBox().inflate(16), x -> !x.isSpectator())) {
                if (p.getY() < b.getY() - 4 || Kinship.of(p).afraid(KinKind.CLOUDCALF, now)) continue;
                b.leader = p.getUUID();
                KinWatch.met(p, b);
                KinWatch.say(p, "[It stopped circling and called to you. Up here, it'll follow you.]", 8);
                return;
            }
            return;
        }
        double px = leader.getX() - b.mark.getX() - 0.5, pz = leader.getZ() - b.mark.getZ() - 0.5, cx = b.getX() - b.mark.getX() - 0.5, cz = b.getZ() - b.mark.getZ() - 0.5;
        if (px * px + pz * pz < 144 && cx * cx + cz * cz < 144) {
            b.leader = null;
            b.wildAt(b.mark);
            level.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 0.5, b.getZ(), 30, 1.5, 0.6, 1.5, 0.02);
            help(b, leader);
            dev.awaken.bells.Bells.ring(leader, "cloud", "the day you brought a Cloudcalf home to its pod");
        }
    }

    // ---- Boulderbacks: the dark ----------------------------------------------------------------------------------------------

    /**
     * A Boulderback in the dark: a light near it and it backs off into the dark. Someone within 12 blocks carrying no light
     * who keeps still (or creeps, crouched) for 30 seconds, and it comes to them: helped. Walking about starts the wait again.
     */
    private static void dark(ServerLevel level, KinBeast b) {
        ServerPlayer near = null;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, b.getBoundingBox().inflate(12), x -> !x.isSpectator()))
            if (near == null || p.distanceToSqr(b) < near.distanceToSqr(b)) near = p;
        if (near == null) {
            b.quietFor = null;
            b.quietTicks = 0;
            return;
        }
        if (lit(near)) {
            b.quietTicks = 0;
            Vec3 away = DefaultRandomPos.getPosAway(b, 10, 4, near.position());
            if (away != null && b.getNavigation().isDone()) b.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            if (b.tickCount % 200 == 0) nudge(b, near, "[It backed away from your light, into the dark.]");
            return;
        }
        boolean moved = !near.isCrouching() && near.position().distanceToSqr(b.quietAt) > 0.25;
        if (!near.getUUID().equals(b.quietFor) || moved) {
            b.quietFor = near.getUUID();
            b.quietAt = near.position();
            b.quietTicks = 0;
            return;
        }
        if (near.isCrouching()) b.quietAt = near.position();
        b.quietTicks += 10;
        if (b.quietTicks == DARK_WAIT / 2) {
            KinWatch.say(near, "[Something big shifted in the dark. It's a step closer.]", 6);
            b.getNavigation().moveTo(near, 0.4);
        }
        if (b.quietTicks >= DARK_WAIT) {
            b.quietTicks = 0;
            b.getNavigation().moveTo(near, 0.5);
            help(b, near);
        }
    }

    /** Whether a player carries a light: anything in either hand that would glow if it were set down. */
    static boolean lit(ServerPlayer p) {
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()})
            if (s.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().getLightEmission() > 0) return true;
        return false;
    }

    /** Something tossed by its spot: what it wants it takes (one at a time), what it doesn't it pushes off, once each. */
    private static void tossed(ServerLevel level, KinBeast b, java.util.function.Predicate<ItemStack> wants, java.util.function.Predicate<ItemStack> refuses, String refused) {
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(b.origin).inflate(6, 3, 6), ie -> ie.isAlive() && ie.getAge() > 10)) {
            Entity thrower = it.getOwner();
            if (!(thrower instanceof ServerPlayer p)) continue;
            ItemStack s = it.getItem();
            if (wants.test(s)) {
                if (b.distanceToSqr(it) > 2.5) {
                    b.getNavigation().moveTo(it, 1.2);
                    b.act(KinBeast.CARRY);
                    return;
                }
                s.shrink(1);
                if (s.isEmpty()) it.discard();
                help(b, p);
                return;
            }
            if (refuses.test(s) && PUSHED.add(it.getId())) {
                it.setDeltaMovement((it.getX() - b.origin.getX()) * 0.15, 0.25, (it.getZ() - b.origin.getZ()) * 0.15);
                nudge(b, p, refused);
                return;
            }
        }
    }

    /** What a Totter tidies: the bits lying about the woods' floor. */
    static boolean tidy(ItemStack s) {
        return s.is(Items.STICK) || s.is(Items.FLINT) || s.is(Items.BONE) || s.is(Items.FEATHER) || s.is(ItemTags.SAPLINGS) || s.is(Items.BROWN_MUSHROOM)
                || s.is(Items.RED_MUSHROOM);
    }

    static boolean diggable(BlockState s) {
        return s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MUD);
    }

    // ---- Dumplins: the lost flock ------------------------------------------------------------------------------------------

    /**
     * A lost flock circles where it is until someone stands up high where they can see (on a rock, a stump, anything a
     * block and a half above them): then it follows them. Led within eight blocks of its nest, every one of it is helped.
     * A while after it's home it wanders off again, somewhere new, so a flock can be helped more than once.
     */
    private static void flock(ServerLevel level, KinBeast b) {
        if (b.mark == null) return;
        long now = level.getGameTime();
        ServerPlayer leader = b.leader != null && level.getServer().getPlayerList().getPlayer(b.leader) instanceof ServerPlayer sp && sp.level() == level
                && sp.distanceToSqr(b) < 32 * 32 ? sp : null;
        if (leader == null) {
            b.leader = null;
            if (b.origin.equals(b.mark)) {
                if (now - b.lastHelp > 2400) b.wildAt(lost(level, b.mark, now / 2400));
                return;
            }
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, b.getBoundingBox().inflate(10), x -> !x.isSpectator())) {
                if (!p.onGround() || p.getY() < b.getY() + 1.5 || Kinship.of(p).afraid(KinKind.DUMPLIN, now)) continue;
                boolean first = level.getEntitiesOfClass(KinBeast.class, b.getBoundingBox().inflate(16), x -> p.getUUID().equals(x.leader)).isEmpty();
                b.leader = p.getUUID();
                KinWatch.met(p, b);
                if (first) KinWatch.say(p, "[The flock stopped circling and looked up at you. They'll follow you now.]", 8);
                return;
            }
            return;
        }
        net.minecraft.world.phys.Vec3 nest = net.minecraft.world.phys.Vec3.atCenterOf(b.mark);
        if (leader.distanceToSqr(nest) < 64 && b.distanceToSqr(nest) < 100) {
            b.leader = null;
            b.wildAt(b.mark);
            help(b, leader);
        }
    }

    /** Where a flock is lost this time: 16 to 24 blocks from its nest, the same spot for the whole flock. */
    static BlockPos lost(ServerLevel level, BlockPos nest, long seed) {
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(nest.asLong() ^ seed * 0x9E3779B97F4A7C15L);
        double a = r.nextDouble() * Math.PI * 2;
        int d = 16 + r.nextInt(9);
        return ground(level, nest.getX() + (int) Math.round(Math.cos(a) * d), nest.getZ() + (int) Math.round(Math.sin(a) * d));
    }

    // ---- Pondlings: the rot ------------------------------------------------------------------------------------------------

    /** The Deep's rot on a pond's bed round {@code pond} (the water's middle): how many blocks of it. */
    static int rotNear(ServerLevel level, BlockPos pond) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 0; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (level.getBlockState(m.set(pond.getX() + dx, pond.getY() + dy, pond.getZ() + dz)).is(dev.awaken.kin.works.WorksContent.POND_ROT.get())) n++;
        return n;
    }

    /** The rot creeps back while a wild Pondling's pond holds less than two of it (so it can always be helped again). */
    static void sour(ServerLevel level, BlockPos pond) {
        if (rotNear(level, pond) >= 2) return;
        net.minecraft.util.RandomSource r = level.getRandom();
        for (int i = 0; i < 8; i++) {
            BlockPos bed = pond.offset(r.nextInt(3) - 1, 0, r.nextInt(3) - 1);
            int deep = 0;
            while (level.getFluidState(bed).is(net.minecraft.tags.FluidTags.WATER) && deep < 4) {
                bed = bed.below();
                deep++;
            }
            BlockState s = level.getBlockState(bed);
            if (deep == 0 || !s.isSolid() || s.is(dev.awaken.kin.works.WorksContent.POND_ROT.get())) continue;
            level.setBlock(bed, dev.awaken.kin.works.WorksContent.POND_ROT.get().defaultBlockState(), 3);
            return;
        }
    }

    // ---- Snufflets: what's buried --------------------------------------------------------------------------------------------

    /** A spot a block under the ground near its own spot where a Snufflet smells something, dirt it can be dug from. */
    static @org.jspecify.annotations.Nullable BlockPos digSpot(ServerLevel level, BlockPos around, long seed) {
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(around.asLong() ^ seed * 0xC2B2AE3D27D4EB4FL);
        for (int i = 0; i < 8; i++) {
            int x = around.getX() + r.nextInt(9) - 4, z = around.getZ() + r.nextInt(9) - 4;
            BlockPos top = ground(level, x, z).below();
            BlockState s = level.getBlockState(top);
            if (!diggable(s) && !s.is(Blocks.MOSS_BLOCK)) continue;
            BlockPos spot = top.below();
            if (!diggable(level.getBlockState(spot))) {
                if (!level.getBlockState(spot).isSolid()) continue;
                level.setBlock(spot, Blocks.DIRT.defaultBlockState(), 3);
            }
            return spot;
        }
        return null;
    }

    /** Dug up where it was snuffling: something small, and now and then an old letter that tells of a sleeping bell. */
    private static void unearth(ServerLevel level, KinBeast b, ServerPlayer p, BlockPos at) {
        int r = level.getRandom().nextInt(100);
        if (r >= 25 || !dev.awaken.bells.Bells.found(p, at)) {
            net.minecraft.world.item.Item it = r < 45 ? Items.BONE : r < 65 ? Items.FLINT : r < 80 ? Items.BROWN_MUSHROOM : r < 93 ? Items.GOLD_NUGGET : Items.AMETHYST_SHARD;
            net.minecraft.world.level.block.Block.popResource(level, at.above(), new ItemStack(it));
        }
        b.mark = digSpot(level, b.origin, level.getGameTime());
    }

    // ---- Chimewrens and Puffwools ----------------------------------------------------------------------------------------------

    /** A bell this player rang or woke, heard by wild Chimewrens within {@code reach}: only a player who holds a bell has one. */
    static void bellHeard(ServerLevel level, ServerPlayer p, BlockPos at, int reach) {
        if (dev.awaken.bells.Bells.count(p) == 0) return;
        for (KinBeast b : level.getEntitiesOfClass(KinBeast.class, new AABB(at).inflate(reach), x -> x.isAlive() && x.kind() == KinKind.CHIMEWREN && x.home == null))
            help(b, p);
    }

    /** A brush on a Puffwool: wild, the burrs come out (the help); at home, its wool, once a day. Shears only frighten it. */
    static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof KinBeast b) || b.kind() != KinKind.PUFFWOOL) return;
        if (!(p.level() instanceof ServerLevel level)) return;
        ItemStack held = e.getItemStack();
        if (held.is(Items.BRUSH)) {
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            if (b.home == null) help(b, p);
            else if (dev.awaken.hearth.Hearth.isMember(p, b.home)) KinWorks.brushed(level, b, p);
        } else if (held.is(Items.SHEARS)) {
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            if (b.home == null) nudge(b, p, "[It backed away from the shears. It wants the burrs out, not its wool off.]");
        }
    }

    static boolean shiny(ItemStack s) {
        return s.is(Items.QUARTZ) || s.is(Items.AMETHYST_SHARD) || s.is(Tags.Items.GEMS) || s.is(Items.RAW_GOLD) || s.is(Items.GOLD_NUGGET)
                || s.is(Items.PRISMARINE_CRYSTALS) || s.is(Items.ECHO_SHARD) || s.is(Items.LAPIS_LAZULI);
    }

    static boolean dull(ItemStack s) {
        return s.is(Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.GRAVEL) || s.is(Items.FLINT) || s.is(Items.ANDESITE) || s.is(Items.DIORITE)
                || s.is(Items.GRANITE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.TUFF) || s.is(Items.DIRT);
    }

    // ---- what comes of it --------------------------------------------------------------------------------------------

    /** The wild ones of a kind near a spot, nearest first (a beast at home has no need of help). */
    static List<KinBeast> wild(ServerLevel level, BlockPos pos, KinKind k) {
        List<KinBeast> out = level.getEntitiesOfClass(KinBeast.class, new AABB(pos).inflate(NEAR), b -> b.isAlive() && b.kind() == k && b.home == null);
        out.sort((a, c) -> Double.compare(a.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)), c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))));
        return out;
    }

    /** The right act: its tell, the band's chime, a line; three from one player and it trusts them. */
    static void help(KinBeast b, ServerPlayer p) {
        long now = p.level().getGameTime();
        boolean lesson = KinHome.lesson(b);
        if (now - b.lastHelp < (lesson ? LESSON_GAP : GAP)) {
            KinWatch.say(p, "[It's still taken up with the last one. Give it a little while.]", 5);
            return;
        }
        if (Kinship.of(p).afraid(b.kind(), now)) {
            KinWatch.say(p, "[It won't come near you. Not after what happened.]", 6);
            return;
        }
        if (b.home == null && b.hideUntil > now) {
            KinWatch.say(p, "[It's running from the bell. Give it a minute to settle.]", 5);
            return;
        }
        KinWatch.met(p, b);
        b.lastHelp = now;
        int n = b.helps.merge(p.getUUID(), 1, Integer::sum);
        b.act(KinBeast.TELL, true);
        var tell = KinContent.TELL.get(b.kind());
        if (tell != null) b.level().playSound(null, b.getX(), b.getY(), b.getZ(), tell.get(), SoundSource.NEUTRAL, 0.8F, 1F);
        KinWatch.chime(p);
        Kinship.add(p, b.kind(), Kinship.HELP);
        Kinship ks = Kinship.of(p);
        ks.kind(b.kind()).misses = 0;
        Kinship.save(p, ks);
        Progression.count(p, "awaken:kin_helped");
        if (n >= 3 && b.trusted.add(p.getUUID())) {
            KinWatch.say(p, "[" + took(b.kind()) + " It trusts you now.]", 10);
            Progression.count(p, "awaken:kin_trusted");
            KinHome.offer(b, p);
        } else {
            KinWatch.say(p, "[" + took(b.kind()) + (n == 1 ? " It's looking at you differently." : " It came a little closer.") + "]", 8);
        }
    }

    private static String took(KinKind k) {
        return switch (k) {
            case LAMPWING -> "It came down to the light and sat beside it.";
            case BURROWBUN -> "It dug straight in and came up covered in soil.";
            case MOSSBACK -> "The garden on its back is lifting toward the sun.";
            case PEBBLEPUP -> "It took the stone to its pile.";
            case TOTTER -> "It took it back to its row and set it straight.";
            case THATCHBACK -> "It dragged the log off to its dam.";
            case EMBERKIT -> "It crept up to the warmth and uncurled.";
            case PUFFWOOL -> "The burrs are out. It bounced.";
            case DUMPLIN -> "It's home at its nest, and it hasn't stopped chirping.";
            case PONDLING -> "The water's clear. It slid straight in.";
            case SNUFFLET -> "It squealed at what you dug up and wagged its whole back end.";
            case CHIMEWREN -> "It's singing the bell's note back to you, nearly right.";
            case BUMBLEPUFF -> "It rolled about in the flowers until it was yellow all over.";
            case LOOMWORM -> "It ate the whole branch and started spinning straight away.";
            case CLOUDCALF -> "It's home with its pod, and they're all calling at once.";
            case BOULDERBACK -> "It came out of the dark and leaned against you, very gently.";
            default -> "It's pleased.";
        };
    }

    /** Close but wrong: a nudge; after three, the nudge says plainly what to do ({@link #closer}). */
    static void nudge(KinBeast b, ServerPlayer p, String line) {
        Kinship ks = Kinship.of(p);
        Kinship.Kind s = ks.kind(b.kind());
        s.misses++;
        Kinship.save(p, ks);
        b.act(KinBeast.NUDGE, true);
        var worry = KinContent.WORRY.get(b.kind());
        if (worry != null) b.level().playSound(null, b.getX(), b.getY(), b.getZ(), worry.get(), SoundSource.NEUTRAL, 0.6F, 1.1F);
        KinWatch.say(p, s.misses >= 3 ? line + " " + closer(b.kind()) : line, 8);
    }

    /** After a few wrong tries: what to do, short and plain (the same act {@link #howTo} spells out). */
    static String closer(KinKind k) {
        return switch (k) {
            case LAMPWING -> "[It wants a warm light it can see: a torch or a lantern set on the ground near it.]";
            case BURROWBUN -> "[It wants the ground by it tilled with a hoe.]";
            case MOSSBACK -> "[Pour water right beside it, out where the sun reaches it.]";
            case PEBBLEPUP -> "[Drop something shiny by its pile: a gold nugget, quartz, lapis, amethyst or a gem.]";
            case TOTTER -> "[Drop a stick, a flint, a bone, a feather, a sapling or a mushroom by its row.]";
            case THATCHBACK -> "[Drop whole logs by its dam.]";
            case EMBERKIT -> "[Set a lit campfire or a magma block right beside it.]";
            case PUFFWOOL -> "[Use a brush on it to get the burrs out.]";
            case DUMPLIN -> "[Stand on something high where they can see you, then lead them to their nest.]";
            case PONDLING -> "[Break the dark rot on the bottom of its pond, every block of it.]";
            case SNUFFLET -> "[Dig straight down where it's snuffling, two blocks deep.]";
            case CHIMEWREN -> "[Ring your Handbell near them.]";
            case BUMBLEPUFF -> "[Plant flowers round its nest: three or more.]";
            case LOOMWORM -> "[Drop leaves or a sapling from the same tree as its log.]";
            case CLOUDCALF -> "[Get up to its height and lead it toward the calling.]";
            case BOULDERBACK -> "[Put your light away and keep still in the dark. It'll come to you.]";
        };
    }

    /**
     * What to do for one of a kind, said plainly (SPEC §43.5; Euan 2026-11-12: "we need to make it a bit more clear what
     * you need to actually do once you find that thing"): the act this class checks for, in words to read aloud. The
     * Tasks step, the Hearth page, the line when you first meet one and the nudges all say this.
     */
    public static String howTo(KinKind k) {
        return switch (k) {
            case LAMPWING -> "Lampwings come to warm light. At night, stand where one can see you and set a torch or a lantern on the ground within "
                    + NEAR + " blocks of it. Not a soul torch: it shies away from cold blue light.";
            case BURROWBUN -> "Till the ground near one with a hoe, within " + NEAR + " blocks of it. It wants soft, broken soil to dig in; a hole dug with a shovel won't do.";
            case MOSSBACK -> "Pour a bucket of water right beside one, out in the open where the sun reaches it. If branches shade it, cut them back first.";
            case PEBBLEPUP -> "Drop something shiny by its pile of stones: a gold nugget, raw gold, quartz, lapis, an amethyst shard or a gem. It pushes plain stone away.";
            case TOTTER -> "Drop one of the things it collects by its row: a stick, a flint, a bone, a feather, a sapling or a mushroom.";
            case THATCHBACK -> "Drop whole logs by its dam on the riverbank. Planks won't do.";
            case EMBERKIT -> "Set a lit campfire or a magma block down right beside one, or light a fire next to it with flint and steel. Not soul fire.";
            case PUFFWOOL -> "Use a brush on one to get the burrs out of its wool (a brush is a feather, a copper ingot and a stick). Shears only frighten it.";
            case DUMPLIN -> "Stand up high where the flock can see you: on a rock, a stump or a block you set down, at least a block and a half above them. "
                    + "They follow you. Lead them to their nest, a flattened hay bale out in the grass about twenty blocks away.";
            case PONDLING -> "Dive into its pond and break the dark Pond Rot on the bottom, every block of it.";
            case SNUFFLET -> "Dig straight down where it's snuffling, two blocks deep, and see what's buried there.";
            case CHIMEWREN -> "Ring a bell near the flock: your Handbell (a stick over two copper ingots), or wake a sleeping bell near them. "
                    + "They only come once you've woken a bell of your own.";
            case BUMBLEPUFF -> "Plant flowers round its old nest, " + FLOWERS + " or more within " + NEST_REACH + " blocks of it. Bone meal on the grass there brings flowers up too.";
            case LOOMWORM -> "Drop a branch of its own tree by it: leaves or a sapling from the same tree as the log it lies on. Leaves off the wrong tree it turns away.";
            case CLOUDCALF -> "Climb or fly up close to it, at its height or no more than four blocks under it, and it follows you. Lead it toward the calling and the puffs of cloud until it's with its pod.";
            case BOULDERBACK -> "Put every light out of your hands, then keep still near it in the dark for half a minute. Crouch if you want to creep closer. It comes to you.";
        };
    }

    /** Where a ground kind stands at a column (for spawns and nooks). */
    static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
