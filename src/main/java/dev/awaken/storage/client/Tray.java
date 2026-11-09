package dev.awaken.storage.client;

import dev.awaken.holo.client.HoloGlass;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.UiCanvas;
import dev.awaken.storage.StorageNet;
import dev.awaken.storage.Tidying.Result;
import dev.awaken.storage.Tidying.Section;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** What a Tidy looks and sounds like (SPEC §56): the items in flight, your pins, and the line under the panel with its Undo. */
public final class Tray {
    private Tray() {}

    private static final long FLIGHT = 220_000_000L, STAGGER = 40_000_000L, FLASH = 200_000_000L, SAY = 4_000_000_000L, PULSE = 400_000_000L;
    private static final int PLATE = 0x040A16;

    private record Flight(int to, ItemStack item, int x0, int y0, int x1, int y1, long start) {}

    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static long flashUntil;
    private static long pulseUntil;
    private static final List<int[]> FLASHES = new ArrayList<>();
    private static long pins;

    private static @Nullable String line;
    private static long lineUntil;
    private static boolean undoable;
    private static int undoContainer;
    private static int[] watched = new int[0];
    private static ItemStack[] left = new ItemStack[0];

    static boolean pinned(int inventorySlot) {
        return inventorySlot >= 0 && inventorySlot < 64 && (pins & 1L << inventorySlot) != 0;
    }

    static void onPins(StorageNet.Pins m) {
        if (m.mask() != pins) play(SoundEvents.CHISELED_BOOKSHELF_INSERT, 1.1f, 0.35f);
        pins = m.mask();
    }

    static boolean canUndo() {
        return undoable;
    }

    static int undoContainer() {
        return undoContainer;
    }

    static void say(String text) {
        line = text;
        lineUntil = System.nanoTime() + SAY;
        undoable = false;
    }

    static void clear() {
        FLIGHTS.clear();
        FLASHES.clear();
        line = null;
        undoable = false;
    }

    /** A click during a flight lands everything at once. */
    static void skip() {
        FLIGHTS.clear();
    }

    /** Whether this slot's item is still in the air. */
    public static boolean hiding(Slot s) {
        for (int i = 0; i < FLIGHTS.size(); i++) if (FLIGHTS.get(i).to == s.index) return true;
        return false;
    }

    static void onDone(StorageNet.Done m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s) || m.containerId() != s.getMenu().containerId) return;
        Result r = Result.values()[m.result()];
        Section section = m.section() == Section.BAG.ordinal() ? Section.BAG : Section.CONTAINER;
        switch (r) {
            case DONE -> {
                fly(s.getMenu(), m.from(), m.to());
                pulseUntil = System.nanoTime() + PULSE;
                if (m.undo()) {
                    play(SoundEvents.BUNDLE_REMOVE_ONE, 1f, 0.6f);
                    say("Put back how it was.");
                } else {
                    play(SoundEvents.BUNDLE_INSERT, 0.8f, 0.7f);
                    // short enough to sit beside Undo inside the panel, and clear of JEI's columns
                    line = section == Section.CONTAINER ? "All tidied up." : m.firstBag() ? "Pinned slots stay put." : "Tidied your bag.";
                    lineUntil = Long.MAX_VALUE;
                    watch(s.getMenu(), m.containerId());
                }
            }
            case ALREADY -> say("That's already tidy.");
            case EMPTY -> say("Nothing to tidy.");
            case REFUSED -> {
                play(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 0.6f);
                say("You can't tidy this one.");
            }
            case FAILED -> {
                play(SoundEvents.BUNDLE_INSERT_FAIL, 1f, 0.6f);
                say("That didn't work. Nothing moved.");
            }
            case STALE -> say(m.undo() ? "Something's moved, so Undo is off." : "Something changed. Try again.");
            case LIMITED -> {}
        }
    }

    private static void watch(AbstractContainerMenu menu, int containerId) {
        undoable = true;
        undoContainer = containerId;
        watched = new int[menu.slots.size()];
        left = new ItemStack[watched.length];
        for (int i = 0; i < watched.length; i++) {
            watched[i] = i;
            left[i] = menu.getSlot(i).getItem().copy();
        }
    }

    /** Undo goes the moment anything changes under it. */
    private static boolean unchanged(AbstractContainerMenu menu) {
        if (left.length != menu.slots.size()) return false;
        for (int i = 0; i < left.length; i++) if (!ItemStack.matches(left[i], menu.getSlot(i).getItem())) return false;
        return true;
    }

    private static void fly(AbstractContainerMenu menu, List<Integer> from, List<Integer> to) {
        FLIGHTS.clear();
        FLASHES.clear();
        long now = System.nanoTime();
        if (HoloGlass.reducedMotion()) {
            flashUntil = now + FLASH;
            for (int t : to) FLASHES.add(new int[] {menu.getSlot(t).x, menu.getSlot(t).y});
            return;
        }
        int n = Math.max(1, from.size() - 1);
        for (int i = 0; i < from.size(); i++) {
            Slot a = menu.getSlot(from.get(i)), b = menu.getSlot(to.get(i));
            if (b.getItem().isEmpty()) continue;
            FLIGHTS.add(new Flight(b.index, b.getItem().copy(), a.x, a.y, b.x, b.y, now + STAGGER * i / n));
        }
    }

    /** Pins, the items in flight and the line, over the panel and under tooltips. */
    static void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> s, int screenHeight) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        UiCanvas c = GuiCanvas.of(g);
        int left = s.getLeftPos(), top = s.getTopPos();
        long now = System.nanoTime();

        float pulse = now < pulseUntil ? (pulseUntil - now) / (float) PULSE : 0f;
        for (Slot slot : s.getMenu().slots) {
            if (slot.container != inv || !pinned(slot.getContainerSlot())) continue;
            int x = left + slot.x, y = top + slot.y;
            c.outline(x - 1, y - 1, 18, 18, Glass.a(Glass.AMBER, Math.min(1f, 0.75f + 0.25f * pulse)));
            c.fill(x + 11, y, x + 15, y + 2, Glass.a(Glass.AMBER, 0.95f));
            c.fill(x + 12, y + 2, x + 14, y + 4, Glass.a(Glass.AMBER, 0.95f));
            c.fill(x + 12, y + 4, x + 13, y + 6, Glass.a(Glass.AMBER, 0.95f));
        }

        for (int i = FLIGHTS.size() - 1; i >= 0; i--) {
            Flight f = FLIGHTS.get(i);
            float t = (now - f.start) / (float) FLIGHT;
            if (t >= 1f) {
                FLIGHTS.remove(i);
                continue;
            }
            float e = t <= 0f ? 0f : 1f - (1f - t) * (1f - t) * (1f - t);
            c.item(f.item, left + Math.round(f.x0 + (f.x1 - f.x0) * e), top + Math.round(f.y0 + (f.y1 - f.y0) * e));
        }
        if (now < flashUntil)
            for (int[] p : FLASHES) c.fill(left + p[0], top + p[1], left + p[0] + 16, top + p[1] + 16, Glass.a(Glass.EDGE, 0.35f * (flashUntil - now) / FLASH));

        if (undoable && !unchanged(s.getMenu())) {
            undoable = false;
            line = null;
        }
        if (line != null && now > lineUntil) line = null;
        if (line != null) {
            var font = mc.font;
            int y = lineY(s, screenHeight), w = font.width(line);
            c.fill(left - 2, y - 1, left + w + 4, y + 10, Glass.a(PLATE, 0.82f));
            c.text(font, line, left + 1, y + 1, Glass.a(Glass.TEXT, 1f), false);
        }
    }

    static int lineY(AbstractContainerScreen<?> s, int screenHeight) {
        return Math.min(s.getTopPos() + s.getImageHeight() + 6, screenHeight - 9);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
