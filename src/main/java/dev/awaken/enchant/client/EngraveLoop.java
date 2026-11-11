package dev.awaken.enchant.client;

import dev.awaken.enchant.EnchantContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The burin's cut while it runs on the line (SPEC §45.10): a loop that swells and climbs with the pen's speed on the
 * line and falls quiet the moment the hand stops or strays, so the hand feels the metal (or the leather) as it draws.
 */
final class EngraveLoop extends AbstractTickableSoundInstance {
    /** On-line distance cut since the last tick (mark units), added by the trace as it follows the pen. */
    static float run;
    /** A steady hand's run per tick at full voice (a stroke of about 1.5 drawn in 0.4 s). */
    private static final float FULL = 0.15F, LOUDEST = 0.32F;
    private static final boolean LOOP = false;
    private static @Nullable EngraveLoop playing;
    private float speed;

    private EngraveLoop(boolean leather, BlockPos pos) {
        super((leather ? EnchantContent.CUT_LEATHER : EnchantContent.CUT_METAL).get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        looping = true;
        delay = 0;
        volume = 0F;
        x = pos.getX() + 0.5;
        y = pos.getY() + 1;
        z = pos.getZ() + 0.5;
    }

    /** The pen picked up a stroke: the loop starts silent (or keeps going) and the trace's run drives it. */
    static void start(BlockPos pos, ItemStack piece) {
        // off since the polish pass: the cut is grains by distance drawn now (InscribeScene.cut), not a looped one-shot
        if (!LOOP) return;
        if (playing != null && !playing.isStopped()) return;
        run = 0;
        playing = new EngraveLoop(soft(piece), pos);
        Minecraft.getInstance().getSoundManager().play(playing);
    }

    /** Leather, wood and string take the soft drag; everything else is metal under the point. */
    static boolean soft(ItemStack piece) {
        String path = BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath();
        return path.startsWith("leather_") || path.startsWith("wooden_") || path.equals("bow") || path.equals("crossbow")
                || path.equals("fishing_rod") || path.equals("elytra") || path.equals("turtle_helmet") || path.contains("on_a_stick");
    }

    /** How loud the cut is now (0 when none is playing), for the dev hand's log. */
    static float voice() {
        return playing == null || playing.isStopped() ? 0F : playing.volume;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        float per = run;
        run = 0;
        speed += (per - speed) * 0.5F;
        boolean on = InscribeScene.live && InscribeScene.phase == InscribeScene.TRACE && InscribeScene.drawing;
        float k = Mth.clamp(speed / FULL, 0F, 1.3F);
        volume += ((on ? Math.min(1F, k) * LOUDEST : 0F) - volume) * 0.45F;
        pitch = 0.85F + 0.3F * k;
        if (!on && volume < 0.005F) {
            stop();
            if (playing == this) playing = null;
        }
    }
}
