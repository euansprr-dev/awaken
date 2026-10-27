package dev.awaken.anims.client;

import dev.awaken.anims.AnimsClips;
import dev.awaken.anims.client.Reaches.Key;
import dev.awaken.anims.client.Reaches.Touch;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * Touches (Hands P4, P4.1, P6; SPEC §24): hand moments on the things you use, from the game's own interaction events,
 * so every module's blocks get them by what they are. The hand goes to the thing and does it there ({@link Reaches}): a
 * palm flat on a door where you clicked it, pushing on through (vanilla's door is open by then), fingers closed on a lever's handle pulling it through, a finger on a button,
 * finger and thumb on a stem and the flower coming away in the hand, a hand stroking a creature's flank, both hands on
 * a chest's lid (its screen waits {@link #LID_WAIT} ticks for them), a palm pressed to a stone (lit by it), the Forge
 * hammered, food laid on a campfire, a grindstone cranked, a page turned, a notice torn off. The action itself never
 * waits: the hands animate what already happened. A swing cuts them. With the main hand full the off hand does it; with
 * both full, nothing.
 */
final class Touches {
    private Touches() {}

    private static final Identifier FORGE = Identifier.fromNamespaceAndPath("awaken", "forge");
    /** Awaken's stones a palm is pressed to (by id: each is another module's block). */
    private static final java.util.Set<String> PALM_STONES = java.util.Set.of("waystone", "conduit", "ascent_stone", "hearth_anchor",
            "rest_shrine", "stillpoint_shrine", "gate_entrance", "gate_seal", "gate_rune");
    /** Ticks a chest's screen waits for the hands to reach its lid. */
    static final int LID_WAIT = 4;

    private static int lastAt = -100;
    private static int ticks;
    private static final boolean TRACE = System.getProperty("awaken.autopilot") != null;
    private static @Nullable Screen held;
    private static int heldUntil, lidAt = -100;

    // ---- the touches (s across to your right, u up, n out toward you; blocks from where the hand meets the thing) -----

    private static Touch t(String name, float apart, int itemFrom, int itemTo, int glowFrom, int glowTo, Key... keys) {
        return new Touch(name, keys, apart, itemFrom, itemTo, glowFrom, glowTo);
    }

    private static Touch t(String name, Key... keys) {
        return t(name, 0, -1, -1, -1, -1, keys);
    }

    private static Key at(int t, float s, float u, float n) {
        return Key.at(t, s, u, n);
    }

    private static Key rest(int t) {
        return Key.rest(t);
    }

    /** A door, trapdoor or gate: the palm meets the wood and pushes it through. */
    static final Touch PUSH = t(AnimsClips.PUSH, rest(0), at(2, 0, 0, 0.05F), at(4, 0, 0, -0.04F), at(8, 0, 0, -0.2F), rest(12));
    /** A lever: the handle gripped and pulled down through its throw (thrown up when it's down). */
    static final Touch LEVER_DOWN = t(AnimsClips.LEVER, rest(0), at(3, 0, 0, 0.03F), at(6, 0, -0.2F, 0.07F), at(8, 0, -0.21F, 0.07F), rest(12));
    static final Touch LEVER_UP = t(AnimsClips.LEVER, rest(0), at(3, 0, 0, 0.03F), at(6, 0, 0.2F, 0.07F), at(8, 0, 0.21F, 0.07F), rest(12));
    /** A button: a finger in and out. */
    static final Touch BUTTON = t(AnimsClips.BUTTON, rest(0), at(3, 0, 0, 0.05F), at(4, 0, 0, -0.01F), at(6, 0, 0, 0.04F), rest(9));
    /** A plant: finger and thumb on the stem, a tug, the flower lifted to you in the hand. */
    static final Touch PLUCK = t(AnimsClips.PLUCK, 0, 3, 15, -1, -1, rest(0), at(3, 0, 0, 0.02F), at(5, 0, 0.05F, 0.02F),
            at(9, 0, 0.42F, 0.45F), at(12, 0.04F, 0.4F, 0.5F), rest(15));
    /** A creature: the hand on its flank, stroking it twice. */
    static final Touch PET = t(AnimsClips.PET, rest(0), at(4, 0, 0, 0.03F), at(7, 0.15F, 0.02F, 0.03F), at(10, -0.1F, 0, 0.03F),
            at(13, 0.12F, 0.02F, 0.03F), rest(17));
    /** A chest, barrel or cache: both hands under the lid's edge, lifting. */
    static final Touch LID = t(AnimsClips.LID, 0.3F, -1, -1, -1, -1, rest(0), at(3, 0, -0.03F, 0.04F), at(5, 0, 0.1F, 0.02F), rest(9));
    /** A stone: the palm pressed flat and held while the light answers (the hand lit by it). */
    static final Touch PALM = t(AnimsClips.PALM, 0, -1, -1, 5, 17, rest(0), at(3, 0, 0, 0.06F), at(5, 0, 0, -0.01F),
            at(12, 0, 0, -0.02F), rest(17));
    /** The Forge, an anvil, a smithing table: three strikes. */
    static final Touch HAMMER = t(AnimsClips.HAMMER, rest(0), at(3, 0, 0.3F, 0.12F), at(5, 0, 0.02F, 0.02F), at(8, 0, 0.3F, 0.12F),
            at(10, 0, 0.02F, 0.02F), at(13, 0, 0.3F, 0.12F), at(15, 0, 0.02F, 0.02F), rest(19));
    /** A campfire: the food in the hand laid on the grate, the hand drawn back from the heat. */
    static final Touch LAY = t(AnimsClips.LAY, 0, 0, 6, -1, -1, rest(0), at(4, 0, 0.14F, 0.04F), at(6, 0, 0.03F, 0.02F),
            at(9, 0.05F, 0.25F, 0.3F), rest(12));
    /** A grindstone: the handle cranked round once. */
    static final Touch CRANK;
    /** A lectern's or an enchanting table's book: a page turned, right to left. */
    static final Touch PAGE = t(AnimsClips.PAGE, rest(0), at(3, 0.12F, 0.02F, 0.02F), at(6, -0.12F, 0.06F, 0.02F),
            at(7, -0.14F, 0.03F, 0.02F), rest(10));
    /** A stonecutter, loom, cartography table or brewing stand: both hands set to the work. */
    static final Touch SET = t(AnimsClips.SET, 0.28F, -1, -1, -1, -1, rest(0), at(3, 0, 0.06F, 0.02F), at(5, 0, 0, 0.01F), rest(8));
    /** A notice board: a notice pinched, torn off and brought to you. */
    static final Touch TEAR = t(AnimsClips.TEAR, 0, 5, 12, -1, -1, rest(0), at(3, 0, 0, 0.03F), at(5, 0, 0, 0.03F),
            at(8, 0.05F, -0.12F, 0.35F), rest(12));

    static {
        Key[] k = new Key[9];
        k[0] = rest(0);
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI / 3; // once round, from the handle's top
            k[i + 1] = at(3 + 2 * i, (float) (0.1 * Math.sin(a)), (float) (0.1 * Math.cos(a)), 0.03F);
        }
        k[8] = rest(18);
        CRANK = t(AnimsClips.CRANK, k);
    }

    // ---- when -------------------------------------------------------------------------------------------------------

    private static void trace(String what) {
        if (TRACE) dev.awaken.Awaken.LOG.info("[anims] touch: {}", what);
    }

    static void tick() {
        ticks++;
        Reaches.tick();
        Minecraft mc = Minecraft.getInstance();
        if (held != null && ticks >= heldUntil) {
            Screen s = held;
            held = null;
            // the lid is up: the chest's screen, unless it was closed or another took its place meanwhile
            if (mc.player != null && mc.gui.screen() == null && s instanceof AbstractContainerScreen<?> cs && mc.player.containerMenu == cs.getMenu()) {
                mc.gui.setScreen(s);
            }
        }
    }

    private static boolean on(LocalPlayer p) {
        return FirstPerson.touchesOn.getAsBoolean() && Minecraft.getInstance().options.getCameraType().isFirstPerson() && !p.isSpectator();
    }

    private static boolean rightMain(LocalPlayer p) {
        return p.getMainArm() != HumanoidArm.LEFT;
    }

    /** Starts a touch with the free hand (the main, else the off one); a two-handed one only with both free. */
    private static void play(LocalPlayer p, Touch t, Reaches.Anchor a) {
        if (ticks - lastAt < 4) return; // a held click repeats the event; once is enough
        boolean mainFree = p.getMainHandItem().isEmpty(), offFree = p.getOffhandItem().isEmpty() && !FirstPerson.offHandShows.getAsBoolean(), rm = rightMain(p);
        trace(t.name() + " (main " + p.getMainHandItem() + ", off " + p.getOffhandItem() + ")");
        if (t.both()) {
            if (!mainFree || !offFree) return;
            lastAt = ticks;
            Reaches.start(t, a, true, true, ItemStack.EMPTY);
            return;
        }
        if (!mainFree && !offFree) return;
        lastAt = ticks;
        boolean right = mainFree == rm; // the main hand's side when it's free, else the other
        Reaches.start(t, a, right, !right, held(t, p));
    }

    private static ItemStack held(Touch t, LocalPlayer p) {
        if (t == TEAR) return new ItemStack(Items.PAPER);
        return ItemStack.EMPTY;
    }

    static void rightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        trace("right click " + e.getLevel().isClientSide() + " " + e.getHand() + " " + e.getLevel().getBlockState(e.getPos()));
        if (!e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || e.getEntity() != p || !on(p)) return;
        Level level = e.getLevel();
        BlockPos pos = e.getPos().immutable();
        BlockState s = level.getBlockState(pos);
        Vec3 hit = e.getHitVec().getLocation();
        if (s.getBlock() instanceof CampfireBlock && level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(p.getMainHandItem())) {
            // the hand holding the food lays it on the grate
            if (ticks - lastAt < 4) return;
            lastAt = ticks;
            trace(AnimsClips.LAY);
            Reaches.start(LAY, fixed(pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5), rightMain(p), !rightMain(p), p.getMainHandItem());
            return;
        }
        if (p.isShiftKeyDown() && !p.getMainHandItem().isEmpty()) return; // a sneak-use places or uses the item
        Touch t = forUse(s);
        if (t == null) return;
        if (t == LID) {
            lidAt = ticks;
            play(p, t, lid(level, pos));
        } else {
            play(p, t, fixed(hit.x, hit.y, hit.z));
        }
    }

    private static @Nullable Touch forUse(BlockState s) {
        Block b = s.getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);
        if (id.getNamespace().equals("awaken")) {
            String path = id.getPath();
            if (PALM_STONES.contains(path)) return PALM;
            if (path.equals("spire_board")) return TEAR;
            if (path.endsWith("_cache")) return LID;
        }
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return PUSH;
        if (b instanceof LeverBlock) return s.getValue(LeverBlock.POWERED) ? LEVER_UP : LEVER_DOWN;
        if (b instanceof ButtonBlock) return BUTTON;
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof EnderChestBlock || b instanceof ShulkerBoxBlock) return LID;
        if (b instanceof SweetBerryBushBlock || b instanceof CaveVinesBlock) return PLUCK;
        if (s.is(Blocks.LODESTONE) || b instanceof RespawnAnchorBlock || b instanceof BeaconBlock) return PALM;
        if (b instanceof GrindstoneBlock) return CRANK;
        if (b instanceof LecternBlock || b instanceof EnchantingTableBlock) return PAGE;
        if (b instanceof SmithingTableBlock || b instanceof AnvilBlock || FORGE.equals(id)) return HAMMER;
        if (b instanceof StonecutterBlock || b instanceof LoomBlock || b instanceof CartographyTableBlock || b instanceof BrewingStandBlock) {
            return SET;
        }
        return null;
    }

    /** Breaking a plant that breaks at a touch (a flower, a fern, grass, a mushroom: the Living Wilds' herbs): plucked. */
    static void leftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (!e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || e.getEntity() != p || !on(p)) return;
        BlockState s = e.getLevel().getBlockState(e.getPos());
        if (!s.isAir() && s.getDestroySpeed(e.getLevel(), e.getPos()) == 0 && s.getCollisionShape(e.getLevel(), e.getPos()).isEmpty()
                && s.getFluidState().isEmpty()) {
            BlockPos pos = e.getPos();
            AABB box = shapeBox(s.getShape(e.getLevel(), pos), pos);
            // the stem, a little above the ground; the flower is in the hand once it's pinched
            Reaches.Anchor a = fixed(box.getCenter().x, box.minY + Math.min(0.25, box.getYsize() * 0.5), box.getCenter().z);
            if (ticks - lastAt < 4) return;
            boolean mainFree = p.getMainHandItem().isEmpty(), offFree = p.getOffhandItem().isEmpty() && !FirstPerson.offHandShows.getAsBoolean();
            if (!mainFree && !offFree) return;
            lastAt = ticks;
            trace(PLUCK.name());
            boolean right = mainFree == rightMain(p);
            ItemStack flower = new ItemStack(s.getBlock().asItem());
            Reaches.start(PLUCK, a, right, !right, flower);
        }
    }

    /** An animal, with nothing in the hand: stroked (with food in it, vanilla feeds it). */
    static void entityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || e.getEntity() != p || !on(p) || !(e.getTarget() instanceof Animal)) return;
        if (p.getMainHandItem().isEmpty()) play(p, PET, flank(e.getTarget()));
    }

    /** A swing cuts any touch (the hands are needed). */
    static void interactionKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Reaches.stop();
        for (String c : AnimsClips.TOUCHES) {
            FirstPerson.stop(c);
            FirstPerson.stop(c + "_off");
        }
    }

    /**
     * A chest's screen just after its lid was touched waits a few ticks for the hands to reach the lid (the menu is
     * open already: the server's answer is never held). A book opening (a written book, a book and quill, the Dream
     * Journal): a page turned as it opens.
     */
    static void screenOpening(ScreenEvent.Opening e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        Screen s = e.getNewScreen();
        if (s instanceof AbstractContainerScreen<?> && held == null && ticks - lidAt <= 2 && LID.name().equals(Reaches.playing())) {
            held = s;
            heldUntil = lidAt + LID_WAIT;
            e.setCanceled(true);
            return;
        }
        if (!on(p)) return;
        if (s instanceof BookViewScreen || s instanceof BookEditScreen || s.getClass().getSimpleName().contains("Journal")) {
            if (ticks - lastAt < 4) return;
            lastAt = ticks;
            if (p.getMainHandItem().isEmpty() || p.getMainHandItem().has(DataComponents.WRITTEN_BOOK_CONTENT)
                    || p.getMainHandItem().has(DataComponents.WRITABLE_BOOK_CONTENT)) {
                FirstPerson.play(AnimsClips.PAGE);
            }
        }
    }

    // ---- where the hand meets the thing -----------------------------------------------------------------------------

    private static Reaches.Anchor fixed(double x, double y, double z) {
        return out -> {
            out[0] = x;
            out[1] = y;
            out[2] = z;
            return true;
        };
    }

    private static AABB shapeBox(VoxelShape shape, BlockPos pos) {
        return shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
    }

    /** A lid's front edge: the face toward you, just under the top. */
    private static Reaches.Anchor lid(Level level, BlockPos pos) {
        return out -> {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) return false;
            AABB b = shapeBox(s.getShape(level, pos), pos);
            Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
            double cx = b.getCenter().x, cz = b.getCenter().z, dx = eye.x - cx, dz = eye.z - cz;
            if (Math.abs(dx) >= Math.abs(dz)) {
                out[0] = dx > 0 ? b.maxX : b.minX;
                out[2] = cz;
            } else {
                out[0] = cx;
                out[2] = dz > 0 ? b.maxZ : b.minZ;
            }
            out[1] = b.maxY - 0.06;
            return true;
        };
    }

    /** A creature's flank: the side of its body toward you, two thirds up, followed as it moves. */
    private static Reaches.Anchor flank(Entity en) {
        return out -> {
            if (!en.isAlive() || en.isRemoved()) return false;
            AABB b = en.getBoundingBox();
            Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
            out[0] = Math.clamp(eye.x, b.minX, b.maxX);
            out[1] = b.minY + b.getYsize() * 0.66;
            out[2] = Math.clamp(eye.z, b.minZ, b.maxZ);
            return true;
        };
    }

    /** A stance: eating a meal from a bowl, the free hand comes up under the bowl. And a palm on a stone glows. */
    static void viewPose(float pt, FirstPerson.View v) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.isUsingItem() || !FirstPerson.touchesOn.getAsBoolean()) return;
        ItemStack using = p.getUseItem();
        if (using.getUseAnimation() != ItemUseAnimation.EAT && using.getUseAnimation() != ItemUseAnimation.DRINK) return;
        UseRemainder r = using.get(DataComponents.USE_REMAINDER);
        if (r == null || r.convertInto().item().value() != Items.BOWL) return;
        boolean mainUses = p.getUsedItemHand() == InteractionHand.MAIN_HAND;
        float k = Math.min(1F, (p.getTicksUsingItem() + pt) / 5F);
        float side = (p.getMainArm() == HumanoidArm.LEFT) == mainUses ? 1 : -1; // the free hand's side
        float[] fist = mainUses ? v.fistOff : v.fistMain;
        if ((mainUses ? p.getOffhandItem() : p.getMainHandItem()).isEmpty()) FirstPerson.View.fist(fist, k, side * 0.04F, -0.46F, -0.72F);
    }

    static void reset() {
        held = null;
        Reaches.reset();
    }
}
