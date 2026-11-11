package dev.awaken.enchant;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writing at an Inscribing Table, on the server (SPEC §45.4, §45.10). Laying a piece on the slate opens a session: the
 * table, the hand's slot and a snapshot of the piece. Choosing takes the price; each stroke's share on the line is
 * clamped and counted; the last stroke writes the enchantment onto the same piece and a clean hand (every stroke done,
 * 80% on the line) gives a third of the levels back. Writing always succeeds: the trace decides only the refund. Esc,
 * walking 6 blocks away, logging out, the table broken or the piece changed end it, and refund everything.
 */
public final class Inscribe {
    private Inscribe() {}

    /** How far from the table a writer may stand. */
    static final double REACH = 6;
    /** The fewest ticks a stroke can take, for the server to count it (a stroke takes about a second). */
    static final int MIN_STROKE_TICKS = 6;
    /** A clean hand: the average share on the line over every stroke. */
    static final float CLEAN = 0.8F;

    /** One writer's time at one table. */
    static final class Session {
        final int id;
        final ResourceKey<Level> dim;
        final BlockPos pos;
        final int slot;
        final boolean study;
        ItemStack snapshot;
        // the choice being written (ench null: at the book)
        @Nullable Holder<Enchantment> ench;
        Inscriptions.@Nullable Entry entry;
        int from, to, levels, lapis, strokes;
        boolean memory;
        long lastStroke;
        float[] cover = new float[8];

        Session(int id, ResourceKey<Level> dim, BlockPos pos, int slot, boolean study, ItemStack snapshot) {
            this.id = id;
            this.dim = dim;
            this.pos = pos.immutable();
            this.slot = slot;
            this.study = study;
            this.snapshot = snapshot;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int next = 1;

    /** The writer's open session (tests). */
    static @Nullable Session of(ServerPlayer p) {
        return SESSIONS.get(p.getUUID());
    }

    // ---- opening ------------------------------------------------------------------------------------------------------

    /** The writer used the table holding a piece: lay it on the slate and open the book. */
    static void open(ServerPlayer p, InscribingTableBlockEntity be) {
        ServerLevel level = (ServerLevel) be.getLevel();
        if (level == null) return;
        UUID writer = be.writer();
        if (writer != null && !writer.equals(p.getUUID())) {
            p.sendSystemMessage(Component.literal("Someone is writing here."), true);
            return;
        }
        Session old = SESSIONS.get(p.getUUID());
        if (old != null) close(p, old, "");
        ItemStack piece = p.getMainHandItem();
        Known k = Known.of(p);
        if (!canWorkOn(level, k, piece)) {
            p.sendSystemMessage(Component.literal(k.count() == 0
                    ? "You don't know any enchantments yet. Gate Lords carry their pages, and the market sells a few."
                    : "Nothing you know can be written on this."), true);
            return;
        }
        boolean study = HearthHooks.studyAt(level, be.getBlockPos());
        if (study && (!k.study || !k.table)) {
            k.study = k.table = true;
            Known.save(p, k);
        }
        Session s = new Session(next++, level.dimension(), be.getBlockPos(), p.getInventory().getSelectedSlot(), study, piece.copy());
        SESSIONS.put(p.getUUID(), s);
        be.set(p.getUUID(), piece, InscribingTableBlockEntity.BOOK, 0, 0);
        level.playSound(null, be.getBlockPos(), EnchantContent.BOOK_OPEN.get(), SoundSource.BLOCKS, 0.9F, 1F);
        Pages.door(p, "opened", null);
        // the facing rides along: a table that has just turned from a vanilla one may not have reached the client yet
        AwakenNet.send(p, new EnchantPayloads.Open(be.getBlockPos(), s.id, s.slot, study,
                be.getBlockState().getValue(InscribingTableBlock.FACING).get2DDataValue()));
    }

    /** Whether there's anything to do with this piece here: something you know fits it, or something on it to scrape. */
    static boolean canWorkOn(ServerLevel level, Known k, ItemStack piece) {
        if (piece.isEmpty()) return false;
        if (!piece.getEnchantments().isEmpty()) return true;
        Registry<Enchantment> reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (Inscriptions.Entry e : Inscriptions.ALL) {
            if (!k.knows(e.id())) continue;
            Optional<Holder.Reference<Enchantment>> h = reg.get(e.key());
            if (h.isPresent() && EnchantRules.fits(piece, h.get())) return true;
        }
        return false;
    }

    /** A table set down: Tasks stop asking for one. */
    static void placed(ServerPlayer p) {
        Known k = Known.of(p);
        if (k.table) return;
        k.table = true;
        Known.save(p, k);
        Pages.door(p, "table set", null);
    }

    // ---- acts ---------------------------------------------------------------------------------------------------------

    static void onAct(ServerPlayer p, EnchantPayloads.Act a) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.id != a.session()) return;
        ServerLevel level = p.level().getServer().getLevel(s.dim);
        if (level == null || !(level.getBlockEntity(s.pos) instanceof InscribingTableBlockEntity be)) {
            close(p, s, "The table is gone. Nothing was written.");
            return;
        }
        String broken = broken(p, s);
        if (broken != null) {
            close(p, s, broken);
            return;
        }
        switch (a.act()) {
            case EnchantPayloads.LOOK -> {
                if (s.ench != null) return;
                Inscriptions.Entry e = entry(a.no());
                be.set(p.getUUID(), s.snapshot, InscribingTableBlockEntity.BOOK, e == null ? 0 : e.no(), 0);
            }
            case EnchantPayloads.CHOOSE, EnchantPayloads.MEMORY -> choose(p, level, be, s, a.no(), a.level(), a.act() == EnchantPayloads.MEMORY);
            case EnchantPayloads.STROKE -> stroke(level, s, a.level(), a.a());
            case EnchantPayloads.FINISH -> finish(p, level, be, s);
            case EnchantPayloads.CANCEL -> close(p, s, "");
            case EnchantPayloads.SCRAPE -> scrape(p, level, be, s, a.no());
            default -> {}
        }
    }

    /** The enchantment numbered {@code no} (1-30), or null. */
    static Inscriptions.@Nullable Entry entry(int no) {
        return no >= 1 && no <= Inscriptions.ALL.size() ? Inscriptions.ALL.get(no - 1) : null;
    }

    static @Nullable Holder<Enchantment> holder(ServerLevel level, Inscriptions.Entry e) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(e.key()).orElse(null);
    }

    /** Choosing: check it all again, take the price, and start the trace (or memory's single stroke). */
    static void choose(ServerPlayer p, ServerLevel level, InscribingTableBlockEntity be, Session s, int no, int to, boolean memory) {
        if (s.ench != null) return;
        Inscriptions.Entry e = entry(no);
        Holder<Enchantment> h = e == null ? null : holder(level, e);
        if (e == null || h == null) return;
        Known k = Known.of(p);
        if (!k.knows(e.id())) return;
        if (memory && !k.inMemory(e.id())) return;
        ItemStack hand = p.getMainHandItem();
        int now = EnchantRules.level(hand, h);
        String why = to <= now ? "It's already that deep." : EnchantRules.why(hand, h, to, s.study);
        if (why != null) {
            AwakenNet.send(p, new EnchantPayloads.State(s.id, EnchantPayloads.BOOK, no, to, why));
            return;
        }
        int[] price = EnchantRules.price(e, now, to);
        if (!p.hasInfiniteMaterials()) {
            int lapis = countLapis(p);
            if (p.experienceLevel < price[0] || lapis < price[1]) {
                AwakenNet.send(p, new EnchantPayloads.State(s.id, EnchantPayloads.BOOK, no, to,
                        "You need " + EnchantRules.priceLine(price) + "."));
                return;
            }
            p.giveExperienceLevels(-price[0]);
            takeLapis(p, price[1]);
            s.levels = price[0];
            s.lapis = price[1];
        } else {
            s.levels = s.lapis = 0;
        }
        s.ench = h;
        s.entry = e;
        s.from = now;
        s.to = to;
        s.memory = memory;
        s.strokes = 0;
        s.lastStroke = level.getGameTime();
        int need = Marks.strokes(e.no());
        if (s.cover.length < need) s.cover = new float[need];
        be.set(p.getUUID(), s.snapshot, InscribingTableBlockEntity.TRACE, e.no(), e.colour());
        AwakenNet.send(p, new EnchantPayloads.State(s.id, memory ? EnchantPayloads.FROM_MEMORY : EnchantPayloads.TRACE, no, to, ""));
    }

    /** One stroke lifted: counted in order, no faster than a stroke can be drawn, its share on the line clamped. */
    static void stroke(ServerLevel level, Session s, int index, float share) {
        if (s.ench == null || s.memory || s.entry == null || index != s.strokes || s.strokes >= Marks.strokes(s.entry.no())) return;
        long now = level.getGameTime();
        if (now - s.lastStroke < MIN_STROKE_TICKS) return;
        s.cover[s.strokes++] = Mth.clamp(Float.isNaN(share) ? 0F : share, 0F, 1F);
        s.lastStroke = now;
    }

    /** The pitches over the new enchantment's note for the others on the piece at its wake: a major third, a fifth, an octave. */
    private static final float[] CHORD = {1.26F, 1.498F, 2F};

    /** The last stroke lifted: write it onto the same piece, refund a clean hand, remember its mark. */
    static void finish(ServerPlayer p, ServerLevel level, InscribingTableBlockEntity be, Session s) {
        Holder<Enchantment> h = s.ench;
        Inscriptions.Entry e = s.entry;
        if (h == null || e == null) return;
        ItemStack hand = p.getMainHandItem();
        int to = s.to;
        EnchantmentHelper.updateEnchantments(hand, m -> m.set(h, to));
        int need = Marks.strokes(e.no());
        float sum = 0;
        for (int i = 0; i < s.strokes; i++) sum += s.cover[i];
        boolean clean = !s.memory && s.strokes >= need && sum / need >= CLEAN;
        int refund = clean ? EnchantRules.refund(s.levels) : 0;
        if (refund > 0) p.giveExperienceLevels(refund);
        Known k = Known.of(p);
        boolean remembered = clean && k.remember(e.id());
        k.written++;
        Pages.door(p, clean ? "written clean" : s.memory ? "written from memory" : "written", e);
        Known.save(p, k);
        s.snapshot = hand.copy();
        s.ench = null;
        s.entry = null;
        be.set(p.getUUID(), s.snapshot, InscribingTableBlockEntity.WAKE, e.no(), e.colour());
        level.playSound(null, s.pos, EnchantContent.WAKE.get(), SoundSource.BLOCKS, 1F, 1F);
        // the piece's chord: the new one's note, and each other enchantment on it a third, a fifth, an octave above
        level.playSound(null, s.pos, EnchantContent.note(e.family()), SoundSource.BLOCKS, 0.8F, 1F);
        int voice = 0;
        for (var on : hand.getEnchantments().entrySet()) {
            Inscriptions.Entry o = Inscriptions.of(on.getKey());
            if (o == null || o == e || voice >= CHORD.length) continue;
            level.playSound(null, s.pos, EnchantContent.note(o.family()), SoundSource.BLOCKS, 0.55F, CHORD[voice++]);
        }
        AwakenNet.send(p, new EnchantPayloads.Done(s.id, e.no(), to, refund, clean, remembered, card(e, h, to, hand, refund, clean, s.memory)));
    }

    /** "Bane of the Dead II is on your Beastbound Longsword. A clean hand: you got 2 levels back." */
    static String card(Inscriptions.Entry e, Holder<Enchantment> h, int level, ItemStack piece, int refund, boolean clean, boolean memory) {
        String top = e.name() + (Inscriptions.top(h) > 1 ? " " + Inscriptions.numeral(level) : "") + " is on your " + piece.getHoverName().getString() + ".";
        if (memory) return top + " Written from memory.";
        if (clean) return top + " A clean hand: you got " + refund + (refund == 1 ? " level" : " levels") + " back.";
        return top;
    }

    /** The burin across it: an enchantment comes off, free, giving nothing back. {@code id} is its registry number. */
    static void scrape(ServerPlayer p, ServerLevel level, InscribingTableBlockEntity be, Session s, int id) {
        if (s.ench != null) return;
        Registry<Enchantment> reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> h = reg.get(id);
        ItemStack hand = p.getMainHandItem();
        if (h.isEmpty() || hand.getEnchantments().getLevel(h.get()) <= 0) return;
        Holder<Enchantment> gone = h.get();
        EnchantmentHelper.updateEnchantments(hand, m -> m.removeIf(x -> x.equals(gone)));
        s.snapshot = hand.copy();
        be.set(p.getUUID(), s.snapshot, InscribingTableBlockEntity.BOOK, 0, 0);
        level.playSound(null, s.pos, EnchantContent.SCRAPE.get(), SoundSource.BLOCKS, 0.9F, 1F);
        AwakenNet.send(p, new EnchantPayloads.State(s.id, EnchantPayloads.BOOK, 0, 0,
                gone.value().description().getString() + " is scraped off."));
    }

    // ---- ending -------------------------------------------------------------------------------------------------------

    /** Why the session can't go on (the piece changed, you walked away), or null. */
    static @Nullable String broken(ServerPlayer p, Session s) {
        if (p.level().dimension() != s.dim || p.distanceToSqr(Vec3.atCenterOf(s.pos)) > REACH * REACH) return "You stepped away. Nothing was written.";
        if (p.getInventory().getSelectedSlot() != s.slot || !ItemStack.isSameItemSameComponents(p.getMainHandItem(), s.snapshot))
            return "The piece left your hand. Nothing was written.";
        return null;
    }

    /** Ends a session: refunds a choice not yet written, clears the table, tells the client (a line for why, or none). */
    static void close(ServerPlayer p, Session s, String line) {
        SESSIONS.remove(p.getUUID());
        refund(p, s);
        ServerLevel level = p.level().getServer().getLevel(s.dim);
        if (level != null && level.isLoaded(s.pos) && level.getBlockEntity(s.pos) instanceof InscribingTableBlockEntity be
                && p.getUUID().equals(be.writer())) be.clear();
        AwakenNet.send(p, new EnchantPayloads.Closed(s.id, line));
        if (!line.isEmpty()) p.sendSystemMessage(Component.literal(line), true);
    }

    private static void refund(ServerPlayer p, Session s) {
        if (s.ench == null) return;
        if (s.levels > 0) p.giveExperienceLevels(s.levels);
        if (s.lapis > 0) {
            ItemStack lapis = new ItemStack(Items.LAPIS_LAZULI, s.lapis);
            if (!p.getInventory().add(lapis)) p.drop(lapis, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        s.ench = null;
        s.entry = null;
        s.levels = s.lapis = 0;
    }

    /** The table's half-second look at its writer. */
    static void tableTick(ServerLevel level, InscribingTableBlockEntity be) {
        UUID w = be.writer();
        if (w == null) return;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(w);
        Session s = p == null ? null : SESSIONS.get(w);
        if (p == null || s == null || !s.pos.equals(be.getBlockPos()) || s.dim != level.dimension()) {
            be.clear();
            return;
        }
        String broken = broken(p, s);
        if (broken != null) close(p, s, broken);
        else if (be.phase() == InscribingTableBlockEntity.WAKE && level.getGameTime() % 40 == 0)
            be.set(w, s.snapshot, InscribingTableBlockEntity.BOOK, be.no(), 0);
    }

    static void tableGone(ServerLevel level, BlockPos pos) {
        for (var it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Session s = en.getValue();
            if (!s.pos.equals(pos) || s.dim != level.dimension()) continue;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(en.getKey());
            it.remove();
            if (p != null) {
                SESSIONS.put(en.getKey(), s); // close() removes it again
                close(p, s, "The table is gone. Nothing was written.");
                return;
            }
        }
    }

    static void logout(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        refund(p, s);
        ServerLevel level = p.level().getServer().getLevel(s.dim);
        if (level != null && level.isLoaded(s.pos) && level.getBlockEntity(s.pos) instanceof InscribingTableBlockEntity be) be.clear();
    }

    // ---- lapis ----------------------------------------------------------------------------------------------------------

    static int countLapis(ServerPlayer p) {
        return p.getInventory().clearOrCountMatchingItems(st -> st.is(Items.LAPIS_LAZULI), true, Integer.MAX_VALUE, p.inventoryMenu.getCraftSlots());
    }

    static void takeLapis(ServerPlayer p, int n) {
        if (n > 0) p.getInventory().clearOrCountMatchingItems(st -> st.is(Items.LAPIS_LAZULI), false, n, p.inventoryMenu.getCraftSlots());
    }
}
