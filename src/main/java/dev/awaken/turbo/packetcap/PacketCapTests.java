package dev.awaken.turbo.packetcap;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.handling.QueuedPacket;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The packet cap (SPEC §28.9, Turbo): {@code turbo_packet_cap_floods} floods the GameTest server's own packet queue with
 * 400 queued tasks of a millisecond each (what the server's loop handles as packets). The hook ran and is on (the
 * probe); no tick handles them for longer than the budget, with the first always handled; the rest wait for the next
 * ticks; all run, in order. The drain alone does the same on a queue of its own.
 */
public final class PacketCapTests {
    private PacketCapTests() {}

    static final int FLOOD = 400;
    static final long TASK_NS = 1_000_000L;

    public static void register() {
        AwakenTests.registerIsolated("turbo_packet_cap_floods", 400, h -> {
            MinecraftServer server = h.getLevel().getServer();
            h.assertTrue(PacketCap.applied(), "the packet loop's hook ran (the mixin applied): " + PacketCap.status());
            h.assertTrue(PacketCap.active(), "the packet cap is on: " + PacketCap.status());

            // the drain alone: 100 one-millisecond packets, 20 ms of budget
            ConcurrentLinkedQueue<QueuedPacket> own = new ConcurrentLinkedQueue<>();
            int[] ran = {0};
            for (int i = 0; i < 100; i++) own.add(new QueuedPacket.CustomPayload(() -> {
                spin(TASK_NS);
                ran[0]++;
            }));
            long t0 = System.nanoTime();
            boolean left = PacketCap.drain(own, 20_000_000L);
            long took = System.nanoTime() - t0;
            h.assertTrue(left && own.size() == 100 - ran[0], "it left the rest queued: " + ran[0] + " ran, " + own.size() + " left");
            h.assertTrue(took < 20_000_000L + 3 * TASK_NS, String.format(Locale.ROOT, "the drain kept to its budget: %.1f ms", took / 1e6));
            h.assertTrue(!PacketCap.drain(new ConcurrentLinkedQueue<QueuedPacket>(), 0), "an empty queue leaves nothing");

            // the server's own queue, flooded: handled over several ticks, never more than the budget's worth in one
            int[] next = {0};
            boolean[] inOrder = {true};
            int[] tickOf = new int[FLOOD];
            long[] at = new long[FLOOD];
            for (int i = 0; i < FLOOD; i++) {
                int k = i;
                server.packetProcessor().scheduleIfPossible(() -> {
                    if (k != next[0]) inOrder[0] = false;
                    next[0]++;
                    tickOf[k] = server.getTickCount();
                    at[k] = System.nanoTime();
                    spin(TASK_NS);
                });
            }
            long held0 = PacketCap.heldEver;
            h.onEachTick(() -> {
                if (next[0] < FLOOD) return;
                int ticks = 1;
                long longest = 0, from = at[0];
                for (int k = 1; k <= FLOOD; k++) {
                    if (k == FLOOD || tickOf[k] != tickOf[k - 1]) {
                        longest = Math.max(longest, at[k - 1] + TASK_NS - from);
                        if (k < FLOOD) {
                            ticks++;
                            from = at[k];
                        }
                    }
                }
                String line = String.format(Locale.ROOT, "%d tasks over %d ticks, the longest tick's share %.1f ms; %s", FLOOD, ticks, longest / 1e6, PacketCap.status());
                dev.awaken.Awaken.LOG.info("[packetcap] flood: {}", line);
                h.assertTrue(inOrder[0], "handled in order: " + line);
                h.assertTrue(ticks >= FLOOD * TASK_NS / PacketCap.BUDGET_NS, "spread over the ticks: " + line);
                h.assertTrue(longest <= PacketCap.BUDGET_NS + 3 * TASK_NS, "no tick held past the budget: " + line);
                h.assertTrue(PacketCap.heldEver > held0, "the ticks that left packets were counted: " + line);
                h.succeed();
            });
        });
    }

    private static void spin(long ns) {
        long until = System.nanoTime() + ns;
        while (System.nanoTime() - until < 0) Thread.onSpinWait();
    }
}
