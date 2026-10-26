package dev.awaken.progression.client;

import dev.awaken.core.api.MapMarks;
import dev.awaken.core.api.Promises;
import dev.awaken.progression.PromiseBook;
import dev.awaken.progression.ProgressionModule;
import dev.awaken.progression.Rank;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "What waits" (SPEC §27): the locks the player has met and not opened, from their synced {@link PromiseBook}. Each row:
 * what it waits for (a chip in the rank's colour, or the Horizon's green for a rung), what it is, and how far; a lock
 * that answers them now leads, marked READY. The same locks are pinned on the Atlas and the Gate Map ({@link #marks}).
 */
final class WaitingSection extends UiSection {
    static final int SHOWN = 8;

    public String title() { return "SAVED FOR LATER"; }

    public int order() { return 5; } // right under the spine and its next-rank line: the long goals

    public boolean visible() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && !book(p).isEmpty();
    }

    static PromiseBook book(Player p) {
        return p.getData(ProgressionModule.PROMISES.get());
    }

    @Override
    protected void build(Ui ui) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        List<PromiseBook.Entry> all = sorted(p);
        for (int i = 0; i < all.size() && i < SHOWN; i++) {
            PromiseBook.Entry e = all.get(i);
            Ui.Flow row = ui.flow();
            if (e.answered()) row.chip("READY", Glass.GOOD);
            else row.chip(e.need().words().toUpperCase(java.util.Locale.ROOT), colour(e.need()));
            row.text(e.what(), e.answered() ? Glass.TITLE : Glass.TEXT);
            String far = where(p, e);
            if (!far.isEmpty()) row.text(far, Glass.LABEL);
            row.end();
        }
        if (all.size() > SHOWN) ui.text("and " + (all.size() - SHOWN) + " more, pinned on the Atlas.", Glass.LABEL);
    }

    /** Answered first, then the nearest in this dimension, then the rest. */
    static List<PromiseBook.Entry> sorted(Player p) {
        List<PromiseBook.Entry> all = new ArrayList<>(book(p).entries());
        String dim = p.level().dimension().identifier().toString();
        all.sort(Comparator.comparing((PromiseBook.Entry e) -> !e.answered())
                .thenComparingDouble(e -> e.where().isPresent() && e.dim().equals(dim) ? dist(p.blockPosition(), e.where().get()) : Double.MAX_VALUE));
        return all;
    }

    /** The line under the next rank (the band's "Far"): what this player has met that the next rank opens. */
    static List<String> nextRankOpens(Player p, Rank next) {
        List<String> out = new ArrayList<>();
        for (PromiseBook.Entry e : book(p).entries()) {
            if (e.kind().equals(Promises.RANK) && e.value().equalsIgnoreCase(next.label) && !out.contains(e.what())) out.add(e.what());
        }
        return out;
    }

    /** Pins on the Atlas and the Gate Map: each lock with a place in this dimension, in its need's colour. */
    static List<MapMarks.Mark> marks(Player p) {
        PromiseBook b = book(p);
        if (b.isEmpty()) return List.of();
        String dim = p.level().dimension().identifier().toString();
        List<MapMarks.Mark> out = new ArrayList<>();
        for (PromiseBook.Entry e : b.entries()) {
            if (e.where().isEmpty() || !e.dim().equals(dim)) continue;
            String label = e.what() + (e.answered() ? " · ready now" : " · needs " + e.need().words());
            out.add(new MapMarks.Mark(e.where().get(), e.answered() ? Glass.GOOD : colour(e.need()), label, false));
        }
        return out;
    }

    static int colour(Promises.Need need) {
        return switch (need.kind()) {
            case Promises.RANK -> {
                for (Rank r : Rank.values()) if (r.label.equalsIgnoreCase(need.value())) yield r.colour;
                yield Glass.AMBER;
            }
            case Promises.TIER -> Glass.HORIZON;
            default -> Glass.AMBER;
        };
    }

    private static String where(Player p, PromiseBook.Entry e) {
        if (e.where().isEmpty()) return "";
        if (!e.dim().equals(p.level().dimension().identifier().toString())) return "elsewhere";
        BlockPos to = e.where().get();
        int dx = to.getX() - p.getBlockX(), dz = to.getZ() - p.getBlockZ();
        double ang = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return String.format("%,d m %s", Math.round(Math.sqrt((double) dx * dx + (double) dz * dz)), names[(int) Math.round(ang / 45.0) % 8]);
    }

    private static double dist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
