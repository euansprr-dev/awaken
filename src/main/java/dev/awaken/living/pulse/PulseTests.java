package dev.awaken.living.pulse;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.api.Regions;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The World Pulse's GameTests (SPEC §21.6): its calendar, a happening's life, the Almanac, the Deep Tide; the Rolling Storm. */
public final class PulseTests {
    private PulseTests() {}

    public static void register() {
        AwakenTests.registerIsolated("living_pulse", 400, h -> {
            ServerLevel level = h.getLevel();
            // the calendar: the Hunt only under a full moon at night, the Tide only on the new moon, caravans by day
            HappeningDef hunt = HappeningBook.get("wild_hunt"), tide = HappeningBook.get("deep_tide"), caravan = HappeningBook.get("caravan");
            h.assertTrue(hunt != null && tide != null && caravan != null, "the launch kinds load");
            h.assertTrue(Pulse.fits(hunt, "night", "clear", 0, 1) && !Pulse.fits(hunt, "night", "clear", 3, 1) && !Pulse.fits(hunt, "day", "clear", 0, 1),
                    "the Wild Hunt runs on full-moon nights only");
            h.assertTrue(!Pulse.fits(hunt, "night", "clear", 0, 0), "and not in the Commons' band");
            h.assertTrue(Pulse.fits(tide, "dusk", "rain", 4, 0) && !Pulse.fits(tide, "dusk", "rain", 0, 0), "the Deep Tide rises on the new moon");
            h.assertTrue(Pulse.fits(caravan, "day", "clear", 5, 2) && !Pulse.fits(caravan, "night", "clear", 2, 2), "caravans travel by day");
            h.assertTrue(Rumours.direction(0, -10).equals("north") && Rumours.direction(10, 0).equals("east"), "rumours give a direction");

            Pulse pulse = Pulse.get(level.getServer());
            Pulse.endAll(level.getServer());
            BlockPos a = h.absolutePos(new BlockPos(-8, 1, 0)), b = h.absolutePos(new BlockPos(8, 1, 0));
            AwakenTests.clearAbove(level, a.offset(8, 0, 0), 12, 0, 6);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Happening mig = null;
            try {
                Progression.awaken(p);
                // a migration: its herd arrives, walks, and leaves with it
                mig = pulse.openAt(level, "great_migration", a, b);
                h.assertTrue(mig != null && mig.participants.size() >= 10, "a herd of ten or more arrives: " + (mig == null ? 0 : mig.participants.size()));
                h.assertTrue(Behaviours.alive(level, mig) >= 10, "and stands in the world");
                // the Almanac: standing near it is witnessing it, once
                pulse.run(level); // the herd stands where the ground really is (a plot may sit under terrain): find it
                p.setPos(mig.centre.add(2, 1, 2));
                pulse.run(level);
                h.assertTrue(mig.witnessed.contains(p.getUUID()), "witnessed");
                h.assertTrue(Progression.profile(p).counter("awaken:discovered/happening/great_migration") == 1, "a discovery in the Almanac");
                long count = Progression.profile(p).counter("awaken:happenings_witnessed");
                pulse.run(level);
                h.assertTrue(Progression.profile(p).counter("awaken:happenings_witnessed") == count, "witnessed once, not every second");
                // it ends on its own, and its creatures go (no player near them)
                p.setPos(Vec3.atCenterOf(a.offset(400, 0, 0)));
                List<java.util.UUID> herd = List.copyOf(mig.participants);
                Pulse.endAll(level.getServer());
                h.assertTrue(pulse.active().isEmpty(), "ended");
                // gone, or (with someone near) left behind as ordinary creatures, never still the happening's
                h.assertTrue(herd.stream().noneMatch(id -> level.getEntity(id) != null && level.getEntity(id).isAlive() && Pulse.participant(level.getEntity(id))),
                        "the herd moved on");
                // the Deep Tide: the night's hunters run two levels harder while it's up
                BlockPos at = h.absolutePos(new BlockPos(0, 1, 0));
                int before = Regions.creatureLevel(level, at, Regions.Role.HUNTER), calm = Regions.creatureLevel(level, at, Regions.Role.NEUTRAL);
                Happening t = pulse.openAt(level, "deep_tide", at, at);
                h.assertTrue(t != null, "the tide rises");
                h.assertTrue(Regions.creatureLevel(level, at, Regions.Role.HUNTER) == Math.min(before + Tides.EXTRA, dev.awaken.living.region.RegionMap.MAX_LEVEL), "hunters two levels harder");
                h.assertTrue(Regions.creatureLevel(level, at, Regions.Role.NEUTRAL) == calm, "grazers no different");
                Pulse.endAll(level.getServer());
                h.assertTrue(Regions.creatureLevel(level, at, Regions.Role.HUNTER) == before, "and back when it ebbs");
            } finally {
                Pulse.endAll(level.getServer());
            }
            h.succeed();
        });

        // the Rolling Storm: the cell's one model (the client draws, rains and thunders by it) and its strikes
        AwakenTests.registerIsolated("living_storm", 200, h -> {
            ServerLevel level = h.getLevel();
            HappeningDef storm = HappeningBook.get("rolling_storm");
            h.assertTrue(storm != null && storm.durationS() == (int) StormCell.DURATION_S, "the client times the storm's end by its real length");
            h.assertTrue(StormCell.baseY(70) == 162 && StormCell.baseY(300) == StormCell.BASE_MAX && StormCell.baseY(-20) == StormCell.BASE_MIN,
                    "its base sits over the land it first stood on");
            h.assertTrue(StormCell.weatherAt(10) == 1F && StormCell.weatherAt(200) == 0F && StormCell.weatherAt(80) > 0F && StormCell.weatherAt(80) < 1F,
                    "its rain falls on you near its core and fades away from it");
            h.assertTrue(StormCell.thunderDelay(StormCell.SOUND_SPEED * 2) == 2F && StormCell.thunderDelay(10) < 0.1F, "thunder comes later the farther the strike");
            h.assertTrue(StormCell.heard(300, 300) && !StormCell.heard(600, 300), "a strike reaches players past the bolt's own 256 blocks, to 640");
            h.assertTrue(StormCell.strength(0F, 0F, 240F) == 0F && StormCell.strength(30F, 0.5F, 240F) == 1F && StormCell.strength(30F, 1F, 240F) == 0F,
                    "it builds, holds and rains itself out");
            // the hook carries the cell to Lens: camera-relative, downwind a unit, its flash where it struck
            float[] out = new float[StormCell.FLOATS];
            StormCell.pack(out, 0, 1000, 2000, 162, 0.5F, 3F, 4F, 70, 12F, 1010, 180, 1990, 0.8F, 990, 64, 2010);
            h.assertTrue(out[0] == 10F && out[1] == -10F && out[2] == 162F && out[3] == 0.5F, "the cell, camera-relative, on its base");
            h.assertTrue(Math.abs(out[4] - 0.6F) < 1e-6F && Math.abs(out[5] - 0.8F) < 1e-6F && out[6] == 70F && out[7] == 12F, "its wind and the ground under it");
            h.assertTrue(out[8] == 20F && out[9] == 116F && out[10] == -20F && out[11] == 0.8F, "its flash, camera-relative");
            // a strike on sand turns it to glass, where strikes may change the land (this plot lands somewhere real: near a
            // gate or on a Hearth's land, its sand must stay sand)
            BlockPos at = h.absolutePos(new BlockPos(0, 2, 0));
            AwakenTests.clearAbove(level, at, 2, 0, 8);
            level.setBlock(at.below(), Blocks.SAND.defaultBlockState(), 3);
            boolean allowed = Behaviours.allowed(level, at);
            StormCell.strike(level, at);
            h.assertTrue(level.getBlockState(at.below()).is(allowed ? Blocks.GLASS : Blocks.SAND),
                    allowed ? "struck sand turns to glass" : "protected land keeps its sand");
            h.succeed();
        });
    }
}
