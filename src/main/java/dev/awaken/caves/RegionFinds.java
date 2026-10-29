package dev.awaken.caves;

import dev.awaken.core.api.NoticeEvents;
import dev.awaken.progression.Progression;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The regions in the Journal (SPEC §42.9): the first time an awakened player stands in a cave region it is counted
 * ({@code awaken:discovered/cave_region/<id>}, no XP: the Wilds' biome discovery pays for the same steps and shows its
 * find) and its page is filed in the Journal without a toast: what the region is, the metal at home there and what to
 * take home from it. The Codex ({@code caves.client.RegionCodex}) and the trails read the same counter and these words.
 * Checked every two seconds per player, staggered, and only under {@link CaveRegion#MAX_Y}.
 */
public final class RegionFinds {
    private RegionFinds() {}

    static final int PERIOD = 40;
    public static final String KEY = "cave_region/";

    /**
     * A region's page: what it is, where to look for it (the Codex's hint before you've been), the metal at home there and
     * what you can take home ({@code keepsake}: its item ids, any one of them; empty for none yet), and the Journal's colour.
     */
    public record Lore(String what, String where, String metal, @Nullable String keep, List<String> keepsake, int colour) {}

    public static Lore lore(CaveRegion r) {
        return switch (r) {
            case ROOTWAYS -> new Lore("The first caves under the forests. The trees' roots come down through the roof, and glowcaps light the floor.",
                    "The first caves under a forest, taiga or jungle.",
                    "Copper and coal lodes lie here more often than anywhere else.",
                    "Root amber lies in beads on the floor under the root curtains, where the old roots bled long ago.", List.of("awaken:root_amber"), 0x6F9A84);
            case PALE_HALLS -> new Lore("Wide, flat-roofed halls of pale limestone, with still pools stepping down the slopes.",
                    "The first caves under plains, meadows, savanna or the shore.",
                    "Iron lodes lie here more often than elsewhere, in the limestone's red stains.",
                    "Cave pearls form in the puddles under the drips.", List.of("awaken:cave_pearls"), 0xB9B4A0);
            case WINDCLEFT -> new Lore("Tall, narrow cracks where the mountains split open. The wind moans through them and frost grows on the walls.",
                    "Deeper under hills and mountains, above y 0.",
                    "Gold lodes lie here more often than elsewhere, in the banded slate.",
                    "The old Delvers left their ropes hanging in the cracks.", List.of("awaken:old_rope"), 0x7C8896);
            case DROWNED_GALLERIES -> new Lore("Flooded caves under the wetlands and the shore. Lakes fill the halls, and the floors are soft silt.",
                    "Deeper under swamps, marshes and the shore, above y 0.",
                    "Lapis and copper lodes lie here more often than elsewhere.",
                    "Driftwood washes up on the lake shores.", List.of("awaken:driftwood"), 0x4F807A);
            case GLIMMER_HOLLOWS -> new Lore("Crystal caves under the hard lands. Crystals grow from the walls and light the air round them.",
                    "Deeper under deserts, badlands, savanna or frozen land, above y 0.",
                    "Redstone here grows in the glimmerstone.",
                    "Break a crystal off the wall to take home.", List.of("awaken:amber_crystal", "awaken:glacial_crystal"), 0xA99A72);
            case OCHRE_DEEP -> new Lore("Iron country. Terraces of red ochre stone, warm and dusty, with ember lichen glowing on the walls.",
                    "Deeper under forests, plains and hills, above y 0.",
                    "Iron lodes lie here more often than anywhere else.",
                    "Shears cut ember lichen off the walls. It still glows at home.", List.of("awaken:ember_lichen"), 0xC08050);
            case BLACK_VAULTS -> new Lore("The great halls at the bottom of the world: columns of black basalt and drifts of ash.",
                    "Below y 0, under any land.",
                    "Diamond lodes lie here more often than elsewhere.",
                    null, List.of(), 0x8C8078);
            case VIOLET_DEEP -> new Lore("Where the Hollows nest. Tight, warped tunnels open into violet chambers, and the rock seeps.",
                    "Below y 0, under any land.",
                    "Deepglass lodes lie here more often than anywhere else.",
                    "The Hollows shed their shells here.", List.of("awaken:shed_chitin"), 0x9B84D6);
        };
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    static void tick(MinecraftServer server) {
        if (server instanceof GameTestServer) return;
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Math.floorMod(t + p.getUUID().hashCode(), PERIOD) == 0) check(p);
        }
    }

    /** One look at the region underfoot. */
    static void check(ServerPlayer p) {
        if (p.isSpectator() || p.level().dimension() != Level.OVERWORLD || p.getBlockY() > CaveRegion.MAX_Y + 4) return;
        var biome = p.level().getBiome(p.blockPosition());
        for (CaveRegion r : CaveRegion.values()) {
            if (biome.is(r.biome())) {
                enter(p, r);
                return;
            }
        }
    }

    /** The first time in a region: counted and filed in the Journal. True when it was a first. */
    public static boolean enter(ServerPlayer p, CaveRegion r) {
        if (!Progression.profile(p).awakened() || !Progression.discover(p, KEY + r.id, 0)) return false;
        NoticeEvents.remember(p, journal(r));
        return true;
    }

    static NoticeEvents.Event journal(CaveRegion r) {
        Lore l = lore(r);
        List<String> details = new ArrayList<>(List.of(l.metal()));
        if (l.keep() != null) details.add(l.keep());
        return new NoticeEvents.Event(KEY + r.id, "Cave region", r.title(), l.what(), details, NoticeEvents.Kind.DISCOVERY, l.colour());
    }
}
