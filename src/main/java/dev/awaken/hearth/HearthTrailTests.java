package dev.awaken.hearth;

import dev.awaken.core.api.TrailsApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import dev.awaken.trails.Conditions;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The Hearth's trail tokens against a home (docs/trails/PLAN.md §3.3): what each word reads, and that none is met without a home. */
final class HearthTrailTests {
    private HearthTrailTests() {}

    static void register() {
        AwakenTests.register("trail_hearth_tokens", 60, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            var data = HearthData.get(sp.level().getServer());
            for (String t : new String[] {"hearth", "room:sanctuary", "grade:fine", "hearth_tier:1", "named", "cottage", "tip:guide_seen"}) {
                h.assertTrue(!Conditions.met(sp, t), "'" + t + "' is not met without a home");
            }
            HearthRecord rec = new HearthRecord(sp.getUUID(), "test", new BlockPos(0, 64, 0), HearthService.OVERWORLD);
            data.put(rec);
            try {
                Reading grand = new Reading(3, 3, 3, 3, 3, 3, Reading.Grade.GRAND, "the Grand Stone Sanctuary", "", RoomType.SANCTUARY);
                rec.rooms = List.of(new Room(EnumSet.of(RoomType.SHELTER, RoomType.BEDROOM, RoomType.SANCTUARY), new BlockPos(0, 64, 0), new BlockPos(4, 66, 4),
                        new LongOpenHashSet(), new int[0], new int[0], Set.of(), grand));
                rec.tier = 1;
                for (String t : new String[] {"hearth", "room:sanctuary", "room:bedroom", "rooms:sanctuary:1", "grade:snug", "grade:fine", "grade:grand", "hearth_tier:1", "forge_tier:1",
                        "cottage|room:sanctuary", "room:kitchen|room:shelter"}) {
                    h.assertTrue(Conditions.met(sp, t), "'" + t + "' holds in a home with a grand sanctuary");
                }
                for (String t : new String[] {"room:kitchen", "rooms:sanctuary:2", "hearth_tier:2", "forge_tier:2", "settlers:1", "cottage", "named",
                        "furniture:forge", "tip:guide_seen", "room:nonsense", "grade:nonsense", "furniture:nonsense"}) {
                    h.assertTrue(!Conditions.met(sp, t), "'" + t + "' does not");
                }
                rec.tier = 3;
                rec.forgeTier = 2;
                rec.name = "Ashford";
                rec.cottage = new BlockPos(1, 65, 1);
                rec.furniture[Furniture.FORGE.ordinal()] = 1;
                HearthService.player(sp).tips.add("guide_seen");
                for (String t : new String[] {"hearth_tier:3", "forge_tier:2", "named", "cottage", "furniture:forge", "tip:guide_seen"}) {
                    h.assertTrue(Conditions.met(sp, t), "'" + t + "' holds after the home grew");
                }
                h.assertTrue(!Conditions.met(sp, "hearth_tier:4") && !Conditions.met(sp, "forge_tier:3"), "and the next step does not");
                h.assertTrue(rec.pos.equals(TrailsApi.placeOf("hearth", sp)), "the place 'hearth' is the fire");
                for (String t : new String[] {"room:bell_tower", "rooms:bedroom:2", "grade:grand", "furniture:trophy", "holds:awaken:hearth_ember", "count:awaken:x:1", "a|b"}) {
                    h.assertTrue(Conditions.known(t) == !t.equals("a|b"), "'" + t + "' is " + (t.equals("a|b") ? "not " : "") + "a word trails knows");
                }
            } finally {
                data.remove(sp.getUUID());
            }
            h.succeed();
        });
    }
}
