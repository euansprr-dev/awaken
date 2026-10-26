package dev.awaken.hearth;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.AtlasHooks;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What rooms do (SPEC §28.2, PLAN 4.6) beyond what the older rooms already did: meals made in a Kitchen are home-cooked
 * and their effects last half again as long; a meal eaten in a Hall feeds everyone in it; a Stable sends the whistle
 * twice as far round home; a bell tower rings at dawn and dusk and calms Deep Tide nights on your land (break warnings
 * two play-days early are in {@link Breaks}); a Lookout fills in the Atlas round home and earns Mira's second rumour; a
 * Workshop's machines spend a quarter less essence. The Study and the Greenhouse are recognised; their effects are
 * wired once the Blueprints and the herbs move (SPEC §28.1).
 */
public final class RoomEffects {
    private RoomEffects() {}

    /** The Stable's whistle: +160 blocks (twice the 160 it reaches) within this far of home. */
    public static final int STABLE_WHISTLE = 160, STABLE_REACH = 512;
    /** The Lookout's Atlas round home. */
    public static final int LOOKOUT_REVEAL = 256;
    /** A Workshop's machines pay this share of their essence. */
    public static final double WORKSHOP_COST = 0.75;
    /** Home-cooked effects last this many times as long. */
    public static final double HOME_COOKED = 1.5;

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RoomEffects::onCrafted);
        bus.addListener(RoomEffects::onSmelted);
        bus.addListener(RoomEffects::onStartEating);
        bus.addListener(RoomEffects::onEaten);
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) lookout(sp);
        });
        Progression.addPerkSource(RoomEffects::perk);
    }

    // ---- the Kitchen: home-cooked ----------------------------------------------------------------------------------------

    private static boolean inKitchen(Player p) {
        return p instanceof ServerPlayer sp && HearthService.roomHere(sp).map(r -> r.types().contains(RoomType.KITCHEN)).orElse(false);
    }

    private static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        ItemStack out = e.getCrafting();
        if (out.has(DataComponents.FOOD) && inKitchen(e.getEntity())) out.set(HearthContent.HOME_COOKED.get(), true);
    }

    private static void onSmelted(PlayerEvent.ItemSmeltedEvent e) {
        ItemStack out = e.getSmelting();
        if (out.has(DataComponents.FOOD) && inKitchen(e.getEntity())) out.set(HearthContent.HOME_COOKED.get(), true);
    }

    /** What the eater had before the meal: each effect's ticks left (so the meal's own share can be stretched). */
    private static final Map<UUID, Map<Holder<MobEffect>, Integer>> BEFORE = new HashMap<>();

    private static void onStartEating(LivingEntityUseItemEvent.Start e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !e.getItem().has(DataComponents.FOOD)) return;
        Map<Holder<MobEffect>, Integer> had = new HashMap<>();
        for (MobEffectInstance i : sp.getActiveEffects()) had.put(i.getEffect(), i.getDuration());
        BEFORE.put(sp.getUUID(), had);
    }

    private static void onEaten(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Map<Holder<MobEffect>, Integer> had = BEFORE.remove(sp.getUUID());
        ItemStack meal = e.getItem();
        FoodProperties food = meal.get(DataComponents.FOOD);
        if (food == null) return;
        if (Boolean.TRUE.equals(meal.get(HearthContent.HOME_COOKED.get())) && had != null) stretch(sp, had);
        hall(sp, food);
    }

    /** Each effect the meal gave (or lengthened) lasts half again as long. */
    private static void stretch(ServerPlayer sp, Map<Holder<MobEffect>, Integer> had) {
        for (MobEffectInstance i : List.copyOf(sp.getActiveEffects())) {
            if (i.isInfiniteDuration()) continue;
            int gained = i.getDuration() - had.getOrDefault(i.getEffect(), 0);
            if (gained <= 20) continue;
            sp.addEffect(new MobEffectInstance(i.getEffect(), i.getDuration() + (int) (gained * (HOME_COOKED - 1)), i.getAmplifier(),
                    i.isAmbient(), i.isVisible(), i.showIcon()));
        }
    }

    /** A meal eaten in your Hall feeds everyone in it. */
    private static void hall(ServerPlayer sp, FoodProperties food) {
        Optional<Room> here = HearthService.roomHere(sp);
        if (here.isEmpty() || !here.get().types().contains(RoomType.HALL)) return;
        Room hall = here.get();
        for (ServerPlayer other : sp.level().players()) {
            if (other == sp || !hall.contains(other.blockPosition()) && !hall.contains(other.blockPosition().above())) continue;
            other.getFoodData().eat(food.nutrition(), food.saturation() / Math.max(1, food.nutrition()) / 2f);
        }
    }

    // ---- the Stable: the whistle carries --------------------------------------------------------------------------------

    /** Perk source (server: the whistle is answered there). */
    static double perk(Player p, String perkId) {
        if (!"awaken:whistle_range".equals(perkId) || !(p instanceof ServerPlayer sp) || sp.level().dimension() != Level.OVERWORLD) return 0;
        for (HearthRecord h : HearthData.get(sp.level().getServer()).hearthsOf(sp.getUUID())) {
            if (h.hasRoom(RoomType.STABLE) && h.pos.closerThan(sp.blockPosition(), STABLE_REACH)) return STABLE_WHISTLE;
        }
        return 0;
    }

    // ---- the bell tower ---------------------------------------------------------------------------------------------------

    /** Whether Deep Tide nights stay calm here: inside a Hearth with a bell tower. */
    public static boolean tideCalm(ServerLevel level, BlockPos pos) {
        return HearthData.get(level.getServer()).containing(pos, level.dimension().identifier().toString())
                .map(h -> h.hasRoom(RoomType.BELL_TOWER)).orElse(false);
    }

    /** Once a second for a loaded Hearth: your bell rings as the day starts and as it ends. */
    static void bells(ServerLevel level, HearthRecord h) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        boolean dawn = time >= 0 && time < 20, dusk = time >= 12000 && time < 12020;
        if (!dawn && !dusk) return;
        for (Room r : h.rooms) {
            if (!r.types().contains(RoomType.BELL_TOWER) || r.cells().isEmpty()) continue;
            var it = r.cells().iterator();
            while (it.hasNext()) {
                BlockPos p = BlockPos.of(it.nextLong());
                if (level.getBlockState(p).getBlock() instanceof BellBlock bell) {
                    bell.attemptToRing(level, p, null);
                    return;
                }
            }
        }
    }

    // ---- the Lookout ------------------------------------------------------------------------------------------------------

    /** From up there the land round home is known: the Atlas fills in round every Hearth you belong to with a Lookout. */
    static void lookout(ServerPlayer sp) {
        for (HearthRecord h : HearthData.get(sp.level().getServer()).hearthsOf(sp.getUUID())) {
            if (h.hasRoom(RoomType.LOOKOUT)) AtlasHooks.reveal(sp, h.pos, LOOKOUT_REVEAL);
        }
    }

    /** Whether Mira may tell this player a second rumour today (their home has a Lookout). */
    public static boolean secondRumour(ServerPlayer sp, HearthRecord h) {
        if (!h.hasRoom(RoomType.LOOKOUT)) return false;
        long day = PlayDays.current(sp.level().getServer());
        var hp = HearthService.player(sp);
        if (hp.rumourDay2 == day) return false;
        hp.rumourDay2 = day;
        return true;
    }

    // ---- the Workshop -------------------------------------------------------------------------------------------------------

    /** Essence a machine at {@code pos} pays for {@code amount}: a quarter less in (or in the wall of) a Workshop. */
    public static int machineCost(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 1) return amount;
        Optional<HearthRecord> h = HearthData.get(level.getServer()).containing(pos, level.dimension().identifier().toString());
        if (h.isEmpty()) return amount;
        for (Room r : h.get().rooms) {
            if (!r.types().contains(RoomType.WORKSHOP)) continue;
            if (r.contains(pos)) return (int) Math.ceil(amount * WORKSHOP_COST);
            for (var d : net.minecraft.core.Direction.values()) if (r.contains(pos.relative(d))) return (int) Math.ceil(amount * WORKSHOP_COST);
        }
        return amount;
    }
}
