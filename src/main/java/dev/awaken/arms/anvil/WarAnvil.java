package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The War Anvil's server side (SPEC §46): its list, the start (the materials into the work), every act judged by the
 * server's own clock, what happens by itself when left too long, and the finish (the story, the Temper, the weapon stood
 * in the slot) and the draw. The work only moves while its smith is at it.
 */
public final class WarAnvil {
    private WarAnvil() {}

    /** How near the Forge must be (blocks, 2 up or down). */
    public static final int REACH = 6;
    /** The one weapon forged here in stage 1, and the blade its recipe takes. */
    public static final String WEAPON = "beastbound_longsword", BLADE = "beastbound_blade";
    private static final String FORGE_IT = "anvil:forge:";

    /** Who is at which anvil now (not saved): the work moves only then. */
    private static final Map<UUID, BlockPos> AT = new HashMap<>();

    static void init() {
        Signal.onAction("anvil", (p, action, accepted) -> {
            if (!accepted || !action.startsWith(FORGE_IT)) return;
            BlockPos pos;
            try {
                pos = BlockPos.of(Long.parseLong(action.substring(FORGE_IT.length())));
            } catch (NumberFormatException e) {
                return;
            }
            if (p.level() instanceof ServerLevel level && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WarAnvilBlockEntity be) start(p, level, be);
        });
    }

    // ---- the list ----------------------------------------------------------------------------------------------------

    /** Use: your own work opens where you left it; someone else's says who; else the list. */
    static void open(ServerPlayer p, WarAnvilBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        AnvilWork w = be.work();
        if (w != null) {
            if (w.forger.equals(p.getUUID())) {
                AT.put(p.getUUID(), be.getBlockPos());
                AwakenNet.send(p, new AnvilPayloads.Open(be.getBlockPos()));
            } else Signal.toast(p, Component.literal(name(level, w.forger) + " is forging at this anvil."));
            return;
        }
        window(p, level, be);
    }

    /** The anvil's list: what the weapon takes, have against need, what's missing in plain words, and Forge it. */
    static void window(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be) {
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("War Anvil"));
        ItemStack standing = be.slot();
        if (!standing.isEmpty()) {
            w.body(Component.literal("The " + standing.getHoverName().getString() + " stands in the stone. Sneak and use the anvil to draw it out."));
            Signal.send(p, w.ttl(20 * 10).build());
            return;
        }
        w.row(Component.literal("Forge"), new ItemStack(item(WEAPON)).getHoverName());
        List<NeedRow> cost = cost(p);
        for (NeedRow n : cost) w.row(label(n), Component.literal(p.isCreative() ? "-" : Math.min(n.have(), n.need()) + " / " + n.need()));
        String missing = missing(p, forge(level, be.getBlockPos()), cost);
        if (missing != null) {
            w.body(Component.literal(missing));
            Signal.send(p, w.ttl(20 * 12).build());
            return;
        }
        w.body(Component.literal("Heat it in the Forge, draw it out on the anvil on the beat, quench it and fit the hilt."));
        w.choice(FORGE_IT + be.getBlockPos().asLong(), "Forge it", "Not now");
        Signal.send(p, w.ttl(20 * 30).build());
    }

    /** What stands in the way, in plain words, or null. */
    static @Nullable String missing(ServerPlayer p, @Nullable BlockPos forge, List<NeedRow> cost) {
        if (Progression.rank(p).ordinal() < Rank.D.ordinal()) return "Iron weapons are forged here from D-rank.";
        if (forge == null) return "It needs the Hearth's Forge within " + REACH + " blocks to heat the metal.";
        if (!p.isCreative() && !afford(p, cost)) return "You don't have everything it takes yet.";
        return null;
    }

    private static Component label(NeedRow n) {
        if (n.kind().equals("tag")) {
            Identifier id = Identifier.parse(n.id());
            return Component.translatable("tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id()))).getHoverName();
    }

    // ---- what it costs -----------------------------------------------------------------------------------------------

    /** The Longsword's recipe; without the blade, the blade's own recipe stands in for it (what the grid would take). */
    static List<NeedRow> cost(ServerPlayer p) {
        List<NeedRow> sword = ArmsArmoury.needsOf(p, WEAPON);
        NeedRow blade = null;
        for (NeedRow r : sword) if (r.kind().equals("item") && r.id().equals(Awaken.MODID + ":" + BLADE)) blade = r;
        if (blade == null || blade.have() >= blade.need()) return sword;
        Map<String, NeedRow> merged = new LinkedHashMap<>();
        for (NeedRow r : sword) if (r != blade) merged.put(r.kind() + r.id(), r);
        for (NeedRow r : ArmsArmoury.needsOf(p, BLADE)) {
            merged.merge(r.kind() + r.id(), r, (a, b) -> new NeedRow(a.kind(), a.id(), a.need() + b.need(), a.have()));
        }
        return List.copyOf(merged.values());
    }

    private static Predicate<ItemStack> matcher(NeedRow n) {
        if (n.kind().equals("tag")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(n.id()));
            return s -> s.is(tag);
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id()));
        return s -> s.is(item);
    }

    /** Whether the pack covers every part at once (a stack counted for one part isn't counted for the next). */
    static boolean afford(ServerPlayer p, List<NeedRow> cost) {
        List<ItemStack> inv = p.getInventory().getNonEquipmentItems();
        int[] left = new int[inv.size()];
        for (int i = 0; i < left.length; i++) left[i] = inv.get(i).getCount();
        for (NeedRow n : cost) {
            Predicate<ItemStack> m = matcher(n);
            int need = n.need();
            for (int i = 0; i < left.length && need > 0; i++) {
                if (left[i] <= 0 || !m.test(inv.get(i))) continue;
                int t = Math.min(need, left[i]);
                left[i] -= t;
                need -= t;
            }
            if (need > 0) return false;
        }
        return true;
    }

    /** Takes every part out of the pack, returning what it took (given back if the anvil is broken mid-work). */
    static List<ItemStack> take(ServerPlayer p, List<NeedRow> cost) {
        List<ItemStack> taken = new ArrayList<>();
        List<ItemStack> inv = p.getInventory().getNonEquipmentItems();
        for (NeedRow n : cost) {
            Predicate<ItemStack> m = matcher(n);
            int need = n.need();
            for (int i = 0; i < inv.size() && need > 0; i++) {
                ItemStack s = inv.get(i);
                if (s.isEmpty() || !m.test(s)) continue;
                taken.add(s.split(Math.min(need, s.getCount())));
                need -= taken.getLast().getCount();
            }
        }
        return taken;
    }

    // ---- the place ---------------------------------------------------------------------------------------------------

    /** The nearest Forge within reach of the anvil (a small cube, read at a use or a start). */
    public static @Nullable BlockPos forge(Level level, BlockPos anvil) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        Identifier id = Awaken.id("forge");
        for (BlockPos q : BlockPos.betweenClosed(anvil.offset(-REACH, -2, -REACH), anvil.offset(REACH, 2, REACH))) {
            if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(q).getBlock()).equals(id)) continue;
            double d = q.distSqr(anvil);
            if (d < bestD) {
                bestD = d;
                best = q.immutable();
            }
        }
        return best;
    }

    // ---- the song ----------------------------------------------------------------------------------------------------

    static void start(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be) {
        if (be.work() != null || !be.slot().isEmpty() || p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 10 * 10) {
            open(p, be);
            return;
        }
        List<NeedRow> cost = cost(p);
        BlockPos fire = forge(level, be.getBlockPos());
        if (fire == null || missing(p, fire, cost) != null) {
            window(p, level, be);
            return;
        }
        List<ItemStack> taken = p.isCreative() ? List.of() : take(p, cost);
        AnvilWork w = AnvilWork.fresh(WEAPON, level.getGameTime(), taken, p.getUUID(), fire);
        AT.put(p.getUUID(), be.getBlockPos());
        be.setWork(w);
        level.playSound(null, fire, AnvilContent.BELLOWS.get(), SoundSource.BLOCKS, 0.9F, 1F);
        AwakenNet.send(p, new AnvilPayloads.Open(be.getBlockPos()));
    }

    public static void onAct(ServerPlayer p, AnvilPayloads.Act a) {
        if (!(p.level() instanceof ServerLevel level) || !level.isLoaded(a.pos())) return;
        if (!(level.getBlockEntity(a.pos()) instanceof WarAnvilBlockEntity be)) return;
        if (p.distanceToSqr(Vec3.atCenterOf(a.pos())) > 10 * 10) return;
        AnvilWork w = be.work();
        long now = level.getGameTime();
        switch (a.act()) {
            case AnvilPayloads.LEAVE -> AT.remove(p.getUUID());
            case AnvilPayloads.RESUME -> {
                if (w == null || !mine(p, w)) return;
                AT.put(p.getUUID(), a.pos());
                w.begin(w.stage, now); // the metal cooled while you were away: the stage counts you in again
                be.changed();
            }
            case AnvilPayloads.PULL -> {
                if (w == null || w.stage != AnvilSong.HEAT || !mine(p, w)) return;
                pull(level, be, w, AnvilSong.heat(clamp(a.t(), now - w.stageStart)), p);
            }
            case AnvilPayloads.STRIKE, AnvilPayloads.TAP -> {
                int stage = a.act() == AnvilPayloads.STRIKE ? AnvilSong.SHAPE : AnvilSong.HILT;
                if (w == null || w.stage != stage || !mine(p, w) || a.a() < w.beats().size()) return;
                float t = clamp(a.t(), now - w.stageStart);
                // beats the smith's clock saw pass unstruck, not yet missed here (the server waits out the lag first)
                while (w.stage == stage && be.work() == w && w.beats().size() < a.a() && t > w.nextDue() + AnvilSong.WINDOW) beat(level, be, w, 0F, null);
                if (be.work() != w || w.stage != stage || a.a() != w.beats().size()) return;
                float off = t - w.nextDue();
                if (off < -AnvilSong.WINDOW) return; // far ahead of its beat: not taken for it
                float score = stage == AnvilSong.SHAPE ? AnvilSong.strike(off, Mth.clamp(a.aim(), 0F, 9F)) : AnvilSong.timing(off);
                beat(level, be, w, score, p);
            }
            case AnvilPayloads.PLUNGE -> {
                if (w == null || w.stage != AnvilSong.QUENCH || !mine(p, w) || w.plunge >= 0F) return;
                w.plunge = clamp(a.t(), now - w.stageStart);
                level.playSound(p, a.pos(), AnvilContent.QUENCH.get(), SoundSource.BLOCKS, 1F, 1F);
                be.changed();
            }
            case AnvilPayloads.LIFT -> {
                if (w == null || w.stage != AnvilSong.QUENCH || !mine(p, w) || w.plunge < 0F) return;
                lift(level, be, w, Math.max(0F, clamp(a.t(), now - w.stageStart) - w.plunge), p);
            }
            case AnvilPayloads.DRAW -> {
                if (w == null) draw(p, be);
            }
            default -> {}
        }
    }

    private static boolean mine(ServerPlayer p, AnvilWork w) {
        return w.forger.equals(p.getUUID());
    }

    /** A client's ticks from the stage's start, held to what the server's clock allows (a little behind, never ahead). */
    static float clamp(float client, long server) {
        return Mth.clamp(client, server - AnvilSong.LAG, server + 2F);
    }

    /** The smith hears their own pull, strikes and quench as they make them (their client plays them); everyone else from here. */
    static void pull(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float heat, @Nullable ServerPlayer smith) {
        w.heat = heat;
        w.begin(AnvilSong.SHAPE, level.getGameTime());
        level.playSound(smith, w.forge, AnvilContent.PULL.get(), SoundSource.BLOCKS, 1F, 1F);
        be.changed();
    }

    /** A beat struck (the draw out's strikes, the hilt's taps) or missed (0). */
    static void beat(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float score, @Nullable ServerPlayer smith) {
        List<Float> l = w.beats();
        l.add(score);
        boolean hilt = w.stage == AnvilSong.HILT;
        BlockPos at = be.getBlockPos();
        if (score > 0F) {
            level.playSound(smith, at, hilt ? AnvilContent.TAP.get() : AnvilContent.STRIKE.get(), SoundSource.BLOCKS, hilt ? 0.8F : 1F,
                    (hilt ? 1.2F : 1F) + level.getRandom().nextFloat() * 0.1F);
            if (!hilt && AnvilSong.clean(score)) level.playSound(smith, at, AnvilContent.RING.get(), SoundSource.BLOCKS, 0.7F, ringPitch(l.size()));
        } else level.playSound(null, at, AnvilContent.MISS.get(), SoundSource.BLOCKS, 0.6F, 1F);
        if (!hilt && l.size() == AnvilSong.FLIP_AFTER) level.playSound(smith, at, AnvilContent.FLIP.get(), SoundSource.BLOCKS, 0.8F, 1F);
        if (l.size() >= w.beatsNeeded()) {
            if (hilt) finish(level, be, w);
            else {
                w.begin(AnvilSong.QUENCH, level.getGameTime());
                be.changed();
            }
            return;
        }
        be.changed();
    }

    /** Each clean strike rings the blade a step nearer its true note (both sides use it). */
    public static float ringPitch(int strikes) {
        return 0.85F + 0.2F * Math.min(1F, strikes / (float) AnvilSong.STRIKES);
    }

    static void lift(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float held, @Nullable ServerPlayer smith) {
        w.held = held;
        w.quench = AnvilSong.quench(held);
        level.playSound(smith, be.getBlockPos(), AnvilContent.LIFT.get(), SoundSource.BLOCKS, 0.9F, 1F);
        w.begin(AnvilSong.HILT, level.getGameTime());
        be.changed();
    }

    /** Each tick of an anvil with work on it: only while its smith is at it, anything left too long happens by itself. */
    static void tick(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w) {
        BlockPos at = AT.get(w.forger);
        ServerPlayer smith = level.getServer().getPlayerList().getPlayer(w.forger);
        if (at == null || !at.equals(be.getBlockPos()) || smith == null || smith.level() != level
                || smith.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 12 * 12) return;
        float t = level.getGameTime() - w.stageStart;
        switch (w.stage) {
            case AnvilSong.HEAT -> {
                if (AnvilSong.heat(t) > AnvilSong.BURNT) pull(level, be, w, AnvilSong.BURNT, null);
            }
            case AnvilSong.SHAPE, AnvilSong.HILT -> {
                if (t > w.nextDue() + AnvilSong.WINDOW + AnvilSong.LAG) beat(level, be, w, 0F, null);
            }
            case AnvilSong.QUENCH -> {
                if (w.plunge >= 0F && t - w.plunge > AnvilSong.HOLD_MAX + AnvilSong.LAG) lift(level, be, w, AnvilSong.HOLD_MAX, null);
                else if (w.plunge < 0F && t > AnvilSong.PLUNGE_LATE + AnvilSong.LAG) {
                    // never plunged: it cools in the air, soft but whole
                    w.quench = AnvilSong.AIR;
                    w.begin(AnvilSong.HILT, level.getGameTime());
                    be.changed();
                }
            }
            default -> {}
        }
    }

    // ---- the finished weapon -----------------------------------------------------------------------------------------

    static void finish(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w) {
        ServerPlayer smith = level.getServer().getPlayerList().getPlayer(w.forger);
        float heat = Math.max(0F, w.heat);
        int flaw = AnvilSong.burnt(heat) ? AnvilSong.BURNT_TIP : w.held >= 0F && AnvilSong.brittle(w.held) ? AnvilSong.BRITTLE : AnvilSong.NO_FLAW;
        float score = AnvilSong.score(heat, AnvilWork.mean(w.scores), Math.max(0F, w.quench), AnvilWork.mean(w.taps));
        AnvilSong.Quality q = AnvilSong.quality(score, flaw != AnvilSong.NO_FLAW);
        ItemStack made = new ItemStack(item(w.weapon));
        be.setWork(null);
        if (made.isEmpty()) return;
        String maker = smith != null ? smith.getGameProfile().name() : "";
        made.set(AnvilContent.FORGED.get(), new WeaponForged(WeaponForged.SCHEMA, q.ordinal(), flaw, maker));
        int cap = smith != null ? HearthHooks.temperCap(smith, w.forge) : 1;
        int mark = smith != null ? (int) Progression.perk(smith, "awaken:temper_start") : 0;
        int temper = AnvilSong.temper(q, mark, cap);
        if (temper > 0) Upgrades.applyTemper(made, temper);
        be.setSlot(made);
        level.playSound(null, be.getBlockPos(), AnvilContent.FLIP.get(), SoundSource.BLOCKS, 1F, 0.7F);
        if (smith != null) {
            Progression.count(smith, "awaken:weapon_forged");
            AwakenNet.send(smith, new AnvilPayloads.Done(be.getBlockPos(), q.ordinal(), flaw, temper));
        }
    }

    /** Draws what stands in the slot into the main hand (what was there goes to the pack, else at your feet). */
    static void draw(ServerPlayer p, WarAnvilBlockEntity be) {
        ItemStack s = be.slot();
        if (s.isEmpty() || be.work() != null) return;
        be.setSlot(ItemStack.EMPTY);
        ItemStack old = p.getMainHandItem();
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        if (!old.isEmpty() && !p.getInventory().add(old)) Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), old);
        p.level().playSound(p, be.getBlockPos(), AnvilContent.DRAWN.get(), SoundSource.BLOCKS, 1F, 1F);
        AT.remove(p.getUUID());
    }

    // ---- shared ------------------------------------------------------------------------------------------------------

    static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Awaken.id(id));
    }

    private static String name(ServerLevel level, UUID id) {
        ServerPlayer o = level.getServer().getPlayerList().getPlayer(id);
        return o == null ? "Someone" : o.getGameProfile().name();
    }

    static void forget(UUID player) {
        AT.remove(player);
    }

    /** Whether a smith is at this anvil now (tests). */
    public static boolean at(UUID player, BlockPos anvil) {
        return anvil.equals(AT.get(player));
    }

    /** Puts a smith at an anvil (tests and the tour's start, as a use would). */
    static void arrive(UUID player, BlockPos anvil) {
        AT.put(player, anvil);
    }
}
