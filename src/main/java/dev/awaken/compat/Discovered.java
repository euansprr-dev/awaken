package dev.awaken.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.above.Above;
import dev.awaken.above.AboveItems;
import dev.awaken.above.MachineType;
import dev.awaken.above.frame.FrameForge;
import dev.awaken.above.frame.FrameModule;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The spoiler guard's record (SPEC §14): which Awaken items a player has discovered. Recipe viewers and look-at
 * tooltips show only these. An Awaken item is discovered, for good, once
 * <ol>
 *   <li>it has been in the player's inventory (or on their cursor),</li>
 *   <li>it is the result of a recipe in their recipe book (Awaken recipes unlock when you hold an ingredient, by the
 *       level road, the Hearth Anchor and town requests), or</li>
 *   <li>one of Awaken's own records names it: a scanned blueprint names its machine, and the Frame Forge's blueprint
 *       names the Frame pieces and modules it builds.</li>
 * </ol>
 * Checked every 40 ticks per player (staggered) and at login; the set only grows and is synced to its owner. Vanilla
 * and other mods' items are never guarded. Plain Awaken code: it runs the same with or without any companion mod.
 */
public final class Discovered {
    private Discovered() {}

    public static final int SCHEMA = 1;

    /** One player's discoveries: Awaken item ids, in the order found. Grows only. */
    public static final class Found {
        int schema = SCHEMA;
        final Set<String> items = new LinkedHashSet<>();
        // the ids resolved to items, built on first use (the codec runs before registries are queried by callers)
        private Set<Item> resolved;

        public Found() {}

        Found(int schema, List<String> items) {
            this.schema = schema;
            this.items.addAll(items);
        }

        public Set<String> items() {
            return Collections.unmodifiableSet(items);
        }

        public int size() {
            return items.size();
        }

        public boolean has(Item item) {
            return resolved().contains(item);
        }

        /** Adds an Awaken item; false when it was known already or isn't Awaken's. */
        boolean add(Item item) {
            if (!isAwaken(item) || resolved().contains(item)) return false;
            resolved.add(item);
            items.add(BuiltInRegistries.ITEM.getKey(item).toString());
            return true;
        }

        void clear() {
            items.clear();
            resolved = null;
        }

        private Set<Item> resolved() {
            if (resolved == null) {
                resolved = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                for (String id : items) {
                    Identifier key = Identifier.tryParse(id);
                    if (key != null) BuiltInRegistries.ITEM.getOptional(key).ifPresent(resolved::add);
                }
            }
            return resolved;
        }

        List<String> list() {
            return List.copyOf(items);
        }
    }

    public static final MapCodec<Found> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(r -> r.schema),
            Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Found::list)
    ).apply(i, Found::new));

    public static final StreamCodec<ByteBuf, Found> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, r -> r.schema,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Found::list,
            Found::new);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Supplier<AttachmentType<Found>> DATA = ATTACHMENTS.register("discovered", () -> AttachmentType
            .builder(Found::new)
            .serialize(MAP_CODEC, r -> !r.items.isEmpty())
            .copyOnDeath()
            .sync((holder, to) -> holder == to, STREAM_CODEC)
            .build());

    static void init(net.neoforged.bus.api.IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Discovered::onTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Discovered::onLogin);
        // the results index holds the server's recipe manager: let it go with the server
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> forget());
    }

    /** The player's record (on the client: the copy synced to the local player). */
    public static Found of(Player player) {
        return player.getData(DATA.get());
    }

    public static boolean isAwaken(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Awaken.MODID);
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && (sp.tickCount + sp.getId()) % 40 == 0) check(sp);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) check(sp);
    }

    /** Applies the rule to the player now. Returns how many items were new (and syncs when any were). */
    public static int check(ServerPlayer sp) {
        Found r = of(sp);
        int added = 0;
        // 1. held
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && r.add(s.getItem())) added++;
        }
        ItemStack carried = sp.containerMenu.getCarried();
        if (!carried.isEmpty() && r.add(carried.getItem())) added++;
        // 2. results of known recipes
        RecipeManager rm = sp.level().getServer().getRecipeManager();
        if (rm != builtFor) rebuild(rm);
        var book = sp.getRecipeBook();
        for (Result res : RESULTS) {
            if (!r.has(res.item) && book.contains(res.key) && r.add(res.item)) added++;
        }
        // 3. Awaken's records (no allocation: this runs every 40 ticks per player)
        added += fromRecords(sp, r);
        if (added > 0) sp.syncData(DATA.get());
        return added;
    }

    /** Adds what Awaken's own records name for this player (rule 3): scanned blueprints, the Frame Forge's catalogue. */
    private static int fromRecords(Player player, Found r) {
        int added = 0;
        for (MachineType t : MACHINE_TYPES) {
            if (r.has(AboveItems.machine(t)) && t != MachineType.FRAME_FORGE) continue;
            if (!Above.knows(player, t)) continue;
            if (r.add(AboveItems.machine(t))) added++;
            if (t == MachineType.FRAME_FORGE) {
                for (FrameForge.Piece p : FrameForge.PIECES) if (r.add(p.item().get())) added++;
                for (FrameModule m : FRAME_MODULES) if (r.add(AboveItems.MODULES.get(m).get())) added++;
            }
        }
        return added;
    }

    private static final MachineType[] MACHINE_TYPES = MachineType.values();
    private static final FrameModule[] FRAME_MODULES = FrameModule.values();

    /** Debug and tours: forget everything this player discovered. */
    static void reset(ServerPlayer sp) {
        of(sp).clear();
        sp.syncData(DATA.get());
    }

    /** Debug and tours: mark every Awaken item discovered. */
    static int all(ServerPlayer sp) {
        Found r = of(sp);
        int added = 0;
        for (Item item : BuiltInRegistries.ITEM) if (r.add(item)) added++;
        sp.syncData(DATA.get());
        return added;
    }

    // ---- results of Awaken's recipes, rebuilt when the recipe manager changes (a data pack reload) ----------------

    private record Result(ResourceKey<Recipe<?>> key, Item item) {}

    private static RecipeManager builtFor;
    private static List<Result> RESULTS = List.of();

    private static void rebuild(RecipeManager rm) {
        List<Result> list = new ArrayList<>();
        for (RecipeHolder<?> r : rm.getRecipes()) {
            if (!r.id().identifier().getNamespace().equals(Awaken.MODID)) continue;
            Set<Item> seen = new HashSet<>();
            for (RecipeDisplay d : r.value().display()) {
                Item item = resultOf(d.result());
                if (item != null && isAwaken(item) && seen.add(item)) list.add(new Result(r.id(), item));
            }
        }
        RESULTS = List.copyOf(list);
        builtFor = rm;
    }

    /** The item a recipe display shows as its result, when it names one. */
    public static Item resultOf(SlotDisplay display) {
        if (display instanceof SlotDisplay.ItemStackSlotDisplay(var template)) return template.item().value();
        if (display instanceof SlotDisplay.ItemSlotDisplay(Holder<Item> item)) return item.value();
        return null;
    }

    static void forget() {
        builtFor = null;
        RESULTS = List.of();
    }
}
