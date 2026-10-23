package dev.awaken.progression;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs.Grant;
import dev.awaken.progression.content.Defs.LevelRoadDef;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The level road, 1 → 50 (plan v2 §4.4): every level gives +3 points and at least one visible unlock from
 * {@code data/awaken/awaken/level_road/*.json}. The profile saves unlock ids, never table positions: editing the
 * table later never revokes, and on login an older save is granted any step it missed. An unknown saved id is skipped.
 */
public final class LevelRoad {
    private LevelRoad() {}

    public record Step(Identifier id, LevelRoadDef def) {}

    private static List<Step> sorted = List.of();
    private static final Map<String, List<Consumer<ServerPlayer>>> FLAG_LISTENERS = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    static void init() {
        Content.LEVEL_ROAD.onReload(LevelRoad::rebuild);
        Progression.onAwakened(p -> catchUp(p, false));
        Progression.onLevelReached((p, level) -> catchUp(p, false));
    }

    private static void rebuild(Content.Store<LevelRoadDef> store) {
        List<Step> out = new ArrayList<>();
        store.map.forEach((id, d) -> out.add(new Step(id, d)));
        out.sort(Comparator.<Step>comparingInt(s -> s.def().level()).thenComparing(s -> s.id().toString()));
        sorted = List.copyOf(out);
    }

    /** The whole table, by level. */
    public static List<Step> steps() {
        if (sorted.size() != Content.LEVEL_ROAD.map.size()) rebuild(Content.LEVEL_ROAD); // client side: filled by sync
        return sorted;
    }

    /** The next step above the player's level that will announce itself (for the Now tracker), or null at the end. */
    public static @Nullable Step next(Profile p) {
        for (Step s : steps()) {
            if (s.def().level() > p.level() && !s.def().quiet() && !p.unlocks.contains(s.id().toString())) return s;
        }
        return null;
    }

    // ---- flags ------------------------------------------------------------------------------------------------------

    /** The counter that marks a flag ("awaken:veil" → "awaken:road/veil"); conditions can read it as a "flag". */
    public static String flagCounter(String flag) {
        Identifier id = Identifier.tryParse(flag);
        return id == null ? Awaken.MODID + ":road/" + flag : id.getNamespace() + ":road/" + id.getPath();
    }

    public static boolean hasFlag(Profile p, String flag) {
        return p.counter(flagCounter(flag)) > 0;
    }

    /** Run something the moment a player is granted a flag (also on a login backfill). */
    public static void onFlag(String flag, Consumer<ServerPlayer> listener) {
        FLAG_LISTENERS.computeIfAbsent(flag, k -> new ArrayList<>()).add(listener);
    }

    // ---- granting ---------------------------------------------------------------------------------------------------

    /**
     * Grants every step at or below the player's level that their profile doesn't hold yet. On a login
     * {@code backfill} the steps are granted with one summary toast instead of one each. Returns how many.
     */
    public static int catchUp(ServerPlayer player, boolean backfill) {
        Profile p = Progression.profile(player);
        if (!p.awakened()) return 0;
        if (backfill) warnUnknown(player, p);
        int granted = 0;
        for (Step s : steps()) {
            if (s.def().level() > p.level()) break;
            String key = s.id().toString();
            if (!p.unlocks.add(key)) continue;
            boolean announced = apply(player, p, s);
            granted++;
            dev.awaken.core.Playtest.log(player, "unlock", "id", key);
            DirectorLog.log(player, "level-road", "level " + s.def().level() + ": " + key + (backfill ? " (caught up)" : ""));
            if (!backfill && !announced && !s.def().quiet()) announce(player, s.def());
        }
        if (granted > 0) {
            if (backfill) {
                Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The level road"))
                        .body(Component.literal(granted + " unlock" + (granted == 1 ? "" : "s") + " caught up from your level · K"))
                        .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
            }
            Progression.markDirty(player);
        }
        return granted;
    }

    private static void announce(ServerPlayer player, LevelRoadDef d) {
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.title()))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6);
        if (!d.text().isEmpty()) w.body(Component.literal(d.text()));
        Signal.send(player, w.build());
    }

    /** Applies one step's grants. Returns true when something else already announced it (a newly learned power). */
    private static boolean apply(ServerPlayer player, Profile p, Step s) {
        boolean announced = false;
        for (Grant g : s.def().grants()) {
            switch (g.type()) {
                case "power" -> g.id().ifPresent(id -> {
                    // already owned (from a core, a Path...): don't level it up for free, just keep it
                    if (!Requirements.powerCheck.test(player, "power:" + id)) RewardHooks.learnPower(player, id);
                });
                case "recipe" -> {
                    List<ResourceKey<Recipe<?>>> keys = new ArrayList<>();
                    for (String id : g.ids().isEmpty() ? g.id().map(List::of).orElse(List.of()) : g.ids()) {
                        Identifier parsed = Identifier.tryParse(id);
                        if (parsed != null) keys.add(ResourceKey.create(Registries.RECIPE, parsed));
                    }
                    if (!keys.isEmpty()) player.awardRecipesByKey(keys);
                }
                case "item" -> g.id().map(Identifier::tryParse).flatMap(BuiltInRegistries.ITEM::getOptional).ifPresent(item ->
                        give(player, new ItemStack(item, Math.max(1, g.count()))));
                case "power_core" -> {
                    for (int i = 0; i < Math.max(1, g.count()); i++) {
                        ItemStack core = PowerCoreHooks.roll(p.rank(), player.getRandom());
                        if (!core.isEmpty()) give(player, core);
                    }
                }
                case "flag" -> g.id().ifPresent(flag -> {
                    Progression.setCounter(player, flagCounter(flag), 1);
                    for (var l : FLAG_LISTENERS.getOrDefault(flag, List.of())) l.accept(player);
                });
                default -> Awaken.LOG.warn("[level road] {} has an unknown grant type '{}'", s.id(), g.type());
            }
            if (g.type().equals("power")) announced = true; // the power's own window says what it is and its key
        }
        return announced && s.def().grants().size() == 1;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    private static void warnUnknown(ServerPlayer player, Profile p) {
        if (Content.LEVEL_ROAD.map.isEmpty()) return;
        for (String id : p.unlocks) {
            if (Content.LEVEL_ROAD.get(id) == null && WARNED.add(id)) {
                Awaken.LOG.info("[level road] {} holds unlock '{}' that the table no longer has: kept, skipped", player.getScoreboardName(), id);
            }
        }
    }
}
