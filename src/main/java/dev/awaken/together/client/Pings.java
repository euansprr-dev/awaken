package dev.awaken.together.client;

import dev.awaken.core.api.Aim;
import dev.awaken.together.TogetherContent;
import dev.awaken.together.TogetherNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.Tags;

/**
 * Pointing (SPEC §70.2), on the key that locks on. A press that locked or let go is the lock's; in a party, any other
 * press points at what the crosshair is on, with one line chosen by what it is. Twice in a third of a second says
 * "Careful here". What you lock is shown to the party as your target, with no press of its own.
 */
final class Pings {
    private Pings() {}

    static final double REACH = 128;
    private static final long DOUBLE_NANOS = 350_000_000L;
    private static long lastPress;
    private static Vec3 lastAt = Vec3.ZERO;
    private static int sharedTarget = -1;

    /** The lock key was pressed and the lock didn't want it. True if it became a ping (then pick block doesn't get it). */
    static boolean press() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || !ClientParty.together()) return false;
        long now = System.nanoTime();
        if (now - lastPress < DOUBLE_NANOS) {
            lastPress = 0;
            send(TogetherNet.CAREFUL, -1, lastAt, "Careful here");
            return true;
        }
        var self = ClientParty.self();
        boolean kneeling = self != null && (self.is(TogetherNet.F_DOWN) || self.is(TogetherNet.F_KNEEL));
        Entity seen = mc.crosshairPickEntity;
        Vec3 at;
        String line;
        int entity = -1;
        if (seen != null) {
            at = seen.position().add(0, seen.getBbHeight() + 0.4, 0);
            entity = seen.getId();
            line = seen instanceof Enemy ? "This one" : seen instanceof LivingEntity ? "Look at this" : "Over here";
        } else {
            HitResult hit = me.pick(REACH, 1F, false);
            if (hit.getType() != HitResult.Type.BLOCK) return false; // the open sky is nothing to point at
            BlockHitResult block = (BlockHitResult) hit;
            at = block.getLocation();
            BlockState state = mc.level.getBlockState(block.getBlockPos());
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (state.is(Tags.Blocks.ORES)) line = "Ore here";
            else if (id.getNamespace().equals("awaken") && id.getPath().startsWith("gate_")) line = "Let's go in";
            else if (id.getNamespace().equals("awaken") && (id.getPath().contains("bell") || id.getPath().contains("monument") || id.getPath().contains("cache"))) line = "Look at this";
            else line = "Over here";
        }
        if (kneeling) line = "Help";
        lastPress = now;
        lastAt = at;
        send(TogetherNet.HERE, entity, at, line);
        return !me.isCreative(); // in creative the same press still picks the block, as it always has
    }

    private static void send(int kind, int entity, Vec3 at, String line) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) return;
        TogetherNet.Ping p = new TogetherNet.Ping(me.getUUID(), me.getGameProfile().name(), kind, entity, at.x, at.y, at.z, line);
        ClientPacketDistributor.sendToServer(p);
        if (kind != TogetherNet.TARGET) ClientParty.receive(p); // your own marker shows for you too, at once
    }

    /** Each tick: a new lock is told to the party once, as "their target". */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientParty.together()) {
            sharedTarget = -1;
            return;
        }
        int now = Aim.locked() ? Aim.target() : -1;
        if (now == sharedTarget) return;
        sharedTarget = now;
        if (now < 0) return;
        Entity e = mc.level.getEntity(now);
        if (e != null) send(TogetherNet.TARGET, now, e.position().add(0, e.getBbHeight() + 0.4, 0), "Their target");
    }

    static void received(TogetherNet.Ping p) {
        ClientParty.receive(p);
        if (p.kind() != TogetherNet.TARGET && TogetherClientConfig.pingSound()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(TogetherContent.PING.value(), p.kind() == TogetherNet.CAREFUL ? 0.8F : 1F, 0.6F));
        }
    }

    static void clear() {
        sharedTarget = -1;
        lastPress = 0;
    }
}
