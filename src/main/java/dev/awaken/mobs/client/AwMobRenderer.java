package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.mobs.client.life.Life;
import dev.awaken.mobs.client.life.LifeModel;
import dev.awaken.mobs.client.life.LifeProfile;
import dev.awaken.mobs.client.life.LifeRig;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Tier;
import dev.awaken.mobs.def.Trait;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.FaunaMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Any non-humanoid model (vanilla or ours) drawing an AWAKEN creature: tint, translucency, skin and glow layers. Our own
 * bodies ({@link LifeModel}) get the life layer (SPEC §23); a boss on a vanilla body gets its root-level part (a blow's
 * stagger, a provocation, a weighty fall), leaving the body's own animation to vanilla or Fresh Animations.
 */
class AwMobRenderer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends MobRenderer<AwakenMob, S, M> {
    protected final MobDef def;
    private final Identifier base;
    private final Supplier<S> factory;
    private final AwRender.@Nullable Filler<S> filler;
    private AwRender.@Nullable Scaler<S> scaler;
    private boolean fullBright;
    private final @Nullable LifeRig life;
    private final boolean ours;
    private final @Nullable Identifier blink;

    AwMobRenderer(EntityRendererProvider.Context ctx, MobDef def, M model, float shadow, Supplier<S> factory, AwRender.@Nullable Filler<S> filler) {
        super(ctx, model, shadow);
        this.def = def;
        this.base = AwRender.base(def);
        this.factory = factory;
        this.filler = filler;
        if (!def.model.ownTexture()) addLayer(new AwLayers.Skin<>(this, def));
        addLayer(new AwLayers.Glow<>(this, def));
        LifeProfile p = LifeProfile.of(def.id, def.fauna != null ? def.fauna.name() : null, def.tier.name());
        this.ours = model instanceof LifeModel;
        this.life = model instanceof LifeModel lm ? lm.bindLife(p) : boss(def) ? LifeRig.rootOnly(p) : null;
        this.blink = ours ? AwRender.exists(AwRender.blink(def)) : null;
    }

    /** Lords, guardians, sovereigns, the Shadow and the gate wardens: big enough that their weight must read. */
    static boolean boss(MobDef d) {
        return d.tier == Tier.LORD || d.tier == Tier.GUARDIAN || d.tier == Tier.SOVEREIGN || d.tier == Tier.FINAL || d.tier == Tier.WARDEN;
    }

    AwMobRenderer<S, M> scaler(AwRender.Scaler<S> s) {
        this.scaler = s;
        return this;
    }

    AwMobRenderer<S, M> fullBright() {
        this.fullBright = true;
        return this;
    }

    @Override
    public S createRenderState() {
        return factory.get();
    }

    @Override
    public void extractRenderState(AwakenMob mob, S state, float partialTicks) {
        super.extractRenderState(mob, state, partialTicks);
        if (state instanceof HumanoidRenderState h) HumanoidMobRenderer.extractHumanoidRenderState(mob, h, partialTicks, itemModelResolver);
        else if (state instanceof ArmedEntityRenderState a) ArmedEntityRenderState.extractArmedEntityRenderState(mob, a, itemModelResolver, partialTicks);
        AwLook l = AwRender.extract(mob, state, partialTicks);
        if (filler != null) filler.fill(mob, state, partialTicks, l);
        if (life == null) return;
        Life lf = Life.of(l.life, life, mob.getId());
        l.life = lf;
        lf.hurt = l.hurt;
        lf.entity = mob.getId();
        lf.windup = l.telegraph > 0 ? 1.0F - l.telegraph : -1; // the telegraph counts down its last second to the strike
        lf.aggressive = l.aggressive;
        lf.boss = boss(def);
        lf.dying = state.deathTime > 0 ? (state.deathTime - 1.0F) / 20.0F : -1;
        lf.deathLength = 0;
        lf.walk = ours ? Math.min(1.0F, state.walkAnimationSpeed * 1.6F) : 0.0F;
        lf.airborne = (l.fauna & FaunaMob.P_AIR) != 0;
        lf.swimming = state.isInWater;
        lf.busy = l.telegraph > 0 || l.fallen;
        lf.tick(state, ours);
    }

    @Override
    protected void setupRotations(S state, PoseStack pose, float bodyRot, float entityScale) {
        Life lf = life != null ? Life.from(state) : null;
        if (lf == null) {
            super.setupRotations(state, pose, bodyRot, entityScale);
            return;
        }
        // the weighty fall replaces vanilla's sideways flip; everything else (yaw, upside-down) stays vanilla's
        float death = state.deathTime;
        if (lf.toppling()) state.deathTime = 0;
        super.setupRotations(state, pose, bodyRot, entityScale);
        state.deathTime = death;
        lf.rootPose(pose, state);
    }

    @Override
    public Identifier getTextureLocation(S state) {
        if (blink != null) {
            Life lf = Life.from(state);
            if (lf != null && lf.closed() > 0.5F) return blink;
        }
        return base;
    }

    @Override
    protected int getModelTint(S state) {
        return AwRender.look(state).tint;
    }

    @Override
    protected @Nullable RenderType getRenderType(S state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
        if (bodyVisible && AwRender.look(state).translucent) return RenderTypes.entityTranslucent(getTextureLocation(state));
        return super.getRenderType(state, bodyVisible, forceTransparent, glowing);
    }

    @Override
    protected float getWhiteOverlayProgress(S state) {
        return AwRender.flash(AwRender.look(state), state.ageInTicks);
    }

    @Override
    public boolean isEntityUpsideDown(AwakenMob mob) {
        return mob.def().has(Trait.UPSIDE_DOWN) || super.isEntityUpsideDown(mob);
    }

    @Override
    protected int getBlockLightLevel(AwakenMob mob, BlockPos pos) {
        return fullBright ? 15 : super.getBlockLightLevel(mob, pos);
    }

    @Override
    protected void scale(S state, PoseStack pose) {
        super.scale(state, pose);
        if (scaler != null) scaler.scale(state, pose, AwRender.look(state));
    }
}
