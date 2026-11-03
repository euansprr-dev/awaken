package dev.awaken.arms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.CoreApi;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Armoury (docs/arms-progression/PLAN.md §3.2): one list answering "what can I make, what do I need, where is it".
 * Four doors lead to it: Hale's rack (sneak and talk to him: patterns for essence, by rank), a Forge (a plain use with an
 * empty hand: copies of patterns you know), a War Anvil (where weapons are made, SPEC §46.7) and the band (read only). The
 * server judges every price, rank and door; the client draws what it is sent.
 */
public final class ArmsArmoury {
    private ArmsArmoury() {}

    public static final int BAND = 0, HALE = 1, FORGE = 2, ANVIL = 3;
    /** What the screen asks (the last four at a War Anvil's door; REBUILD the anvil a build up, SPEC §46.6). */
    public static final int DETAIL = 0, BUY = 1, DRAW = 2, TRACK = 3, UNTRACK = 4, RAISE = 5, FORGE_IT = 6, FROM_MEMORY = 7, FORGE_BLADE = 8,
            REBUILD = 9;

    /** The six tiers, their weapons' name prefixes and the level each opens at (the level road, SPEC §28.3). */
    public static final String[] TIERS = {"E", "D", "C", "B", "A", "S"};
    public static final String[] PREFIX = {"wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign"};
    public static final int[] LEVEL = {3, 13, 22, 42, 67, 97};

    private static final long DOOR_TICKS = 20L * 60 * 3;
    /** Which door each fighter last opened, when, and at a War Anvil where (not saved: closing the screen is enough). */
    private record Opened(int door, long at, net.minecraft.core.@Nullable BlockPos pos) {}
    private static final Map<UUID, Opened> OPEN = new HashMap<>();

    static void forget(UUID player) {
        OPEN.remove(player);
    }

    // ---- what the screen is sent -------------------------------------------------------------------------------------

    public static void open(ServerPlayer p, int door) {
        open(p, door, null);
    }

    /** The War Anvil's door: the weapons you can make there, and Forge it (SPEC §46.7). */
    public static void openAnvil(ServerPlayer p, net.minecraft.core.BlockPos anvil) {
        open(p, ANVIL, anvil.immutable());
    }

    private static void open(ServerPlayer p, int door, net.minecraft.core.@Nullable BlockPos pos) {
        dev.awaken.progression.Progression.count(p, "awaken:armoury_opened");
        OPEN.put(p.getUUID(), new Opened(door, p.level().getGameTime(), pos));
        AwakenNet.send(p, view(p, door, true));
        sendDetail(p, defaultStyle(p), null);
    }

    /** Refreshes the client's copy (the band reads it), at the door they last opened or the band. */
    public static void sendView(ServerPlayer p) {
        AwakenNet.send(p, view(p, doorOf(p), false));
        String tracked = ArmsProgress.of(p).tracked;
        int colon = tracked.indexOf(':');
        if (colon > 0) sendDetail(p, tracked.substring(0, colon), tracked.substring(colon + 1));
    }

    private static int doorOf(ServerPlayer p) {
        Opened o = OPEN.get(p.getUUID());
        return o != null && p.level().getGameTime() - o.at() < DOOR_TICKS ? o.door() : BAND;
    }

    /** The War Anvil this fighter opened the Armoury at, while that door is fresh. */
    private static net.minecraft.core.@Nullable BlockPos anvilOf(ServerPlayer p) {
        Opened o = OPEN.get(p.getUUID());
        return o != null && o.door() == ANVIL && p.level().getGameTime() - o.at() < DOOR_TICKS ? o.pos() : null;
    }

    static ArmsPayloads.ArmouryView view(ServerPlayer p, int door, boolean open) {
        Rank rank = Progression.rank(p);
        int essence = ArmsForge.count(p, CoreApi.ESSENCE_SHARD.get());
        List<ArmsPayloads.StyleEntry> out = new ArrayList<>();
        int hidden = 0;
        Rank lowest = null;
        boolean canDraw = ArmsForge.affords(p, ArmsForge.blueprintPrice(p));
        for (Archetypes.Loaded st : Archetypes.all()) {
            boolean known = ArmsIssue.INSTANCE.knows(p, st.id());
            if (known) {
                out.add(new ArmsPayloads.StyleEntry(st.id(), 0, 0, canDraw && !st.id().equals(Arms.LONGSWORD)));
                continue;
            }
            ArmsHomes.Home h = ArmsHomes.of(st.id());
            if (h != null && h.rank().ordinal() <= rank.ordinal()) {
                int price = ArmsHomes.rackPrice(h.rank());
                out.add(new ArmsPayloads.StyleEntry(st.id(), 1, price, essence >= price || p.hasInfiniteMaterials()));
            } else {
                hidden++;
                if (h != null && (lowest == null || h.rank().ordinal() < lowest.ordinal())) lowest = h.rank();
            }
        }
        // Raise is a reforging at a War Anvil now (SPEC §46.8): the Forge's door no longer offers it
        return new ArmsPayloads.ArmouryView(door, essence, out, hidden, lowest == null ? "" : lowest.label, ArmsProgress.of(p).tracked, open,
                "", finds(p));
    }

    /** What the fighter has found: the Lord-forged weapons they hold of the fifteen, and when the next Lord is certain to carry one. */
    static List<String> finds(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        int owned = 0;
        for (LordForged.Forged f : LordForged.ALL) {
            Item item = ArmsItems.item(f.weapon());
            if (item != null && ArmsForge.count(p, item) > 0) owned++;
        }
        out.add("Lord-forged " + owned + " of " + LordForged.ALL.size());
        if (p.getData(ArmsContent.MEMORY).lordsWithout() + 1 >= LordForged.PITY) out.add("The next Lord is certain to carry its weapon.");
        return out;
    }

    private static String defaultStyle(ServerPlayer p) {
        String tracked = ArmsProgress.of(p).tracked;
        if (!tracked.isEmpty()) return tracked.substring(0, tracked.indexOf(':'));
        return Arms.LONGSWORD;
    }

    /** The detail of one style: its tiers, and what the chosen one needs (the highest you have reached when none is named). */
    static void sendDetail(ServerPlayer p, String style, @Nullable String tier) {
        Archetypes.Loaded st = Archetypes.get(style);
        if (st == null) return;
        int level = Progression.profile(p).level();
        List<ArmsPayloads.TierRow> tiers = new ArrayList<>();
        int best = Math.max(0, ArmsTiers.best(p));
        for (int i = 0; i < TIERS.length; i++) tiers.add(new ArmsPayloads.TierRow(TIERS[i], LEVEL[i], level >= LEVEL[i], ArmsTiers.pull(p, i, ArmsTiers.best(p))));
        int at = tier == null ? best : Math.max(0, tierIndex(tier));
        var anvil = anvilOf(p);
        var status = anvil == null ? null : dev.awaken.arms.anvil.WarAnvil.status(p, anvil, style, at);
        AwakenNet.send(p, new ArmsPayloads.ArmouryDetail(style, TIERS[at], tiers, needs(p, style, at),
                status == null ? 0 : status.flags(), status == null ? "" : status.line()));
    }

    /** The highest tier whose level the fighter has reached. */
    static int bestTier(ServerPlayer p) {
        int level = Progression.profile(p).level(), best = 0;
        for (int i = 0; i < LEVEL.length; i++) if (level >= LEVEL[i]) best = i;
        return best;
    }

    public static int tierIndex(String tier) {
        for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equals(tier)) return i;
        return -1;
    }

    // ---- what a weapon needs -----------------------------------------------------------------------------------------

    private record Raw(String kind, String id, int n) {}

    private static final Map<String, List<Raw>> RECIPES = new HashMap<>();

    /** What a recipe asks for, counted from its pattern (a tag or an item each, in the order they first appear). */
    private static List<Raw> raw(String recipe) {
        return RECIPES.computeIfAbsent(recipe, id -> {
            try (InputStream in = ArmsArmoury.class.getResourceAsStream("/data/awaken/recipe/" + id + ".json")) {
                if (in == null) return List.of();
                JsonObject r = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject key = r.getAsJsonObject("key");
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (JsonElement row : r.getAsJsonArray("pattern")) {
                    for (char ch : row.getAsString().toCharArray()) {
                        if (ch == ' ') continue;
                        JsonElement k = key.get(String.valueOf(ch));
                        String token = k.isJsonObject() ? (k.getAsJsonObject().has("item") ? k.getAsJsonObject().get("item").getAsString()
                                : "#" + k.getAsJsonObject().get("tag").getAsString()) : k.getAsString();
                        counts.merge(token, 1, Integer::sum);
                    }
                }
                List<Raw> out = new ArrayList<>();
                counts.forEach((token, n) -> out.add(token.startsWith("#") ? new Raw("tag", token.substring(1), n) : new Raw("item", token, n)));
                return List.copyOf(out);
            } catch (IOException e) {
                return List.of();
            }
        });
    }

    /** The recipe id of a style's weapon at a tier ("wildhide_daggers"). */
    static String recipeId(String style, int tier) {
        return PREFIX[tier] + "_" + style;
    }

    /** What the weapon needs and how much of it the fighter holds. */
    public static List<ArmsPayloads.NeedRow> needs(ServerPlayer p, String style, int tier) {
        return needsOf(p, recipeId(style, tier));
    }

    /**
     * What a thing of ours needs (by its id, "beastbound_blade") and how much of it the player holds: a weapon or a Hunter
     * blade from the War Anvil's cost table (SPEC §46.7), anything else from its crafting recipe.
     */
    public static List<ArmsPayloads.NeedRow> needsOf(ServerPlayer p, String recipe) {
        if (dev.awaken.arms.anvil.AnvilCosts.of(recipe) != null) return dev.awaken.arms.anvil.AnvilCosts.needs(p, recipe);
        List<ArmsPayloads.NeedRow> out = new ArrayList<>();
        for (Raw r : raw(recipe)) out.add(new ArmsPayloads.NeedRow(r.kind(), r.id(), r.n(), have(p, r.kind(), r.id())));
        return out;
    }

    /** Takes what a need asked for out of the pack (a tag's items or the item, up to the count). */
    static void take(ServerPlayer p, ArmsPayloads.NeedRow n) {
        int left = n.need();
        TagKey<Item> tag = n.kind().equals("tag") ? TagKey.create(Registries.ITEM, Identifier.parse(n.id())) : null;
        Item item = tag == null ? BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id())) : null;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
            if (left <= 0) break;
            if (s.isEmpty() || !(tag != null ? s.is(tag) : s.is(item))) continue;
            int t = Math.min(left, s.getCount());
            s.shrink(t);
            left -= t;
        }
    }

    /** How many of a tag's items ("tag") or an item ("item") the player holds, the off hand counted. */
    public static int have(ServerPlayer p, String kind, String id) {
        int n = 0;
        if (kind.equals("tag")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(id));
            for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(tag)) n += s.getCount();
            if (p.getOffhandItem().is(tag)) n += p.getOffhandItem().getCount();
        } else {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            n = ArmsForge.count(p, item);
        }
        return n;
    }

    // ---- what the screen does ----------------------------------------------------------------------------------------

    public static void onAct(ServerPlayer p, ArmsPayloads.ArmouryAct a) {
        Archetypes.Loaded st = Archetypes.get(a.style());
        if (st == null) return;
        Opened o = OPEN.get(p.getUUID());
        boolean fresh = o != null && p.level().getGameTime() - o.at() < DOOR_TICKS;
        int door = fresh ? o.door() : BAND;
        switch (a.op()) {
            case DETAIL -> sendDetail(p, st.id(), a.tier().isEmpty() ? null : a.tier());
            case BUY -> {
                if (door == HALE && buy(p, st.id())) {
                    OPEN.put(p.getUUID(), new Opened(door, p.level().getGameTime(), null));
                    sendView(p);
                    sendDetail(p, st.id(), null);
                }
            }
            case DRAW -> {
                if (door == FORGE && !st.id().equals(Arms.LONGSWORD) && ArmsIssue.INSTANCE.knows(p, st.id()) && ArmsForge.drawBlueprint(p, st.id())) {
                    OPEN.put(p.getUUID(), new Opened(door, p.level().getGameTime(), null));
                    sendView(p);
                    sendDetail(p, st.id(), null);
                }
            }
            case TRACK -> {
                int t = a.tier().isEmpty() ? bestTier(p) : tierIndex(a.tier());
                if (t >= 0 && ArmsIssue.INSTANCE.knows(p, st.id())) {
                    ArmsProgress g = ArmsProgress.of(p);
                    g.tracked = st.id() + ":" + TIERS[t];
                    p.setData(ArmsContent.PROGRESS, g);
                    dev.awaken.core.api.TrailsApi.unfollow(p); // the task card is one thing at a time
                    sendView(p);
                }
            }
            case FORGE_IT, FROM_MEMORY, FORGE_BLADE -> {
                var anvil = anvilOf(p);
                int t = tierIndex(a.tier());
                if (anvil == null || t < 0) return;
                String id = a.op() == FORGE_BLADE ? PREFIX[t] + "_blade" : recipeId(st.id(), t);
                // begun, the anvil's scene opens over this screen; refused, the line under the buttons says why
                if (!dev.awaken.arms.anvil.WarAnvil.begin(p, anvil, id, a.op() == FROM_MEMORY)) sendDetail(p, st.id(), a.tier());
            }
            case REBUILD -> {
                var anvil = anvilOf(p);
                if (anvil != null && dev.awaken.arms.anvil.WarAnvil.rebuild(p, anvil)) sendView(p);
                sendDetail(p, st.id(), a.tier());
            }
            case UNTRACK -> {
                ArmsProgress g = ArmsProgress.of(p);
                g.tracked = "";
                p.setData(ArmsContent.PROGRESS, g);
                sendView(p);
            }
            default -> {}
        }
    }

    /** Hale's rack: the pattern for its price in essence, if the fighter's rank has opened it. */
    static boolean buy(ServerPlayer p, String style) {
        ArmsHomes.Home h = ArmsHomes.of(style);
        if (h == null || ArmsIssue.INSTANCE.knows(p, style) || h.rank().ordinal() > Progression.rank(p).ordinal()) return false;
        var price = List.of(ArmsForge.cost(CoreApi.ESSENCE_SHARD.get(), ArmsHomes.rackPrice(h.rank())));
        if (!ArmsForge.affords(p, price)) return false;
        ArmsForge.pay(p, price);
        dev.awaken.progression.Progression.count(p, "awaken:patterns_bought");
        return ArmsIssue.INSTANCE.learn(p, style, "Hale's rack");
    }
}
