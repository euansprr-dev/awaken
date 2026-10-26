package dev.awaken.hearth;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafting at home draws from your chests (SPEC §28.2, PLAN 4.6): a crafting table inside a Hearth you belong to opens
 * the vanilla crafting screen, but when the recipe book places a recipe the ingredients you don't carry are fetched
 * from chests, barrels and shulker boxes within {@link #REACH} blocks of the table. No mixin: the server's menu is a
 * {@link CraftingMenu} whose recipe placement first tops up your inventory (the client still opens the vanilla screen,
 * which always sends the placement, so a recipe the book shows in red because the chests hold its wood still crafts).
 */
public final class HomeCrafting {
    private HomeCrafting() {}

    public static final int REACH = 8;
    static final String TIP = "home_chests";

    static void init() {
        NeoForge.EVENT_BUS.addListener(HomeCrafting::onUse);
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel level) || e.getHand() != InteractionHand.MAIN_HAND) return;
        BlockPos pos = e.getPos();
        if (!level.getBlockState(pos).is(Blocks.CRAFTING_TABLE) || !HearthService.enabled()) return;
        // as vanilla: sneaking with something in hand uses the item, not the table
        if (sp.isSecondaryUseActive() && (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty())) return;
        if (HearthService.sharedAt(sp, pos).isEmpty()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new HomeMenu(id, inv, ContainerLevelAccess.create(level, pos), level, pos),
                Component.translatable("container.crafting")));
        sp.awardStat(Stats.INTERACT_WITH_CRAFTING_TABLE);
    }

    /** The crafting table's menu at home: the same slots, a placement that fetches from the chests first. */
    static final class HomeMenu extends CraftingMenu {
        private final ServerLevel level;
        private final BlockPos table;

        HomeMenu(int id, Inventory inv, ContainerLevelAccess access, ServerLevel level, BlockPos table) {
            super(id, inv, access);
            this.level = level;
            this.table = table;
        }

        @Override
        public RecipeBookMenu.PostPlaceAction handlePlacement(boolean useMaxItems, boolean allowDroppingItemsToClear, RecipeHolder<?> recipe,
                                                              ServerLevel lvl, Inventory inventory) {
            if (inventory.player instanceof ServerPlayer sp && fetch(sp, recipe.value(), useMaxItems)) {
                var hp = HearthService.player(sp);
                if (hp.tips.add(TIP)) dev.awaken.signal.Signal.toast(sp, Component.literal("Your chests nearby fed the recipe. At home, crafting reaches them."));
            }
            return super.handlePlacement(useMaxItems, allowDroppingItemsToClear, recipe, lvl, inventory);
        }

        /** Moves what the recipe lacks from the chests into the inventory (one craft, or a stack's worth). True if any came. */
        private boolean fetch(ServerPlayer sp, Recipe<?> recipe, boolean max) {
            PlacementInfo info = recipe.placementInfo();
            if (info.isImpossibleToPlace()) return false;
            int crafts = max ? 64 : 1;
            StackedItemContents have = new StackedItemContents();
            sp.getInventory().fillStackedContents(have);
            fillCraftSlotsStackedContents(have);
            if (have.canCraft(recipe, crafts, null)) return false;
            List<Container> stores = stores(level, table, sp);
            if (stores.isEmpty()) return false;
            // the ingredients as groups (four planks are one group of four), and what the player and the grid hold of each
            List<Ingredient> groups = new ArrayList<>();
            List<Integer> needs = new ArrayList<>();
            for (Ingredient ing : info.ingredients()) {
                int at = groups.indexOf(ing);
                if (at < 0) {
                    groups.add(ing);
                    needs.add(crafts);
                } else {
                    needs.set(at, needs.get(at) + crafts);
                }
            }
            boolean moved = false;
            for (int g = 0; g < groups.size(); g++) {
                Ingredient ing = groups.get(g);
                int missing = needs.get(g) - held(sp.getInventory(), ing) - held(craftSlots, ing);
                for (Container c : stores) {
                    if (missing <= 0) break;
                    for (int i = 0; i < c.getContainerSize() && missing > 0; i++) {
                        ItemStack st = c.getItem(i);
                        if (st.isEmpty() || !ing.test(st)) continue;
                        int take = Math.min(missing, st.getCount());
                        ItemStack part = st.copyWithCount(take);
                        if (!sp.getInventory().add(part)) {
                            take -= part.getCount(); // the inventory filled up: keep what didn't fit in the chest
                            missing = 0;
                        }
                        if (take > 0) {
                            st.shrink(take);
                            c.setChanged();
                            missing -= take;
                            moved = true;
                        }
                    }
                }
            }
            return moved;
        }

        private static int held(Container c, Ingredient ing) {
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (!st.isEmpty() && ing.test(st)) n += st.getCount();
            }
            return n;
        }
    }

    /** Chests, barrels and shulker boxes within reach of the table that this player may open, nearest first. */
    static List<Container> stores(ServerLevel level, BlockPos table, ServerPlayer sp) {
        List<BlockEntity> found = new ArrayList<>();
        int r = REACH;
        for (int cx = (table.getX() - r) >> 4; cx <= (table.getX() + r) >> 4; cx++) {
            for (int cz = (table.getZ() - r) >> 4; cz <= (table.getZ() + r) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - table.getX()) > r || Math.abs(p.getY() - table.getY()) > r || Math.abs(p.getZ() - table.getZ()) > r) continue;
                    if (!(be instanceof Container) || !(be instanceof BaseContainerBlockEntity bc) || !bc.canOpen(sp)) continue;
                    BlockState s = be.getBlockState();
                    if (s.is(Tags.BlockItems.CHESTS.block()) || s.is(Tags.BlockItems.BARRELS.block()) || be instanceof ShulkerBoxBlockEntity) found.add(be);
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(be -> be.getBlockPos().distSqr(table)));
        List<Container> out = new ArrayList<>(found.size());
        for (BlockEntity be : found) out.add((Container) be);
        return out;
    }
}
