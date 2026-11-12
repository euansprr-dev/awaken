package dev.awaken.atlas.client;

import dev.awaken.atlas.AtlasNet;
import dev.awaken.atlas.Discovery;
import dev.awaken.core.api.Destinations;
import dev.awaken.core.api.MapMarks;
import dev.awaken.holo.HoloSounds;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Choosing where you're going on the Atlas (SPEC §16.8; Euan 2026-10-27: "make sure I can actually choose destinations on
 * the map"). Click a place on the table (a gate, a mark, a rumour, a Hearth, a region's name, a waypoint, or ground you know)
 * and a small card offers <b>Set as destination</b> (Enter); on the current destination it offers <b>Clear
 * destination</b>. The destination is the server's ({@link Destinations}): the Now line leads you there with its compass
 * until you arrive. It stands on the table in its own look, a white-gold pin with a beam and a slow ring, wherever it is.
 * A drag still turns the map and a double-click still drops a waypoint: only a click that didn't move picks. A waypoint's
 * card says whose it is and how far, and on one of yours offers <b>Change it</b> (E), which opens its {@link WaypointCard}.
 */
final class AtlasDestination {
    /** How near the cursor (GUI px) a mark must be to be the one clicked. */
    static final int REACH = 12;
    static final int GOLD = 0xFFF2D58A, INK = 0xFFE9F1FF, NAVY = 0xFF0B1626;

    /** What a click picked: its name as the Now line will say it, its kind, and where (and the waypoint, when it's one). */
    record Pick(String name, String kind, BlockPos at, AtlasNet.@Nullable WaypointView waypoint) {
        Pick(String name, String kind, BlockPos at) {
            this(name, kind, at, null);
        }
    }

    /** Where a world point lands on the table this frame (false: off it). */
    interface Projector {
        boolean at(double x, double y, double z, float[] out);
    }

    private @Nullable Pick picked;
    private final float[] p = new float[2];
    // the card, as drawn this frame (for clicks), and its second row's top (a waypoint's Change it)
    private int cardX0 = -1, cardY0, cardX1, cardY1, secondY = -1;
    /** Opens one of your waypoints' card (Change it). */
    private final Consumer<AtlasNet.WaypointView> change;

    AtlasDestination(Consumer<AtlasNet.WaypointView> change) {
        this.change = change;
    }

    boolean hasPick() {
        return picked != null;
    }

    void deselect() {
        picked = null;
        cardX0 = -1;
    }

    /** Whether (mx, my) is on the card (a click there is the card's, not the table's). */
    boolean overCard(double mx, double my) {
        return picked != null && cardX0 >= 0 && mx >= cardX0 && mx < cardX1 && my >= cardY0 && my < cardY1;
    }

    // ---- picking ------------------------------------------------------------------------------------------------------

    /**
     * A click on the table at (mx, my): the card's button sets or clears; otherwise the nearest place within reach is
     * picked (marks and gates first, then a region's name under the cursor, then the ground), or the pick is let go.
     */
    void click(Minecraft mc, AtlasState atlas, Projector proj, double mx, double my, int chipZone, double @Nullable [] ground) {
        if (overCard(mx, my)) {
            if (secondY >= 0 && my >= secondY - 2) changeIt();
            else act();
            return;
        }
        Pick best = null;
        double bestD = REACH * REACH;
        if (mc.player != null) {
            for (MapMarks.Gate gt : MapMarks.gates(mc.player)) {
                BlockPos at = gt.pos();
                double y = Discovery.CLIENT.explored(at.getX() >> 4, at.getZ() >> 4) ? atlas.store.groundY(at.getX(), at.getZ(), at.getY())
                        : atlas.store.groundY(at.getX(), at.getZ(), 64) + 34;
                // the pillar is the target: anywhere along it
                for (int up = 0; up <= AtlasScreen.PILLAR; up += 10) {
                    double d = near(proj, at.getX() + 0.5, y, at.getZ() + 0.5, mx, my + up);
                    if (d < bestD) {
                        bestD = d;
                        best = new Pick((gt.broken() ? "Broken " : "") + gt.name(), "gate", at);
                    }
                }
            }
            for (MapMarks.Mark m : MapMarks.collect(mc.player)) {
                BlockPos at = m.pos();
                boolean rumour = m.label().startsWith("? ");
                if (!rumour && !Discovery.CLIENT.explored(at.getX() >> 4, at.getZ() >> 4)) continue;
                double y = rumour ? atlas.store.groundY(at.getX(), at.getZ(), 64) + 34 : atlas.store.groundY(at.getX(), at.getZ(), at.getY());
                double d = near(proj, at.getX() + 0.5, y, at.getZ() + 0.5, mx, my);
                if (d < bestD) {
                    bestD = d;
                    String name = rumour ? m.label().substring(2) : m.label();
                    // a mark's own height is a guess when the land isn't known (a landmark's is 64): the ground's, where it is
                    BlockPos where = rumour ? at : new BlockPos(at.getX(), (int) Math.round(y), at.getZ());
                    best = new Pick(name, rumour ? "rumour" : "mark", where);
                }
            }
        }
        // your Hearths: the little gold house over each Anchor (on the clouds where the land isn't known)
        for (dev.awaken.atlas.AtlasNet.HomeView home : atlas.homes) {
            double y = Discovery.CLIENT.explored(home.x() >> 4, home.z() >> 4) ? atlas.store.groundY(home.x(), home.z(), home.y()) + 1
                    : atlas.store.groundY(home.x(), home.z(), 64) + 34;
            double d = near(proj, home.x() + 0.5, y, home.z() + 0.5, mx, my + 6);
            if (d < bestD) {
                bestD = d;
                best = new Pick(home.name(), "home", new BlockPos(home.x(), home.y(), home.z()));
            }
        }
        // waypoints: the head is the target, or the foot
        if (AtlasScreen.showWaypoints) for (AtlasNet.WaypointView w : atlas.waypoints) {
            double y = atlas.store.groundY(w.x(), w.z(), w.y());
            double d = Math.min(near(proj, w.x() + 0.5, y, w.z() + 0.5, mx, my + WaypointGlyphs.STEM + WaypointGlyphs.HEAD),
                    near(proj, w.x() + 0.5, y, w.z() + 0.5, mx, my));
            if (d < bestD) {
                bestD = d;
                best = new Pick(w.name(), "pin", new BlockPos(w.x(), w.y(), w.z()), w);
            }
        }
        if (best == null && chipZone != 0) {
            for (AtlasState.ZoneLabel z : atlas.zoneLabels()) {
                if (z.zone() != chipZone) continue;
                int x = (int) Math.floor(z.x()), zz = (int) Math.floor(z.z());
                best = new Pick(z.name(), "region", new BlockPos(x, (int) Math.round(atlas.store.groundY(x, zz, 64)), zz));
            }
        }
        if (best == null && ground != null) {
            int x = (int) Math.floor(ground[0]), z = (int) Math.floor(ground[2]);
            if (Discovery.CLIENT.explored(x >> 4, z >> 4)) best = new Pick(spotName(atlas, x, z), "spot", new BlockPos(x, (int) Math.round(ground[1]), z));
        }
        if (best == null) {
            deselect();
            return;
        }
        picked = best;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), 1.15f, 0.7f));
    }

    /** Picks a waypoint (a legend row): its card opens beside it. */
    void pick(AtlasNet.WaypointView w) {
        picked = new Pick(w.name(), "pin", new BlockPos(w.x(), w.y(), w.z()), w);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), 1.15f, 0.7f));
    }

    /** The waypoint picked, if one is. */
    AtlasNet.@Nullable WaypointView pickedWaypoint() {
        return picked == null ? null : picked.waypoint();
    }

    /** "A spot in the Commons": the land a spot lies in, by its zone. */
    static String spotName(AtlasState atlas, int x, int z) {
        int zone = atlas.zoneView().zone(x, z);
        if (zone != 0) for (AtlasState.ZoneLabel l : atlas.zoneLabels()) {
            if (l.zone() != zone) continue;
            String n = l.name();
            return "A spot in " + (n.startsWith("The ") ? "the " + n.substring(4) : n);
        }
        return "A spot you picked";
    }

    private double near(Projector proj, double x, double y, double z, double mx, double my) {
        if (!proj.at(x, y, z, p)) return Double.MAX_VALUE;
        double dx = p[0] - mx, dy = p[1] - my;
        return dx * dx + dy * dy;
    }

    /** A waypoint card's second row, or E: one of yours opens to be changed (where you fell is taken off the map). */
    boolean changeIt() {
        AtlasNet.WaypointView w = pickedWaypoint();
        if (w == null || !w.mine()) return false;
        deselect();
        if (w.fell()) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                    new AtlasNet.WaypointEdit(AtlasNet.WaypointEdit.REMOVE, 0, 0, 0, 0, "", 0, 0, false));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), 0.8f, 0.7f));
        } else change.accept(w);
        return true;
    }

    /** The card's button, or Enter: the pick becomes the destination, or the destination is cleared. */
    boolean act() {
        if (picked == null) return false;
        toggle(picked.name(), picked.kind(), picked.at());
        deselect();
        return true;
    }

    /** Sets {@code name} at {@code at} as the destination, or clears it when it is already (the card, the legend's gate row, T). */
    static void toggle(String name, String kind, BlockPos at) {
        Minecraft mc = Minecraft.getInstance();
        if (isDestination(at)) {
            Destinations.request(Optional.empty());
            mc.getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.HOVER.get(), 0.8f, 0.8f));
            return;
        }
        Identifier dim = mc.player == null ? Identifier.withDefaultNamespace("overworld") : mc.player.level().dimension().identifier();
        Destinations.request(Optional.of(new Destinations.Target(name, dim, at.immutable(), kind)));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.CONFIRM.get(), 1.0f, 0.7f));
        if (mc.player != null) mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("atlas.awaken.point"));
    }

    /** Whether the destination now set is this place (in the dimension the table shows). */
    static boolean isDestination(BlockPos at) {
        return isDestination(at.getX(), at.getZ());
    }

    static boolean isDestination(int x, int z) {
        Destinations.Target d = Destinations.mine().orElse(null);
        return d != null && d.at().getX() == x && d.at().getZ() == z && here(d);
    }

    private static boolean here(Destinations.Target d) {
        var player = Minecraft.getInstance().player;
        return player != null && player.level().dimension().identifier().equals(d.dimension());
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /**
     * The destination on the table, in its own look (a white-gold pin standing on its place, a beam up from it and a slow
     * ring round its foot, "DESTINATION" over its name), then the pick's card beside what was picked.
     */
    void draw(GuiGraphicsExtractor g, Font font, AtlasState atlas, Projector proj, int x0, int y0, int x1, int y1, float age, float zoom,
              int mouseX, int mouseY, int lx0, int ly0, int lx1, int ly1) {
        g.enableScissor(x0, y0, x1, y1);
        Destinations.Target d = Destinations.mine().orElse(null);
        if (d != null && here(d)) {
            BlockPos at = d.at();
            boolean known = Discovery.CLIENT.explored(at.getX() >> 4, at.getZ() >> 4);
            double y = known ? atlas.store.groundY(at.getX(), at.getZ(), at.getY()) : Math.max(at.getY(), atlas.store.groundY(at.getX(), at.getZ(), 64) + 34);
            if (proj.at(at.getX() + 0.5, y, at.getZ() + 0.5, p)) {
                int sx = Math.round(p[0]), sy = Math.round(p[1]);
                float pulse = 0.5f + 0.5f * (float) Math.sin(age * 2.4);
                g.fillGradient(sx, sy - 46, sx + 1, sy - 20, Glass.a(GOLD & 0xFFFFFF, 0f), Glass.a(GOLD & 0xFFFFFF, 0.9f));
                ring(g, sx, sy, 7 + Math.round(pulse * 3), Glass.a(GOLD & 0xFFFFFF, 0.25f + 0.5f * (1 - pulse)));
                ring(g, sx, sy, 4, 0xC00B1626);
                // the pin: a dark casing, then the gold head and stem
                g.fill(sx - 1, sy - 20, sx + 2, sy + 1, NAVY);
                g.fill(sx, sy - 19, sx + 1, sy, GOLD);
                g.fill(sx - 4, sy - 26, sx + 5, sy - 17, NAVY);
                g.fill(sx - 3, sy - 25, sx + 4, sy - 18, GOLD);
                g.fill(sx - 1, sy - 23, sx + 2, sy - 20, 0xFFFFFFFF);
                label(g, font, "DESTINATION", sx, sy - 60, GOLD);
                if (zoom > 0.15f) label(g, font, d.name(), sx, sy - 49, INK);
            }
        }
        cardX0 = -1;
        secondY = -1;
        if (picked != null && proj.at(picked.at().getX() + 0.5, pickY(atlas, picked), picked.at().getZ() + 0.5, p)) {
            int sx = Math.round(p[0]), sy = Math.round(p[1]);
            ring(g, sx, sy, 6, 0xE0E9F1FF);
            AtlasNet.WaypointView wp = picked.waypoint();
            boolean clear = isDestination(picked.at());
            String title = picked.name(), action = clear ? "✕ Clear destination" : "› Set as destination", key = "Enter";
            // a waypoint's card: whose it is and how far, then Change it (yours) or who can
            String sub = wp == null ? null : waypointLine(wp);
            String second = wp == null ? null : wp.fell() ? "Remove it" : wp.mine() ? "Change it" : "Only " + wp.owner() + " can change it.";
            int w = Math.max(font.width(title), font.width(action) + 10 + font.width(key));
            if (sub != null) w = Math.max(w, Math.max(font.width(sub), font.width(second) + 10 + font.width("E")));
            w += 14;
            int h = sub == null ? 30 : 54;
            // beside the spot, right or left, never over the legend (lx..: its box); with no room either side, under or over it
            int cy = Math.clamp(sy - h / 2, y0 + 4, Math.max(y0 + 4, y1 - 4 - h));
            int cx = sx + 14;
            if (cx + w > x1 - 4 || overlaps(cx, cy, w, h, lx0, ly0, lx1, ly1)) cx = sx - 14 - w;
            if (cx < x0 + 4 || overlaps(cx, cy, w, h, lx0, ly0, lx1, ly1)) {
                // clear of the legend if it fits; on a narrow screen (GUI scale 9 at 4K: 427 wide) the legend's edge can lie
                // past the last place the card fits, and Math.clamp throws when its low end passes its high end
                int lo = Math.max(x0 + 4, lx1 + 4), hi = Math.max(x0 + 4, x1 - 4 - w);
                cx = lo <= hi ? Math.clamp(sx - w / 2, lo, hi) : hi;
                cy = sy + 12 + h <= y1 - 4 ? sy + 12 : Math.max(y0 + 4, sy - 34 - h);
            }
            cx = Math.clamp(cx, x0 + 4, Math.max(x0 + 4, x1 - 4 - w));
            cy = Math.clamp(cy, y0 + 4, Math.max(y0 + 4, y1 - 4 - h));
            int edge = clear ? Glass.a(Glass.LABEL, 1f) : wp != null ? 0xFF000000 | wp.colour() : GOLD;
            g.fill(cx, cy, cx + w, cy + h, Glass.a(0x0B1626, 0.92f));
            g.fill(cx, cy, cx + 2, cy + h, edge);
            g.fill(cx + 2, cy, cx + w, cy + 1, Glass.a(0x7FC3FF, 0.55f));
            g.text(font, title, cx + 7, cy + 4, INK, false);
            int by = cy + 16;
            if (sub != null) {
                g.text(font, sub, cx + 7, cy + 14, Glass.a(Glass.LABEL, 0.95f), false);
                by = cy + 28;
            }
            boolean over = mouseX >= cx && mouseX < cx + w && mouseY >= by - 2 && mouseY < by + 12;
            g.fill(cx + 5, by - 2, cx + w - 4, by + 11, Glass.a(clear ? 0x7FC3FF : GOLD & 0xFFFFFF, over ? 0.34f : 0.18f));
            g.text(font, action, cx + 8, by, clear ? Glass.a(Glass.LABEL, 1f) : 0xFFFFFFFF, false);
            g.text(font, key, cx + w - 7 - font.width(key), by, Glass.a(Glass.LABEL, 0.8f), false);
            if (second != null) {
                int ry = by + 13;
                boolean mine = wp.mine(), overSecond = mine && mouseX >= cx && mouseX < cx + w && mouseY >= ry - 2 && mouseY < cy + h;
                if (overSecond) g.fill(cx + 5, ry - 2, cx + w - 4, ry + 10, Glass.a(0x7FC3FF, 0.22f));
                g.text(font, second, cx + 8, ry, mine ? overSecond ? 0xFFFFFFFF : INK : Glass.a(Glass.LABEL, 0.8f), false);
                if (mine) g.text(font, "E", cx + w - 7 - font.width("E"), ry, Glass.a(Glass.LABEL, 0.8f), false);
                secondY = ry;
            }
            cardX0 = cx;
            cardY0 = cy;
            cardX1 = cx + w;
            cardY1 = cy + h;
        }
        g.disableScissor();
    }

    private static boolean overlaps(int cx, int cy, int w, int h, int lx0, int ly0, int lx1, int ly1) {
        return cx + w >= lx0 && cx < lx1 && cy + h >= ly0 && cy < ly1;
    }

    private static double pickY(AtlasState atlas, Pick k) {
        BlockPos at = k.at();
        if (k.kind().equals("rumour") || !Discovery.CLIENT.explored(at.getX() >> 4, at.getZ() >> 4))
            return atlas.store.groundY(at.getX(), at.getZ(), 64) + 34;
        return atlas.store.groundY(at.getX(), at.getZ(), at.getY());
    }

    /** "Yours · 212 blocks · only you see it", "Ana's · shared with your party · 340 blocks". */
    private static String waypointLine(AtlasNet.WaypointView w) {
        var pl = Minecraft.getInstance().player;
        long far = pl == null ? 0 : Math.round(Math.hypot(w.x() + 0.5 - pl.getX(), w.z() + 0.5 - pl.getZ()));
        String dist = far >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk blocks", far / 1000.0) : far + (far == 1 ? " block" : " blocks");
        if (w.fell()) return dist + " · it goes when you get back there";
        if (w.mine()) return "Yours · " + dist + (w.shared() ? " · your party sees it" : " · only you see it");
        return w.owner() + "'s · shared with your party · " + dist;
    }

    private static void label(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int colour) {
        String t = text.length() > 40 ? text.substring(0, 39) + "…" : text;
        int tw = font.width(t);
        g.fill(cx - tw / 2 - 3, y - 2, cx + tw / 2 + 3, y + 9, 0x9A0B1626);
        g.text(font, t, cx - tw / 2, y, colour, false);
    }

    private static void ring(GuiGraphicsExtractor g, int x, int y, int r, int colour) {
        for (int k = 0; k < 48; k++) {
            double t = k * Math.PI * 2 / 48;
            int px = x + (int) Math.round(Math.cos(t) * r), py = y + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, colour);
        }
    }
}
