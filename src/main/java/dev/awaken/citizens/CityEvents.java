package dev.awaken.citizens;

import net.minecraft.server.level.ServerPlayer;

/** The city's own troubles (brief M3 §5): filled in below the core. */
final class CityEvents {
    final Citizens c;

    CityEvents(Citizens c) {
        this.c = c;
    }

    void tick(long now, int tod) {}

    void roleArrived(int i, long now) {}

    void show(CitizenBody b, int i, long now) {}

    boolean hitByPlayer(ServerPlayer p, CitizenBody b, int rec) {
        return false;
    }

    void actorDown(CitizenBody b, int rec) {}
}
