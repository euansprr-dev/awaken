package dev.awaken.village.trouble;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What a village's troubles put in the world (SPEC §28.7): the person you bring home ({@link Rescuee}), the marks of a
 * trail (drag marks and torn cloth), the carved token on a family's door, scaffolding's masons' marks, the keepsake a
 * village gives, the bell you haul up its tower, and the world option {@code awaken:calm_villages}.
 */
public final class TroubleContent {
    private TroubleContent() {}

    static final DeferredRegister<GameRule<?>> RULES = DeferredRegister.create(Registries.GAME_RULE, Awaken.MODID);
    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    /**
     * {@code /gamerule awaken:calm_villages true} (and the world screen's game rules): no troubles in villages. Festivals and
     * growth go on (SPEC §28.0 decision 6).
     */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> CALM_VILLAGES = RULES.register("calm_villages",
            () -> new GameRule<>(GameRuleCategory.MISC, GameRuleType.BOOL, BoolArgumentType.bool(), (visitor, rule) -> visitor.visitBoolean(rule),
                    Codec.BOOL, b -> b ? 1 : 0, false, FeatureFlagSet.of()));

    /** Someone you bring home: bound in a lair, on your back, following you, then back with their family. Saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<Rescuee>> RESCUEE = ENTITIES.registerEntityType("village_rescuee", Rescuee::new,
            MobCategory.MISC, b -> b.sized(0.6f, 1.8f).eyeHeight(1.55f).clientTrackingRange(10));

    /** Scuffed ground where someone was dragged (the Taken's trail). No drops; the trouble takes it away. */
    public static final DeferredBlock<TrailMark> DRAG_MARKS = BLOCKS.registerBlock("drag_marks", TrailMark::new,
            p -> p.mapColor(MapColor.DIRT).sound(SoundType.ROOTED_DIRT).instabreak().noCollision().noOcclusion().noLootTable()
                    .replaceable().pushReaction(PushReaction.POPPED));
    /** A strip of someone's clothes caught on the way (the Taken's and Gone Missing's clues). */
    public static final DeferredBlock<TrailMark> TORN_CLOTH = BLOCKS.registerBlock("torn_cloth", TrailMark::new,
            p -> p.mapColor(MapColor.WOOL).sound(SoundType.WOOL).instabreak().noCollision().noOcclusion().noLootTable()
                    .pushReaction(PushReaction.POPPED));
    /** The family's thanks, carved and nailed by their door: your mark (SPEC §28.7, the Taken's lasting change). */
    public static final DeferredBlock<CarvedToken> CARVED_TOKEN = BLOCKS.registerBlock("carved_token", CarvedToken::new,
            p -> p.mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(1.0f).noCollision().noOcclusion().noLootTable());

    /** A keepsake a village gives (its name and words on the stack). */
    public static final DeferredItem<Keepsake> KEEPSAKE = ITEMS.registerItem("keepsake", Keepsake::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    /** A village's bell, cast new at its smith from bell bronze or taken back from a camp: carried up the tower and hung. */
    public static final DeferredItem<VillageBell> VILLAGE_BELL = ITEMS.registerItem("village_bell", VillageBell::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    /** The herb remedy that brings the Hollowed back (the Grey Fields). */
    public static final DeferredItem<Item> GREY_REMEDY = ITEMS.registerSimpleItem("grey_remedy", p -> p.stacksTo(16));
    /** The carved token in hand (creative and tests only: it's placed by the family). */
    public static final DeferredItem<BlockItem> CARVED_TOKEN_ITEM = ITEMS.registerSimpleBlockItem(CARVED_TOKEN);

    public static void register(IEventBus modBus) {
        RULES.register(modBus);
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(RESCUEE.get(), Rescuee.createAttributes().build()));
    }

    /** Whether troubles are off in this world (festivals and growth go on). */
    public static boolean calm(ServerLevel level) {
        return level.getGameRules().get(CALM_VILLAGES.get());
    }
}
