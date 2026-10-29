package dev.awaken.caves.client;

import dev.awaken.caves.CaveRegion;
import dev.awaken.caves.RegionFinds;
import dev.awaken.progression.Progression;
import dev.awaken.progression.client.CodexEntries;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The cave regions in the Codex (SPEC §42.9), one page each, read from the same counter the Journal's line sets: a region
 * you've stood in shows what it is, its metal and what to take home; one you haven't shows only where to look. Shown once
 * you've found the first, so a world without the regions never lists them.
 */
final class RegionCodex {
    private RegionCodex() {}

    static final String CATEGORY = "Cave regions";

    static List<CodexEntries.Entry> entries() {
        var player = Minecraft.getInstance().player;
        if (player == null) return List.of();
        var profile = Progression.profile(player);
        List<CaveRegion> found = new ArrayList<>();
        for (CaveRegion r : CaveRegion.values()) if (profile.counter("awaken:discovered/" + RegionFinds.KEY + r.id) > 0) found.add(r);
        if (found.isEmpty()) return List.of();
        List<String> ids = found.stream().map(RegionCodex::id).toList();
        List<CodexEntries.Entry> out = new ArrayList<>();
        for (CaveRegion r : CaveRegion.values()) {
            RegionFinds.Lore l = RegionFinds.lore(r);
            if (!found.contains(r)) {
                // not found yet: the card shows only where to look
                out.add(new CodexEntries.Entry(id(r), CATEGORY, l.where(), "", false, List.of()));
                continue;
            }
            String body = l.what() + " " + l.metal() + (l.keep() == null ? "" : " " + l.keep());
            out.add(new CodexEntries.Entry(id(r), CATEGORY, r.title(), body, true, ids.stream().filter(x -> !x.equals(id(r))).toList()));
        }
        return out;
    }

    private static String id(CaveRegion r) {
        return "cave_region_" + r.id;
    }
}
