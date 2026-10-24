package dev.awaken.story;

import dev.awaken.Awaken;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Vael's beats. Patent rule: they advance only on the player's own rank milestones and never react to what the player did
 * to other NPCs.
 */
final class Rival {
    private Rival() {}

    private record Scene(ServerPlayer player, List<String> lines, int[] next, VaelEntity[] vael, long startAt) {}

    private static final List<Scene> SCENES = new ArrayList<>();

    static void forget() {
        SCENES.clear();
    }

    static void onRankUp(ServerPlayer p, Rank rank) {
        String key = rank == Rank.SOVEREIGN ? "sovereign" : rank.name().toLowerCase();
        StoryContent.RivalBeat beat = StoryContent.RIVAL.get(Awaken.id(key));
        if (beat == null) return;
        StoryProgress s = Prologue.progress(p);
        if (!s.seen.add("rival:" + key)) return;
        Prologue.sync(p);
        SCENES.add(new Scene(p, beat.lines(), new int[]{0}, new VaelEntity[1], p.level().getGameTime() + 20 * 8));
    }

    static void tick(MinecraftServer server) {
        Iterator<Scene> it = SCENES.iterator();
        while (it.hasNext()) {
            Scene sc = it.next();
            ServerPlayer p = sc.player();
            if (p.isRemoved()) { it.remove(); continue; }
            long now = p.level().getGameTime();
            if (now < sc.startAt()) continue;
            int i = sc.next()[0];
            if (sc.vael()[0] == null && i == 0) sc.vael()[0] = spawnNear(p);
            if (i >= sc.lines().size()) {
                if (sc.vael()[0] != null) sc.vael()[0].lifeTicks = Math.min(sc.vael()[0].lifeTicks, 100);
                it.remove();
                continue;
            }
            if ((now - sc.startAt()) >= i * 20L * 6) {
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Vael"))
                        .body(Component.literal(sc.lines().get(i))).ttl(20 * 6).priority(6).build());
                sc.next()[0] = i + 1;
            }
        }
    }

    static VaelEntity spawnNear(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return null;
        var look = p.getLookAngle();
        BlockPos at = BlockPos.containing(p.getX() + look.x * 4, p.getY(), p.getZ() + look.z * 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        if (Math.abs(y - p.getY()) > 6) y = (int) Math.floor(p.getY());
        VaelEntity v = StoryModule.VAEL.get().create(level, EntitySpawnReason.TRIGGERED);
        if (v == null) return null;
        v.snapTo(at.getX() + 0.5, y, at.getZ() + 0.5, p.getYRot() + 180, 0);
        v.viewer = p.getUUID(); // the beat is this player's: nobody else nearby sees Vael
        level.addFreshEntity(v);
        return v;
    }
}
