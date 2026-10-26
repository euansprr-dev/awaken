package dev.awaken.opening.client;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The opening's film kit for scenes outside it (SPEC §16.4, §27.6: the first thread's High Gate shot and Ilse by her
 * fire): the one camera, the letterbox, people's lines as the Registry shows them, a prompt with its key, a title card
 * and a hand-over from someone's hand to yours. Client only.
 */
public final class Cinema {
    private Cinema() {}

    /** Starts a camera shot (a running one is replaced). */
    public static void take(OpeningCamera.Shot shot) {
        OpeningCamera.take(shot, 0);
    }

    /** Whether a shot holds the camera. */
    public static boolean filming() {
        return OpeningCamera.active();
    }

    /** Gives the view back now. */
    public static void cut(String reason) {
        OpeningCamera.finish(reason);
    }

    /** The letterbox in or out (it eases). */
    public static void bars(boolean on) {
        OpeningHud.barsTarget = on ? 1 : 0;
    }

    /** Someone's line, one at a time, over the hotbar with their name above it. */
    public static void line(String who, String text, int ticks) {
        OpeningHud.line(who, text, ticks);
    }

    /** Any line still on screen fades within {@code ticks} (a scene ended). */
    public static void shortenLines(int ticks) {
        OpeningHud.shortenLines(ticks);
    }

    /** A title card: big spaced capitals, a line under them. */
    public static void title(String big, String small, int ticks) {
        OpeningHud.title(big, small, ticks);
    }

    /** The game's HUD in the frame or out of it, outside a camera shot (a hand-over in your own eyes). */
    public static void hud(boolean shown) {
        OpeningHud.film = !shown;
    }

    /** A caption in the lower bar of the letterbox (a place's name, a step done), in spaced capitals. */
    public static void barNote(String big, String small, int ticks) {
        OpeningHud.barNote(big, small, ticks);
    }

    /** A prompt the scene waits on: the key as a cap, then what it does; a null key fades it. */
    public static void prompt(@Nullable String key, String text) {
        OpeningHud.prompt(key, text);
    }

    /** Takes {@code what} from {@code from}'s hand into yours; {@code took} runs as your hand closes on it. */
    public static void handOver(LivingEntity from, ItemStack what, Runnable took) {
        HandOver.begin(from, what, took);
    }

    public static boolean handing() {
        return HandOver.active();
    }

    /** A sound in the ear, not in the world. */
    public static void sound(SoundEvent sound, float pitch, float volume) {
        OpeningSounds.ui(sound, pitch, volume);
    }

    /** The opening's client clock (ticks). */
    public static long ticks() {
        return OpeningClient.ticks();
    }
}
