package dev.awaken.above;

import dev.awaken.Awaken;
import dev.awaken.above.block.BrokenMachineBlock;
import dev.awaken.above.block.DoorCoreBlock;
import dev.awaken.above.block.LaunchRingFrameBlock;
import dev.awaken.above.block.LogTerminalBlock;
import dev.awaken.above.block.MembraneBlock;
import dev.awaken.above.block.PrecursorDoorBlock;
import dev.awaken.above.block.ReturnPadBlock;
import dev.awaken.above.block.VoidGlassBlock;
import dev.awaken.above.machine.MachineBlock;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.above.block.DoorCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** Every Above block and block entity type. Machines live in {@link #MACHINES}, one block per {@link MachineType}. */
public final class AboveBlocks {
    private AboveBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);

    private static BlockBehaviour.Properties metal(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_BLACK).strength(4.0f, 9.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK);
    }

    // ---- building blocks ------------------------------------------------------------------------------------------
    public static final DeferredBlock<Block> PLATING = BLOCKS.registerSimpleBlock("precursor_plating", AboveBlocks::metal);
    public static final DeferredBlock<Block> PANEL = BLOCKS.registerSimpleBlock("precursor_panel", AboveBlocks::metal);
    public static final DeferredBlock<Block> TRIM = BLOCKS.registerSimpleBlock("precursor_trim",
            p -> metal(p).mapColor(MapColor.GOLD));
    public static final DeferredBlock<Block> GLOW_PANEL = BLOCKS.registerSimpleBlock("precursor_glow_panel",
            p -> metal(p).mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 15).emissiveRendering((s) -> true));
    public static final DeferredBlock<Block> WARNING_LIGHT = BLOCKS.registerSimpleBlock("precursor_warning_light",
            p -> metal(p).mapColor(MapColor.COLOR_ORANGE).lightLevel(s -> 12).emissiveRendering((s) -> true));
    public static final DeferredBlock<VoidGlassBlock> VOID_GLASS = BLOCKS.registerBlock("precursor_void_glass", VoidGlassBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0f, 12.0f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, pos, e) -> false).isRedstoneConductor((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false));
    public static final DeferredBlock<MembraneBlock> MEMBRANE = BLOCKS.registerBlock("precursor_membrane", MembraneBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0f, 6.0f).sound(SoundType.AMETHYST).noCollision().noOcclusion()
                    .lightLevel(s -> 6).isValidSpawn((s, l, pos, e) -> false).isRedstoneConductor((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false).pushReaction(PushReaction.IMMOVEABLE));

    // ---- ruin blocks ----------------------------------------------------------------------------------------------
    public static final DeferredBlock<BrokenMachineBlock> BROKEN_MACHINE = BLOCKS.registerBlock("broken_machine", BrokenMachineBlock::new,
            p -> metal(p).strength(12.0f, 30.0f).lightLevel(s -> s.getValue(BrokenMachineBlock.SCANNED) ? 0 : 4).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<PrecursorDoorBlock> DOOR = BLOCKS.registerBlock("precursor_door", PrecursorDoorBlock::new,
            p -> metal(p).strength(-1.0f, 3_600_000.0f).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<DoorCoreBlock> DOOR_CORE = BLOCKS.registerBlock("precursor_door_core", DoorCoreBlock::new,
            p -> metal(p).strength(-1.0f, 3_600_000.0f).noLootTable().lightLevel(s -> 7).pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<LogTerminalBlock> LOG_TERMINAL = BLOCKS.registerBlock("precursor_log_terminal", LogTerminalBlock::new,
            p -> metal(p).strength(6.0f, 12.0f).noOcclusion().lightLevel(s -> 9));
    public static final DeferredBlock<LaunchRingFrameBlock> RING_FRAME = BLOCKS.registerBlock("launch_ring_frame", LaunchRingFrameBlock::new,
            p -> metal(p).lightLevel(s -> 5));
    public static final DeferredBlock<ReturnPadBlock> RETURN_PAD = BLOCKS.registerBlock("halo_return_pad", ReturnPadBlock::new,
            p -> metal(p).strength(-1.0f, 3_600_000.0f).noLootTable().noOcclusion().lightLevel(s -> 12));

    // ---- machines -------------------------------------------------------------------------------------------------
    public static final Map<MachineType, DeferredBlock<MachineBlock>> MACHINES = new EnumMap<>(MachineType.class);
    public static final DeferredBlock<MachineBlock> FABRICATOR;

    static {
        for (MachineType t : MachineType.values()) {
            MACHINES.put(t, BLOCKS.registerBlock(t.id, p -> new MachineBlock(p, t),
                    p -> metal(p).noOcclusion().lightLevel(s -> s.getValue(MachineBlock.LIT) ? (t == MachineType.REACTOR ? 13 : 7) : 0)));
        }
        FABRICATOR = BLOCKS.registerBlock("fabricator", p -> new MachineBlock(p, null),
                p -> metal(p).noOcclusion().lightLevel(s -> s.getValue(MachineBlock.LIT) ? 7 : 0));
    }

    public static MachineBlock machine(MachineType t) {
        return MACHINES.get(t).get();
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> MACHINE_BE = BLOCK_ENTITIES.register("precursor_machine",
            () -> {
                Block[] blocks = new Block[MACHINES.size() + 1];
                int i = 0;
                for (var b : MACHINES.values()) blocks[i++] = b.get();
                blocks[i] = FABRICATOR.get();
                return new BlockEntityType<>(MachineBlockEntity::new, blocks);
            });
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DoorCoreBlockEntity>> DOOR_CORE_BE = BLOCK_ENTITIES.register("precursor_door_core",
            () -> new BlockEntityType<>(DoorCoreBlockEntity::new, DOOR_CORE.get()));

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
