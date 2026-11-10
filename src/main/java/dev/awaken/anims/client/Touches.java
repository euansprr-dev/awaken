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
import net.minecraft.util.Mth;
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
 * a chest's lid, going up with it and letting it down again as you leave, a palm pressed to a stone (lit by it), the Forge
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
    private static int lastAt = -100;
    private static int ticks;
    private static final boolean TRACE = System.getProperty("awaken.autopilot") != null;
    /**
     * The box whose lid you lifted and whose screen is open (SPEC §64.7): the view looks in while it is, and the hands
     * let the lid down when it shuts. Its screen opens at once (it used to wait four ticks for the hands): no click waits.
     */
    private static @Nullable BlockPos box;
    private static int lidAt = -100;
    private static boolean boxOpen;
    private static long boxOpenedAt;
    private static final long RISE_NS = 240_000_000L;
    private static final double[] RISE = new double[2];
    /** How far the view has tipped toward the open box (0..1), this tick and last. */
    private static float look, lookBefore;

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
    /**
     * A chest, ender chest, shulker box or cache: both hands under the lid's front edge, going up with it as it really
     * opens (the anchor is the lid's own edge, frame by frame), letting go as it stands.
     */
    static final Touch LID = t(AnimsClips.LID, 0.3F, -1, -1, -1, -1, rest(0), at(2, 0, -0.02F, 0.05F), at(7, 0, 0, 0.04F), at(9, 0, 0.03F, 0.1F), rest(13));
    /** The same lid let down again as you leave: the hands take its raised edge and bring it down. */
    static final Touch LID_DOWN = t(AnimsClips.LID, 0.3F, -1, -1, -1, -1, rest(0), at(2, 0, 0.02F, 0.08F), at(8, 0, -0.01F, 0.04F), rest(11));
    /** A barrel: one hand to the hatch in its face, drawing it open. */
    static final Touch HATCH = t(AnimsClips.LID, rest(0), at(3, 0, 0, 0.03F), at(6, 0.06F, 0, 0.12F), rest(10));
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
    /** A note block, a repeater's or comparator's switch, a daylight detector: a tap of the fingers. */
    static final Touch TAP = t(AnimsClips.BUTTON, rest(0), at(2, 0, 0.02F, 0.04F), at(3, 0, -0.01F, 0), at(5, 0, 0.02F, 0.04F), rest(8));
    /** A bell: the palm on it, setting it swinging, and the hand going with the swing. */
    static final Touch RING = t(AnimsClips.PUSH, rest(0), at(2, 0, 0, 0.05F), at(4, 0.04F, 0, -0.06F), at(7, 0.1F, 0.02F, 0.02F), rest(11));
    /** Something put in or on a thing (a disc, scraps, a book, a flower): carried to it in the hand and let go. */
    static final Touch PUT = t(AnimsClips.LAY, 0, 0, 6, -1, -1, rest(0), at(4, 0, 0.1F, 0.06F), at(6, 0, 0.02F, 0.01F), at(8, 0, 0.12F, 0.12F), rest(11));
    /** Something taken with the fingers (a slice of cake, a token, a book off a shelf): pinched and drawn back to you. */
    static final Touch TAKE = t(AnimsClips.PLUCK, rest(0), at(3, 0, 0, 0.03F), at(5, 0, 0.03F, 0.02F), at(8, 0.03F, 0.2F, 0.3F), rest(12));
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
        // the box's screen is gone some other way than by its own closing (a death, a new level): the box is shut
        if (boxOpen && !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) boxOpen = false;
        lookBefore = look;
        look += ((boxOpen ? 1F : 0F) - look) * 0.3F;
        if (look < 0.003F) look = 0;
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
        // a station whose scene is about to open: its own hands take over (SPEC §64.2), so no touch plays over them
        if (FirstPerson.stationsOn.getAsBoolean() && !p.isShiftKeyDown() && dev.awaken.anims.station.StationView.kindOf(s) != null) return;
        Touch t = forUse(s);
        if (t == null) return;
        if (t == PUT) {
            // the hand that holds the thing carries it there
            if (ticks - lastAt < 4) return;
            lastAt = ticks;
            trace(PUT.name());
            Reaches.start(PUT, fixed(hit.x, hit.y, hit.z), rightMain(p), !rightMain(p), p.getMainHandItem());
            return;
        }
        // a palm on a stone and both hands set to a thing have no sound of the game's own: one of its recordings each
        if (t == PALM || t == SET) {
            level.playLocalSound(hit.x, hit.y, hit.z, (t == PALM ? dev.awaken.anims.AnimsSounds.PALM : dev.awaken.anims.AnimsSounds.SET).get(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1F, false);
        }
        if (t == LID || t == HATCH) {
            lidAt = ticks;
            box = pos;
            play(p, t, t == HATCH ? fixed(hit.x, hit.y, hit.z) : lid(level, pos));
        } else {
            play(p, t, fixed(hit.x, hit.y, hit.z));
        }
    }

    /** The touch a use of this block gets (anims.station.TouchKinds names it, for both sides), as your own hands play it. */
    private static @Nullable Touch forUse(BlockState s) {
        LocalPlayer p = Minecraft.getInstance().player;
        dev.awaken.anims.station.TouchKinds.Kind k = dev.awaken.anims.station.TouchKinds.of(s, p == null ? ItemStack.EMPTY : p.getMainHandItem());
        if (k == null) return null;
        return switch (k) {
            case PUSH -> PUSH;
            case LEVER -> s.hasProperty(LeverBlock.POWERED) && s.getValue(LeverBlock.POWERED) ? LEVER_UP : LEVER_DOWN;
            case BUTTON -> BUTTON;
            case LID -> LID;
            case HATCH -> HATCH;
            case PLUCK -> PLUCK;
            case PALM -> PALM;
            case HAMMER -> HAMMER;
            case LAY -> LAY;
            case CRANK -> CRANK;
            case PAGE -> PAGE;
            case SET -> SET;
            case TEAR -> TEAR;
            case TAP -> TAP;
            case RING -> RING;
            case PUT -> PUT;
            case TAKE -> TAKE;
        };
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
     * A box's screen opening just after its lid was touched: it opens at once (nothing waits for the hands), and the box
     * is open until that screen shuts. A book opening (a written book, a book and quill, the Dream Journal): a page
     * turned as it opens.
     */
    static void screenOpening(ScreenEvent.Opening e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        Screen s = e.getNewScreen();
        if (s instanceof AbstractContainerScreen<?> && box != null && ticks - lidAt <= 4) {
            boxOpen = true;
            boxOpenedAt = System.nanoTime();
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

    /**
     * A lid's front edge, where the hands hold it: for a chest or an ender chest the lip of the lid as it really stands
     * this frame (the lid turns about a hinge a texel in from the back, nine up; its lip is fourteen out from the hinge);
     * for a shulker box the shell's rim as it rises; for anything else the face toward you, just under the top.
     */
    private static Reaches.Anchor lid(Level level, BlockPos pos) {
        return out -> {
            BlockState s = level.getBlockState(pos);
            if (s.isAir()) return false;
            float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            var be = level.getBlockEntity(pos);
            if (be instanceof net.minecraft.world.level.block.entity.LidBlockEntity lidded && s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                var front = s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                // vanilla eases the lid: 1 - (1 - open)^3 of a quarter turn
                float open = lidded.getOpenNess(pt), k = 1 - (1 - open) * (1 - open) * (1 - open);
                double turn = k * Math.PI / 2, along = Math.cos(turn) * 0.875 - 0.4375, up = Math.sin(turn) * 0.875;
                out[0] = pos.getX() + 0.5 + front.getStepX() * along;
                out[1] = pos.getY() + 0.5625 + up;
                out[2] = pos.getZ() + 0.5 + front.getStepZ() * along;
                return true;
            }
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
            if (be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity shell) out[1] = pos.getY() + 1 - 0.06 + shell.getProgress(pt) * 0.5;
            return true;
        };
    }

    /** The box's screen is shutting: the hands take the lid and let it down (both free), and the view comes back. */
    static void screenClosing(ScreenEvent.Closing e) {
        if (!boxOpen || !(e.getScreen() instanceof AbstractContainerScreen<?>)) return;
        boxOpen = false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        BlockPos at = box;
        if (p == null || at == null || mc.level == null || !on(p)) return;
        BlockState s = mc.level.getBlockState(at);
        if (forUse(s) != LID) return;
        lastAt = -100;
        play(p, LID_DOWN, lid(mc.level, at));
    }

    /**
     * The glass rises out of the box (SPEC §64.7): for a quarter of a second after a box's screen opens, two thin lines
     * of light run from the box's mouth, where the world shows it, to the foot of the screen's panel. The slots are in
     * their places and clickable from the first frame; only the light moves.
     */
    static void glassRise(ScreenEvent.Render.Foreground e) {
        BlockPos at = box;
        if (!boxOpen || at == null || !(e.getScreen() instanceof AbstractContainerScreen<?> scr) || FirstPerson.reducedMotion.getAsBoolean()) return;
        float u = (System.nanoTime() - boxOpenedAt) / (float) RISE_NS;
        if (u < 0 || u >= 1) return;
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.mainCamera();
        Vec3 o = cam.position();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        if (!dev.awaken.anims.station.StationView.project(at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, o.x, o.y, o.z, cam.rotation(), cam.getFov(), aspect,
                scr.width, scr.height, RISE)) return;
        int x0 = scr.getLeftPos(), x1 = x0 + scr.getImageWidth(), y1 = scr.getTopPos() + scr.getImageHeight();
        int alpha = Math.round(200 * (1 - u) * Math.min(1F, u * 5));
        for (int side = 0; side < 2; side++) {
            double fx = RISE[0] + (side == 0 ? -6 : 6), fy = RISE[1], tx = side == 0 ? x0 : x1, ty = y1;
            // the light runs out from the box: a short stroke that travels the line
            for (int k = 0; k < 14; k++) {
                double t = Math.min(1, u * 1.4) - k * 0.02;
                if (t <= 0) break;
                int x = (int) Math.round(fx + (tx - fx) * t), y = (int) Math.round(fy + (ty - fy) * t);
                e.getGuiGraphics().fill(x, y, x + 1, y + 1, (Math.max(0, alpha - k * 12) << 24) | 0x7FC3FF);
            }
        }
    }

    /** How far the view tips toward the open box now (0..1). */
    static float look(float pt) {
        return Mth.lerp(pt, lookBefore, look);
    }

    /**
     * Looking in (SPEC §64.7): while a box's screen is open the view tips toward the box, up to four degrees, and
     * narrows a touch. Your own look is not moved, and with reduced motion nothing happens.
     */
    static void lookIn(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        float w = look((float) e.getPartialTick());
        BlockPos at = box;
        if (w <= 0 || at == null || FirstPerson.reducedMotion.getAsBoolean() || dev.awaken.anims.client.station.StationClient.ownsCamera()) return;
        Vec3 o = e.getCamera().position();
        double dx = at.getX() + 0.5 - o.x, dy = at.getY() + 0.8 - o.y, dz = at.getZ() + 0.5 - o.z;
        float pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float k = w * w * (3 - 2 * w);
        e.setPitch(e.getPitch() + Mth.clamp(pitch - e.getPitch(), -4F, 4F) * k);
        e.setYaw(e.getYaw() + Mth.clamp(Mth.wrapDegrees(yaw - e.getYaw()), -2F, 2F) * k);
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
        box = null;
        boxOpen = false;
        look = lookBefore = 0;
        Reaches.reset();
    }
}
