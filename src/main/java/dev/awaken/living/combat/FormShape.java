package dev.awaken.living.combat;

import dev.awaken.living.LivingContent;
import dev.awaken.living.combat.Forms.Species;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * The true size of a form body (SPEC §21.13): from C up, animals, spiders and creepers grow, while the walkers (zombie,
 * skeleton) stay under two blocks and gain breadth. The hitbox follows through {@link EntityEvent.Size}, computed on both sides from
 * the synced form, so what you see is what you hit. D keeps the vanilla body and size. Relative to vanilla, so a scale attribute
 * and the eye height carry over.
 */
public final class FormShape {
    private FormShape() {}

    /** [layout][rung 2..5] width and height in blocks; index 0 is the vanilla size the factors are taken against. */
    private static final float[][] W = {
            {0.9F, 1.0F, 1.2F, 1.4F, 1.6F},   // pig
            {0.9F, 1.1F, 1.3F, 1.5F, 1.7F},   // cow
            {0.9F, 1.0F, 1.15F, 1.3F, 1.45F}, // sheep
            {0.4F, 0.5F, 0.6F, 0.75F, 0.9F},  // chicken
            {0.6F, 0.7F, 0.8F, 0.9F, 0.9F},   // zombie
            {0.6F, 0.65F, 0.7F, 0.8F, 0.85F}, // skeleton
            {1.4F, 1.6F, 1.9F, 2.2F, 2.5F},   // spider
            {0.6F, 0.65F, 0.7F, 0.8F, 0.9F},  // creeper
    };
    private static final float[][] H = {
            {0.9F, 1.0F, 1.15F, 1.35F, 1.5F},
            {1.4F, 1.55F, 1.75F, 1.95F, 2.15F},
            {1.3F, 1.4F, 1.55F, 1.7F, 1.85F},
            {0.7F, 0.85F, 1.0F, 1.25F, 1.5F},
            {1.95F, 1.95F, 1.9F, 1.85F, 1.98F},
            {1.99F, 1.99F, 1.99F, 1.99F, 1.99F},
            {0.9F, 1.0F, 1.1F, 1.25F, 1.4F},
            {1.7F, 1.8F, 1.95F, 2.1F, 2.3F},
    };
    private static final String[] LAYOUTS = {"pig", "cow", "sheep", "chicken", "zombie", "skeleton", "spider", "creeper"};

    static int row(String layout) {
        for (int i = 0; i < LAYOUTS.length; i++) if (LAYOUTS[i].equals(layout)) return i;
        return -1;
    }

    /** Width and height factors over vanilla for a layout at a rung (1, 1 below C). */
    public static float widthFactor(String layout, int rung) {
        int r = row(layout);
        return r < 0 || rung < 2 ? 1.0F : W[r][Math.min(rung, 5) - 1] / W[r][0];
    }

    public static float heightFactor(String layout, int rung) {
        int r = row(layout);
        return r < 0 || rung < 2 ? 1.0F : H[r][Math.min(rung, 5) - 1] / H[r][0];
    }

    /** The body's height in blocks at a rung (the walkers' stays under two). */
    public static float height(String layout, int rung) {
        int r = row(layout);
        return r < 0 ? 0 : H[r][rung < 2 ? 0 : Math.min(rung, 5) - 1];
    }

    public static void onSize(EntityEvent.Size e) {
        Entity en = e.getEntity();
        if (!(en instanceof Mob)) return;
        Species sp = Species.of(en.getType());
        if (sp == null) return;
        Forms.Form f = en.getExistingDataOrNull(LivingContent.FORM.get());
        if (f == null || f.rung() < 2) return;
        float fw = widthFactor(sp.layout, f.rung()), fh = heightFactor(sp.layout, f.rung());
        if (fw != 1.0F || fh != 1.0F) e.setNewSize(e.getNewSize().scale(fw, fh));
    }

    /** Re-reads the size after the form is set or loaded (the client does the same when the synced form arrives). */
    public static void refresh(Mob mob) {
        mob.refreshDimensions();
    }
}
