package dev.awaken.kin;

import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
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
 * close but wrong gets a nudge, and after a few misses the nudge points closer (never naming the act). Three helps of one
 * beast and it trusts you.
 */
public final class KinHelp {
    private KinHelp() {}

    /** How near the act must be to the beast, and how long one beast takes before it can be helped again. */
    static final int NEAR = 10, GAP = 20 * 20, LESSON_GAP = 20 * 4;

    private record Pending(ServerPlayer player, BlockPos pos, int what, long at) {}

    private static final int TILL = 0, WATER = 1, CLEARED = 2;
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
        if (s.getLightEmission(level, pos) < 10) return;
        for (KinBeast b : wild(level, pos, KinKind.LAMPWING)) {
            if (cold(s)) nudge(b, p, "[It flinched from the cold blue light and went back into the trees.]");
            else if (!b.hasLineOfSight(p) && !KinHome.lesson(b)) nudge(b, p, "[It can't see that light from where it is.]");
            else help(b, p);
            return;
        }
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
        else if (held.is(Items.BONE_MEAL)) for (KinBeast b : wild(level, pos, KinKind.MOSSBACK))
            nudge(b, p, "[The garden on its back perked up for a moment. That isn't what it's after.]");
    }

    static void broken(BreakBlockEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState s = e.getState();
        BlockPos pos = e.getPos();
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
                default -> {}
            }
        }
    }

    /** A Pebblepup's pile, every half second: a shiny stone tossed near it is taken; a dull one pushed off. */
    static void beastTick(ServerLevel level, KinBeast b) {
        if (b.kind() != KinKind.PEBBLEPUP || b.home != null) return;
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(b.origin).inflate(6, 3, 6), ie -> ie.isAlive() && ie.getAge() > 10)) {
            Entity thrower = it.getOwner();
            if (!(thrower instanceof ServerPlayer p)) continue;
            ItemStack s = it.getItem();
            if (shiny(s)) {
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
            if (dull(s) && PUSHED.add(it.getId())) {
                it.setDeltaMovement((it.getX() - b.origin.getX()) * 0.15, 0.25, (it.getZ() - b.origin.getZ()) * 0.15);
                nudge(b, p, "[It pushed the dull one off its pile.]");
                return;
            }
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
            default -> "It's pleased.";
        };
    }

    /** Close but wrong: a nudge; after three, the nudge points closer, and still never says what to do. */
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

    static String closer(KinKind k) {
        return switch (k) {
            case LAMPWING -> "[It keeps looking from the trees to the warm glow of a torch, and back.]";
            case BURROWBUN -> "[It keeps pawing at the grass, as if the top needed breaking up first.]";
            case MOSSBACK -> "[It keeps looking up at the branches over it, and over at the nearest water.]";
            case PEBBLEPUP -> "[It's staring at the glinting bits in the rock nearby.]";
            default -> "";
        };
    }

    /** Where a ground kind stands at a column (for spawns and nooks). */
    static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
