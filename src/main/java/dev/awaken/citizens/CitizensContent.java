package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.citizens.block.CityFireBlock;
import dev.awaken.citizens.block.LaundryBlock;
import dev.awaken.citizens.block.StreetLampBlock;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The citizens module's registrations: the citizen body (never saved; tracked to 80 blocks), the blocks citizens work
 * with (street lamps, laundry lines, the fire event's flames, cargo crates), the props they hold, their particles and
 * the city's bells.
 */
public final class CitizensContent {
    private CitizensContent() {}

    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CitizenBody>> CITIZEN = ENTITIES.registerEntityType("citizen",
            CitizenBody::new, MobCategory.MISC, b -> b.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(5).noSave().noSummon());

    /** Carillon's animals (M4 B3b): made and moved on the client only (citizens.client.CityCritters). */
    public static final DeferredHolder<EntityType<?>, EntityType<CityCritter>> CRITTER = ENTITIES.registerEntityType("city_critter",
            CityCritter::new, MobCategory.MISC, b -> b.sized(0.5f, 0.5f).clientTrackingRange(0).noSave().noSummon());

    public static final DeferredBlock<StreetLampBlock> STREET_LAMP = BLOCKS.registerBlock("street_lamp", StreetLampBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.LANTERN).noOcclusion().noLootTable()
                    .lightLevel(StreetLampBlock::light).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<LaundryBlock> LAUNDRY = BLOCKS.registerBlock("laundry", LaundryBlock::new,
            p -> p.mapColor(MapColor.WOOL).strength(0.2f).sound(SoundType.WOOL).noOcclusion().noCollision().noLootTable()
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<CityFireBlock> CITY_FIRE = BLOCKS.registerBlock("city_fire", CityFireBlock::new,
            p -> p.mapColor(MapColor.FIRE).instabreak().sound(SoundType.WOOL).noOcclusion().noCollision().noLootTable()
                    .lightLevel(CityFireBlock::light).replaceable().pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<Block> CARGO_CRATE = BLOCKS.registerSimpleBlock("cargo_crate",
            p -> p.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noLootTable());

    // props citizens hold (the third-person item in hand); never obtainable
    public static final DeferredItem<Item> LAMPLIGHTER_POLE = prop("lamplighter_pole");
    public static final DeferredItem<Item> SMITH_HAMMER = prop("smith_hammer");
    public static final DeferredItem<Item> MASON_TROWEL = prop("mason_trowel");
    public static final DeferredItem<Item> CRIER_SCROLL = prop("crier_scroll");
    public static final DeferredItem<Item> WATCH_HALBERD = prop("watch_halberd");
    public static final DeferredItem<Item> HAND_LANTERN = prop("hand_lantern");
    public static final DeferredItem<Item> BREAD_BASKET = prop("bread_basket");
    public static final DeferredItem<Item> LAUNDRY_BASKET = prop("laundry_basket");
    public static final DeferredItem<Item> LEDGER_BOOK = prop("ledger_book");
    public static final DeferredItem<Item> COURIER_SATCHEL = prop("courier_satchel");
    /** The crate on a porter's back (drawn from the cargo crate's block model). */
    public static final DeferredItem<Item> PORTER_CRATE = prop("porter_crate");
    /** (SPEC §28.6) the village hunter's kill slung from a pole on the walk home at dusk, and the shepherd's crook. */
    public static final DeferredItem<Item> CARCASS_POLE = prop("carcass_pole");
    public static final DeferredItem<Item> SHEPHERD_CROOK = prop("shepherd_crook");
    /** What a thief runs off with; a player who catches the thief gets it, and gives it back to its owners. */
    public static final DeferredItem<Item> STOLEN_BAG = ITEMS.registerSimpleItem("stolen_bag", p -> p.stacksTo(1));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CHIMNEY_SMOKE = particle("chimney_smoke", false);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FORGE_SPARK = particle("forge_spark", false);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAMP_FLARE = particle("lamp_flare", false);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_EMBER = particle("fire_ember", false);

    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_HOUR = sound("city.bell.hour");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_DISTRICT = sound("city.bell.district");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_ALARM = sound("city.bell.alarm");
    /** (SPEC §28.7) A village's alarm bell, heard to 256 blocks. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGE_ALARM = sound("village.alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANDBELL = sound("city.handbell");
    // (M4 B3b) the animals and the places that sound; the client plays them (citizens.client.CitySound, CitySounds)
    public static final DeferredHolder<SoundEvent, SoundEvent> PIGEON_FLUTTER = sound("city.pigeon.flutter"), PIGEON_COO = sound("city.pigeon.coo"),
            GULL_CALL = sound("city.gull.call"), CART_ROLL = sound("city.cart.roll"), CART_BELLS = sound("city.cart.bells"),
            ANVIL = sound("city.anvil"), BELLOWS = sound("city.bellows"), STALL = sound("city.stall"), BOAT = sound("city.boat"),
            SONGBIRD = sound("city.songbird"), FOUNTAIN = sound("city.fountain"), LAP = sound("city.lap"), MILL = sound("city.mill"),
            TAVERN = sound("city.tavern"), CARILLON_DAWN = sound("city.carillon.dawn"), CARILLON_NOON = sound("city.carillon.noon"),
            CARILLON_DUSK = sound("city.carillon.dusk");

    private static DeferredItem<Item> prop(String name) {
        return ITEMS.registerSimpleItem(name, p -> p.stacksTo(1));
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String name, boolean always) {
        return PARTICLES.register(name, () -> new SimpleParticleType(always));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> switch (name) {
            case "city.bell.hour" -> SoundEvent.createFixedRangeEvent(Awaken.id(name), 192);
            case "city.bell.district", "city.bell.alarm" -> SoundEvent.createFixedRangeEvent(Awaken.id(name), 112);
            default -> SoundEvent.createVariableRangeEvent(Awaken.id(name));
        });
    }

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
    }
}
