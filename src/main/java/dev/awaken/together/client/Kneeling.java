package dev.awaken.together.client;

import dev.awaken.core.api.PlayerClips;
import dev.awaken.together.TogetherNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A knee you can see (SPEC §70.3, §70.4): a party member on a second wind, or down and waiting for a hand, kneels in
 * third person for everyone of the party who can see them (the stations' kneel, held). Without the animation library
 * nothing plays and the strip and the lines still say it.
 */
final class Kneeling {
    private Kneeling() {}

    private static final String CLIP = "station_kneel";
    /** Who this client has put on a knee, and the body it did it to. */
    private static final Map<UUID, Integer> ON = new HashMap<>();

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ON.clear();
            return;
        }
        for (TogetherNet.Member m : ClientParty.members()) {
            boolean kneel = m.is(TogetherNet.F_DOWN) || m.is(TogetherNet.F_KNEEL);
            Player body = mc.level.getPlayerByUUID(m.id());
            Integer was = ON.get(m.id());
            if (kneel && body != null && (was == null || was != body.getId())) {
                if (PlayerClips.play(body.getId(), CLIP, 3)) ON.put(m.id(), body.getId());
            } else if ((!kneel || body == null) && was != null) {
                PlayerClips.stop(was);
                ON.remove(m.id());
            }
        }
        // someone who left the party while on a knee gets up on this screen too
        ON.entrySet().removeIf(e -> {
            for (TogetherNet.Member m : ClientParty.members()) if (m.id().equals(e.getKey())) return false;
            PlayerClips.stop(e.getValue());
            return true;
        });
    }

    static void clear() {
        ON.clear();
    }
}
