package dev.awaken.armour;

import dev.awaken.Awaken;
import dev.awaken.armour.net.ArmourPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.gear.SetBonus;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Forge Song on the server (SPEC §44.4): what a Fitting Stand offers, starting a piece (the materials leave your pack
 * and wait on the work), judging each step by the server's clock, and the finished piece fitted onto the stand with its
 * quality, starting Temper and {@code awaken:forged}. The work only moves while its smith is at it; step away and it
 * waits, and you are counted in again when you come back. A helper at the anvil strikes the off-beats (twin-struck).
 */
public final class ArmourForge {
    private ArmourForge() {}

    /** How near the stand the anvil, the Forge and the works table must be. */
    public static final int REACH = 6;
    /** Smiths at a stand now (the work only moves while they are). Not saved: a smith who logs out has stepped away. */
    private static final Map<UUID, BlockPos> AT = new HashMap<>();

    // ---- the stand's list --------------------------------------------------------------------------------------------

    static void open(ServerPlayer p, FittingStandBlockEntity be) {
        AwakenNet.send(p, offers(p, be));
    }

    static ArmourPayloads.Offers offers(ServerPlayer p, FittingStandBlockEntity be) {
        Rank rank = Progression.rank(p);
        ArmouryMemory mem = ArmouryMemory.of(p);
        ServerLevel level = (ServerLevel) be.getLevel();
        boolean metal = anvil(level, be.getBlockPos()) != null && forge(level, be.getBlockPos()) != null;
        boolean hand = table(level, be.getBlockPos()) != null;
        List<ArmourPayloads.Offer> out = new ArrayList<>();
        for (ArmourSet set : ArmourSet.values()) {
            if (set.rank.ordinal() > rank.ordinal() || !set.made()) continue;
            boolean known = known(mem, set);
            for (ArmorType piece : ForgeSong.PIECES) {
                List<ForgeSong.Need> cost = ForgeSong.cost(set, piece);
                if (cost == null) continue;
                List<ArmourPayloads.Need> needs = new ArrayList<>();
                boolean afford = true;
                for (ForgeSong.Need n : cost) {
                    int have = count(p.getInventory(), matcher(n));
                    afford &= have >= n.count() || p.isCreative();
                    needs.add(new ArmourPayloads.Need(label(n, set), icon(n), have, n.count()));
                }
                boolean placed = ForgeSong.byHand(set) ? hand : metal;
                int flags = (afford && placed && known ? ArmourPayloads.CAN : 0) | (mem.fromMemory(set, piece) ? ArmourPayloads.FROM_MEMORY : 0)
                        | (mem.forgedWhole(set) ? ArmourPayloads.KNOWN_WHOLE : 0) | (known ? 0 : ArmourPayloads.NO_PATTERN);
                out.add(new ArmourPayloads.Offer(set.ordinal() * 4 + ForgeSong.slot(piece), needs, flags));
            }
        }
        String place = metal ? "" : anvil(level, be.getBlockPos()) == null
                ? "Metal is shaped on an anvil. Set one within a few steps of this stand, near your Forge."
                : "Metal is heated in a Forge. This stand needs your Hearth's Forge within a few steps.";
        String byHand = hand ? "" : "Hide is cut and riveted at a works table. Set a crafting table within a few steps of this stand.";
        return new ArmourPayloads.Offers(be.getBlockPos(), rank.ordinal(), out, place, byHand);
    }

    /** Hunter sets come with rank and the E sets are known from the start; every other set's pattern is found in the world. */
    static boolean known(ArmouryMemory mem, ArmourSet set) {
        return set.hunter != null || set.rank == Rank.E || mem.patterns.contains(set.id);
    }

    // ---- the place -----------------------------------------------------------------------------------------------------

    public static @Nullable BlockPos anvil(Level level, BlockPos stand) {
        return near(level, stand, s -> s.is(BlockTags.ANVIL));
    }

    public static @Nullable BlockPos forge(Level level, BlockPos stand) {
        return near(level, stand, s -> BuiltInRegistries.BLOCK.getKey(s.getBlock()).equals(Awaken.id("forge")));
    }

    public static @Nullable BlockPos table(Level level, BlockPos stand) {
        return near(level, stand, s -> s.is(Blocks.CRAFTING_TABLE));
    }

    /** The nearest block within reach of the stand that passes (a small cube, read once a use or a start; the client reads it to frame its shot). */
    private static @Nullable BlockPos near(Level level, BlockPos stand, Predicate<net.minecraft.world.level.block.state.BlockState> test) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.betweenClosed(stand.offset(-REACH, -2, -REACH), stand.offset(REACH, 2, REACH))) {
            if (!test.test(level.getBlockState(q))) continue;
            double d = q.distSqr(stand);
            if (d < bestD) {
                bestD = d;
                best = q.immutable();
            }
        }
        return best;
    }

    // ---- materials ---------------------------------------------------------------------------------------------------

    static Predicate<ItemStack> matcher(ForgeSong.Need n) {
        if (n.tag() != null) return s -> s.is(n.tag());
        Item item = BuiltInRegistries.ITEM.getValue(n.item());
        return s -> s.is(item);
    }

    static int count(Inventory inv, Predicate<ItemStack> m) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && m.test(s)) n += s.getCount();
        }
        return n;
    }

    /** Takes n matching items from the pack (hotbar first), returning what was taken. */
    static List<ItemStack> take(Inventory inv, Predicate<ItemStack> m, int n) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !m.test(s)) continue;
            int k = Math.min(n, s.getCount());
            out.add(s.split(k));
            n -= k;
        }
        return out;
    }

    private static String label(ForgeSong.Need n, ArmourSet set) {
        if (n.tag() != null) {
            String path = n.tag().location().getPath();
            String r = set.rank.label;
            if (path.startsWith("hunter_hide")) return "Hide (" + r + ")";
            if (path.startsWith("hunter_core")) return "Trophy (" + r + ")";
            if (path.startsWith("hunter_bind")) return "Binding (" + r + ")";
            if (path.equals("logs")) return "Logs";
            if (path.equals("wool")) return "Wool";
            return switch (set.rank) {
                case D -> "Iron";
                case C -> "Diamond";
                case B -> "Deepglass";
                case A -> "Netherite";
                default -> "Starmetal";
            };
        }
        return BuiltInRegistries.ITEM.getValue(n.item()).getName(ItemStack.EMPTY).getString();
    }

    /** An item that shows the need: the tag's first member, or the item. */
    private static String icon(ForgeSong.Need n) {
        if (n.tag() != null) {
            for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(n.tag())) return BuiltInRegistries.ITEM.getKey(h.value()).toString();
            return "minecraft:barrier";
        }
        return n.item().toString();
    }

    // ---- acts ----------------------------------------------------------------------------------------------------------

    public static void onAct(ServerPlayer p, ArmourPayloads.Act a) {
        if (!(p.level() instanceof ServerLevel level) || !level.isLoaded(a.pos())) return;
        if (!(level.getBlockEntity(a.pos()) instanceof FittingStandBlockEntity be)) return;
        if (p.distanceToSqr(Vec3.atCenterOf(a.pos())) > 10 * 10) return;
        ForgeWork w = be.work();
        long now = level.getGameTime();
        switch (a.act()) {
            case ArmourPayloads.START, ArmourPayloads.MEMORY -> start(p, level, be, a.a(), a.act() == ArmourPayloads.MEMORY);
            case ArmourPayloads.TAKE -> takeSuit(p, be);
            case ArmourPayloads.OPEN -> open(p, be);
            case ArmourPayloads.LEAVE -> AT.remove(p.getUUID());
            case ArmourPayloads.RESUME -> {
                if (w == null) return;
                AT.put(p.getUUID(), a.pos());
                if (w.forger.equals(p.getUUID())) {
                    w.begin(w.stage, now); // the bar cooled while you were away; the song counts you in again
                    be.changed();
                }
            }
            case ArmourPayloads.PULL -> {
                if (w == null || w.stage != ForgeSong.HEAT || !mine(p, w)) return;
                pull(level, be, w, ForgeSong.heat(clamp(a.t(), now - w.stageStart)), p);
            }
            case ArmourPayloads.STRIKE -> {
                if (w == null || (w.stage != ForgeSong.SHAPE && w.stage != ForgeSong.RIVET) || !mine(p, w) || a.a() < w.scores.size()) return;
                float t = clamp(a.t(), now - w.stageStart);
                // beats the smith's clock saw pass unstruck, not yet missed here (the server waits out the lag first)
                int stage = w.stage;
                while (w.stage == stage && be.work() == w && w.scores.size() < a.a() && t > w.nextDue() + ForgeSong.WINDOW) strike(level, be, w, 0F, null);
                if (be.work() != w || w.stage != stage || a.a() != w.scores.size()) return;
                float off = t - w.nextDue();
                if (off < -ForgeSong.WINDOW) return; // far ahead of its beat: not taken for it
                strike(level, be, w, ForgeSong.strike(off, Mth.clamp(a.aim(), 0F, 9F)), p);
            }
            case ArmourPayloads.QUENCH -> {
                if (w == null || w.stage != ForgeSong.QUENCH || !mine(p, w)) return;
                float off = clamp(a.t(), now - w.stageStart) - ForgeSong.quenchCue(w.stageStart);
                w.quench = ForgeSong.quench(off);
                level.playSound(p, w.work, ArmourContent.QUENCH.get(), SoundSource.BLOCKS, 1F, 1F);
                finish(level, be, w);
            }
            case ArmourPayloads.CUT -> {
                if (w == null || w.stage != ForgeSong.CUT || !mine(p, w)) return;
                w.cut = Mth.clamp(a.aim(), 0F, 1F);
                w.begin(ForgeSong.RIVET, now);
                be.changed();
            }
            case ArmourPayloads.TWIN -> {
                if (w == null || w.stage != ForgeSong.SHAPE || w.forger.equals(p.getUUID())) return;
                float t = clamp(a.t(), now - w.stageStart);
                ForgeSong.Rhythm r = ForgeSong.song(w.set).rhythm();
                if (!r.offBeat(t) || r.offBy(t) > 1.5F) return;
                w.twin++;
                level.playSound(p, w.work, ArmourContent.strikeSound(w.set), SoundSource.BLOCKS, 0.6F, 1.25F);
                be.changed();
            }
            default -> {}
        }
    }

    private static boolean mine(ServerPlayer p, ForgeWork w) {
        return w.forger.equals(p.getUUID());
    }

    /** A client's ticks from the stage's start, held to what the server's clock allows (a little behind, never ahead). */
    static float clamp(float client, long server) {
        return Mth.clamp(client, server - ForgeSong.LAG, server + 2F);
    }

    static void start(ServerPlayer p, ServerLevel level, FittingStandBlockEntity be, int code, boolean memory) {
        ArmourSet[] sets = ArmourSet.values();
        if (code < 0 || code / 4 >= sets.length || be.work() != null) return;
        ArmourSet set = sets[code / 4];
        ArmorType piece = ForgeSong.PIECES[code % 4];
        ArmouryMemory mem = ArmouryMemory.of(p);
        List<ForgeSong.Need> cost = ForgeSong.cost(set, piece);
        if (cost == null || set.rank.ordinal() > Progression.rank(p).ordinal() || !known(mem, set)) return;
        if (memory && !mem.fromMemory(set, piece)) return;
        BlockPos stand = be.getBlockPos();
        BlockPos work = ForgeSong.byHand(set) ? table(level, stand) : anvil(level, stand);
        BlockPos fire = ForgeSong.byHand(set) ? null : forge(level, stand);
        if (work == null || (!ForgeSong.byHand(set) && fire == null)) {
            open(p, be);
            return;
        }
        for (ForgeSong.Need n : cost) if (count(p.getInventory(), matcher(n)) < n.count() && !p.isCreative()) {
            open(p, be);
            return;
        }
        List<ItemStack> taken = new ArrayList<>();
        if (!p.isCreative()) for (ForgeSong.Need n : cost) taken.addAll(take(p.getInventory(), matcher(n), n.count()));
        long now = level.getGameTime();
        ForgeWork w = new ForgeWork(set, piece, ForgeSong.firstStage(set), now, 0, taken, -1F, -1F, -1F, List.of(), 0, p.getUUID(), memory,
                work, Optional.ofNullable(fire));
        AT.put(p.getUUID(), stand);
        be.setWork(w);
        level.playSound(null, fire != null ? fire : work, fire != null ? ArmourContent.BELLOWS.get() : ArmourContent.CUT.get(), SoundSource.BLOCKS, 0.9F, 1F);
        Progression.count(p, "awaken:armour_started");
    }

    /** The smith hears their own pull, strikes and quench as they make them (their client plays them); everyone else from here. */
    static void pull(ServerLevel level, FittingStandBlockEntity be, ForgeWork w, float heat, @Nullable ServerPlayer smith) {
        w.heat = heat;
        w.begin(ForgeSong.SHAPE, level.getGameTime());
        level.playSound(smith, w.forge.orElse(w.work), ArmourContent.PULL.get(), SoundSource.BLOCKS, 1F, 1F);
        be.changed();
    }

    static void strike(ServerLevel level, FittingStandBlockEntity be, ForgeWork w, float score, @Nullable ServerPlayer smith) {
        w.scores.add(score);
        if (score > 0F) {
            SoundEvent ring = w.stage == ForgeSong.RIVET ? ArmourContent.STRIKE_COPPER.get() : ArmourContent.strikeSound(w.set);
            level.playSound(smith, w.work, ring, SoundSource.BLOCKS, 1F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        } else level.playSound(null, w.work, ArmourContent.MISS.get(), SoundSource.BLOCKS, 0.6F, 1F);
        if (ForgeSong.clean(score)) {
            int[] melody = ForgeSong.song(w.set).melody();
            int note = melody[(w.scores.size() - 1) % melody.length];
            level.playSound(smith, w.work, ArmourContent.NOTE.get(), SoundSource.BLOCKS, 0.7F, ForgeSong.notePitch(note));
        }
        if (w.scores.size() >= w.strikesNeeded()) {
            if (w.memory || w.stage == ForgeSong.RIVET) finish(level, be, w);
            else {
                w.begin(ForgeSong.QUENCH, level.getGameTime());
                be.changed();
            }
            return;
        }
        be.changed();
    }

    /** Each tick of a stand with work on it: only while its smith is at it, anything left too long happens by itself. */
    static void tick(ServerLevel level, FittingStandBlockEntity be, ForgeWork w) {
        BlockPos at = AT.get(w.forger);
        ServerPlayer smith = level.getServer().getPlayerList().getPlayer(w.forger);
        if (at == null || !at.equals(be.getBlockPos()) || smith == null || smith.level() != level
                || smith.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 12 * 12) return;
        float t = level.getGameTime() - w.stageStart;
        switch (w.stage) {
            case ForgeSong.HEAT -> {
                if (ForgeSong.heat(t) > ForgeSong.BURNT) pull(level, be, w, ForgeSong.BURNT, null);
            }
            case ForgeSong.SHAPE, ForgeSong.RIVET -> {
                if (t > w.nextDue() + ForgeSong.WINDOW + ForgeSong.LAG) strike(level, be, w, 0F, null);
            }
            case ForgeSong.QUENCH -> {
                if (t > ForgeSong.quenchCue(w.stageStart) + ForgeSong.QUENCH_LATE + ForgeSong.LAG) {
                    w.quench = ForgeSong.quench(ForgeSong.QUENCH_LATE);
                    level.playSound(null, w.work, ArmourContent.QUENCH.get(), SoundSource.BLOCKS, 1F, 0.9F);
                    finish(level, be, w);
                }
            }
            default -> {}
        }
    }

    // ---- the finished piece --------------------------------------------------------------------------------------------

    static void finish(ServerLevel level, FittingStandBlockEntity be, ForgeWork w) {
        ServerPlayer smith = level.getServer().getPlayerList().getPlayer(w.forger);
        ForgeSong.Quality q;
        boolean flaw = false;
        if (w.memory) q = ForgeSong.FROM_MEMORY;
        else {
            float score = ForgeSong.byHand(w.set) ? ForgeSong.handScore(Math.max(0F, w.cut), w.strikeMean())
                    : ForgeSong.forgeScore(Math.max(0F, w.heat), w.strikeMean(), Math.max(0F, w.quench));
            score += Math.min(ForgeSong.TWIN_MAX, w.twin * ForgeSong.TWIN_BONUS);
            flaw = !ForgeSong.byHand(w.set) && ForgeSong.flawed(w.heat);
            q = ForgeSong.quality(score, flaw);
        }
        boolean twin = w.twin >= ForgeSong.TWIN_STRIKES;
        ItemStack piece = make(w.set, w.piece);
        if (piece.isEmpty()) {
            be.setWork(null);
            return;
        }
        String maker = smith != null ? smith.getGameProfile().name() : "";
        piece.set(ArmourContent.FORGED.get(), new Forged(Forged.SCHEMA, w.set.id, q.ordinal(), 0, flaw, twin, maker));
        if (q.temper > 0) Upgrades.applyTemper(piece, q.temper);

        int slot = ForgeSong.slot(w.piece);
        ItemStack old = be.suit(slot);
        if (!old.isEmpty()) give(smith, be, old);
        be.setSuit(slot, piece);
        be.setWork(null);
        level.playSound(null, be.getBlockPos(), ArmourContent.FIT.get(), SoundSource.BLOCKS, 1F, 1F);

        boolean woke = be.wholeSet() == w.set, first = false;
        if (smith != null) {
            ArmouryMemory mem = ArmouryMemory.of(smith);
            if (q.ordinal() >= ForgeSong.Quality.MASTERWORK.ordinal()) mem.masterwork.add(ArmouryMemory.key(w.set, w.piece));
            if (woke) {
                first = mem.forged.add(w.set.id);
                if (first) name(smith, w.set);
            }
            AT.remove(smith.getUUID());
            Progression.count(smith, "awaken:armour_forged");
            AwakenNet.send(smith, new ArmourPayloads.Done(be.getBlockPos(), w.set.ordinal() * 4 + slot, q.ordinal(), woke, first));
        }
        if (woke) {
            be.wokeAt = level.getGameTime();
            level.playSound(null, be.getBlockPos(), ArmourContent.WAKE.get(), SoundSource.BLOCKS, 1F, 1F);
        }
    }

    /** The set's item for a piece (the Hunter sets' own ids; later sets bring their own). */
    static ItemStack make(ArmourSet set, ArmorType piece) {
        String name = switch (piece) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            default -> "boots";
        };
        Item item = BuiltInRegistries.ITEM.getValue(Awaken.id(set.id + "_" + name));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** The first time a set stands whole: the System names it and what wearing it does, plainly. */
    static void name(ServerPlayer p, ArmourSet set) {
        var w = SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(set.setName))
                .body(Component.literal("You forged " + set.setName + " whole. " + set.archetype.label + " armour, " + set.rank.label + "-rank."));
        for (ArmourBonus.Line l : ArmourBonus.lines(set)) w.row(Component.literal(l.pieces() + " pieces"), Component.literal(l.text()));
        Signal.send(p, w.ttl(20 * 12).build());
    }

    static void takeSuit(ServerPlayer p, FittingStandBlockEntity be) {
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            ItemStack s = be.suit(i);
            if (s.isEmpty()) continue;
            be.setSuit(i, ItemStack.EMPTY);
            give(p, be, s);
            any = true;
        }
        if (any) be.getLevel().playSound(null, be.getBlockPos(), ArmourContent.FIT.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    private static void give(@Nullable ServerPlayer p, FittingStandBlockEntity be, ItemStack s) {
        if (p != null && p.getInventory().add(s)) return;
        BlockPos at = be.getBlockPos();
        net.minecraft.world.Containers.dropItemStack(be.getLevel(), at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, s);
    }

    static void forget(UUID player) {
        AT.remove(player);
    }

    /** Whether a smith is at this stand now (tests and the client's helper check). */
    public static boolean at(UUID player, BlockPos stand) {
        return stand.equals(AT.get(player));
    }
}
