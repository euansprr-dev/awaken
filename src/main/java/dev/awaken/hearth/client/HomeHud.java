package dev.awaken.hearth.client;

import dev.awaken.Awaken;
import dev.awaken.core.api.Notices;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.Rested;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.hearth.room.Reading;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The home's words on the screen (SPEC §28.2), each through the director (SPEC §26): walking onto your land, its name
 * as a place caption ("ASHFORD" over "your hearth", the region caption's way, in the hearth's gold); walking into a room
 * the Hearth just recognised or regraded, its name once as a quiet caption (AMBIENT, topic {@code hearth_room}: shown
 * only when nothing else wants the screen, else filed to the Journal); and Rested on the band, over the level line.
 */
final class HomeHud {
    private HomeHud() {}

    static void register(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, Awaken.id("hearth_home"), HomeHud::render);
    }

    static final int WAIT = 200, ROOM_WAIT = 600;
    static final float PLACE_IN = 10, PLACE_HOLD = 60, PLACE_OUT = 22, ROOM_IN = 8, ROOM_HOLD = 56, ROOM_OUT = 18;
    private static long ticks;

    // ---- the place caption -------------------------------------------------------------------------------------------

    private static String placeName = "", pendingPlace;
    private static float placeFrom = -1000;
    private static long placeSince;
    private static boolean wasInside, known;

    /** Shows the home's name now (the card just closed: the first time you stand on it named). */
    static void place(String name) {
        pendingPlace = name;
        placeSince = ticks;
        wasInside = true;
    }

    // ---- the room caption --------------------------------------------------------------------------------------------

    private static String roomName = "", roomGrade = "", pendingRoom, pendingGrade, pendingPurpose;
    private static float roomFrom = -1000;
    private static long roomSince;

    static void room(String name, int grade, String purpose) {
        pendingRoom = name;
        pendingGrade = Reading.Grade.values()[Math.max(0, Math.min(3, grade))].label;
        pendingPurpose = purpose;
        roomSince = ticks;
    }

    static void clear() {
        pendingPlace = null;
        pendingRoom = null;
        placeFrom = roomFrom = -1000;
        wasInside = known = false;
    }

    /** Client tick: walking onto your land, and the waiting captions asking the director for their moment. */
    static void tick(Minecraft mc) {
        ticks++;
        if (mc.player == null || mc.level == null) return;
        HearthView v = mc.player.getData(HearthContent.VIEW.get());
        boolean inside = v.has() && mc.level.dimension() == Level.OVERWORLD && within(v, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
        // the first look after joining sets where you are without a caption (you didn't walk in)
        if (!known) {
            known = true;
            wasInside = inside;
        } else if (inside && !wasInside && !v.home().name().isEmpty()) {
            pendingPlace = v.home().name();
            placeSince = ticks;
        }
        wasInside = inside;
        var notices = Notices.client();
        if (pendingPlace != null && ticks % 5 == 0) {
            if (notices.claim(Notices.Tier.IMPORTANT, "hearth_place")) {
                placeName = pendingPlace.toUpperCase(java.util.Locale.ROOT);
                placeFrom = ticks;
                pendingPlace = null;
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(HearthContent.SND_ROOM.get(), 0.8f, 0.55f));
            } else if (ticks - placeSince > WAIT) {
                notices.file(Notices.Tier.AMBIENT, Component.literal("Home · " + pendingPlace), Component.literal("your hearth"));
                pendingPlace = null;
            }
        }
        if (pendingRoom != null && ticks % 5 == 2) {
            // a room's name waits for its moment while you're still in that room (up to half a minute), else it's filed
            // (the first two seconds count as there: the room's sync and its caption may arrive in either order)
            boolean stillThere = ticks - roomSince < 40 || pendingRoom.equals(RoomPanel.here().name());
            if (stillThere && notices.claim(Notices.Tier.AMBIENT, "hearth_room")) {
                roomName = RoomPanel.capitalise(pendingRoom);
                roomGrade = pendingGrade.toUpperCase(java.util.Locale.ROOT) + (pendingPurpose.isEmpty() ? "" : " · " + pendingPurpose.toUpperCase(java.util.Locale.ROOT));
                roomFrom = ticks;
                pendingRoom = null;
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(HearthContent.SND_ROOM.get(), 1f, 0.35f));
            } else if (!stillThere || ticks - roomSince > ROOM_WAIT) {
                notices.file(Notices.Tier.AMBIENT, Component.literal(RoomPanel.capitalise(pendingRoom)), Component.literal(pendingGrade + " · " + pendingPurpose));
                pendingRoom = null;
            }
        }
    }

    static boolean within(HearthView v, int x, int y, int z) {
        int r = v.radius();
        return Math.abs(x - v.pos().getX()) <= r && Math.abs(z - v.pos().getZ()) <= r && y >= v.pos().getY() - 32 && y <= v.pos().getY() + 64;
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        float t = ticks + dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        place(g, font, w, h, t);
        room(g, font, w, h, t);
        rested(g, font, mc, w, h);
    }

    /** "ASHFORD" between two hairlines that grow out from the middle, "your hearth" under it: the region caption's way. */
    private static void place(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        float age = t - placeFrom;
        if (age < 0 || age > PLACE_IN + PLACE_HOLD + PLACE_OUT) return;
        float a = age < PLACE_IN ? age / PLACE_IN : age > PLACE_IN + PLACE_HOLD ? 1f - (age - PLACE_IN - PLACE_HOLD) / PLACE_OUT : 1f;
        a = Mth.clamp(a, 0f, 1f);
        int y = h / 4;
        String big = spaced(placeName);
        float s = 1.6f;
        int bw = (int) (font.width(big) * s);
        g.pose().pushMatrix();
        g.pose().translate((w - bw) / 2f, y);
        g.pose().scale(s, s);
        g.text(font, big, 0, 0, Glass.a(0xFFE2A8, a), true);
        g.pose().popMatrix();
        int lineW = (int) (Math.max(bw, 120) * Mth.clamp(age / (PLACE_IN * 1.5f), 0f, 1f));
        g.horizontalLine((w - lineW) / 2, (w + lineW) / 2, y - 6, Glass.a(Glass.LANTERN, 0.6f * a));
        g.horizontalLine((w - lineW) / 2, (w + lineW) / 2, y + (int) (font.lineHeight * s) + 3, Glass.a(Glass.LANTERN, 0.6f * a));
        String sub = "your hearth";
        g.text(font, sub, (w - font.width(sub)) / 2, y + (int) (font.lineHeight * s) + 8, Glass.a(Glass.AMBER, 0.9f * a), true);
    }

    /** A room's name in a quiet line below the middle of the screen, its grade spaced small above it. */
    private static void room(GuiGraphicsExtractor g, Font font, int w, int h, float t) {
        float age = t - roomFrom;
        if (age < 0 || age > ROOM_IN + ROOM_HOLD + ROOM_OUT) return;
        float a = age < ROOM_IN ? age / ROOM_IN : age > ROOM_IN + ROOM_HOLD ? 1f - (age - ROOM_IN - ROOM_HOLD) / ROOM_OUT : 1f;
        a = Mth.clamp(a, 0f, 1f);
        int y = h * 62 / 100;
        String label = spaced(roomGrade);
        g.text(font, label, (w - font.width(label)) / 2, y, Glass.a(Glass.LANTERN, 0.75f * a), false);
        g.text(font, roomName, (w - font.width(roomName)) / 2, y + 11, Glass.a(0xFFF1D6, 0.95f * a), true);
    }

    /** "RESTED 14m" over the level line, in the hearth's gold, while it lasts. */
    private static void rested(GuiGraphicsExtractor g, Font font, Minecraft mc, int w, int h) {
        if (mc.player.isSpectator() || !Progression.profile(mc.player).awakened()) return;
        long left = Rested.left(mc.player);
        if (left <= 0) return;
        int minutes = (int) Math.max(1, (left + 1199) / 1200);
        String s = "◆ RESTED " + minutes + "m";
        int right = w / 2 - 91 - 6;
        int y = h - 22 - 14 + 9;
        float pulse = left < 20 * 60 ? 0.65f + 0.35f * Mth.sin(mc.player.tickCount * 0.2f) : 1f;
        g.text(font, s, right - font.width(s), y, Glass.a(Glass.LANTERN, 0.9f * pulse), true);
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
