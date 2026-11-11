package dev.awaken.enchant;

import dev.awaken.Awaken;
import dev.awaken.core.api.EnchantApi;
import dev.awaken.core.api.MobRole;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The doors to knowing an enchantment (SPEC §45.2): pages (used, they teach for good), old enchanted books (they teach
 * what can still be written and turn to plain books), a gate's Lord (its kind's page, the first time and a spare one in
 * five after) and the market (a few pages a day). One System line, the first time: every kind of gate keeps its own.
 */
public final class Pages {
    private Pages() {}

    /** Where a page says it came from, kept on it ({@code custom_data} "awaken_where"). */
    static final String WHERE = "awaken_where";
    /** How far from a fallen Lord a fighter still gets its page. */
    static final double LORD_REACH = 48;
    /** A Lord of a kind you've cleared gives its page again one time in this many. */
    static final int SPARE_ONE_IN = 5;
    /** Pages the market draws a day (each buyer is shown the first three they don't know). */
    static final int MARKET_DRAWN = 6;

    // ---- pages ---------------------------------------------------------------------------------------------------------

    /** A page of one enchantment (its top level, which the page doesn't use: what you learn is the enchantment). */
    public static ItemStack page(Holder<Enchantment> h, String where) {
        ItemStack s = new ItemStack(EnchantContent.PAGE.get());
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        m.set(h, 1);
        s.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        // the page's own lines say what it is; vanilla's "Heat Ward I" would read as a level the page doesn't carry
        s.set(DataComponents.TOOLTIP_DISPLAY, net.minecraft.world.item.component.TooltipDisplay.DEFAULT.withHidden(DataComponents.STORED_ENCHANTMENTS, true));
        if (!where.isEmpty()) {
            CompoundTag tag = new CompoundTag();
            tag.putString(WHERE, where);
            s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return s;
    }

    static @Nullable ItemStack page(Level level, Identifier id, String where) {
        Optional<Holder.Reference<Enchantment>> h = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(
                net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id));
        return h.map(ref -> page(ref, where)).orElse(null);
    }

    public static String where(ItemStack page) {
        CustomData d = page.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getStringOr(WHERE, "");
    }

    /** What a page or book would teach: its enchantments that can still be written. */
    public static List<Inscriptions.Entry> teaches(ItemStack s) {
        List<Inscriptions.Entry> out = new ArrayList<>(1);
        for (Holder<Enchantment> h : s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet()) {
            Inscriptions.Entry e = Inscriptions.of(h);
            if (e != null) out.add(e);
        }
        return out;
    }

    /** Whether this page or book would teach the player something new. */
    static boolean wanted(ServerPlayer p, ItemStack s) {
        Known k = Known.of(p);
        for (Inscriptions.Entry e : teaches(s)) if (!k.knows(e.id())) return true;
        return false;
    }

    /** Using a page: learn what's on it. The page is spent if it taught anything. */
    static boolean read(ServerPlayer p, ItemStack page) {
        List<Inscriptions.Entry> on = teaches(page);
        if (on.isEmpty()) {
            if (page.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) {
                p.sendSystemMessage(Component.literal("The writing has faded."), true);
                return false;
            }
            p.sendSystemMessage(Component.literal("This one can't be learned any more."), true);
            return false;
        }
        String where = where(page);
        List<Inscriptions.Entry> learned = learn(p, on, where.isEmpty() ? "From a page" : where);
        if (learned.isEmpty()) {
            p.sendSystemMessage(Component.literal("You already know this. You can give it away."), true);
            return false;
        }
        return true;
    }

    /** Learns these enchantments; says so, rings, and the first time tells the System's line. Returns what was new. */
    static List<Inscriptions.Entry> learn(ServerPlayer p, List<Inscriptions.Entry> entries, String where) {
        Known k = Known.of(p);
        long day = p.level().getOverworldClockTime() / 24000L;
        List<Inscriptions.Entry> learned = new ArrayList<>(entries.size());
        for (Inscriptions.Entry e : entries) if (k.learn(e.id(), where, day)) learned.add(e);
        if (learned.isEmpty()) return learned;
        boolean first = k.tell();
        Known.save(p, k);
        p.level().playSound(null, p.blockPosition(), EnchantContent.PAGE_TURN.get(), SoundSource.PLAYERS, 0.9F, 1F);
        p.level().playSound(null, p.blockPosition(), EnchantContent.LEARN.get(), SoundSource.PLAYERS, 0.9F, 1F);
        for (Inscriptions.Entry e : learned) {
            door(p, "read", e);
            // chat keeps the record short; the card says what it does and how it gets onto a piece
            p.sendSystemMessage(Component.literal("You learned " + e.name() + ". ").withColor(e.colour())
                    .append(Component.literal("It isn't on anything yet. " + e.howTo()).withColor(0xC9D2E0)), false);
            dev.awaken.signal.Signal.send(p, learnedCard(p, k, e, first && e == learned.getFirst()));
        }
        EnchantFx.learned(p, learned.getFirst());
        return learned;
    }

    /**
     * The card a learned enchantment gets (a panel, so nothing is cut): what it does, that it is on nothing yet, how it
     * gets onto a piece, the piece you carry that it fits, and where a table comes from while you have none. The first
     * one ever also carries the System's line about gates (it was a toast, and was cut after "and its L").
     */
    static dev.awaken.signal.SignalWindow learnedCard(ServerPlayer p, Known k, Inscriptions.Entry e, boolean first) {
        var w = dev.awaken.signal.SignalWindow.builder(dev.awaken.signal.SignalWindow.Kind.NOTICE, Component.literal(e.name()).withColor(e.colour()))
                .body(Component.literal(e.line()))
                .row("Learned", "You know it for good. It isn't on anything yet.")
                .row("To use it", e.howTo() + " You write it on there.");
        ItemStack fit = fits(p, e);
        if (!fit.isEmpty()) w.row("It fits", "your " + fit.getHoverName().getString());
        if (!k.hadTable()) w.row("The table", "You can make one. It's on your Tasks page.");
        if (first) w.footer(Component.literal("Every kind of gate keeps its own enchantment, and its Lord carries the page."));
        return w.presentation(dev.awaken.signal.SignalWindow.Presentation.PANEL).tier(doorTier(k)).topic("enchant").ttl(20 * 14).build();
    }

    /**
     * How loud the door's two cards are. Until you have written your first enchantment they skip the System's pacing:
     * they are the one thing that says a page is not yet on your gear, and a paced notice waits out a world's first
     * minutes and the gaps between messages (the first filmed run showed neither card). After that they are paced like
     * any other notice.
     */
    static dev.awaken.core.api.Notices.Tier doorTier(Known k) {
        return k.written() == 0 ? dev.awaken.core.api.Notices.Tier.CRITICAL : dev.awaken.core.api.Notices.Tier.IMPORTANT;
    }

    /**
     * The card a page gets as it reaches you (chat alone was missed: Euan read a page's arrival as the enchantment
     * already being his gear's). It says the two steps: read it, then write it on at a table.
     */
    static void givenCard(ServerPlayer p, Inscriptions.Entry e) {
        dev.awaken.signal.Signal.send(p, dev.awaken.signal.SignalWindow.builder(dev.awaken.signal.SignalWindow.Kind.NOTICE,
                        Component.literal("Page of " + e.name()).withColor(e.colour()))
                .body(Component.literal(e.line()))
                .row("First", "Use the page to learn it.")
                .row("Then", e.howTo())
                .presentation(dev.awaken.signal.SignalWindow.Presentation.PANEL).tier(doorTier(Known.of(p))).topic("enchant").ttl(20 * 10).build());
    }

    /** A piece the player carries that this enchantment fits (worn first, then the hands, then the pack), or empty. */
    static ItemStack fits(ServerPlayer p, Inscriptions.Entry e) {
        var h = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(e.key());
        if (h.isEmpty()) return ItemStack.EMPTY;
        for (ItemStack s : EnchantGoals.carried(p)) if (EnchantRules.fits(s, h.get())) return s;
        return ItemStack.EMPTY;
    }

    /** One line a step of the way from a page to a written piece, with the game's time, so a play's log says where it stops. */
    static void door(ServerPlayer p, String step, Inscriptions.@Nullable Entry e) {
        Awaken.LOG.info("[enchant] door: {} {} t={} ({})", step, e == null ? "-" : e.id().getPath(), p.level().getGameTime(), p.getGameProfile().name());
    }

    /** "Use the page to learn it, then write it onto a weapon at an Inscribing Table." */
    static String then(Inscriptions.Entry e) {
        return "Use the page to learn it, then write it onto " + e.piece() + " at an Inscribing Table.";
    }

    // ---- old books ------------------------------------------------------------------------------------------------------

    /** A vanilla enchanted book teaches what can still be written and becomes a plain book; one that can't stays. */
    static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        ItemStack s = e.getItemStack();
        if (!s.is(Items.ENCHANTED_BOOK)) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<Inscriptions.Entry> on = teaches(s);
        if (on.isEmpty()) {
            p.sendSystemMessage(Component.literal("This one can't be learned any more."), true);
            return;
        }
        List<Inscriptions.Entry> learned = learn(p, on, "From an old book");
        if (learned.isEmpty()) {
            p.sendSystemMessage(Component.literal("You already know everything in this book."), true);
            return;
        }
        ItemStack plain = new ItemStack(Items.BOOK);
        if (s.getCount() == 1) p.setItemInHand(e.getHand(), plain);
        else {
            s.shrink(1);
            if (!p.getInventory().add(plain)) p.drop(plain, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    // ---- gates ----------------------------------------------------------------------------------------------------------

    /**
     * A gate's Lord fell to a fighter: everyone who fought it (within 48 blocks) gets its kind's page in their pack, the
     * first time for that kind and one time in five after; a Lord killed by a decoy or a reflection gives nothing.
     */
    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(e.getSource().getEntity() instanceof ServerPlayer)) return;
        MobRole role = MobRole.of(dead);
        if (role.role() != MobRole.Role.BOSS || role.theme().isEmpty()) return;
        Inscriptions.Entry entry = Inscriptions.ofGate(role.theme());
        if (entry == null) return;
        String theme = role.theme().startsWith("awaken:") ? role.theme().substring(7) : role.theme();
        if (theme.equals("lantern_night_market")) theme = "lantern_market";
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator() || p.distanceToSqr(dead) > LORD_REACH * LORD_REACH) continue;
            if (give(p, level, entry, theme)) EnchantFx.page(p, entry, dead.position().add(0, dead.getBbHeight() * 0.5, 0));
        }
    }

    /** One fighter's page from a Lord of this kind of gate. */
    static boolean give(ServerPlayer p, ServerLevel level, Inscriptions.Entry entry, String theme) {
        Known k = Known.of(p);
        boolean first = !k.gatePaid(theme);
        if (!first && level.getRandom().nextInt(SPARE_ONE_IN) != 0) return false;
        String lord = Inscriptions.lordName(theme);
        ItemStack page = page(level, entry.id(), "From " + lord + ", the Lord of " + Inscriptions.gateName(theme));
        if (page == null) return false;
        if (first) {
            k.payGate(theme);
            Known.save(p, k);
        }
        if (!p.getInventory().add(page)) p.drop(page, false, net.minecraft.util.Prediction.SERVER_ONLY);
        door(p, "page given", entry);
        givenCard(p, entry);
        p.sendSystemMessage(Component.literal(Character.toUpperCase(lord.charAt(0)) + lord.substring(1)
                + " carried a page of " + entry.name() + ". " + then(entry)).withColor(entry.colour()), false);
        return true;
    }

    // ---- the market -----------------------------------------------------------------------------------------------------

    /** The day's market pages: drawn from the market list by the village and the day, the same for everyone there. */
    static List<ItemStack> market(ServerLevel level, long seed, long day) {
        List<Inscriptions.Entry> pool = new ArrayList<>();
        for (Inscriptions.Entry e : Inscriptions.ALL) if (e.market()) pool.add(e);
        Random r = new Random(seed ^ day * 0x9E3779B97F4A7C15L ^ 0x5EED);
        List<ItemStack> out = new ArrayList<>(MARKET_DRAWN);
        for (int k = 0; k < MARKET_DRAWN && !pool.isEmpty(); k++) {
            ItemStack page = page(level, pool.remove(r.nextInt(pool.size())).id(), "Bought at the market");
            if (page != null) out.add(page);
        }
        return out;
    }

    static void install() {
        EnchantApi.install(new EnchantApi.Hooks() {
            @Override public List<ItemStack> marketPages(ServerLevel level, long seed, long day) { return market(level, seed, day); }
            @Override public boolean wanted(ServerPlayer player, ItemStack page) { return Pages.wanted(player, page); }
            @Override public ItemStack page(Identifier id, String where) {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server == null) return ItemStack.EMPTY;
                ItemStack s = Pages.page(server.overworld(), id, where);
                return s == null ? ItemStack.EMPTY : s;
            }
        });
        Awaken.LOG.debug("[enchant] pages installed");
    }
}
