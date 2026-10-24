package dev.awaken.hearth.guide;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.hearth.Costs;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Upgrades;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.town.Townsfolk;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * "Your Hearth": the screen that opens when the owner uses their Anchor (tier, rooms with what they need and why they
 * aren't recognised yet, the town, the next upgrade), Oren's cottage offer, and the Hearth's Now lines. Server side;
 * the screen is {@code hearth.client.HearthScreen}. Everything shown is derived from {@link RoomType},
 * {@link Furniture}, the last room scan and the real recipes.
 */
public final class Guide {
    private Guide() {}

    /** Signal action prefix for the guide's decisions (the cottage card). */
    public static final String ACTION = "hearthguide";
    /** {@link HearthPlayer#tips} marker: the player has opened their Hearth at least once. */
    public static final String TIP_SEEN = "guide_seen";

    /** Recipes the Anchor teaches when it takes root: the Hearth's furniture. */
    public static final List<String> FURNITURE_RECIPES = List.of("meditation_mat", "forge", "trophy_mount", "role_pad_guard",
            "role_pad_haul", "role_pad_farm", "role_pad_mine", "role_pad_train");

    private static final Set<UUID> OPEN = new HashSet<>();

    // ---- network ----------------------------------------------------------------------------------------------------

    /** Server → client: open the screen ({@code open}) or update it if it is open. */
    public record Show(GuideData data, boolean open) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(Awaken.id("hearth_guide"));
        public static final StreamCodec<FriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.composite(
                GuideData.STREAM_CODEC, Show::data, ByteBufCodecs.BOOL, Show::open, Show::new);
        @Override public Type<Show> type() { return TYPE; }
    }

    /** Client → server: the screen asks for fresh numbers, a button was pressed, or it closed. */
    public record Act(int action) implements CustomPacketPayload {
        public static final int REFRESH = 0, UPGRADE = 1, COTTAGE = 2, OPEN = 3, CLOSED = 4;
        public static final Type<Act> TYPE = new Type<>(Awaken.id("hearth_guide_act"));
        public static final StreamCodec<FriendlyByteBuf, Act> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Act::action, Act::new);
        @Override public Type<Act> type() { return TYPE; }
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(Guide::payloads);
        Signal.onAction(ACTION, (player, action, yes) -> {
            if (action.equals(Cottage.ACTION)) Cottage.answer(player, yes);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> OPEN.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            // a cottage finished while its owner was away still counts as their one free cottage
            if (e.getEntity() instanceof ServerPlayer sp && HearthService.hearthOf(sp).map(h -> h.cottage != null).orElse(false)) {
                HearthService.player(sp).tips.add(Cottage.TIP_BUILT);
            }
        });
        Cottage.init();
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(Show.TYPE, Show.STREAM_CODEC);
        r.playToServer(Act.TYPE, Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) act(sp, msg.action());
        });
    }

    static void act(ServerPlayer sp, int action) {
        if (action == Act.CLOSED) {
            OPEN.remove(sp.getUUID());
            return;
        }
        if (!HearthService.enabled()) return;
        Optional<HearthRecord> ho = HearthService.hearthOf(sp);
        if (ho.isEmpty()) return;
        HearthRecord h = ho.get();
        switch (action) {
            case Act.OPEN -> open(sp, HearthService.levelOf(sp.level().getServer(), h), h);
            case Act.UPGRADE -> {
                Upgrades.upgradeAnchor(sp, h.owner);
                refresh(sp);
            }
            case Act.COTTAGE -> {
                Cottage.start(sp);
                refresh(sp);
            }
            default -> {
                OPEN.add(sp.getUUID()); // the screen is open (it asks every two seconds)
                refresh(sp);
            }
        }
    }

    // ---- opening and refreshing -------------------------------------------------------------------------------------

    /** The owner used their Anchor: open "Your Hearth" (a client without the screen gets the old notice). */
    public static void open(ServerPlayer sp, ServerLevel level, HearthRecord h) {
        if (!AwakenNet.canReceive(sp, Show.TYPE)) {
            Upgrades.offerAnchor(sp, level, h);
            return;
        }
        HearthService.player(sp).tips.add(TIP_SEEN);
        OPEN.add(sp.getUUID());
        h.scheduleScan(level.getGameTime(), 1); // fresh rooms in a moment: the screen updates when the scan ends
        AwakenNet.send(sp, new Show(build(sp, h), true));
    }

    /** Pushes fresh numbers to an open screen (after a scan, an upgrade, the cottage). */
    public static void refresh(ServerPlayer sp) {
        if (!OPEN.contains(sp.getUUID())) return;
        HearthService.hearthOf(sp).ifPresent(h -> AwakenNet.send(sp, new Show(build(sp, h), false)));
    }

    public static boolean isOpen(ServerPlayer sp) {
        return OPEN.contains(sp.getUUID());
    }

    // ---- the data ---------------------------------------------------------------------------------------------------

    public static GuideData build(ServerPlayer sp, HearthRecord h) {
        HearthPlayer hp = HearthService.player(sp);
        int rest = HearthService.restSeconds(h.tier);
        List<GuideData.RoomCard> cards = new ArrayList<>();
        for (RoomType t : RoomType.values()) cards.add(card(t, h));
        List<GuideData.RecipeView> recipes = new ArrayList<>();
        for (Furniture f : Furniture.values()) recipe(sp, f).ifPresent(recipes::add);
        return new GuideData(h.tier, h.radius(), rest, Math.max(1, rest / 2), hp.respawnAtHearth, h.pos, cards, recipes,
                town(h), coming(sp, h), upgrade(sp, h), Cottage.state(sp, h));
    }

    /** One room card from the last scan. Public for the tests. */
    public static GuideData.RoomCard card(RoomType t, HearthRecord h) {
        int have = 0;
        for (Furniture f : t.furniture) if (h.furniture.length > f.ordinal() && h.furniture[f.ordinal()] > 0) have |= f.bit();
        boolean closed = false;
        boolean byOren = false;
        List<BlockPos> built = new ArrayList<>();
        for (Room r : h.rooms) {
            for (Furniture f : t.furniture) if (r.count(f) > 0) closed = true;
            if (!r.types().contains(t)) continue;
            built.add(r.centre());
            if (h.cottage != null && r.contains(h.cottage)) byOren = true;
        }
        String why = "";
        Optional<BlockPos> mark = Optional.empty();
        if (built.isEmpty()) {
            Optional<WhyNot.Reason> reason = WhyNot.of(t, h.rooms, h.misses);
            if (reason.isPresent()) {
                why = reason.get().card();
                if (reason.get().mark() && reason.get().at() != null) mark = Optional.of(reason.get().at());
            }
        }
        return new GuideData.RoomCard(t.ordinal(), have, closed, built.size() > 8 ? List.copyOf(built.subList(0, 8)) : built, why, mark, byOren);
    }

    private static Optional<GuideData.RecipeView> recipe(ServerPlayer sp, Furniture f) {
        if (f.recipe == null) return Optional.empty();
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Awaken.id(f.recipe));
        Optional<RecipeHolder<?>> holder = sp.level().getServer().getRecipeManager().byKey(key);
        if (holder.isEmpty()) return Optional.empty();
        Recipe<?> r = holder.get().value();
        List<List<Identifier>> slots = new ArrayList<>();
        int w, hgt;
        ItemStack result;
        if (r instanceof ShapedRecipe shaped) {
            w = shaped.getWidth();
            hgt = shaped.getHeight();
            for (Optional<Ingredient> ing : shaped.getIngredients()) slots.add(ing.map(Guide::items).orElse(List.of()));
            result = shaped.assemble(CraftingInput.EMPTY);
        } else if (r instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ings = shapeless.placementInfo().ingredients();
            w = Math.min(3, ings.size());
            hgt = (ings.size() + 2) / 3;
            for (Ingredient ing : ings) slots.add(items(ing));
            result = shapeless.assemble(CraftingInput.EMPTY);
        } else {
            return Optional.empty();
        }
        Identifier out = BuiltInRegistries.ITEM.getKey(result.getItem());
        return Optional.of(new GuideData.RecipeView(f.ordinal(), w, hgt, slots, out, Math.max(1, result.getCount())));
    }

    private static List<Identifier> items(Ingredient ing) {
        List<Identifier> out = new ArrayList<>();
        ing.items().limit(16).forEach((Holder<Item> h) -> out.add(BuiltInRegistries.ITEM.getKey(h.value())));
        return out;
    }

    private static List<GuideData.Person> town(HearthRecord h) {
        List<GuideData.Person> out = new ArrayList<>();
        for (String id : h.arrived) {
            Townsfolk.get(id).ifPresent(d -> out.add(new GuideData.Person(d.name(), d.role(), "")));
        }
        return out;
    }

    /** Who comes next: the first townsfolk still to arrive, and anyone arriving at the same rank. */
    private static List<GuideData.Person> coming(ServerPlayer sp, HearthRecord h) {
        Rank rank = Progression.rank(sp);
        List<GuideData.Person> out = new ArrayList<>();
        Rank at = null;
        for (Map.Entry<String, Townsfolk.Def> e : Townsfolk.inArrivalOrder()) {
            Townsfolk.Def d = e.getValue();
            if (h.arrived.contains(e.getKey()) || d.arrivesAt().ordinal() <= rank.ordinal()) continue;
            if (at != null && d.arrivesAt() != at) break;
            at = d.arrivesAt();
            out.add(new GuideData.Person(d.name(), d.role(), d.arrivesAt().label + "-rank"));
        }
        return out;
    }

    private static GuideData.Upgrade upgrade(ServerPlayer sp, HearthRecord h) {
        if (h.tier >= HearthRecord.MAX_TIER) return GuideData.Upgrade.NONE;
        int next = h.tier + 1;
        Upgrades.Def def = Upgrades.anchorTier(next);
        Rank rank = Progression.rank(sp);
        boolean rankMet = rank.ordinal() >= def.tier().ordinal();
        List<Costs.Part> price = Costs.price(def.base(), rankMet ? rank : def.tier(), def.tier());
        List<GuideData.Part> parts = new ArrayList<>();
        for (Costs.Part p : price) {
            int have = sp.hasInfiniteMaterials() ? p.count() : Costs.countOf(sp, p.item());
            parts.add(new GuideData.Part(BuiltInRegistries.ITEM.getKey(p.item()), p.count(), have));
        }
        return new GuideData.Upgrade(false, next, HearthRecord.radiusForTier(next), HearthService.restSeconds(next),
                def.tier().label + "-rank", rankMet, parts, rankMet ? Costs.discountPercent(rank, def.tier()) : 0,
                rankMet && Costs.canAfford(sp, price));
    }
}
