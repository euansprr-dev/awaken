package dev.awaken.opening.client;

import dev.awaken.citizens.CitizensContent;
import dev.awaken.opening.net.OpeningPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The Registry as the player sees it (SPEC §16.4, a stub per the lean plan): the Assessment Lens's shard turning over
 * its pedestal; standing on the bronze ring, the reading (a scan of light, head to foot, twice; you're held still); the
 * licence (a title card and the paper in your hand); the wristband fitted (the band boots with the Holo's own sound);
 * your Status for the first time (the K menu, from the wrist); then the first power, the Directive and "The city is
 * yours." The server moves the steps; screens report back when they close.
 */
final class RegistryScene {
    private RegistryScene() {}

    private static int step = -1;
    private static long stepAt;
    private static @Nullable Class<?> waitingFor;
    private static boolean sawScreen;
    private static @Nullable ClientInput heldInput;
    private static @Nullable LocalPlayer heldPlayer;
    private static long holdUntil;

    static void reset() {
        step = -1;
        waitingFor = null;
        release();
        OpeningFx.lens = null;
    }

    static void onScene(OpeningPayloads.Scene s, boolean fresh) {
        if (s.beat() != OpeningPayloads.REGISTRY) {
            if (step >= 0) {
                release();
                OpeningFx.lens = null;
                step = -1;
            }
            return;
        }
        OpeningFx.lens = s.at();
        int n = s.n();
        if (n == step && !fresh) return;
        step = n;
        stepAt = OpeningClient.ticks();
        Minecraft mc = Minecraft.getInstance();
        switch (n) {
            case 1 -> { // the reading
                hold(110);
                OpeningFx.scanFrom = OpeningClient.ticks();
                OpeningFx.lensGlow = 1;
                OpeningSounds.ui(dev.awaken.holo.HoloSounds.CARD.get(), 0.7F, 0.9F);
                OpeningSounds.ui(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.5F);
                OpeningHud.title("ASSESSING", "the Lens reads what came back", 60);
                OpeningClient.later(70, () -> {
                    if (step == 1) {
                        OpeningHud.title("AWAKENED", "rank E · the Signal's newest", 50);
                        OpeningSounds.ui(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1F);
                    }
                });
            }
            case 2 -> { // the licence
                OpeningFx.lensGlow = 0.3F;
                OpeningHud.title("HUNTER'S LICENCE", "E-RANK · HUNTER ASSOCIATION OF CARILLON", 90);
                OpeningSounds.ui(SoundEvents.BOOK_PAGE_TURN, 1F, 1F);
                OpeningClient.later(30, () -> OpeningSounds.ui(SoundEvents.ANVIL_LAND, 1.8F, 0.25F)); // the stamp
            }
            case 3 -> { // the wristband
                OpeningSounds.ui(dev.awaken.holo.HoloSounds.OPEN.get(), 0.8F, 1F);
                OpeningHud.flash(0x9ED2FF, 0.25F);
                OpeningHud.title("THE HOLO", "fitted to your wrist · booting", 60);
            }
            case 4 -> { // your Status, from the wrist, the first time
                waitingFor = null;
                sawScreen = false;
                Screen sc = dev.awaken.holo.client.HoloClient.statusScreen();
                mc.gui.setScreen(sc);
                waitingFor = sc.getClass();
            }
            case 5 -> { // the first power: the server opens its cards; we tell it when they close
                waitingFor = null;
                sawScreen = false;
            }
            case 7 -> {
                OpeningHud.title("THE CITY IS YOURS.", "", 110);
                OpeningSounds.ui(CitizensContent.BELL_HOUR.get(), 1.0F, 0.8F);
                OpeningClient.later(14, () -> OpeningSounds.ui(CitizensContent.BELL_DISTRICT.get(), 1.12F, 0.5F));
                OpeningClient.later(26, () -> OpeningSounds.ui(CitizensContent.BELL_DISTRICT.get(), 1.26F, 0.5F));
            }
            default -> {}
        }
    }

    static void tick() {
        if (step < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (heldInput != null && OpeningClient.ticks() >= holdUntil) release();
        if (step == 4 && waitingFor != null) {
            if (mc.gui.screen() != null && waitingFor.isInstance(mc.gui.screen())) sawScreen = true;
            else if (sawScreen || OpeningClient.ticks() - stepAt > 40 && mc.gui.screen() == null) {
                waitingFor = null;
                ready(4);
            }
        }
        if (step == 5) {
            // the first power's cards come from the server; once they've shown and gone, the step is over
            if (mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("StarterScreen")) sawScreen = true;
            else if (sawScreen && mc.gui.screen() == null) {
                sawScreen = false;
                ready(5);
            }
        }
        if (OpeningFx.lens != null) OpeningFx.lensGlow = Math.max(0.15F, OpeningFx.lensGlow * 0.985F);
    }

    private static void ready(int n) {
        ClientPacketDistributor.sendToServer(new OpeningPayloads.Ready(OpeningPayloads.READY_STEP, n));
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
