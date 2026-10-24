package dev.awaken.citizens;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** What the city remembers of players (brief M3 §6): filled in below the core. */
final class Recognition {
    static final Identifier HIT_CITIZEN = Awaken.id("hit_citizen");
    final Citizens c;

    Recognition(Citizens c) {
        this.c = c;
    }

    void tick(long now, int tod) {}

    boolean think(CitizenBody b, int rec, ServerPlayer near, double distSq, long now) {
        return false;
    }

    void witness(ServerPlayer p, Identifier deed, BlockPos pos, int weight) {}
}
