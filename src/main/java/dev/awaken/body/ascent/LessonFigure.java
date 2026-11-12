package dev.awaken.body.ascent;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The Lesson's figure of light (SPEC §63.3, §71): a body the client makes for itself and walks along a move's path, so the
 * player sees the move before making it. Client only: never spawned by a server, never sent, never saved; it has no
 * mind, no weight and no body to bump into. Its look is its renderer's (a figure of gold light, not a copy of a skin:
 * the first clear picture of the Gauntlet's ghost in this role read as another player standing in the way).
 */
public class LessonFigure extends PathfinderMob {
    public LessonFigure(EntityType<? extends LessonFigure> type, Level level) {
        super(type, level);
        // (not noPhysics: it is never moved by the game at all, and mob_bodies_have_physics lists every body that is)
        setNoGravity(true);
        setNoAi(true);
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    /** The Lesson moves it; nothing of its own runs. */
    @Override
    public void tick() {
        if (!level().isClientSide()) discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
