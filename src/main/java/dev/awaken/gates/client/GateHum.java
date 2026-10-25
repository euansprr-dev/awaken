package dev.awaken.gates.client;

import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.gates.GateVortex;
import dev.awaken.gates.GateVortexBlockEntity;
import dev.awaken.gates.GatesRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hum of a surface gate: two looping sounds at the vortex centre (a low beacon hum and a slow portal whoosh) that
 * swell as you come near (linear falloff over 48 blocks, set in sounds.json). Lower for higher ranks, harsher and
 * louder once broken, soft when the gates are calm, pulsing while a break is near, nearly silent when spent.
 */
final class GateHum {
    private GateHum() {}

    static final double START = 50, STOP = 58;
    private static final Map<GateVortexBlockEntity, Loop[]> PLAYING = new HashMap<>();
    private static int clock;

    static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stopAll(mc);
            return;
        }
        if (++clock % 10 != 0) return;
        Vec3 me = mc.player.position();
        for (GateVortexBlockEntity be : GateVortexBlockEntity.CLIENT_LOADED) hush(be, me);
        for (GateVortexBlockEntity be : List.copyOf(GateVortexBlockEntity.CLIENT_LOADED)) {
            if (be.isRemoved() || be.getLevel() != mc.level || PLAYING.containsKey(be)) continue;
            if (me.distanceToSqr(be.centreX(), be.centreY(), be.centreZ()) > START * START) continue;
            Loop hum = new Loop(be, GatesRegistry.SND_HUM.get(), 0.9F, 0.86F - 0.065F * be.rank());
            Loop whoosh = new Loop(be, GatesRegistry.SND_VORTEX.get(), 0.55F, 0.62F - 0.03F * be.rank());
            mc.getSoundManager().play(hum);
            mc.getSoundManager().play(whoosh);
            PLAYING.put(be, new Loop[]{hum, whoosh});
        }
        List<GateVortexBlockEntity> done = new ArrayList<>();
        PLAYING.forEach((be, loops) -> {
            boolean gone = be.isRemoved() || be.getLevel() != mc.level
                    || me.distanceToSqr(be.centreX(), be.centreY(), be.centreZ()) > STOP * STOP;
            boolean ended = true;
            for (Loop l : loops) {
                if (gone) l.end();
                ended &= !mc.getSoundManager().isActive(l) || l.isStopped();
            }
            if (gone || ended) done.add(be);
        });
        for (GateVortexBlockEntity be : done) PLAYING.remove(be);
    }

    /**
     * The world holds its breath near a gate (world sound, SPEC §13): birds, insects and the nature ambience step back
     * within 48 blocks of an E-rank gate to 108 of an S-rank, deepest at its heart; wider and deeper while a break is
     * near, deepest once broken; barely at all when the gates are calm, not at all when spent. Renewed every half
     * second while the gate is in range, so it lets go on its own when you leave.
     */
    private static void hush(GateVortexBlockEntity be, Vec3 me) {
        if (be.isRemoved() || be.getLevel() != Minecraft.getInstance().level) return;
        float radius = 48F + 12F * be.rank(), depth;
        switch (be.mode()) {
            case GateVortex.WARNING -> {
                radius *= 1.4F;
                depth = 0.15F;
            }
            case GateVortex.BROKEN -> depth = 0.1F;
            case GateVortex.FORMING -> depth = 0.6F;
            case GateVortex.CALM -> depth = 0.85F;
            case GateVortex.SPENT -> {
                return;
            }
            default -> depth = 0.35F;
        }
        Vec3 c = new Vec3(be.centreX(), be.centreY(), be.centreZ());
        if (me.distanceToSqr(c) > radius * radius) return;
        Soundscape.hush(be, c, radius, depth, 30);
    }

    private static void stopAll(Minecraft mc) {
        if (PLAYING.isEmpty()) return;
        for (Loop[] loops : PLAYING.values()) for (Loop l : loops) l.end();
        PLAYING.clear();
    }

    /** One looping layer, eased in and out, following its gate's look. */
    static final class Loop extends AbstractTickableSoundInstance {
        private final GateVortexBlockEntity be;
        private final float base;
        private float level;
        private boolean ending;
        private int age;

        Loop(GateVortexBlockEntity be, SoundEvent event, float base, float pitch) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.be = be;
            this.base = base;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = Math.max(0.45F, pitch);
            this.x = be.centreX();
            this.y = be.centreY();
            this.z = be.centreZ();
        }

        void end() {
            ending = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            if (be.isRemoved()) ending = true;
            float target = ending ? 0F : base * switch (be.mode()) {
                case GateVortex.BROKEN -> 1.2F;
                case GateVortex.CALM -> 0.35F;
                case GateVortex.SPENT -> 0.2F;
                case GateVortex.FORMING -> 0.6F;
                case GateVortex.WARNING -> 0.85F + 0.35F * (float) Math.abs(Math.sin(age * Math.PI / 26.0));
                default -> 1F;
            };
            level += (target - level) * 0.08F;
            volume = Math.max(0.001F, level);
            pitch = be.mode() == GateVortex.BROKEN ? Math.max(0.45F, pitch * 0.999F) : pitch;
            if (ending && level < 0.01F) stop();
        }
    }
}
