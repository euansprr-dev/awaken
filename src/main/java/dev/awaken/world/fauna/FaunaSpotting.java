package dev.awaken.world.fauna;

import dev.awaken.core.api.Spotting;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;

/**
 * Creatures and lairs spotted through the binoculars (SPEC §39.10): a creature kind you haven't studied, out to the
 * glass's reach (past {@link Sightings#RANGE}), is studied as by the crosshair; a den of a beast whose lair you haven't
 * found pays the lair's discovery and goes on your tracker (it is "found" in the band's sense when you stand in it).
 */
final class FaunaSpotting {
    private FaunaSpotting() {}

    /** How far round the den a lair is seen (blocks: its clearing and bones). */
    static final int LAIR_HALF = 6, LAIR_BELOW = 2, LAIR_ABOVE = 7;

    static void register() {
        Spotting.register(CREATURES);
        Spotting.register(LAIRS);
    }

    static final Spotting.Source CREATURES = new Spotting.Source() {
        @Override
        public boolean moves() {
            return true;
        }

        @Override
        public void gather(ServerPlayer p, double reach, Spotting.Sink out) {
            List<FaunaMob> live = FaunaCensus.in(p.level());
            if (live.isEmpty()) return;
            Set<String> known = Sightings.known(p);
            if (known.size() >= Bestiary.fauna().size()) return;
            double r2 = reach * reach;
            for (int i = 0, n = live.size(); i < n; i++) {
                FaunaMob m = live.get(i);
                if (m.isAlive() && !known.contains(m.def().id) && m.distanceToSqr(p) <= r2) out.entity(m, m);
            }
        }

        @Override
        public boolean find(ServerPlayer p, Object ref) {
            return ref instanceof FaunaMob m && m.isAlive() && !Sightings.known(p).contains(m.def().id) && Sightings.discover(p, m.def());
        }
    };

    static final Spotting.Source LAIRS = new Spotting.Source() {
        @Override
        public boolean moves() {
            return false;
        }

        @Override
        public void gather(ServerPlayer p, double reach, Spotting.Sink out) {
            ServerLevel level = p.level();
            if (level.dimension() != Level.OVERWORLD) return;
            LairRegistry reg = LairRegistry.get(level.getServer());
            if (reg.isEmpty()) return;
            double r2 = reach * reach;
            for (LairRegistry.Entry e : reg.all()) {
                BlockPos d = e.den();
                double dx = d.getX() + 0.5 - p.getX(), dz = d.getZ() + 0.5 - p.getZ();
                if (dx * dx + dz * dz > r2 || spotted(p, e.beast())) continue;
                out.place(e.key(), d.getX() - LAIR_HALF, d.getY() - LAIR_BELOW, d.getZ() - LAIR_HALF,
                        d.getX() + 1 + LAIR_HALF, d.getY() + LAIR_ABOVE, d.getZ() + 1 + LAIR_HALF, e);
            }
        }

        @Override
        public boolean find(ServerPlayer p, Object ref) {
            if (!(ref instanceof LairRegistry.Entry e) || LairRegistry.get(p.level().getServer()).get(e.key()) == null) return false;
            return Lairs.spotted(p, e);
        }
    };

    /** The lair's discovery is per beast: one of its dens found or spotted, the rest are nothing new. */
    static boolean spotted(ServerPlayer p, String beast) {
        return Progression.profile(p).counter("awaken:discovered/lair/" + beast) > 0;
    }
}
