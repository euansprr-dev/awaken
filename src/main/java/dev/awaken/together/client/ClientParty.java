package dev.awaken.together.client;

import dev.awaken.together.TogetherNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** What this client knows of its party (SPEC §70.2): the members as the server last described them, and live pings. */
final class ClientParty {
    private ClientParty() {}

    /** A member's colour by their place in the party: the strip, the edge arrows and their pings all use it. */
    static final int[] COLOURS = {0xFFF0B79C, 0xFFB9D47A, 0xFFB48CFF, 0xFFF2A541};
    static final long PING_NANOS = 8_000_000_000L;

    /** A ping on screen: what was sent, and when it arrived. */
    record Live(TogetherNet.Ping ping, long at) {}

    private static List<TogetherNet.Member> members = List.of();
    private static final List<Live> PINGS = new ArrayList<>();

    static void receive(TogetherNet.Party m) {
        members = List.copyOf(m.members());
    }

    static void receive(TogetherNet.Ping p) {
        PINGS.removeIf(l -> l.ping().by().equals(p.by())); // one marker a person: a new one takes the old one's place
        PINGS.add(new Live(p, System.nanoTime()));
    }

    static void clear() {
        members = List.of();
        PINGS.clear();
    }

    static List<TogetherNet.Member> members() {
        return members;
    }

    static TogetherNet.@Nullable Member self() {
        for (TogetherNet.Member m : members) if (m.is(TogetherNet.F_SELF)) return m;
        return null;
    }

    /** Whether anyone else is in the party right now. */
    static boolean together() {
        return members.size() > 1;
    }

    static int colourOf(UUID id) {
        int i = 0;
        for (TogetherNet.Member m : members) {
            if (m.is(TogetherNet.F_SELF)) continue;
            if (m.id().equals(id)) return COLOURS[i % COLOURS.length];
            i++;
        }
        return COLOURS[0];
    }

    /** Pings still showing; the old ones are dropped on the way. */
    static List<Live> pings() {
        long now = System.nanoTime();
        PINGS.removeIf(l -> now - l.at() > PING_NANOS);
        return PINGS;
    }

    /** Where a member is this frame: their body when this client has it, else where the server last said. */
    static Vec3 where(TogetherNet.Member m, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Player body = mc.level == null ? null : mc.level.getPlayerByUUID(m.id());
        if (body != null) return body.getPosition(pt).add(0, body.getBbHeight() * 0.6, 0);
        return new Vec3(m.x() + 0.5, m.y() + 1.0, m.z() + 0.5);
    }

    /** Where a ping points this frame: its creature while this client has it, else the place it was made. */
    static Vec3 where(TogetherNet.Ping p, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (p.entity() >= 0 && mc.level != null) {
            Entity e = mc.level.getEntity(p.entity());
            if (e != null) return e.getPosition(pt).add(0, e.getBbHeight() + 0.4, 0);
        }
        return new Vec3(p.x(), p.y(), p.z());
    }
}
