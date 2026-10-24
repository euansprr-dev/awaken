package dev.awaken.hearth.client;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.GuideData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of "Your Hearth": opens and updates the screen, asks for fresh numbers while it is open, and draws the
 * two world hints: the Hearth's edge as a faint ring of embers at ground level for a few seconds after the Anchor is
 * used, and a pulsing signal-blue mark where a room leaks while the screen is open (and half a minute after).
 * Cheap: a couple of dozen particles a tick for eight seconds, a mark every few ticks.
 */
public final class GuideClient {
    private GuideClient() {}

    static final int RING_TICKS = 160, MARK_AFTER_CLOSE = 20 * 30, REFRESH_EVERY = 40;

    private static @Nullable GuideData data;
    private static long ringUntil, marksUntil;
    private static int sinceRefresh;
    private static final List<BlockPos> MARKS = new ArrayList<>();

    static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(Guide.Show.TYPE, (msg, ctx) -> onShow(msg));
    }

    private static void onShow(Guide.Show msg) {
        Minecraft mc = Minecraft.getInstance();
        data = msg.data();
        MARKS.clear();
        for (GuideData.RoomCard c : data.rooms()) c.mark().ifPresent(MARKS::add);
        if (mc.gui.screen() instanceof HearthScreen s) {
            s.update(data);
            if (msg.open() && mc.level != null) ringUntil = mc.level.getGameTime() + RING_TICKS;
        } else if (msg.open()) {
            if (mc.level != null) ringUntil = mc.level.getGameTime() + RING_TICKS;
            mc.gui.setScreen(new HearthScreen(data));
        }
    }

    static @Nullable GuideData data() {
        return data;
    }

    /** Leaving a world forgets its Hearth (game times differ between worlds). */
    static void clear() {
        data = null;
        ringUntil = 0;
        marksUntil = 0;
        MARKS.clear();
    }

    static void send(int action) {
        ClientPacketDistributor.sendToServer(new Guide.Act(action));
    }

    /** The screen closed: stop the updates; the marks linger a little so you can walk to them. */
    static void closed() {
        send(Guide.Act.CLOSED);
        var mc = Minecraft.getInstance();
        if (mc.level != null) marksUntil = mc.level.getGameTime() + MARK_AFTER_CLOSE;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || data == null) return;
        long now = level.getGameTime();
        boolean open = mc.gui.screen() instanceof HearthScreen;
        if (open && ++sinceRefresh >= REFRESH_EVERY) {
            sinceRefresh = 0;
            send(Guide.Act.REFRESH);
        }
        if (now < ringUntil) ring(level, mc.player.blockPosition(), now);
        if ((open || now < marksUntil) && now % 5 == 0) {
            for (BlockPos p : MARKS) {
                if (p.distSqr(mc.player.blockPosition()) > 96 * 96) continue;
                level.addParticle(HearthContent.HEARTH_LEAK.get(), true, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 0, 0, 0);
            }
        }
    }

    /** Embers along the square edge of the radius, on the ground, near the player. */
    private static void ring(ClientLevel level, BlockPos player, long now) {
        GuideData d = data;
        if (d == null) return;
        int r = d.radius(), ax = d.anchor().getX(), az = d.anchor().getZ();
        RandomSource rand = level.getRandom();
        // fade in over the first second, out over the last two
        long left = ringUntil - now, age = RING_TICKS - left;
        int count = (int) (24 * Math.min(1.0, Math.min(age / 20.0, left / 40.0)));
        for (int i = 0; i < count; i++) {
            int t = rand.nextInt(8 * r);
            int side = t / (2 * r), along = t % (2 * r) - r;
            int x = switch (side) { case 0 -> ax + along; case 1 -> ax + r; case 2 -> ax - along; default -> ax - r; };
            int z = switch (side) { case 0 -> az - r; case 1 -> az + along; case 2 -> az + r; default -> az - along; };
            if (Math.abs(x - player.getX()) > 96 || Math.abs(z - player.getZ()) > 96 || !level.hasChunkAt(x, z)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            level.addParticle(HearthContent.HEARTH_RING.get(), true, true, x + rand.nextDouble(), y + 0.1, z + rand.nextDouble(), 0, 0.01, 0);
        }
    }
}
