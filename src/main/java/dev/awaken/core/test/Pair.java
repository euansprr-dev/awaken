package dev.awaken.core.test;

import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * The pair kit (SPEC §70.7): two real survival test players, awakened, a few blocks apart, each with a channel whose
 * packets can be read. Every two-player test starts here; what makes them a party is the test's own business.
 */
public final class Pair {
    private Pair() {}

    private static int made;

    public record Two(AwakenTests.Live a, AwakenTests.Live b) {
        public ServerPlayer first() {
            return a.player();
        }

        public ServerPlayer second() {
            return b.player();
        }

        /** Both leave the server (a test's finally). */
        public void leave() {
            a.leave();
            b.leave();
        }
    }

    /** One awakened survival player at {@code x} blocks along the test's floor. */
    public static AwakenTests.Live one(GameTestHelper h, String name, int x) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, name + "-" + ++made, new BlockPos(0, 2, 0));
        ServerPlayer p = live.player();
        Progression.awaken(p);
        BlockPos b = h.absolutePos(new BlockPos(x, 2, 0));
        p.teleportTo(h.getLevel(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5, Set.of(), 0F, 0F, true);
        live.sent(); // what joining sent is not what a test counts
        return live;
    }

    /** Two of them, two blocks apart. */
    public static Two of(GameTestHelper h) {
        return new Two(one(h, "pair", 0), one(h, "pair", 2));
    }

    /** How many of the packets sent to this player since the last look were of this class. */
    public static int count(AwakenTests.Live live, Class<?> payload) {
        int n = 0;
        for (Object o : live.sent()) {
            if (o instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket c && payload.isInstance(c.payload())) n++;
        }
        return n;
    }
}
