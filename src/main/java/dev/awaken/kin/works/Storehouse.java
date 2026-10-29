package dev.awaken.kin.works;

import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.kin.KinApi;
import dev.awaken.kin.KinKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Storehouse (SPEC §43.12; JOURNEY part 3, Euan's addition): one place to drop everything. You tip things into the
 * Drop and they pile up; the Totters take an armful at a time and shelve it in the room's chests and barrels, one bay for
 * each sort of thing, and paint a sign over a bay the first time something goes there. The Ledger desk finds anything:
 * a Totter fetches it to the desk, or a light shows its shelf. It only holds what you bring.
 */
public final class Storehouse {
    private Storehouse() {}

    /** The bays, in the order the Ledger lists them. */
    public enum Bay {
        STONE("Stone and earth"), WOOD("Wood"), METAL("Ore and metal"), GEMS("Gems"), FOOD("Food"), PLANTS("Plants and seed"),
        DROPS("Beast and monster drops"), GATE("Gate loot"), GEAR("Gear"), BELLS("Bells and leads"), ODD("Odd things");

        public final String label;

        Bay(String label) {
            this.label = label;
        }

        public static Bay of(ItemStack s) {
            Item i = s.getItem();
            String path = BuiltInRegistries.ITEM.getKey(i).getPath(), ns = BuiltInRegistries.ITEM.getKey(i).getNamespace();
            if (path.contains("bell") || path.contains("clapper") || path.contains("letter")) return BELLS;
            if (s.isDamageableItem() || s.has(DataComponents.EQUIPPABLE)) return GEAR;
            if (s.is(Tags.Items.ORES) || s.is(Tags.Items.RAW_MATERIALS) || s.is(Tags.Items.INGOTS) || s.is(Tags.Items.NUGGETS)
                    || s.is(Tags.Items.STORAGE_BLOCKS) || Block.byItem(i) instanceof dev.awaken.delve.LodeHeartBlock) return METAL;
            if (s.is(Tags.Items.GEMS) || s.is(Items.AMETHYST_SHARD) || s.is(Items.QUARTZ) || s.is(Items.ECHO_SHARD) || s.is(Items.PRISMARINE_CRYSTALS)) return GEMS;
            if (s.has(DataComponents.FOOD)) return FOOD;
            if (s.is(Tags.Items.SEEDS) || s.is(Tags.Items.CROPS) || s.is(ItemTags.SAPLINGS) || s.is(Tags.BlockItems.FLOWERS.item()) || s.is(ItemTags.LEAVES)
                    || s.is(Items.SUGAR_CANE) || s.is(Items.BAMBOO) || s.is(Items.KELP) || s.is(Items.CACTUS)) return PLANTS;
            if (s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS) || s.is(Items.STICK) || s.is(ItemTags.WOODEN_SLABS) || s.is(ItemTags.WOODEN_STAIRS)
                    || s.is(ItemTags.WOODEN_FENCES)) return WOOD;
            if (s.is(Tags.Items.BONES) || s.is(Tags.Items.STRINGS) || s.is(Tags.Items.LEATHERS) || s.is(Tags.Items.FEATHERS) || s.is(Tags.Items.SLIME_BALLS)
                    || s.is(Tags.Items.ENDER_PEARLS) || s.is(Tags.Items.GUNPOWDERS) || s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE)
                    || s.is(Items.BONE_MEAL) || s.is(Items.PHANTOM_MEMBRANE) || s.is(ItemTags.WOOL)) return DROPS;
            if (ns.equals(dev.awaken.Awaken.MODID) && !(i instanceof BlockItem)) return GATE;
            if (i instanceof BlockItem bi) {
                BlockState b = bi.getBlock().defaultBlockState();
                if (b.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || b.is(net.minecraft.tags.BlockTags.DIRT) || s.is(Tags.Items.COBBLESTONES)
                        || s.is(Tags.Items.SANDS) || s.is(Tags.Items.GRAVELS) || s.is(Tags.Items.STONES) || b.is(net.minecraft.tags.BlockTags.STONE_BRICKS)
                        || b.is(Blocks.CLAY) || b.is(Blocks.NETHERRACK) || b.is(Blocks.CALCITE) || b.is(Blocks.TUFF)) return STONE;
            }
            return ODD;
        }
    }

    // ---- dropping ------------------------------------------------------------------------------------------------------

    static void tip(ServerLevel level, ServerPlayer p, DropBlockEntity be, InteractionHand hand) {
        ItemStack held = p.getItemInHand(hand);
        if (be.full()) {
            say(p, "[The Drop's heaped as high as it goes. The Totters need to shelve some first.]");
            return;
        }
        ItemStack in = held.copy();
        ItemStack left = be.add(in);
        land(level, be.getBlockPos(), in, 1F);
        p.setItemInHand(hand, left);
    }

    /** "Empty my bag": everything but the hotbar, armour and off hand. */
    static void emptyBag(ServerLevel level, ServerPlayer p, DropBlockEntity be) {
        Inventory inv = p.getInventory();
        int n = 0;
        ItemStack last = ItemStack.EMPTY;
        for (int i = Inventory.getSelectionSize(); i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (be.full()) break;
            ItemStack left = be.add(s.copy());
            if (left.getCount() == s.getCount()) continue;
            last = s.copy();
            inv.setItem(i, left);
            n++;
            if (n <= 6) land(level, be.getBlockPos(), s, 0.6F + 0.08F * n);
        }
        inv.setChanged();
        if (n == 0) {
            say(p, be.full() ? "[The Drop's heaped as high as it goes. The Totters need to shelve some first.]"
                    : "[Your bag's empty already. Your hotbar and what you're wearing always stay with you.]");
            return;
        }
        say(p, "[You tipped " + n + (n == 1 ? " stack" : " stacks") + " into the Drop. Your hotbar and what you're wearing stay with you."
                + (be.full() ? " The Drop's full now." : "") + "]");
    }

    static void status(ServerLevel level, ServerPlayer p, DropBlockEntity be) {
        Optional<BlockPos> home = dev.awaken.hearth.Hearth.anchor(p);
        int totters = home.map(a -> KinApi.loaded(level, a, KinKind.TOTTER).size()).orElse(0);
        int n = be.size();
        String what = n == 0 ? "The Drop's empty." : "The Drop holds " + n + (n == 1 ? " stack." : " stacks.");
        String who = totters == 0 ? " No Totter lives here to shelve it yet. Totters live on woods' floors."
                : totters == 1 ? " One Totter is shelving it." : " " + totters + " Totters are shelving it.";
        say(p, "[" + what + who + " Sneak and use it with an empty hand to tip your whole bag in.]");
    }

    /** Each kind of thing lands with its own sound. */
    static void land(ServerLevel level, BlockPos at, ItemStack s, float pitch) {
        Bay b = Bay.of(s);
        SoundEvent e = switch (b) {
            case METAL, GEAR -> WorksContent.LAND_METAL.get();
            case WOOD -> WorksContent.LAND_WOOD.get();
            case GEMS, BELLS -> WorksContent.LAND_GEM.get();
            case STONE -> WorksContent.LAND_STONE.get();
            default -> WorksContent.LAND_SOFT.get();
        };
        level.playSound(null, at, e, SoundSource.BLOCKS, 0.8F, pitch);
    }

    // ---- bays ----------------------------------------------------------------------------------------------------------

    /** The bay a container is (by the first thing in it), or null while it's empty. */
    static @Nullable Bay bayOf(Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) return Bay.of(c.getItem(i));
        return null;
    }

    static boolean fits(Container c, ItemStack s) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack t = c.getItem(i);
            if (t.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(t, s) && t.getCount() < t.getMaxStackSize()) return true;
        }
        return false;
    }

    /** Where a stack goes: a bay of its sort with room, else an empty container (a new bay), else null (the hall's full). */
    public static @Nullable BlockPos bayFor(ServerLevel level, Room store, ItemStack s) {
        Bay want = Bay.of(s);
        BlockPos empty = null;
        for (BlockPos p : WorkRooms.containers(level, store)) {
            Container c = WorkRooms.container(level, p);
            if (c == null) continue;
            Bay b = bayOf(c);
            if (b == want && fits(c, s)) return p;
            if (b == null && empty == null) empty = p;
        }
        return empty;
    }

    /** A Totter puts an armful away: what doesn't fit comes back. A new bay gets its sign, a bay that fills a chirp. */
    public static ItemStack shelve(ServerLevel level, BlockPos at, ItemStack s) {
        Container c = WorkRooms.container(level, at);
        if (c == null) return s;
        boolean fresh = bayOf(c) == null;
        ItemStack left = KinApi.put(c, s);
        level.playSound(null, at, WorksContent.SHELVED.get(), SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (fresh) sign(level, at, Bay.of(s));
        if (!fits(c, new ItemStack(Items.STONE))) level.playSound(null, at, WorksContent.BAY_FULL.get(), SoundSource.NEUTRAL, 0.8F, 1.1F);
        return left;
    }

    /** A sign over a new bay, on the wall above it, if there's a wall to hang it on. */
    private static void sign(ServerLevel level, BlockPos container, Bay bay) {
        BlockPos at = container.above();
        if (!level.getBlockState(at).isAir()) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos wall = at.relative(d);
            if (!level.getBlockState(wall).isFaceSturdy(level, wall, d.getOpposite())) continue;
            level.setBlock(at, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, d.getOpposite()), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof SignBlockEntity sb) {
                String[] words = bay.label.split(" ");
                SignText.Mutable t = SignText.EMPTY.asMutable();
                StringBuilder line = new StringBuilder();
                int row = 1;
                for (String w : words) {
                    if (line.length() + w.length() > 14 && !line.isEmpty() && row < 3) {
                        t.setLine(row++, Component.literal(line.toString()));
                        line.setLength(0);
                    }
                    if (!line.isEmpty()) line.append(' ');
                    line.append(w);
                }
                t.setLine(row, Component.literal(line.toString()));
                sb.setText(t.asImmutable().withColor(net.minecraft.world.item.DyeColor.BROWN), SignTextSlot.FRONT);
                sb.setWaxed(true);
            }
            return;
        }
    }

    // ---- the Ledger ----------------------------------------------------------------------------------------------------

    /** The Storehouse room a desk stands in, at the home whose land it's on. */
    static @Nullable Room storeAt(ServerLevel level, BlockPos pos) {
        return dev.awaken.hearth.HearthService.data(level.getServer()).containing(pos, dev.awaken.hearth.HearthService.OVERWORLD)
                .flatMap(h -> h.roomAt(pos, RoomType.STOREHOUSE)).orElse(null);
    }

    /** What the Storehouse holds, by item: its count and its bay. The Drop's heap counts as "on the heap". */
    public record Line(Item item, int count, String bay) {}

    static List<Line> holdings(ServerLevel level, Room store) {
        Map<Item, int[]> counts = new LinkedHashMap<>();
        Map<Item, String> bays = new HashMap<>();
        for (BlockPos p : WorkRooms.containers(level, store)) {
            Container c = WorkRooms.container(level, p);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty()) continue;
                counts.computeIfAbsent(s.getItem(), k -> new int[1])[0] += s.getCount();
                bays.putIfAbsent(s.getItem(), Bay.of(s).label);
            }
        }
        BlockPos drop = WorkRooms.heart(level, store, DropBlock.class);
        if (drop != null && level.getBlockEntity(drop) instanceof DropBlockEntity be)
            for (ItemStack s : be.heap()) {
                counts.computeIfAbsent(s.getItem(), k -> new int[1])[0] += s.getCount();
                bays.putIfAbsent(s.getItem(), "On the heap");
            }
        List<Line> out = new ArrayList<>();
        counts.forEach((i, n) -> out.add(new Line(i, n[0], bays.get(i))));
        out.sort((a, b) -> {
            int c = Integer.compare(bayOrder(a.bay()), bayOrder(b.bay()));
            return c != 0 ? c : a.item().getName(new ItemStack(a.item())).getString().compareTo(b.item().getName(new ItemStack(b.item())).getString());
        });
        return out;
    }

    private static int bayOrder(String label) {
        for (Bay b : Bay.values()) if (b.label.equals(label)) return b.ordinal();
        return Bay.values().length;
    }

    static void ledger(ServerLevel level, ServerPlayer p, BlockPos desk) {
        Room store = storeAt(level, desk);
        if (store == null) {
            say(p, "[The Ledger's blank. It keeps count only in a Storehouse: a room with the Drop and this desk.]");
            return;
        }
        List<WorksNet.LedgerRow> rows = new ArrayList<>();
        for (Line l : holdings(level, store)) rows.add(new WorksNet.LedgerRow(BuiltInRegistries.ITEM.getKey(l.item()).toString(), l.count(), l.bay()));
        WorksNet.send(p, new WorksNet.Ledger(desk, rows));
    }

    /** From the Ledger: a Totter fetches the thing to the desk, or (show) a light rises over its shelf. */
    static void fetch(ServerPlayer p, BlockPos desk, String itemId, boolean show) {
        ServerLevel level = p.level();
        if (p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(desk)) > 64 || !(level.getBlockState(desk).getBlock() instanceof LedgerDeskBlock)) return;
        Room store = storeAt(level, desk);
        Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
        if (store == null || item == Items.AIR) return;
        BlockPos shelf = null;
        for (BlockPos c : WorkRooms.containers(level, store)) {
            Container ct = WorkRooms.container(level, c);
            if (ct != null && ct.hasAnyOf(java.util.Set.of(item))) {
                shelf = c;
                break;
            }
        }
        if (shelf == null) {
            say(p, "[It isn't on a shelf. If it's on the heap, the Totters will have it shelved soon.]");
            return;
        }
        if (show) {
            level.sendParticles(p, ParticleTypes.END_ROD, true, false, shelf.getX() + 0.5, shelf.getY() + 1.4, shelf.getZ() + 0.5, 24, 0.15, 0.6, 0.15, 0.02);
            level.sendParticles(p, ParticleTypes.WAX_ON, true, false, shelf.getX() + 0.5, shelf.getY() + 0.8, shelf.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.5);
            say(p, "[It's in the " + Bay.of(new ItemStack(item)).label.toLowerCase(java.util.Locale.ROOT) + " bay, where the light is.]");
            return;
        }
        if (dev.awaken.kin.KinWorks.fetch(level, p, shelf, desk, item)) {
            say(p, "[A Totter's gone to fetch it to the desk.]");
            return;
        }
        // nobody free: it's taken off the shelf for you
        Container ct = WorkRooms.container(level, shelf);
        if (ct == null) return;
        ItemStack got = takeAll(ct, item);
        if (got.isEmpty()) return;
        ItemEntity e = new ItemEntity(level, desk.getX() + 0.5, desk.getY() + 1.0, desk.getZ() + 0.5, got, 0, 0.05, 0);
        e.setNoPickUpDelay();
        level.addFreshEntity(e);
        say(p, "[No Totter was free, so it's been set out on the desk for you.]");
    }

    /** Up to a stack of an item off a shelf. */
    public static ItemStack takeAll(Container c, Item item) {
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.is(item)) continue;
            if (out.isEmpty()) out = s.split(s.getCount());
            else if (ItemStack.isSameItemSameComponents(out, s)) {
                int n = Math.min(s.getCount(), out.getMaxStackSize() - out.getCount());
                out.grow(n);
                s.shrink(n);
            }
            if (out.getCount() >= out.getMaxStackSize()) break;
        }
        c.setChanged();
        return out;
    }

    static void say(ServerPlayer p, String line) {
        p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
