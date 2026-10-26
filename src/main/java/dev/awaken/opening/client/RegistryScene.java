package dev.awaken.opening.client;

import dev.awaken.Awaken;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.core.api.HoloBand;
import dev.awaken.core.api.TipHooks;
import dev.awaken.opening.OpeningPlaces;
import dev.awaken.opening.net.OpeningPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The Registry ceremony as the player sees it (SPEC §16.4). Bram walks you there (his lines come from the server);
 * the Assessment Lens's shard turns over its pedestal and reads you (a scan of light, head to foot, twice; you're held
 * still); at the desk you sign the register ({@link SigningScene}); you take the Holo band from his hand
 * ({@link HandOver}); it sits in your inventory until you put it on the wrist slot, and the inventory closes on its own
 * a moment later so the boot plays on your wrist (the holo module's); then "Go on, open it": the K tip shows only now;
 * the K menu; the Atlas opens with the run you made (a proposal); the first power; "The city is yours." The server moves
 * the steps; this side reports what only it can see end. Step numbers mirror OpeningRegistry's.
 */
final class RegistryScene {
    private RegistryScene() {}

    static final int WALK = 0, READ = 1, SIGN = 2, BAND = 3, WEAR = 4, BOOT = 5, BRIEF = 6, OPEN = 7, STATUS = 8, ATLAS = 9,
            POWER = 10, DIRECTIVE = 11, YOURS = 12;
    private static final Identifier TIP_WEAR = Awaken.id("holo/wear"), TIP_OPEN = Awaken.id("holo/open");

    private static int step = -1;
    private static long stepAt;
    private static boolean sawScreen, bootTitled, closedInventory, readySent;
    private static long wornSeenAt = -1, bootedAt = -1;
    private static @Nullable ClientInput heldInput;
    private static @Nullable LocalPlayer heldPlayer;
    private static long holdUntil;

    static void init() {
        NeoForge.EVENT_BUS.addListener(InputEvent.InteractionKeyMappingTriggered.class, RegistryScene::onUse);
        HoloBand.listenClient(on -> {
            if (on) bootedAt = OpeningClient.ticks();
        });
        SigningScene.init();
        HandOver.init();
    }

    static void reset() {
        step = -1;
        release();
        OpeningFx.lens = null;
        OpeningHud.prompt(null, "");
        SigningScene.reset();
        HandOver.reset();
        tipHold(false);
    }

    static void onScene(OpeningPayloads.Scene s, boolean fresh) {
        if (s.beat() != OpeningPayloads.REGISTRY) {
            if (step >= 0) {
                release();
                OpeningFx.lens = null;
                OpeningHud.prompt(null, "");
                tipHold(false);
                step = -1;
            }
            return;
        }
        OpeningFx.lens = s.at();
        int n = s.n();
        if (n == step && !fresh) return;
        step = n;
        stepAt = OpeningClient.ticks();
        sawScreen = false;
        readySent = false;
        OpeningHud.prompt(null, "");
        switch (n) {
            case READ -> { // the reading
                hold(110);
                OpeningFx.scanFrom = OpeningClient.ticks();
                OpeningFx.lensGlow = 1;
                OpeningSounds.ui(dev.awaken.holo.HoloSounds.CARD.get(), 0.7F, 0.9F);
                OpeningSounds.ui(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.5F);
                OpeningHud.title("ASSESSING", "the Lens reads what came back", 60);
                OpeningClient.later(70, () -> {
                    if (step == READ) {
                        OpeningHud.title("AWAKENED", "rank E · the Signal's newest", 50);
                        OpeningSounds.ui(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1F);
                    }
                });
            }
            case SIGN -> OpeningFx.lensGlow = 0.3F;
            case WEAR -> {
                closedInventory = false;
                bootTitled = false;
                wornSeenAt = -1;
            }
            case BOOT -> {
                // the boot may already have played out (worn with the inventory shut)
                if (bootedAt >= 0 && OpeningClient.ticks() - bootedAt < 200) ready(BOOT);
            }
            case OPEN -> tipHold(false);
            case ATLAS -> OpeningClient.later(12, () -> {
                if (step != ATLAS) return;
                if (dev.awaken.atlas.client.AtlasClient.openReveal()) sawScreen = true;
                else ready(ATLAS);
            });
            case YOURS -> {
                OpeningHud.title("THE CITY IS YOURS.", "", 110);
                OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 1.0F, 0.8F);
                OpeningClient.later(14, () -> OpeningSounds.ui(CitizensContent.BELL_DISTRICT.get(), 1.12F, 0.5F));
                OpeningClient.later(26, () -> OpeningSounds.ui(CitizensContent.BELL_DISTRICT.get(), 1.26F, 0.5F));
            }
            default -> {}
        }
    }

    static void tick() {
        SigningScene.tick();
        HandOver.tick();
        if (step < 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (heldInput != null && OpeningClient.ticks() >= holdUntil && !SigningScene.active()) release();
        Screen sc = mc.gui.screen();
        boolean worn = HoloBand.worn(p);
        String use = mc.options.keyUse.getTranslatedKeyMessage().getString();
        switch (step) {
            case SIGN -> {
                boolean near = lens() != null && p.position().distanceTo(OpeningPlaces.deskStandFrom(lens())) < 2.0;
                OpeningHud.prompt(near && !SigningScene.active() ? use : null, near && !SigningScene.active() ? "Sign the register" : "");
            }
            case BAND -> {
                LivingEntity b = HandOver.holder(6);
                boolean can = b != null && b.distanceTo(p) < 3.6 && !HandOver.active();
                OpeningHud.prompt(can ? use : null, can ? "Take the band" : "");
            }
            case WEAR -> {
                String inv = mc.options.keyInventory.getTranslatedKeyMessage().getString();
                if (!worn) {
                    if (TipHooks.client().wants(TIP_WEAR)) TipHooks.client().offer(TIP_WEAR);
                    OpeningHud.prompt(sc == null ? inv : null, sc == null ? "Open your inventory · put the band on your wrist" : "");
                } else {
                    OpeningHud.prompt(null, "");
                    if (wornSeenAt < 0) {
                        wornSeenAt = OpeningClient.ticks();
                        TipHooks.client().done(TIP_WEAR);
                    }
                    // a moment to see it in the slot, then the inventory steps aside for the boot
                    if (!closedInventory && sc instanceof InventoryScreen && OpeningClient.ticks() - wornSeenAt > 14) {
                        closedInventory = true;
                        sc.onClose();
                    }
                }
                bootTitle(mc, worn);
            }
            case BOOT -> {
                bootTitle(mc, worn);
                if (bootedAt >= stepAt - 40 && bootedAt >= 0) ready(BOOT);
            }
            case OPEN -> {
                boolean status = isStatus(sc);
                if (!status) {
                    // only now: the tip that shows your K
                    if (TipHooks.client().wants(TIP_OPEN)) TipHooks.client().offer(TIP_OPEN);
                    String k = dev.awaken.core.client.AwakenKeys.STATUS.getTranslatedKeyMessage().getString();
                    OpeningHud.prompt(sc == null ? k : null, sc == null ? "Open your band" : "");
                } else {
                    OpeningHud.prompt(null, "");
                    TipHooks.client().done(TIP_OPEN);
                    ready(OPEN);
                }
            }
            case STATUS -> {
                if (isStatus(sc)) sawScreen = true;
                else if (sawScreen || OpeningClient.ticks() - stepAt > 40 && sc == null) ready(STATUS);
            }
            case ATLAS -> {
                if (sawScreen && !dev.awaken.atlas.client.AtlasClient.isOpen() && OpeningClient.ticks() - stepAt > 30) ready(ATLAS);
            }
            case POWER -> {
                // the first power's cards come from the server; once they've shown and gone, the step is over
                if (sc != null && sc.getClass().getSimpleName().equals("StarterScreen")) sawScreen = true;
                else if (sawScreen && sc == null) {
                    sawScreen = false;
                    ready(POWER);
                }
            }
            default -> {}
        }
        if (OpeningFx.lens != null) OpeningFx.lensGlow = Math.max(0.15F, OpeningFx.lensGlow * 0.985F);
    }

    /** The band's title as its boot begins on the wrist (once the inventory is shut). */
    private static void bootTitle(Minecraft mc, boolean worn) {
        if (bootTitled || !worn || mc.gui.screen() != null) return;
        bootTitled = true;
        OpeningClient.later(14, () -> OpeningHud.title("THE HOLO", "fitted to your wrist · booting", 70));
    }

    private static boolean isStatus(@Nullable Screen sc) {
        return sc != null && (sc instanceof dev.awaken.progression.client.StatusScreen || sc.getClass().getSimpleName().equals("HoloStatusScreen"));
    }

    /** Use at the desk signs; use at Bram takes the band. Nothing else happens with that click. */
    private static void onUse(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || step < 0) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gui.screen() != null) return;
        if (SigningScene.active() || HandOver.active()) {
            // the hands are busy (both hands' use events come through)
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (step == SIGN && lens() != null && !SigningScene.active() && p.position().distanceTo(OpeningPlaces.deskStandFrom(lens())) < 2.0) {
            e.setCanceled(true);
            e.setSwingHand(false);
            OpeningHud.prompt(null, "");
            hold(20 * 30);
            tipHold(true);
            SigningScene.begin(OpeningPlaces.deskPaperFrom(lens()), 1, 0);
        } else if (step == BAND && !HandOver.active()) {
            LivingEntity b = HandOver.holder(6);
            if (b == null || b.distanceTo(p) > 3.6) return;
            e.setCanceled(true);
            e.setSwingHand(false);
            OpeningHud.prompt(null, "");
            HandOver.begin(b);
        }
    }

    /** The signing: the ink is down (Bram stamps now). */
    static void inkDone() {
        sendReady(SIGN);
    }

    /** The signing: the paper's folded and handed to you. */
    static void signed() {
        release();
        tipHold(false);
        OpeningHud.title("HUNTER'S LICENCE", "E-RANK · HUNTER ASSOCIATION OF CARILLON", 90);
        sendReady(SIGN);
    }

    /** Your hand closed on the band. */
    static void tookBand() {
        sendReady(BAND);
    }

    private static void ready(int n) {
        if (readySent || step != n) return;
        readySent = true;
        sendReady(n);
    }

    private static void sendReady(int n) {
        ClientPacketDistributor.sendToServer(new OpeningPayloads.Ready(OpeningPayloads.READY_STEP, n));
    }

    private static final Object TIPS = new Object();

    private static void tipHold(boolean held) {
        TipHooks.client().hold(TIPS, held);
    }

    private static void hold(int ticks) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (heldInput == null) {
            heldInput = p.input;
            heldPlayer = p;
            p.input = new ClientInput();
        }
        holdUntil = OpeningClient.ticks() + ticks;
    }

    private static void release() {
        if (heldInput != null && heldPlayer != null && heldPlayer.input != heldInput) heldPlayer.input = heldInput;
        heldInput = null;
        heldPlayer = null;
    }

    static @Nullable Vec3 lens() {
        return OpeningFx.lens;
    }
}
