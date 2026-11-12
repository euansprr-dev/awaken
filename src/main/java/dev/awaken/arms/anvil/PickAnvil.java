package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.ArmsTrack;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.ForgeHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.delve.PickVerses;
import dev.awaken.delve.api.Pick;
import dev.awaken.delve.forged.ForgedPickItem;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.delve.forged.PickForged;
import dev.awaken.delve.forged.PickForm;
import dev.awaken.delve.forged.PickStats;
import dev.awaken.enchant.EnchantRules;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks at the War Anvil (SPEC §49.4). The anvil's own acts judge the work ({@link WarAnvil}); this says which come next, what
 * a pick in your hand can have done, what it costs and what comes out. Nothing here can fail a pick: a bad heat makes a
 * Plain one, and whatever goes in comes back if you back out before the first blow or the anvil is broken.
 */
public final class PickAnvil {
    private PickAnvil() {}

    private static final int DONE = -1;

    // ---- which act comes next ------------------------------------------------------------------------------------------

    /** The rank's own acts in the order they're done, with the rank that first has each. */
    private static final int[] ACTS = {AnvilSong.TIPS, AnvilSong.SPINE, AnvilSong.SEAM}, ACT_RANK = {2, 3, 4};

    /** A raise above D is only that rank's new work: it has been an eye, and a head, for a long time. */
    private static boolean onlyNew(AnvilWork w) {
        return w.kind == AnvilWork.RAISE && w.tier >= 2;
    }

    static int next(AnvilWork w) {
        boolean memory = w.memory();
        return switch (w.stage) {
            case AnvilSong.HEAT -> memory || onlyNew(w) ? AnvilSong.SHAPE : AnvilSong.EYE;
            case AnvilSong.EYE -> AnvilSong.SHAPE;
            case AnvilSong.SHAPE -> memory ? AnvilSong.COLOURS : actFrom(w, 0);
            case AnvilSong.TIPS -> actFrom(w, 1);
            case AnvilSong.SPINE -> actFrom(w, 2);
            case AnvilSong.SEAM -> AnvilSong.COLOURS;
            case AnvilSong.COLOURS -> memory ? DONE : w.tier >= 5 ? AnvilSong.STAR : AnvilSong.HANG;
            case AnvilSong.STAR -> AnvilSong.HANG;
            case AnvilSong.KNAP -> memory ? DONE : AnvilSong.BIND;
            case AnvilSong.BIND -> AnvilSong.HANG;
            default -> DONE;
        };
    }

    private static int actFrom(AnvilWork w, int from) {
        for (int i = from; i < ACTS.length; i++) {
            if (w.tier < ACT_RANK[i]) break;
            if (!onlyNew(w) || w.tier == ACT_RANK[i]) return ACTS[i];
        }
        return AnvilSong.COLOURS;
    }

    static void advance(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w) {
        int n = next(w);
        if (n == DONE) {
            WarAnvil.finish(level, be, w);
            return;
        }
        w.begin(n, level.getGameTime());
        be.changed();
    }

    // ---- the acts --------------------------------------------------------------------------------------------------------

    /** A beat struck or missed (0): the head's draw, the eye, the tips, the hanging, or quiet work. */
    static void beat(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float score, @Nullable ServerPlayer smith) {
        List<Float> l = w.beats();
        l.add(score);
        BlockPos at = be.getBlockPos();
        if (score > 0F) {
            float jitter = 0.95F + level.getRandom().nextFloat() * 0.1F;
            switch (w.stage) {
                case AnvilSong.EYE -> level.playSound(smith, at, AnvilContent.EYE.get(), SoundSource.BLOCKS, 1F, jitter);
                case AnvilSong.TIPS -> level.playSound(smith, at, AnvilContent.TIP.get(), SoundSource.BLOCKS, 1F, jitter);
                // each rap higher as the wood seats
                case AnvilSong.HANG -> level.playSound(smith, at, AnvilContent.HANG.get(), SoundSource.BLOCKS, 0.9F, 0.9F + 0.14F * (l.size() - 1));
                case AnvilSong.TEND -> level.playSound(smith, at, AnvilContent.TEND.get(), SoundSource.BLOCKS, 0.8F, jitter);
                default -> {
                    level.playSound(smith, at, AnvilContent.strike(w.tier), SoundSource.BLOCKS, 1F, jitter);
                    if (AnvilSong.clean(score))
                        level.playSound(smith, at, AnvilContent.RING.get(), SoundSource.BLOCKS, 0.7F, AnvilSong.ringPitch(w.song, AnvilSong.cleanOf(l, l.size()), w.beatsNeeded()));
                }
            }
        } else level.playSound(null, at, AnvilContent.MISS.get(), SoundSource.BLOCKS, 0.6F, 1F);
        boolean turn = w.stage == AnvilSong.EYE ? l.size() == AnvilSong.EYES / 2 : w.stage == AnvilSong.SHAPE && !w.memory() && l.size() == w.song.flipAfter();
        if (turn) level.playSound(smith, at, AnvilContent.FLIP.get(), SoundSource.BLOCKS, 0.8F, 1F);
        if (l.size() < w.beatsNeeded()) {
            be.changed();
            return;
        }
        float mean = AnvilWork.mean(l);
        switch (w.stage) {
            case AnvilSong.EYE -> w.eye = mean;
            case AnvilSong.TIPS -> w.tips = mean;
            case AnvilSong.HANG -> w.hang = mean;
            default -> {}
        }
        // only the draw's strikes stay for the score; what's kept of the others is its mean
        if (w.stage != AnvilSong.SHAPE) w.taps.clear();
        advance(level, be, w);
    }

    /** A sample of the spine's thread: the line followed, like the grind's. */
    static void threaded(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, @Nullable ServerPlayer smith) {
        if (w.taps.size() % 2 == 0) level.playSound(smith, be.getBlockPos(), AnvilContent.THREAD.get(), SoundSource.BLOCKS, 0.7F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        if (w.taps.size() < AnvilSong.SPINE_SAMPLES) {
            be.changed();
            return;
        }
        w.spine = AnvilWork.mean(w.taps);
        w.taps.clear();
        advance(level, be, w);
    }

    static SoundEvent holdSound(AnvilWork w) {
        return w.stage == AnvilSong.SEAM ? AnvilContent.POUR.get() : AnvilContent.COLOURS.get();
    }

    /** The colours stopped, or the seam's pour lifted. */
    static void released(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float held, @Nullable ServerPlayer smith) {
        if (w.stage == AnvilSong.COLOURS) {
            w.colour = Math.min(AnvilSong.COLOUR_BLUE, AnvilSong.colour(held));
            level.playSound(smith, be.getBlockPos(), AnvilContent.QUENCH.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
        } else {
            w.held = held;
            w.seam = AnvilSong.quench(held);
            level.playSound(smith, be.getBlockPos(), AnvilContent.LIFT.get(), SoundSource.BLOCKS, 0.9F, 1F);
        }
        advance(level, be, w);
    }

    /** Left alone: colours held all the way or never begun go blue, a seam cools by itself in the air. Nothing fails. */
    static void waited(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, float t) {
        if (w.stage == AnvilSong.COLOURS) {
            if (w.plunge >= 0F ? t - w.plunge > AnvilSong.COLOUR_FULL + AnvilSong.LAG : t > AnvilSong.COLOUR_LATE + AnvilSong.LAG) released(level, be, w, AnvilSong.COLOUR_FULL, null);
        } else if (w.plunge >= 0F) {
            if (t - w.plunge > AnvilSong.HOLD_MAX + AnvilSong.LAG) released(level, be, w, AnvilSong.HOLD_MAX, null);
        } else if (t > AnvilSong.PLUNGE_LATE + AnvilSong.LAG) {
            w.seam = AnvilSong.AIR;
            advance(level, be, w);
        }
    }

    // ---- what's in your hand -------------------------------------------------------------------------------------------

    /** The old pick, a forged one, or one of the old mattocks. */
    static boolean holds(ItemStack s) {
        return ForgedPicks.is(s) || old(s) != null;
    }

    /** What an old tool is remade as: its form and rank; null when it isn't one. */
    record Remade(PickForm form, int tier) {}

    static @Nullable Remade old(ItemStack s) {
        int v = PickVerses.verse(s);
        if (v >= 0) return new Remade(PickForm.DELVER, tierOfVerse(v));
        Identifier key = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (!key.getNamespace().equals(Awaken.MODID) || !key.getPath().endsWith("_mattock")) return null;
        String prefix = key.getPath().substring(0, key.getPath().length() - "_mattock".length());
        for (int t = 0; t < ForgedPicks.PREFIX.length; t++) if (ForgedPicks.PREFIX[t].equals(prefix)) return new Remade(PickForm.MATTOCK, t);
        return null;
    }

    /** An old pick's rank by the verses it woke (§49.7). */
    static int tierOfVerse(int v) {
        return v == 0 ? 0 : v <= 2 ? 1 : v <= 4 ? 2 : v == 5 ? 3 : 4;
    }

    /** Use with a pick in hand and the anvil free: its window. False when it isn't one. */
    static boolean open(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be) {
        ItemStack s = p.getMainHandItem();
        if (!holds(s)) return false;
        AwakenNet.send(p, new AnvilPayloads.Held(be.getBlockPos(), options(p, level, be.getBlockPos(), s), List.of(), ""));
        return true;
    }

    private static final int FORM = 100;

    static List<AnvilPayloads.Option> options(ServerPlayer p, ServerLevel level, BlockPos anvil, ItemStack s) {
        Remade r = old(s);
        if (r != null) return List.of(remakeRow(s, r));
        ForgedPickItem i = ForgedPicks.item(s);
        if (i == null) return List.of();
        List<AnvilPayloads.Option> out = new ArrayList<>();
        if (i.tier + 1 < PickStats.TIERS) out.add(raiseRow(p, level, anvil, i));
        for (PickForm f : PickForm.values()) if (f != i.form) out.add(redrawRow(p, level, anvil, i, f));
        int waiting = ForgedPicks.waiting(p, s);
        if (waiting != 0) out.add(workInRow(waiting));
        out.add(dressRow(p, i, s));
        return out;
    }

    private static List<AnvilPayloads.Cost> costs(List<NeedRow> needs) {
        List<AnvilPayloads.Cost> out = new ArrayList<>();
        for (NeedRow n : needs) out.add(new AnvilPayloads.Cost(ArmsTrack.label(n), n.have(), n.need(), n.id(), n.kind().equals("tag")));
        return out;
    }

    /** A card and its page on the anvil's window (SPEC §46.8): short words for the card, up to six across. */
    private static AnvilPayloads.Option row(int kind, String name, String headline, String button, boolean ready, String line, List<AnvilPayloads.Cost> costs) {
        return new AnvilPayloads.Option(kind, name, button, ready, line, costs, headline, List.of(), List.of());
    }

    private static AnvilPayloads.Option remakeRow(ItemStack s, Remade r) {
        String into = WarAnvil.tierName(r.tier()) + " " + r.form().label;
        String line = r.form() == PickForm.DELVER
                ? "Free, and it keeps every gift it has woken and every enchantment on it. It comes back as a " + into + ", and you forge it on from there. If you'd rather not, it goes on working as it is."
                : "Free. It comes back as a " + into + ", with everything written on it, and turns in the hand for earth and stone.";
        return row(AnvilWork.MIGRATE, "Remake", r.form().label, "Remake it as a " + into, true, line, List.of());
    }

    /** Its next rank's parts, less one of the metal: the head is most of the steel it needs. */
    static List<NeedRow> raiseNeeds(ServerPlayer p, String nextId) {
        List<NeedRow> out = new ArrayList<>(AnvilCosts.needs(p, nextId));
        if (!out.isEmpty()) {
            NeedRow first = out.getFirst();
            out.set(0, new NeedRow(first.kind(), first.id(), Math.max(1, first.need() - 1), first.have()));
        }
        return out;
    }

    /** Half of what it would cost new (rounded down), and always at least one of the first two parts. */
    static List<NeedRow> halved(ServerPlayer p, String id) {
        List<NeedRow> out = new ArrayList<>();
        int k = 0;
        for (NeedRow n : AnvilCosts.needs(p, id)) {
            int half = n.need() / 2;
            if (k++ < 2) half = Math.max(1, half);
            if (half > 0) out.add(new NeedRow(n.kind(), n.id(), half, n.have()));
        }
        return out;
    }

    private static AnvilPayloads.Option raiseRow(ServerPlayer p, ServerLevel level, BlockPos anvil, ForgedPickItem i) {
        String id = ForgedPicks.id(i.form, i.tier + 1);
        List<NeedRow> needs = raiseNeeds(p, id);
        String why = missing(p, level, anvil, id, false, needs);
        return row(AnvilWork.RAISE, "Raise", ArmsArmoury.TIERS[i.tier + 1] + "-rank", "Raise it to " + ArmsArmoury.TIERS[i.tier + 1] + "-rank", why == null,
                why != null ? why : "It keeps its gifts, its enchantments and everything it has done.", costs(needs));
    }

    private static AnvilPayloads.Option redrawRow(ServerPlayer p, ServerLevel level, BlockPos anvil, ForgedPickItem i, PickForm f) {
        String id = ForgedPicks.id(f, i.tier);
        List<NeedRow> needs = halved(p, id);
        String why = missing(p, level, anvil, id, false, needs);
        return row(AnvilWork.REDRAW + FORM * (f.ordinal() + 1), "Re-draw", f.label, "Re-draw it as a " + f.label, why == null, why != null ? why : "Half the parts, and it keeps everything it has.", costs(needs));
    }

    private static AnvilPayloads.Option workInRow(int waiting) {
        StringBuilder names = new StringBuilder();
        for (Pick.Gift g : Pick.Gift.values()) {
            if ((waiting & g.bit()) == 0) continue;
            if (!names.isEmpty()) names.append(", ");
            names.append(giftName(g));
        }
        return row(AnvilWork.WORK_IN, "Work in", "Free", "Work in what you've done", true, "Free, and quick: " + names + ".", List.of());
    }

    static String giftName(Pick.Gift g) {
        return switch (g) {
            case HUM -> "the Hum";
            case KINDLE -> "Kindle";
            case SOUNDING -> "Sounding";
            case WIDE_SEAM -> "a wider seam";
            case LONG_SOUNDING -> "Long Sounding";
            case HUSH -> "Hush";
            case CALL -> "the Call";
        };
    }

    /** One of its rank's first part: a little metal, worked into the edge. */
    static List<NeedRow> dressNeeds(ServerPlayer p, ForgedPickItem i) {
        List<AnvilCosts.Part> parts = AnvilCosts.of(ForgedPicks.id(i.form, i.tier));
        if (parts == null || parts.isEmpty()) return List.of();
        AnvilCosts.Part first = parts.getFirst();
        return List.of(new NeedRow(first.kind(), first.id(), 1, ArmsArmoury.have(p, first.kind(), first.id())));
    }

    private static AnvilPayloads.Option dressRow(ServerPlayer p, ForgedPickItem i, ItemStack s) {
        List<NeedRow> needs = dressNeeds(p, i);
        boolean worn = s.getDamageValue() > 0, keen = ForgedPicks.data(s).tally().keen() >= PickStats.KEEN_BLOCKS;
        boolean afford = p.isCreative() || WarAnvil.afford(p, needs);
        String line = !worn && keen ? "The edge is as good as it gets. Dress it again once it's dulled a little."
                : !afford ? "You don't have the metal to dress it."
                : ForgedPicks.blunt(s) ? "It's blunt. A quarter of its wear comes off and it cuts faster for the next " + PickStats.KEEN_BLOCKS + " blocks."
                : "A quarter of its wear comes off and it cuts faster for the next " + PickStats.KEEN_BLOCKS + " blocks.";
        return row(AnvilWork.DRESS, "Dress", "The edge", "Dress the edge", (worn || !keen) && afford, line, costs(needs));
    }

    // ---- at the door ------------------------------------------------------------------------------------------------------

    /** What stands in the way, in plain words, or null. Its cost is passed in: fresh, halved and raised picks all differ. */
    static @Nullable String missing(ServerPlayer p, ServerLevel level, BlockPos anvil, String id, boolean memory, List<NeedRow> cost) {
        int tier = AnvilWork.tierOf(id);
        String style = AnvilWork.styleOf(id);
        PickForm f = PickForm.ofStyle(style);
        if (tier < 0 || f == null) return "That isn't made at a War Anvil.";
        if (Progression.profile(p).level() < ArmsArmoury.LEVEL[tier]) return WarAnvil.tierName(tier) + " picks open at level " + ArmsArmoury.LEVEL[tier] + ".";
        if (!ForgedPicks.open(p, f)) return howOpens(f);
        if (tier > 0 && WarAnvil.forge(level, anvil) == null) return AnvilHeld.NO_FORGE;
        String rebuild = WarAnvil.unbuilt(tier, WarAnvil.build(level.getBlockState(anvil)), "picks");
        if (rebuild != null) return rebuild;
        if (memory && !SmithMemory.of(p).remembers(style, tier)) return "You haven't forged this one well enough yet to make it from memory.";
        if (!p.isCreative() && !WarAnvil.afford(p, cost)) return "You don't have everything it takes yet.";
        return null;
    }

    private static String howOpens(PickForm f) {
        return f == PickForm.BREAKER ? "You don't know how to make one of these yet. Break a lode's heart, and you'll know what the head has to stand up to."
                : "You don't know how to make one of these yet. Light one of the old Delver's lamps, and you'll start listening to the rock.";
    }

    /** Whether Make it again is on offer: you've forged picks of this rank before and carry none now. Half the parts. */
    static boolean again(ServerPlayer p, ServerLevel level, BlockPos anvil, String id) {
        int tier = AnvilWork.tierOf(id);
        if (tier < 0 || Progression.profile(p).counter(ForgedPicks.BEST_TIER) != tier + 1 || carriesOne(p)) return false;
        return missing(p, level, anvil, id, false, halved(p, id)) == null;
    }

    private static boolean carriesOne(ServerPlayer p) {
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (ForgedPicks.is(s)) return true;
        return ForgedPicks.is(p.getOffhandItem());
    }

    /** The door's Make it again: begun like a fresh forging, for half the parts. */
    public static boolean forgeAgain(ServerPlayer p, BlockPos pos, String id) {
        if (!(p.level() instanceof ServerLevel level) || !dev.awaken.core.api.Ready.loaded(level, pos) || !(level.getBlockEntity(pos) instanceof WarAnvilBlockEntity be)) return false;
        if (be.work() != null || !be.slot().isEmpty() || p.distanceToSqr(Vec3.atCenterOf(pos)) > 10 * 10 || !again(p, level, pos, id)) return false;
        int tier = AnvilWork.tierOf(id);
        BlockPos fire = tier == 0 ? pos : WarAnvil.forge(level, pos);
        if (fire == null) return false;
        List<ItemStack> taken = p.isCreative() ? List.of() : WarAnvil.take(p, halved(p, id));
        start(p, level, be, AnvilWork.fresh(id, level.getGameTime(), taken, p.getUUID(), fire, AnvilWork.FORGE));
        return true;
    }

    private static void start(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be, AnvilWork w) {
        w.bellows = !w.hand() && !w.tended() && WarAnvil.bellows(level, p, w.forge);
        WarAnvil.arrive(p.getUUID(), be.getBlockPos());
        SmithMemory.of(p).at(level.dimension(), be.getBlockPos());
        be.setWork(w);
        if (w.stage == AnvilSong.HEAT) level.playSound(null, w.forge, AnvilContent.BELLOWS.get(), SoundSource.BLOCKS, 0.9F, 1F);
        AwakenNet.send(p, new AnvilPayloads.Open(be.getBlockPos()));
    }

    // ---- the start --------------------------------------------------------------------------------------------------------

    /** A button in the window. False if it can't begin (the window is sent again, its lines as they now are). */
    static boolean begin(ServerPlayer p, ServerLevel level, WarAnvilBlockEntity be, int code) {
        ItemStack s = p.getMainHandItem();
        if (!holds(s) || be.work() != null || !be.slot().isEmpty() || p.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 10 * 10) return false;
        int kind = code % FORM, formCode = code / FORM;
        BlockPos anvil = be.getBlockPos();
        boolean free = p.isCreative();
        Remade r = old(s);
        ForgedPickItem i = ForgedPicks.item(s);
        long now = level.getGameTime();
        String id;
        int stage, target = 0;
        BlockPos fire = anvil;
        List<NeedRow> cost = List.of();
        switch (kind) {
            case AnvilWork.MIGRATE -> {
                if (r == null) return false;
                id = ForgedPicks.id(r.form(), r.tier());
                stage = AnvilSong.TEND;
            }
            case AnvilWork.WORK_IN -> {
                if (i == null || ForgedPicks.waiting(p, s) == 0) return false;
                id = ForgedPicks.id(i.form, i.tier);
                stage = AnvilSong.TEND;
            }
            case AnvilWork.DRESS -> {
                if (i == null || s.getDamageValue() == 0 && ForgedPicks.data(s).tally().keen() >= PickStats.KEEN_BLOCKS) return false;
                id = ForgedPicks.id(i.form, i.tier);
                stage = AnvilSong.TEND;
                cost = dressNeeds(p, i);
                if (!free && !WarAnvil.afford(p, cost)) return false;
            }
            case AnvilWork.RAISE -> {
                if (i == null || i.tier + 1 >= PickStats.TIERS) return false;
                id = ForgedPicks.id(i.form, i.tier + 1);
                cost = raiseNeeds(p, id);
                if (missing(p, level, anvil, id, false, cost) != null) return false;
                stage = AnvilSong.HEAT;
                fire = WarAnvil.forge(level, anvil);
                target = i.tier + 1;
            }
            case AnvilWork.REDRAW -> {
                if (i == null || formCode < 1 || formCode > PickForm.values().length) return false;
                PickForm f = PickForm.values()[formCode - 1];
                if (f == i.form) return false;
                id = ForgedPicks.id(f, i.tier);
                cost = halved(p, id);
                if (missing(p, level, anvil, id, false, cost) != null) return false;
                stage = i.tier == 0 ? AnvilSong.KNAP : AnvilSong.HEAT;
                if (i.tier > 0) fire = WarAnvil.forge(level, anvil);
                target = i.tier;
            }
            default -> {
                return false;
            }
        }
        if (fire == null) return false;
        // the pick leaves the hand first, so nothing it is can be taken as a part
        ItemStack piece = s.copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        List<ItemStack> taken = free ? List.of() : WarAnvil.take(p, cost);
        start(p, level, be, AnvilWork.picked(id, stage, now, taken, piece, p.getUUID(), fire, kind, target));
        return true;
    }

    /** Backs out before the first blow: everything back in the pack, the pick back in the hand. */
    static void cancel(ServerPlayer p, WarAnvilBlockEntity be, AnvilWork w) {
        if (!w.pick) return;
        boolean first = w.stage == AnvilSong.HEAT || w.stage == AnvilSong.TEND && w.taps.isEmpty() || w.stage == AnvilSong.KNAP && w.scores.isEmpty();
        if (!first) return;
        be.setWork(null);
        // the pick first, or the parts would take the hand it came from
        if (!w.piece.isEmpty()) handBack(p, be, w.piece.copy());
        for (ItemStack m : w.materials) give(p, m);
        WarAnvil.forget(p.getUUID());
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!s.isEmpty() && !p.getInventory().add(s)) Containers.dropItemStack(p.level(), p.getX(), p.getY(), p.getZ(), s);
    }

    /** Back in the hand if it's free, else the pack, else at your feet; with nobody there it stands in the anvil. */
    private static void handBack(@Nullable ServerPlayer smith, WarAnvilBlockEntity be, ItemStack out) {
        if (smith == null) {
            be.setSlot(out);
            return;
        }
        if (smith.getMainHandItem().isEmpty()) smith.setItemInHand(InteractionHand.MAIN_HAND, out);
        else give(smith, out);
    }

    // ---- what comes out ---------------------------------------------------------------------------------------------------

    /** Done flags beyond the weapons': a pick, and quiet work that's already back in your hand. */
    public static final int PICK = 256, TENDED = 512;

    static void finish(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, @Nullable ServerPlayer smith) {
        if (w.tended()) {
            tended(level, be, w, smith);
            return;
        }
        boolean twin = w.help >= AnvilSong.TWIN_STRIKES;
        AnvilSong.Quality made = quality(w);
        PickForm form = PickForm.ofStyle(w.style);
        ItemStack out = new ItemStack(ForgedPicks.get(form, w.tier));
        boolean raised = !w.piece.isEmpty();
        PickForged was = raised ? ForgedPicks.data(w.piece) : null;
        int quality = was == null ? made.ordinal() : Math.max(made.ordinal(), was.quality());
        int cap = smith != null ? HearthHooks.temperCap(smith, w.forge) : 1;
        int mark = smith != null ? (int) Progression.perk(smith, "awaken:temper_start") : 0;
        int temper = AnvilSong.temper(AnvilSong.Quality.of(quality), mark, cap);
        if (was != null) temper = Math.max(temper, was.temper());
        float colour = w.colour >= 0F ? w.colour : was != null ? was.colour() : PickStats.COLOUR_PEACOCK;
        int gifts = (was == null ? 0 : was.gifts()) | (smith == null ? 0 : ForgedPicks.earned(smith, w.tier));
        boolean armoury = HearthHooks.armoury(level, be.getBlockPos()) || was != null && was.is(PickForged.ARMOURY);
        int story = (twin ? PickForged.TWIN : 0) | (w.memory() ? PickForged.MEMORY : 0) | (armoury ? PickForged.ARMOURY : 0) | (was != null && was.is(PickForged.OLD) ? PickForged.OLD : 0);
        String name = smith != null ? smith.getGameProfile().name() : "";
        ForgedPicks.write(out, new PickForged(PickForged.SCHEMA, quality, temper, colour, w.forger.toString(), was != null && !was.maker().isEmpty() ? was.maker() : name,
                day(level), was == null ? PickForged.Tally.NONE : was.tally(), gifts, was == null ? w.tier : was.from(), story, was == null ? 0 : was.room()));
        if (raised) keep(w.piece, out);
        if (smith != null) {
            Progression.count(smith, "awaken:pick_forged");
            SmithMemory.of(smith).remember(w.style, w.tier, made);
            ForgedPicks.remember(smith, form, w.tier);
            ForgeHooks.forged(smith, out, quality, "war_anvil");
        }
        be.setWork(null);
        be.setSlot(out);
        level.playSound(null, be.getBlockPos(), AnvilContent.FLIP.get(), SoundSource.BLOCKS, 1F, 0.7F);
        if (smith != null) AwakenNet.send(smith, new AnvilPayloads.Done(be.getBlockPos(), quality, AnvilSong.burnt(Math.max(0F, w.heat)) ? AnvilSong.BURNT_TIP : AnvilSong.NO_FLAW, temper,
                PICK | (twin ? WarAnvil.TWIN : 0) | (w.memory() ? WarAnvil.FROM_MEMORY : 0) | (armoury ? WarAnvil.ARMOURY : 0)));
    }

    private static long day(ServerLevel level) {
        return level.getOverworldClockTime() / 24000L + 1;
    }

    /** How well it was made. Burnt only keeps it from Flawless; a pick is never spoiled. */
    private static AnvilSong.Quality quality(AnvilWork w) {
        if (w.memory()) return AnvilSong.Quality.FINE;
        float hang = Math.max(0F, w.hang);
        if (w.hand()) {
            float knap = AnvilWork.mean(w.scores);
            return AnvilSong.quality(AnvilSong.helped(AnvilSong.handScore(knap, Math.max(0F, w.bind)) * 0.85F + hang * 0.15F, w.help), false);
        }
        float heat = Math.max(0F, w.heat), strikes = AnvilWork.mean(w.scores), eye = w.eye >= 0F ? w.eye : strikes;
        float score = AnvilSong.withActs(AnvilSong.score(heat, strikes, eye, hang), w.tips, w.spine, w.seam, w.star);
        return AnvilSong.quality(AnvilSong.helped(score, w.help), AnvilSong.burnt(heat));
    }

    /** What a pick keeps through any remaking. Never by copying components wholesale: a removal patch would strip the new item's own tool rules. */
    private static void keep(ItemStack from, ItemStack to) {
        copy(from, to, DataComponents.ENCHANTMENTS);
        copy(from, to, DataComponents.CUSTOM_NAME);
        copy(from, to, DataComponents.LORE);
        copy(from, to, DataComponents.REPAIR_COST);
        int t = Upgrades.temperOf(from);
        if (t > 0) Upgrades.applyTemper(to, t);
    }

    private static <T> void copy(ItemStack from, ItemStack to, net.minecraft.core.component.DataComponentType<T> type) {
        T v = from.get(type);
        if (v != null) to.set(type, v);
    }

    private static void tended(ServerLevel level, WarAnvilBlockEntity be, AnvilWork w, @Nullable ServerPlayer smith) {
        ItemStack out = w.piece.copy();
        String told;
        switch (w.kind) {
            case AnvilWork.WORK_IN -> {
                PickForged d = ForgedPicks.data(out);
                int earned = smith == null ? 0 : ForgedPicks.earned(smith, w.tier);
                ForgedPicks.write(out, d.withGifts(d.gifts() | earned));
                told = "What you've done is in the steel now.";
            }
            case AnvilWork.DRESS -> {
                PickForged d = ForgedPicks.data(out);
                out.setDamageValue(Math.max(0, out.getDamageValue() - Math.max(1, out.getMaxDamage() / 4)));
                ForgedPicks.write(out, d.withTally(new PickForged.Tally(d.tally().blocks(), d.tally().ores(), d.tally().hearts(), PickStats.KEEN_BLOCKS)));
                told = "The edge is fresh.";
            }
            default -> {
                out = remade(w.piece, level, w.forger, smith);
                if (smith != null) {
                    Progression.count(smith, "awaken:pick_remade");
                    ForgedPickItem i = ForgedPicks.item(out);
                    if (i != null) ForgedPicks.remember(smith, i.form, i.tier);
                }
                told = "It's a forged pick now. Anything more it needs, the anvil can do.";
            }
        }
        be.setWork(null);
        handBack(smith, be, out);
        WarAnvil.forget(w.forger);
        level.playSound(null, be.getBlockPos(), AnvilContent.FLIP.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
        if (smith != null) {
            dev.awaken.signal.Signal.toast(smith, Component.literal(told));
            AwakenNet.send(smith, new AnvilPayloads.Done(be.getBlockPos(), 0, 0, 0, PICK | TENDED));
        }
    }

    /** The old pick or mattock, remade (§49.7): same rank, same gifts, same enchantments and the same share of wear. */
    static ItemStack remade(ItemStack old, ServerLevel level, java.util.UUID forger, @Nullable ServerPlayer smith) {
        Remade r = old(old);
        if (r == null) return old.copy();
        int verse = PickVerses.verse(old), gifts = 0, room = 0;
        if (verse >= 0) {
            for (Pick.Gift g : Pick.Gift.values()) if (g.ordinal() <= verse) gifts |= g.bit();
            room = EnchantRules.room(old);
        }
        if (smith != null) gifts |= ForgedPicks.earned(smith, r.tier());
        ItemStack out = new ItemStack(ForgedPicks.get(r.form(), r.tier()));
        ForgedPicks.write(out, new PickForged(PickForged.SCHEMA, 0, 0, PickStats.COLOUR_PEACOCK, forger.toString(), "", day(level), PickForged.Tally.NONE, gifts, r.tier(), PickForged.OLD, room));
        keep(old, out);
        if (old.isDamageableItem() && old.getMaxDamage() > 0)
            out.setDamageValue(Math.min(out.getMaxDamage() - 1, Math.round(old.getDamageValue() / (float) old.getMaxDamage() * out.getMaxDamage())));
        return out;
    }
}
