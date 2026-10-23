package dev.awaken.hearth.request;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.hearth.Costs;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Later;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.town.Townsfolk;
import dev.awaken.progression.LevelRoad;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Requirements;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Anchor requests (plan v2 §4.5, the Vault Hunters altar pattern): once a player has a Hearth Anchor the System asks
 * for one thing at a time (two from C-rank) that they can really get where they play: "Bring 8 Mire Hide to the
 * Anchor". Offers are quiet (a toast; the decision waits in the tracker and the Status screen). A taken request is a
 * Now line with progress; using the Anchor while carrying the items delivers them (partly is fine) with a moment: they
 * stream into the Anchor, it chimes, a toast says what it paid. A new request comes after each delivery and each
 * play-day; ignoring or declining one costs nothing. See docs/modules/requests.md.
 */
public final class Requests {
    private Requests() {}

    public static final String ACTION = "request";
    /** Progression world-XP source key: its own diminishing-returns budget (XpBudget's default for module keys). */
    public static final String XP_SOURCE = "hearth_request";
    public static final String COUNTER_DONE = "awaken:requests_done";

    /** The first request waits a little after the Anchor takes root, so it isn't lost in "Home exists". */
    static final int FIRST_DELAY = 20 * 90;
    static final int AFTER_DELIVERY = 20 * 30;
    static final int DAY_DELAY = 20 * 20;
    static final int BETWEEN_OFFERS = 20 * 20;
    /** Nothing doable to ask for yet (no creatures met, nothing mined): look again later. */
    static final int RETRY = 20 * 60 * 5;
    /** An offer stays answerable for one play-day; the next day brings a fresh one. */
    static final int OFFER_TTL = PlayDays.DAY;

    public static void init(IEventBus modBus) {
        RequestContent.register(modBus);
        modBus.addListener(RequestNet::register);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RequestPools::addReload);
        bus.addListener(RequestPools::onSync);
        bus.addListener(Requests::onPlayerTick);
        bus.addListener((ServerTickEvent.Post e) -> RequestFx.tick(e.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> RequestNet.forget(e.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent e) -> RequestFx.clear());
        Signal.onAction(ACTION, Requests::answer);
        NowHooks.register(Requests::suggest);
        RequestCommands.register();
        RequestTests.register();
    }

    public static RequestState state(Player p) {
        return p.getData(RequestContent.STATE.get());
    }

    public static void sync(ServerPlayer p) {
        p.syncData(RequestContent.STATE.get());
    }

    // ---- what a player can do -----------------------------------------------------------------------------------------

    static RequestRules.Context context(ServerPlayer sp, RequestState st) {
        var prof = Progression.profile(sp);
        return new RequestRules.Context() {
            @Override public Rank rank() { return prof.rank(); }
            @Override public int level() { return prof.level(); }
            @Override public boolean met(String token) { return Requests.met(sp, st, token); }
        };
    }

    /**
     * One requirement token for a real player. Own tokens read sightings (fauna discoveries), vanilla statistics (what
     * they killed, mined, picked up or crafted) and the depth they've stood at; the rest is progression's.
     */
    static boolean met(ServerPlayer sp, RequestState st, String token) {
        try {
            if (token.startsWith("seen:")) return Progression.profile(sp).counter("awaken:discovered/creature/" + token.substring(5)) > 0;
            if (token.startsWith("killed:")) {
                var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(token.substring(7)));
                return type.isPresent() && sp.getStats().getValue(Stats.ENTITY_KILLED.get(type.get())) > 0;
            }
            if (token.startsWith("mined:")) {
                var block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(token.substring(6)));
                return block.isPresent() && sp.getStats().getValue(Stats.BLOCK_MINED.get(block.get())) > 0;
            }
            if (token.startsWith("got:")) {
                var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(token.substring(4)));
                return item.isPresent() && (sp.getStats().getValue(Stats.ITEM_PICKED_UP.get(item.get())) > 0
                        || sp.getStats().getValue(Stats.ITEM_CRAFTED.get(item.get())) > 0);
            }
            if (token.startsWith("depth:")) return st.lowestY <= Integer.parseInt(token.substring(6));
        } catch (RuntimeException bad) {
            return false;
        }
        return Requirements.met(sp, token);
    }

    // ---- the per-second tick ------------------------------------------------------------------------------------------

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || (sp.tickCount + sp.getId()) % 20 != 0) return;
        tick(sp);
    }

    static void tick(ServerPlayer sp) {
        RequestState st = state(sp);
        ServerLevel level = sp.level();
        if (level.dimension() == Level.OVERWORLD && sp.getBlockY() < st.lowestY) st.lowestY = sp.getBlockY();
        if (!HearthService.enabled() || !Progression.profile(sp).awakened()) return;
        Optional<HearthRecord> home = HearthService.hearthOf(sp);
        boolean dirty = forgeCredit(sp, st, home);
        if (home.isEmpty()) {
            if (dirty) sync(sp);
            return;
        }
        long day = PlayDays.current(level.getServer()), now = level.getGameTime();
        if (st.day != day) {
            newDay(sp, st, day, now);
            dirty = true;
        }
        // an offer whose decision ran out or was cleared counts as let go (it cost nothing)
        dirty |= st.offers.removeIf(o -> !Signal.isPending(sp, o.action()));
        if (st.credits > 0 && now >= st.nextOfferAt && free(sp, st) > 0 && calm(sp)) dirty |= offer(sp, st, day, now);
        if (dirty) sync(sp);
        touches(sp, st, home.get(), now);
    }

    static int free(ServerPlayer sp, RequestState st) {
        return RequestRules.slots(Progression.rank(sp)) - st.active.size() - st.offers.size();
    }

    /** Offers wait while you're in a fight, in a gate, asleep or away from the waking world. */
    static boolean calm(ServerPlayer sp) {
        if (sp.level().dimension() != Level.OVERWORLD || sp.isSleeping() || GateHooks.inGate(sp)) return false;
        return sp.getLastHurtByMobTimestamp() == 0 || sp.tickCount - sp.getLastHurtByMobTimestamp() > 200;
    }

    static void newDay(ServerPlayer sp, RequestState st, long day, long now) {
        int slots = RequestRules.slots(Progression.rank(sp));
        if (st.day < 0) {
            st.credits = slots;
            st.nextOfferAt = now + FIRST_DELAY;
        } else {
            for (RequestState.Offer o : st.offers) Signal.withdraw(sp, o.action());
            st.offers.clear();
            st.credits = Math.max(st.credits, slots - st.active.size());
            st.nextOfferAt = Math.max(st.nextOfferAt, now + DAY_DELAY);
        }
        if (RequestRules.liveStreak(st.streak, st.lastDeliveryDay, day) == 0) st.streak = 0;
        st.day = day;
    }

    // ---- offers -------------------------------------------------------------------------------------------------------

    /** Makes one offer if anything doable is left. Returns whether the state changed. */
    static boolean offer(ServerPlayer sp, RequestState st, long day, long now) {
        Rank rank = Progression.rank(sp);
        Set<String> busy = new HashSet<>();
        for (ActiveRequest r : st.active) busy.add(group(r));
        for (RequestState.Offer o : st.offers) busy.add(group(o.request()));
        Optional<RequestRules.Entry> pick = RequestRules.pick(RequestPools.entries(), context(sp, st), busy, st.recent, sp.getRandom());
        if (pick.isEmpty()) {
            st.nextOfferAt = now + RETRY;
            DirectorLog.log(sp, "requests", "nothing the Anchor could ask for yet (pool " + RequestPools.all().size() + ")");
            return true;
        }
        RequestRules.Entry e = pick.get();
        ActiveRequest req = RequestRules.create(e, rank, e.def().name().orElse(""), day);
        RequestState.Offer o = new RequestState.Offer(++st.nonce, req, day);
        st.offers.add(o);
        st.credits--;
        st.remember(e.id());
        st.nextOfferAt = now + BETWEEN_OFFERS;
        DirectorLog.log(sp, "requests", "offered " + e.id() + " ×" + req.count() + " (effort " + Math.round(req.effort()) + ")");
        Signal.offerQuietly(sp, offerWindow(sp, st, o, e.def()), Component.literal("Anchor request · " + req.count() + " ")
                .append(name(req)).append(" · K"));
        return true;
    }

    private static String group(ActiveRequest r) {
        RequestDef d = RequestPools.get(r.id());
        return d == null ? r.id() : d.groupOr(r.id());
    }

    static SignalWindow offerWindow(ServerPlayer sp, RequestState st, RequestState.Offer o, RequestDef def) {
        ActiveRequest r = o.request();
        long day = PlayDays.current(sp.level().getServer());
        int streak = RequestRules.streakAfter(RequestRules.liveStreak(st.streak, st.lastDeliveryDay, day), st.lastDeliveryDay, day);
        String hint = def == null || def.hint().isBlank() ? "" : " " + def.hint() + ".";
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Anchor request · " + r.count() + " ").append(name(r)))
                .body(Component.literal("Bring " + r.count() + " ").append(name(r)).append(" to your Anchor." + hint))
                .row(Component.literal("Reward"), Component.literal(preview(r.effort(), Progression.rank(sp), streak, st.reputation)));
        if (streak > 1) w.row(Component.literal("Streak"), Component.literal(streak + " days · +" + Math.round((RequestRules.streakMultiplier(streak) - 1) * 100) + "%"));
        return w.choice(o.action(), "Take it on", "Not now").ttl(OFFER_TTL).priority(2).build();
    }

    /** "about 12 essence · 20% of a level · Forge credit", the expected part of a reward (shards and recipes are luck). */
    static String preview(double effort, Rank rank, int streak, int reputation) {
        RequestRules.Reward r = RequestRules.reward(effort, rank, streak, reputation, net.minecraft.util.RandomSource.create(0));
        return "about " + r.essence() + " essence · " + Math.round(r.levels() * 100) + "% of a level · Forge credit";
    }

    /** Signal answer "request:take:&lt;nonce&gt;". Yes takes it on; no lets it go (no penalty). */
    static void answer(ServerPlayer sp, String action, boolean accepted) {
        String[] parts = action.split(":");
        if (parts.length != 3 || !parts[1].equals("take")) return;
        int nonce;
        try {
            nonce = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        RequestState st = state(sp);
        Optional<RequestState.Offer> o = st.offer(nonce);
        if (o.isEmpty()) return;
        st.offers.remove(o.get());
        if (accepted) {
            if (st.active.size() >= RequestRules.slots(Progression.rank(sp))) {
                st.credits++;
                Signal.toast(sp, Component.literal("Your request slots are full · finish or drop one"));
            } else {
                ActiveRequest r = o.get().request().startedOn(PlayDays.current(sp.level().getServer()));
                st.active.add(r);
                Signal.toast(sp, Component.literal("Request taken · ").append(name(r)).append(" 0/" + r.count()));
                DirectorLog.log(sp, "requests", "took " + r.id());
            }
        }
        sync(sp);
    }

    /** Lets go of an active request (Status screen, command). No penalty; a new one comes the next play-day. */
    static void drop(ServerPlayer sp, int index, String id) {
        RequestState st = state(sp);
        if (index < 0 || index >= st.active.size() || !st.active.get(index).id().equals(id)) return;
        ActiveRequest r = st.active.remove(index);
        Signal.toast(sp, Component.literal("Request dropped · ").append(name(r)));
        sync(sp);
    }

    // ---- delivery -----------------------------------------------------------------------------------------------------

    /**
     * The hook in the Anchor's use handler (owner only). Delivers what the player carries for their requests and hands
     * over the town's gift. Returns true when something happened, so the Anchor's own window waits for the next use.
     */
    public static boolean onAnchorUsed(ServerPlayer sp, ServerLevel level, HearthRecord h) {
        if (!HearthService.enabled()) return false;
        RequestState st = state(sp);
        long day = PlayDays.current(level.getServer());
        boolean any = gift(sp, st, day);
        any |= deliver(sp, level, h.pos, st, day, level.getGameTime()) > 0;
        if (any) sync(sp);
        return any;
    }

    /** Takes what the player carries toward every active request. Returns how many items went in. */
    static int deliver(ServerPlayer sp, ServerLevel level, BlockPos anchor, RequestState st, long day, long now) {
        List<ItemStack> kinds = new ArrayList<>();
        List<ActiveRequest> finished = new ArrayList<>();
        List<ActiveRequest> partial = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < st.active.size(); i++) {
            ActiveRequest r = st.active.get(i);
            int took = take(sp, r.target(), r.remaining(), kinds);
            if (took <= 0) continue;
            total += took;
            r = r.withDelivered(r.delivered() + took);
            st.active.set(i, r);
            (r.done() ? finished : partial).add(r);
        }
        if (total == 0) return 0;
        st.active.removeIf(ActiveRequest::done);
        RequestFx.deliver(level, sp, anchor, kinds, !finished.isEmpty());
        List<Runnable> after = new ArrayList<>();
        for (ActiveRequest r : partial) {
            after.add(() -> Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE,
                            Component.literal("Delivered · ").append(name(r)).append(" " + r.delivered() + "/" + r.count()))
                    .body(Component.literal(r.remaining() + " more and the Anchor pays."))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 5).build()));
        }
        for (ActiveRequest r : finished) {
            Paid paid = complete(sp, st, r, day, now);
            after.add(() -> announce(sp, r, paid));
        }
        if (!finished.isEmpty() && RequestRules.Standing.of(st.reputation).ordinal() >= RequestRules.Standing.KNOWN.ordinal()) {
            after.add(() -> townWatches(sp, level, anchor));
        }
        Later.run(level.getServer(), RequestFx.FLIGHT, () -> after.forEach(Runnable::run));
        return total;
    }

    /** Removes up to {@code max} matching items from the carried slots; notes one stack of each kind for the stream. */
    static int take(ServerPlayer sp, String target, int max, List<ItemStack> kinds) {
        Inventory inv = sp.getInventory();
        int left = max;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            if (!RequestRules.carriedSlot(i)) continue;
            ItemStack s = inv.getItem(i);
            if (!RequestRules.matches(target, s)) continue;
            boolean seen = false;
            for (ItemStack k : kinds) if (k.is(s.getItem())) seen = true;
            if (!seen) kinds.add(s.copyWithCount(1));
            int n = Math.min(left, s.getCount());
            s.shrink(n);
            left -= n;
        }
        if (left < max) inv.setChanged();
        return max - left;
    }

    /** What a finished request paid: the rolled reward, the share of a level after diminishing returns, the recipe taught. */
    record Paid(RequestRules.Reward reward, double levels, String recipe, RequestRules.Standing standingUp) {}

    /** Pays a finished request (once: it has already left the active list) and moves the pacing on. */
    static Paid complete(ServerPlayer sp, RequestState st, ActiveRequest r, long day, long now) {
        Rank rank = Progression.rank(sp);
        int streak = RequestRules.streakAfter(st.streak, st.lastDeliveryDay, day);
        RequestRules.Reward rw = RequestRules.reward(r.effort(), rank, streak, st.reputation, sp.getRandom());
        RequestRules.Standing before = RequestRules.Standing.of(st.reputation);
        st.streak = streak;
        st.lastDeliveryDay = day;
        st.completed++;
        st.reputation += rw.reputation();
        st.forgeCredit = Math.min(RequestRules.FORGE_CREDIT_CAP, st.forgeCredit + rw.forgeCredit());
        st.credits++;
        st.nextOfferAt = Math.max(st.nextOfferAt, now + AFTER_DELIVERY);
        give(sp, new ItemStack(CoreApi.ESSENCE_SHARD.get(), rw.essence()));
        if (rw.shards() > 0) give(sp, new ItemStack(RequestContent.CORE_SHARD.get(), rw.shards()));
        double paid = Progression.worldXp(sp, XP_SOURCE, rw.levels());
        String recipe = rw.recipe() ? teachRecipe(sp).orElse(null) : null;
        Progression.count(sp, COUNTER_DONE);
        if (st.completed == 1) Progression.markNewThing(sp, "hearth_request");
        DirectorLog.log(sp, "requests", "completed " + r.id() + ": " + rw.essence() + " essence, " + String.format(Locale.ROOT, "%.2f", paid)
                + " of a level (asked " + String.format(Locale.ROOT, "%.2f", rw.levels()) + "), streak " + streak
                + ", reputation " + st.reputation + (recipe != null ? ", recipe " + recipe : ""));
        RequestRules.Standing after = RequestRules.Standing.of(st.reputation);
        return new Paid(rw, paid, recipe, after != before ? after : null);
    }

    private static void announce(ServerPlayer sp, ActiveRequest r, Paid paid) {
        RequestRules.Reward rw = paid.reward();
        double levels = paid.levels();
        RequestState st = state(sp);
        List<String> parts = new ArrayList<>();
        parts.add("+" + rw.essence() + " essence");
        if (levels >= 0.005) parts.add(Math.round(levels * 100) + "% of a level");
        parts.add("Forge credit " + st.forgeCredit + "%");
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Request complete · ").append(name(r)))
                .body(Component.literal(String.join(", ", parts) + "."));
        w.row("Essence", "+" + rw.essence());
        w.row("Level", Math.round(levels * 100) + "%" + (levels + 1e-6 < rw.levels() ? " (requests paid a lot lately)" : ""));
        w.row("Forge credit", st.forgeCredit + "% back on your next temper or Forge upgrade");
        if (rw.shards() > 0) w.row("Power Core Shard", "+" + rw.shards() + (RequestRules.milestone(rw.streak()) ? " (streak)" : ""));
        if (paid.recipe() != null) w.row("Recipe, early", paid.recipe());
        if (rw.streak() > 1) w.row("Streak", rw.streak() + " days · +" + rw.streakBonusPercent() + "%");
        w.row("Town", RequestRules.Standing.of(st.reputation).label + " · " + st.reputation);
        Signal.send(sp, w.presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).priority(3).build());
        RequestRules.Standing up = paid.standingUp();
        if (up != null) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The town's regard · " + up.label))
                    .body(Component.literal(up.touch + ".")).presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).priority(3).build());
        }
    }

    private static void give(ServerPlayer sp, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!sp.addItem(stack)) sp.drop(stack, false, Prediction.SERVER_ONLY);
    }

    /**
     * Teaches one recipe early: from the level road's next {@link RequestRules#RECIPE_LOOKAHEAD} levels, one the player
     * doesn't know yet. Returns its name.
     */
    static Optional<String> teachRecipe(ServerPlayer sp) {
        int level = Progression.profile(sp).level();
        var recipes = sp.level().getServer().getRecipeManager();
        List<ResourceKey<Recipe<?>>> candidates = new ArrayList<>();
        for (LevelRoad.Step s : LevelRoad.steps()) {
            int at = s.def().level();
            if (at <= level || at > level + RequestRules.RECIPE_LOOKAHEAD) continue;
            for (dev.awaken.progression.content.Defs.Grant g : s.def().grants()) {
                if (!g.type().equals("recipe")) continue;
                for (String id : g.ids().isEmpty() ? g.id().map(List::of).orElse(List.of()) : g.ids()) {
                    Identifier rid = Identifier.tryParse(id);
                    if (rid == null) continue;
                    ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, rid);
                    if (recipes.byKey(key).isPresent() && !sp.getRecipeBook().contains(key)) candidates.add(key);
                }
            }
        }
        if (candidates.isEmpty()) return Optional.empty();
        ResourceKey<Recipe<?>> key = candidates.get(sp.getRandom().nextInt(candidates.size()));
        sp.awardRecipesByKey(List.of(key));
        Item item = BuiltInRegistries.ITEM.getValue(key.identifier());
        if (item != Items.AIR) return Optional.of(item.getName(item.getDefaultInstance()).getString());
        return Optional.of(prettify(key.identifier().getPath()));
    }

    static String prettify(String path) {
        StringBuilder b = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    /** The request's display name: its def's name, else the item's own (translated on the client). */
    public static Component name(ActiveRequest r) {
        if (!r.name().isBlank()) return Component.literal(r.name());
        if (r.target().startsWith("#")) return Component.literal(prettify(r.target().substring(r.target().indexOf(':') + 1)));
        Identifier id = Identifier.tryParse(r.target());
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? Component.literal(r.target()) : Component.translatable(item.getDescriptionId());
    }

    // ---- the Now tracker ----------------------------------------------------------------------------------------------

    static final int NOW_GATHER = 42, NOW_READY = 64;

    /** One line per active request: "Mire Hide 3/8 · Anchor" with a compass to the Anchor; brighter when it's all in hand. */
    static List<NowHooks.Suggestion> suggest(ServerPlayer sp) {
        RequestState st = state(sp);
        if (st.active.isEmpty()) return List.of();
        Optional<HearthRecord> home = HearthService.hearthOf(sp);
        if (home.isEmpty()) return List.of();
        BlockPos anchor = home.get().pos;
        boolean near = sp.level().dimension() == Level.OVERWORLD && sp.blockPosition().closerThan(anchor, 6);
        List<NowHooks.Suggestion> out = new ArrayList<>(st.active.size());
        for (ActiveRequest r : st.active) {
            int progress = Math.min(r.count(), r.delivered() + RequestRules.carried(sp, r.target()));
            boolean ready = progress >= r.count();
            MutableComponent text = Component.empty().append(name(r)).append(" " + progress + "/" + r.count()
                    + (ready ? (near ? " · use the Anchor" : " · bring it home") : " · Anchor"));
            out.add(new NowHooks.Suggestion("request:" + r.id(), ready ? NOW_READY : NOW_GATHER, text, Optional.of(anchor)));
        }
        return out;
    }

    // ---- Forge credit -------------------------------------------------------------------------------------------------

    /**
     * Forge credit pays back a share of the next temper or Forge upgrade (iron, gold, diamonds, essence), noticed from
     * the temper counter and the Forge tier once a second. Returns whether the state changed.
     */
    static boolean forgeCredit(ServerPlayer sp, RequestState st, Optional<HearthRecord> home) {
        long tempered = Progression.profile(sp).counter("awaken:gear_tempered");
        int forgeTier = home.map(h -> h.forgeTier).orElse(-1);
        boolean first = st.seenTempered < 0;
        boolean temperedNow = !first && tempered > st.seenTempered;
        boolean upgraded = st.seenForgeTier >= 0 && forgeTier > st.seenForgeTier;
        st.seenTempered = tempered;
        if (forgeTier >= 0) st.seenForgeTier = forgeTier;
        if (first || st.forgeCredit <= 0 || (!temperedNow && !upgraded)) return false;
        Rank rank = Progression.rank(sp);
        List<Costs.Part> price;
        String what;
        if (upgraded) {
            Upgrades.Def def = Upgrades.forgeTier(forgeTier);
            price = Costs.price(def.base(), rank, def.tier());
            what = def.name();
        } else {
            int lv = Math.max(1, Math.min(Upgrades.MAX_TEMPER, Math.max(Upgrades.temperOf(sp.getMainHandItem()), Upgrades.temperOf(sp.getOffhandItem()))));
            Upgrades.Def def = Upgrades.temper(lv, true); // the metal of a temper (Oren's price: the essence part is his)
            price = Costs.price(def.base(), rank, def.tier());
            what = def.name();
        }
        List<Integer> counts = new ArrayList<>();
        for (Costs.Part p : price) counts.add(p.count());
        List<Integer> back = refund(counts, st.forgeCredit);
        List<String> named = new ArrayList<>();
        for (int i = 0; i < price.size(); i++) {
            if (back.get(i) <= 0) continue;
            Item item = price.get(i).item();
            give(sp, new ItemStack(item, back.get(i)));
            named.add(back.get(i) + " " + item.getName(item.getDefaultInstance()).getString());
        }
        int pct = st.forgeCredit;
        st.forgeCredit = 0;
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Forge credit · " + pct + "% back"))
                .body(Component.literal(String.join(", ", named) + " back for " + what + "."))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 5).build());
        DirectorLog.log(sp, "requests", "forge credit " + pct + "% on " + what + ": " + named);
        return true;
    }

    /** Each part of a price times the credit, rounded down; never nothing (at least one of the first part comes back). */
    public static List<Integer> refund(List<Integer> counts, int percent) {
        List<Integer> out = new ArrayList<>(counts.size());
        boolean any = false;
        for (int c : counts) {
            int n = (int) Math.floor(c * percent / 100.0 + 1e-9);
            out.add(n);
            any |= n > 0;
        }
        if (!any && !out.isEmpty() && percent > 0) out.set(0, 1);
        return out;
    }

    // ---- the town's regard ---------------------------------------------------------------------------------------------

    /** Known: the townsfolk come to the Anchor to watch, and the nearest has a word for it. */
    static void townWatches(ServerPlayer sp, ServerLevel level, BlockPos anchor) {
        Optional<HearthRecord> h = HearthService.hearthOf(sp);
        if (h.isEmpty()) return;
        TownNpc nearest = null;
        double best = Double.MAX_VALUE;
        Vec3 at = Vec3.atBottomCenterOf(anchor);
        for (UUID u : h.get().npcs.values()) {
            if (!(level.getEntity(u) instanceof TownNpc npc) || !npc.isAlive() || npc.distanceToSqr(at) > 48 * 48) continue;
            npc.gather(sp.position(), 20 * 6);
            double d = npc.distanceToSqr(at);
            if (d < best) {
                best = d;
                nearest = npc;
            }
        }
        if (nearest == null) return;
        nearest.getLookControl().setLookAt(sp);
        Signal.toast(sp, Component.literal(Townsfolk.shortName(nearest.kind()) + ": “" + thanks(Townsfolk.skin(nearest.kind()), sp.getRandom().nextInt(2)) + "”"));
    }

    static String thanks(String skin, int pick) {
        return switch (skin) {
            case "oren" -> pick == 0 ? "Good haul. The forge won't go hungry." : "Bring me more like that and I'll make you something.";
            case "mira" -> pick == 0 ? "You went out there for us? Thank you." : "I'll tell everyone at the well.";
            case "teodor" -> pick == 0 ? "Noted, with gratitude. In ink." : "That goes in the town ledger. Twice.";
            case "sun" -> pick == 0 ? "Not a scratch on you. Good." : "The town eats well tonight because of you.";
            case "vael" -> pick == 0 ? "Not bad. I'd have brought more." : "Fine. The town owes you. Don't let it go to your head.";
            default -> pick == 0 ? "Thank you, Hunter." : "The Hearth is warmer for it.";
        };
    }

    /** Trusted: at night, while you're home near the Anchor, the town keeps its lamps lit (embers rising around it). */
    static void touches(ServerPlayer sp, RequestState st, HearthRecord h, long now) {
        if (RequestRules.Standing.of(st.reputation).ordinal() < RequestRules.Standing.TRUSTED.ordinal() || (now / 20) % 2 != 0) return;
        ServerLevel level = sp.level();
        if (level.dimension() != Level.OVERWORLD || !level.isDarkOutside() || !sp.blockPosition().closerThan(h.pos, 32)) return;
        var rnd = sp.getRandom();
        for (int i = 0; i < 3; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 2 + rnd.nextDouble() * 3;
            level.sendParticles(sp, HearthContent.HEARTH_EMBER.get(), false, false, h.pos.getX() + 0.5 + Math.cos(a) * r,
                    h.pos.getY() + 0.4 + rnd.nextDouble(), h.pos.getZ() + 0.5 + Math.sin(a) * r, 1, 0.05, 0.1, 0.05, 0.005);
        }
    }

    /** Beloved: once a play-day the town leaves something at the Anchor (food, a little essence, sometimes a shard). */
    static boolean gift(ServerPlayer sp, RequestState st, long day) {
        if (RequestRules.Standing.of(st.reputation).ordinal() < RequestRules.Standing.BELOVED.ordinal() || st.giftDay == day) return false;
        st.giftDay = day;
        int rank = Math.min(Progression.rank(sp).ordinal(), Rank.S.ordinal());
        give(sp, new ItemStack(Items.BREAD, 4));
        give(sp, new ItemStack(CoreApi.ESSENCE_SHARD.get(), 3 + 2 * rank));
        boolean shard = sp.getRandom().nextInt(4) == 0;
        if (shard) give(sp, new ItemStack(RequestContent.CORE_SHARD.get()));
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("A gift at the Anchor"))
                .body(Component.literal("The town left bread and " + (3 + 2 * rank) + " essence" + (shard ? ", and a Power Core Shard" : "") + "."))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 5).build());
        return true;
    }

    // ---- commands and tests -----------------------------------------------------------------------------------------

    /** Offers a request right now if a slot is free (admin command, tests). */
    static boolean offerNow(ServerPlayer sp) {
        RequestState st = state(sp);
        long day = PlayDays.current(sp.level().getServer()), now = sp.level().getGameTime();
        if (st.day != day) newDay(sp, st, day, now);
        if (free(sp, st) <= 0) return false;
        st.credits = Math.max(1, st.credits);
        st.nextOfferAt = 0;
        int before = st.offers.size();
        offer(sp, st, day, now);
        sync(sp);
        return st.offers.size() > before;
    }

    static MutableComponent describe(ActiveRequest r) {
        return Component.empty().append(name(r)).append(" " + r.delivered() + "/" + r.count()).withStyle(ChatFormatting.GOLD);
    }
}
