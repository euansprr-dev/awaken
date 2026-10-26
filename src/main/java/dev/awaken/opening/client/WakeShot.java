package dev.awaken.opening.client;

import dev.awaken.Awaken;
import dev.awaken.citizens.CitizensContent;
import dev.awaken.lens.client.LensClient;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.story.RitualSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The infirmary (SPEC §16.4): after the Void, you wake. Black, a heartbeat, bells far off; the eyes open and close and
 * open again on white linen and dark beams, blurred, washed in Lens's wake look, slowly coming into focus. Sun the healer
 * and an Association officer at the bedside say their lines (the gate put you back on the stones; most people stay dead); then you
 * sit up and stand, and the body is yours again, beside the bed, facing the door to the Registry. About thirty seconds.
 */
final class WakeShot implements OpeningCamera.Shot {
    static final int OPEN = 50, LOOK = 140, SIT = 540, STAND = 580, LENGTH = 612;
    /** The blur, strongest first (post chains assets/awaken/post_effect/opening_blur_N.json). */
    static final Identifier[] BLUR = {Awaken.id("opening_blur_1"), Awaken.id("opening_blur_2"), Awaken.id("opening_blur_3")};

    private final Vec3 pillow, stand;
    private final float feetYaw, sideYaw;
    private final boolean party;
    private static int blur;

    private WakeShot(OpeningPayloads.Scene s, boolean party) {
        pillow = s.eye();
        stand = s.at();
        boolean north = s.n() % 8 < 4;
        feetYaw = north ? 0F : 180F;   // lying with the head on the pillow, looking toward the feet
        sideYaw = -90F;                 // the room's middle and the door are east of every bed
        this.party = party;
    }

    static void start(OpeningPayloads.Scene s) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        boolean party = p.level().players().size() > 1;
        int from = Math.min(s.age(), LENGTH - 1);
        OpeningHud.lidsOn = true;
        OpeningHud.lids = OpeningHud.lidsO = from < OPEN ? 0 : 1;
        OpeningHud.blackTarget = 0;
        OpeningHud.black = OpeningHud.blackO = 0;
        OpeningHud.barsTarget = 0;
        OpeningCamera.take(new WakeShot(s, party), from);
    }

    @Override
    public int length() {
        return LENGTH;
    }

    @Override
    public void tick(int age) {
        // the eyelids: a flutter, closed again, then open
        float lids;
        if (age < OPEN) lids = 0;
        else if (age < OPEN + 8) lids = (age - OPEN) / 8F * 0.35F;
        else if (age < OPEN + 14) lids = 0.35F - (age - OPEN - 8) / 6F * 0.35F;
        else if (age < 80) lids = 0;
        else if (age < 92) lids = (age - 80) / 12F * 0.6F;
        else if (age < 100) lids = 0.6F - (age - 92) / 8F * 0.25F;
        else if (age < 125) lids = 0.35F + (age - 100) / 25F * 0.65F;
        else lids = 1;
        OpeningHud.lidsO = OpeningHud.lids;
        OpeningHud.lids = lids;
        if (age == 10 || age == 44 || age == 76) OpeningSounds.ui(RitualSounds.HEARTBEAT.get(), 1F, age == 76 ? 0.5F : 0.8F);
        if (age == 30) OpeningSounds.ui(CitizensContent.BELL_DISTRICT.get(), 0.8F, 0.22F);
        if (age == 200) OpeningSounds.ui(CitizensContent.BELL_DISTRICT.get(), 0.94F, 0.16F);
        if (age == OPEN) {
            LensClient.override("awaken:carillon_wake", 10);
            blur = 3;
        }
        if (age == 160) blur = 2;
        if (age == 230) blur = 1;
        if (age == 300) {
            blur = 0;
            LensClient.clearOverride(140);
        }
        // story.md, "The wake": Sun the healer and Bram, the Association officer who walks you to the Registry
        if (age == 150) OpeningHud.line("Sun", "Easy, easy. You're awake. Don't try to sit up just yet.", 90);
        if (age == 240) OpeningHud.line("Bram", "The gate closed with you still inside. When the bell rang again, it opened and dropped you back out on the square.", 100);
        if (age == 340) OpeningHud.line("Sun", party ? "Your friends made it out with you. They're waking up in the other beds." : "Take your time. You've been out for a couple of hours.", 90);
        if (age == 430) OpeningHud.line("Bram", "You died in there. Most people who die in a gate don't come back.", 80);
        if (age == 505) OpeningHud.line("Sun", "So, how are you feeling?", 60);
        if (age == 565) OpeningHud.line("Bram", "When you're ready, get up. You need to be assessed at the Registry, and I'll walk you there.", 100);
    }

    /** Re-asserts the blur every tick while it plays (the server replaces the post-effect list when it syncs). */
    static void tickPost() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        List<Identifier> fx = p.getActivePostEffects();
        for (int i = 0; i < BLUR.length; i++) {
            boolean want = blur == i + 1;
            boolean has = fx.contains(BLUR[i]);
            if (want && !has) fx.add(BLUR[i]);
            else if (!want && has) fx.remove(BLUR[i]);
        }
    }

    @Override
    public void pose(float t, double[] out) {
        Vec3 sit = new Vec3(Mth.lerp(0.55, pillow.x, stand.x), pillow.y + 0.75, Mth.lerp(0.55, pillow.z, stand.z));
        double x, y, z, yaw, pitch;
        double breathe = Math.sin(t * 0.09) * 0.015;
        if (t < LOOK) {
            x = pillow.x;
            y = pillow.y + 0.12 + breathe;
            z = pillow.z;
            yaw = feetYaw;
            pitch = -78 + Math.sin(t * 0.05) * 1.5;
        } else if (t < SIT) {
            double k = ease(Mth.clamp((t - LOOK) / 40.0, 0, 1));
            x = pillow.x;
            y = pillow.y + 0.12 + breathe;
            z = pillow.z;
            yaw = feetYaw + Mth.wrapDegrees(sideYaw - feetYaw) * k * 0.8;
            pitch = Mth.lerp(k, -78, -18) + Math.sin(t * 0.04) * 1.2;
        } else if (t < STAND) {
            double k = ease(Mth.clamp((t - SIT) / (STAND - SIT), 0, 1));
            x = Mth.lerp(k, pillow.x, sit.x);
            y = Mth.lerp(k, pillow.y + 0.12, sit.y);
            z = Mth.lerp(k, pillow.z, sit.z);
            double from = feetYaw + Mth.wrapDegrees(sideYaw - feetYaw) * 0.8;
            yaw = Mth.lerp(k, from, sideYaw);
            pitch = Mth.lerp(k, -18, 6);
        } else {
            double k = ease(Mth.clamp((t - STAND) / (LENGTH - STAND), 0, 1));
            Vec3 eye = stand.add(0, 1.62, 0);
            x = Mth.lerp(k, sit.x, eye.x);
            y = Mth.lerp(k, sit.y, eye.y);
            z = Mth.lerp(k, sit.z, eye.z);
            yaw = sideYaw;
            pitch = Mth.lerp(k, 6, 0);
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = yaw;
        out[4] = pitch;
        out[5] = t < SIT ? Math.sin(t * 0.03) * 1.2 : 0;
        out[6] = t < 300 ? 0.9 + 0.1 * Mth.clamp((t - OPEN) / 250.0, 0, 1) : 1.0;
    }

    private static double ease(double k) {
        return k * k * (3 - 2 * k);
    }

    @Override
    public boolean hideBody(double camDistSq) {
        return true;
    }

    @Override
    public @Nullable CameraType landIn() {
        return OpeningConfig.view().equals("third") ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
    }

    @Override
    public void finished(String reason) {
        OpeningHud.lidsOn = false;
        blur = 0;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            p.setYRot(sideYaw);
            p.setXRot(0);
            p.yRotO = sideYaw;
            p.xRotO = 0;
            p.setYHeadRot(sideYaw);
        }
        if (reason.equals("done") || reason.equals("taken") || reason.equals("error")) {
            ClientPacketDistributor.sendToServer(new OpeningPayloads.Ready(OpeningPayloads.READY_WAKE, 0));
        }
    }

    static void reset() {
        blur = 0;
    }

    /** The Registry begins: no blur, no lids. */
    static void clearLook() {
        blur = 0;
        OpeningHud.lidsOn = false;
    }
}
