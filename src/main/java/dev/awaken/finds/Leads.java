package dev.awaken.finds;

import dev.awaken.finds.verb.Ctx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Leads (SPEC §58.6): a find that points at another, further out. The player gets a direction and nothing else: no
 * marker, no distance, no name. Kept in their Journal as "from where, which way". The lead is a real site, the nearest
 * find (never a curio) the player hasn't done that lies further from home than where they stand.
 */
public final class Leads {
    private Leads() {}

    private static final String[] WAYS = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};

    /** The compass word for the way from one place to another. */
    public static String way(int fromX, int fromZ, int toX, int toZ) {
        double a = Math.atan2(-(toX - fromX), toZ - fromZ); // 0 is south (+z), turning toward west
        int i = (int) Math.floorMod(Math.round(a / (Math.PI / 4)), 8L);
        return WAYS[i];
    }

    static void give(Ctx c, ServerPlayer p) {
        FindsData d = p.getData(FindsContent.FINDS);
        BlockPos spawn = c.level.getRespawnData().pos();
        Site to = Sites.lead(c.level, c.site.x(), c.site.z(), spawn.getX(), spawn.getZ(), k -> d.count(k.id()) == 0 && k != c.kind());
        if (to == null) return;
        String way = way(c.site.x(), c.site.z(), to.x(), to.z());
        // from|x|z|way|toKey: the Journal shows the first four; the key lets the lead close when its find is done
        p.setData(FindsContent.FINDS, p.getData(FindsContent.FINDS).withLead(c.kind().name() + "|" + c.site.x() + "|" + c.site.z() + "|" + way + "|" + to.key()));
    }
}
