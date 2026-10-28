package dev.awaken.delve;

import dev.awaken.core.api.HearthHooks;
import dev.awaken.progression.Progression;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the Delver's Pick's verses give (SPEC §34.4), server side: Kindle's embers (drawn light, saved in
 * {@link DelveData}, sent to the level's players), Echo's ring and Glass's sight (the server only says when; the client
 * looks through its own world), the Forge's rites (Tempered, Call), its mending and re-forging, Hush (no sound to
 * creatures, full speed sneaking off the ground or under water) and Call (a broken heart's open blocks answer).
 */
public final class PickGifts {
    private PickGifts() {}

    public static final int EMBER_LIFE = 20 * 60 * 5, EMBERS_EACH = 3, EMBER_APART = 5;
    public static final int ECHO_TICKS = 80, ECHO_REST = 20 * 20, GLASS_TICKS = 100, GLASS_REST = 20 * 40;
    /** The Glass sight wants the release looking up at least this far (degrees above level). */
    public static final float GLASS_LOOK = 30F;
    public static final int CALL_REACH = 8, CALL_MAX = 24, CALL_TICKS = 60;
    public static final int TEMPER_DIAMONDS = 3, REFORGE_IRON = 3;

    private static final Map<UUID, Long> ECHOED = new HashMap<>(), GLASSED = new HashMap<>();

    // ---- Kindle: embers -------------------------------------------------------------------------------------------------

    /** Sneak and strike bare rock with a Kindled pick: an ember sticks in the wall (none within EMBER_APART of your own). */
    static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!p.isShiftKeyDown() || e.getFace() == null || !PickVerses.has(p.getMainHandItem(), PickVerses.Verse.KINDLE)) return;
        ServerLevel level = p.level();
        if (StrikeBlocks.family(level.getBlockState(e.getPos()).getBlock()) != StrikeBlocks.Family.ROCK) return;
        ember(level, p, e.getPos().relative(e.getFace()), e.getFace().getOpposite());
    }

    /** An ember in the air cell {@code at}, on the rock face toward {@code rock}; false when one of yours is too near or it's no air. */
    public static boolean ember(ServerLevel level, ServerPlayer p, BlockPos at, Direction rock) {
        if (!level.getBlockState(at).isAir()) return false;
        DelveData data = DelveData.get(level);
        List<DelveData.Ember> mine = new ArrayList<>();
        for (DelveData.Ember em : data.embers()) {
            if (!em.owner().equals(p.getUUID())) continue;
            if (BlockPos.of(em.light()).distSqr(at) < EMBER_APART * EMBER_APART) return false;
            mine.add(em);
        }
        // the oldest of yours goes out when you light a fourth
        mine.sort((a, b) -> Long.compare(a.until(), b.until()));
        for (int i = 0; i <= mine.size() - EMBERS_EACH; i++) data.removeEmber(mine.get(i));
        data.addEmber(new DelveData.Ember(at.asLong(), rock.ordinal(), level.getGameTime() + EMBER_LIFE, p.getUUID()));
        level.playSound(null, at, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.6F);
        level.playSound(null, at, DelveContent.TRUE_STRIKE.get(), SoundSource.PLAYERS, 0.4F, 1.5F);
        Progression.count(p, "awaken:delve/embers");
        syncEmbers(level);
        return true;
    }

    /** Every ember burning in the level, to everyone in it (a handful: three a player). */
    static void syncEmbers(ServerLevel level) {
        DelveNet.Embers m = embersOf(level);
        for (ServerPlayer p : level.players()) DelveNet.send(p, m);
    }

    private static DelveNet.Embers embersOf(ServerLevel level) {
        List<DelveData.Ember> all = DelveData.get(level).embers();
        int n = Math.min(all.size(), 512);
        long[] at = new long[n];
        byte[] face = new byte[n];
        for (int i = 0; i < n; i++) {
            at[i] = all.get(i).light();
            face[i] = (byte) all.get(i).face();
        }
        return new DelveNet.Embers(at, face);
    }

    static void onJoin(ServerPlayer p) {
        DelveNet.send(p, embersOf(p.level()));
    }

    // ---- Echo and the Forge (right-click) -------------------------------------------------------------------------------

    static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND) return;
        ServerLevel level = p.level();
        BlockState s = level.getBlockState(e.getPos());
        ItemStack tool = p.getMainHandItem();
        if (isForge(s)) {
            if (forge(p, level, e.getPos(), tool)) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (!p.isShiftKeyDown() || !PickVerses.has(tool, PickVerses.Verse.ECHO)) return;
        if (StrikeBlocks.family(s.getBlock()) != StrikeBlocks.Family.ROCK) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        echo(p, e.getPos());
    }

    /** Echo: a ring through the rock from {@code at}; ores and open caves within 12 glow through it (drawn by the client). */
    public static boolean echo(ServerPlayer p, BlockPos at) {
        if (p.getKnownMovement().horizontalDistanceSqr() > 0.0025) {
            p.sendOverlayMessage(Component.literal("Hold still and the rock will answer").withColor(0xC8C2B0));
            return false;
        }
        if (!rested(p, ECHOED, ECHO_REST, "The rock is still ringing")) return false;
        ServerLevel level = p.level();
        level.playSound(null, at, DelveContent.TRUE_STRIKE.get(), SoundSource.PLAYERS, 0.9F, 0.5F);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        DelveNet.send(p, new DelveNet.PickSight(0, at.asLong(), ECHO_TICKS));
        Progression.count(p, "awaken:delve/echoes");
        return true;
    }

    /**
     * Glass, from the heavy strike: a glint released looking up turns the rock before you to glass for five seconds (to
     * your eyes only: the client draws it). True when it took the release (the strike doesn't bore).
     */
    static boolean glass(ServerPlayer p, boolean glint) {
        if (!glint || p.getXRot() > -GLASS_LOOK || !PickVerses.has(p.getMainHandItem(), PickVerses.Verse.GLASS)) return false;
        if (!rested(p, GLASSED, GLASS_REST, "The glass in the rock needs time to clear")) return true;
        p.level().playSound(null, p.blockPosition(), DelveContent.DEEPGLASS_HUM.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
        DelveNet.send(p, new DelveNet.PickSight(1, BlockPos.containing(p.getEyePosition()).asLong(), GLASS_TICKS));
        Progression.count(p, "awaken:delve/glass_sights");
        return true;
    }

    private static boolean rested(ServerPlayer p, Map<UUID, Long> last, int rest, String tired) {
        long now = p.level().getGameTime();
        Long was = last.get(p.getUUID());
        if (was != null && now - was < rest && now >= was) {
            p.sendOverlayMessage(Component.literal(tired + " (" + ((rest - (now - was)) / 20 + 1) + " s)").withColor(0xC8C2B0));
            return false;
        }
        last.put(p.getUUID(), now);
        return true;
    }

    // ---- the Forge's rites --------------------------------------------------------------------------------------------

    static boolean isForge(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).equals(dev.awaken.Awaken.id("forge"));
    }

    /**
     * Right-click your Forge (one not inside anyone else's Hearth) with the pick: the next rite if you carry what it
     * takes (3 diamonds wake Tempered, a starmetal ingot wakes Call), else a mend (an iron ingot, a quarter of its uses).
     * With iron ingots in hand and no pick on you (one pulled before): the Forge makes you another at your verse.
     */
    static boolean forge(ServerPlayer p, ServerLevel level, BlockPos pos, ItemStack held) {
        if (HearthHooks.inside(level, pos)) {
            var anchor = HearthHooks.nearest(level, pos, 96);
            if (anchor.isEmpty() || !HearthHooks.belongs(p, anchor.get())) return false;
        }
        int v = PickVerses.verse(held);
        if (v < 0) {
            if (!held.is(Items.IRON_INGOT) || WakingPick.carries(p) || Progression.profile(p).counter(WakingPick.PULLED) == 0) return false;
            if (held.getCount() < REFORGE_IRON) {
                say(p, "The Forge can make you another Delver's Pick from " + REFORGE_IRON + " iron ingots");
                return true;
            }
            held.shrink(REFORGE_IRON);
            WakingPick.reforge(p);
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 0.8F);
            return true;
        }
        PickVerses.Verse next = v + 1 < PickVerses.VERSES.length ? PickVerses.VERSES[v + 1] : null;
        if (next == PickVerses.Verse.TEMPERED && take(p, Items.DIAMOND, TEMPER_DIAMONDS)) {
            rite(p, level, pos, held, next);
            return true;
        }
        if (next == PickVerses.Verse.CALL && take(p, DelveContent.STARMETAL_INGOT.get(), 1)) {
            rite(p, level, pos, held, next);
            return true;
        }
        if (held.isDamaged() && take(p, Items.IRON_INGOT, 1)) {
            held.setDamageValue(Math.max(0, held.getDamageValue() - held.getMaxDamage() / 4));
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
            say(p, "The Forge mends the pick");
            return true;
        }
        if (next != null && next.deed == null) say(p, "Next: " + PickVerses.wakesWhen(next));
        else if (held.isDamaged()) say(p, "An iron ingot mends the pick at the Forge");
        else say(p, "The pick is whole");
        return true;
    }

    private static void rite(ServerPlayer p, ServerLevel level, BlockPos pos, ItemStack held, PickVerses.Verse v) {
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9F, 0.6F);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.7F, 0.7F);
        PickVerses.wake(p, held, v);
        PickVerses.check(p);
    }

    private static boolean take(ServerPlayer p, Item item, int n) {
        if (p.isCreative()) return true;
        var inv = p.getInventory();
        if (ContainerHelper.clearOrCountMatchingItems(inv, s -> s.is(item), n, true) < n) return false;
        ContainerHelper.clearOrCountMatchingItems(inv, s -> s.is(item), n, false);
        return true;
    }

    private static void say(ServerPlayer p, String s) {
        p.sendOverlayMessage(Component.literal(s).withColor(0xC8C2B0));
    }

    // ---- Hush -----------------------------------------------------------------------------------------------------------

    /** Whether a player's strikes are hushed (the pick in hand has woken Hush). */
    public static boolean hushed(Player p) {
        return PickVerses.has(p.getMainHandItem(), PickVerses.Verse.HUSH);
    }

    /** A Hushed pick's breaks make no sound to creatures (no block-destroy vibration: wardens, sculk, the Hollows' ears). */
    static void onGameEvent(VanillaGameEvent e) {
        if (e.getCause() instanceof Player p && e.getVanillaEvent().is(GameEvent.BLOCK_DESTROY.key()) && hushed(p)) e.setCanceled(true);
    }

    /** A Hushed pick mines at full speed while you sneak, off the ground or with your head under water. */
    static void onBreakSpeed(PlayerEvent.BreakSpeed e) {
        Player p = e.getEntity();
        if (!p.isShiftKeyDown() || !hushed(p)) return;
        float speed = e.getNewSpeed();
        if (!p.onGround()) speed *= 5F;
        if (p.getFluidInteraction().isEyeInFluidMatching(p, (_, type, _) -> type.getIsWaterLike())) {
            double sub = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SUBMERGED_MINING_SPEED);
            if (sub > 0 && sub < 1) speed /= (float) sub;
        }
        e.setNewSpeed(speed);
    }

    // ---- Call -----------------------------------------------------------------------------------------------------------

    private record Answer(UUID player, ResourceKey<Level> dim, Ore ore, long[] cells, long start) {}

    private static final List<Answer> ANSWERS = new ArrayList<>();

    /**
     * A heart broke under a Called pick: the lode's open blocks within {@value #CALL_REACH} of it (nearest first, at most
     * {@value #CALL_MAX}, each one a strike may take) crack one after another over three seconds, their ore going to the
     * player. Returns the blocks that answer.
     */
    public static long[] call(ServerLevel level, ServerPlayer p, Lode l, LongArrayList left) {
        ItemStack tool = p.getMainHandItem();
        if (!PickVerses.has(tool, PickVerses.Verse.CALL) || left.isEmpty()) return new long[0];
        BlockPos h = l.heartPos();
        LongArrayList near = new LongArrayList();
        for (long c : left) if (BlockPos.of(c).distSqr(h) <= CALL_REACH * CALL_REACH) near.add(c);
        near.sort((a, b) -> Double.compare(BlockPos.of(a).distSqr(h), BlockPos.of(b).distSqr(h)));
        LongArrayList answer = new LongArrayList();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long c : near) {
            if (answer.size() >= CALL_MAX) break;
            m.set(c);
            BlockState s = level.getBlockState(m);
            if (l.ore().matches(s) && HeavyStrikes.mayBore(level, p, m, s, tool)) answer.add(c);
        }
        if (answer.isEmpty()) return new long[0];
        long[] cells = answer.toLongArray();
        ANSWERS.add(new Answer(p.getUUID(), level.dimension(), l.ore(), cells.clone(), level.getGameTime()));
        DelveNet.near(level, null, h.getX() + 0.5, h.getY() + 0.5, h.getZ() + 0.5, 48, new DelveNet.CallAnswer(p.getId(), l.ore().ordinal(), cells, CALL_TICKS));
        Progression.count(p, "awaken:delve/calls");
        return cells;
    }

    // ---- ticking --------------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (!ANSWERS.isEmpty()) answers(server);
        if (server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            DelveData data = DelveData.get(level);
            if (data.embers().isEmpty()) continue;
            long now = level.getGameTime();
            boolean gone = data.embers().removeIf(em -> em.until() <= now || em.until() - now > EMBER_LIFE);
            if (gone) {
                data.setDirty();
                syncEmbers(level);
            }
        }
    }

    private static void answers(MinecraftServer server) {
        for (Iterator<Answer> it = ANSWERS.iterator(); it.hasNext(); ) {
            Answer a = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(a.player());
            ServerLevel level = server.getLevel(a.dim());
            if (p == null || level == null || p.level() != level) {
                it.remove();
                continue;
            }
            long t = level.getGameTime() - a.start();
            int due = (int) Math.min(a.cells().length, (t + 1) * a.cells().length / CALL_TICKS + 1);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int i = 0; i < due; i++) {
                if (a.cells()[i] == Long.MIN_VALUE) continue;
                m.set(a.cells()[i]);
                a.cells()[i] = Long.MIN_VALUE;
                BlockState s = level.getBlockState(m);
                if (!a.ore().matches(s) || !level.isLoaded(m)) continue;
                List<ItemStack> drops = Block.getDrops(s, level, m, null, p, p.getMainHandItem());
                level.destroyBlock(m, false, p);
                for (ItemStack d : drops) if (!p.getInventory().add(d)) p.drop(d, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            if (due >= a.cells().length) it.remove();
        }
    }

    static void forget(UUID id) {
        ECHOED.remove(id);
        GLASSED.remove(id);
    }

    static void clear() {
        ECHOED.clear();
        GLASSED.clear();
        ANSWERS.clear();
    }
}
