package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwLook;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.client.model.AwModels;
import dev.awaken.mobs.client.model.FaunaModels;
import dev.awaken.mobs.client.model.WildModels;
import dev.awaken.mobs.client.anim.HeroRenderer;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.model.HeroModel;
import net.minecraft.client.model.animal.feline.AdultOcelotModel;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.model.animal.goat.GoatModel;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.client.renderer.entity.state.GoatRenderState;
import dev.awaken.mobs.entity.FaunaMob;
import net.minecraft.client.model.animal.allay.AllayModel;
import net.minecraft.client.model.animal.fox.AdultFoxModel;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.renderer.entity.state.AllayRenderState;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.util.Mth;
import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.animal.polarbear.PolarBearModel;
import net.minecraft.client.model.animal.wolf.AdultWolfModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.model.monster.breeze.BreezeModel;
import net.minecraft.client.model.monster.creaking.CreakingModel;
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.model.monster.endermite.EndermiteModel;
import net.minecraft.client.model.monster.ghast.GhastModel;
import net.minecraft.client.model.monster.guardian.GuardianModel;
import net.minecraft.client.model.monster.hoglin.HoglinModel;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.monster.phantom.PhantomModel;
import net.minecraft.client.model.monster.piglin.AdultPiglinModel;
import net.minecraft.client.model.monster.ravager.RavagerModel;
import net.minecraft.client.model.monster.silverfish.SilverfishModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.monster.slime.MagmaCubeModel;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.model.monster.spider.SpiderModel;
import net.minecraft.client.model.monster.vex.VexModel;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.model.monster.wither.WitherBossModel;
import net.minecraft.client.model.monster.zombie.DrownedModel;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.layers.BreezeWindLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import net.minecraft.client.renderer.entity.state.BreezeRenderState;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.GhastRenderState;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.client.renderer.entity.state.HoglinRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PhantomRenderState;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.client.renderer.entity.state.PolarBearRenderState;
import net.minecraft.client.renderer.entity.state.RavagerRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.entity.state.VexRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.renderer.entity.state.WitchRenderState;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.PiglinArmPose;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Maps every bestiary def to a renderer for its model. */
final class AwRenderers {
    private AwRenderers() {}

    static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(AwModels.SERPENT, AwModels.Serpent::create);
        e.registerLayerDefinition(AwModels.LISTENER, AwModels.Listener::create);
        e.registerLayerDefinition(AwModels.BELL, AwModels.Bell::create);
        e.registerLayerDefinition(AwModels.LANTERN, AwModels.Lantern::create);
        e.registerLayerDefinition(AwModels.SHADE, AwModels.Shade::create);
        e.registerLayerDefinition(FaunaModels.MOTH, FaunaModels.Moth::create);
        e.registerLayerDefinition(FaunaModels.STAG, FaunaModels.Stag::create);
        e.registerLayerDefinition(FaunaModels.TORTOISE, FaunaModels.Tortoise::create);
        e.registerLayerDefinition(FaunaModels.RAY, FaunaModels.Ray::create);
        e.registerLayerDefinition(FaunaModels.SCARAB, FaunaModels.Scarab::create);
        e.registerLayerDefinition(WildModels.LIZARD, WildModels.Lizard::create);
        e.registerLayerDefinition(WildModels.SNAIL, WildModels.Snail::create);
        e.registerLayerDefinition(WildModels.MUSHROOM, WildModels.Mushroom::create);
        e.registerLayerDefinition(WildModels.MARMOT, WildModels.Marmot::create);
        // hero bodies: the named beasts, the Hollow Owl, the Bogwick
        for (HeroRig r : HeroRig.all().values()) e.registerLayerDefinition(HeroModel.layer(r.id), () -> HeroModel.create(r));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(MobTypes.BOLT.get(), ctx -> new ThrownItemRenderer<>(ctx, 0.9F, true));
        for (MobDef d : dev.awaken.mobs.def.Bestiary.all()) {
            EntityType<? extends AwakenMob> type = MobTypes.type(d);
            if (type == null) continue;
            e.registerEntityRenderer((EntityType) type, ctx -> create((EntityRendererProvider.Context) ctx, d));
        }
    }

    private static EntityRenderer<AwakenMob, ?> create(EntityRendererProvider.Context ctx, MobDef d) {
        return switch (d.model) {
            case ZOMBIE -> zombie(ctx, d, ModelLayers.ZOMBIE, ModelLayers.ZOMBIE_ARMOR);
            case HUSK -> zombie(ctx, d, ModelLayers.HUSK, ModelLayers.HUSK_ARMOR);
            case DROWNED -> new AwHumanoidRenderer<ZombieRenderState, DrownedModel>(ctx, d, new DrownedModel(ctx.bakeLayer(ModelLayers.DROWNED)),
                    ArmorModelSet.bake(ModelLayers.DROWNED_ARMOR, ctx.getModelSet(), DrownedModel::new), ZombieRenderState::new, s -> AwRender.base(d),
                    (m, s, pt, l) -> s.isAggressive = m.isAggressive() || l.telegraph > 0);
            case SKELETON -> skeleton(ctx, d, ModelLayers.SKELETON, ModelLayers.SKELETON_ARMOR);
            case STRAY -> skeleton(ctx, d, ModelLayers.STRAY, ModelLayers.STRAY_ARMOR);
            case WITHER_SKELETON -> skeleton(ctx, d, ModelLayers.WITHER_SKELETON, ModelLayers.WITHER_SKELETON_ARMOR);
            case BOGGED -> skeleton(ctx, d, ModelLayers.BOGGED, ModelLayers.BOGGED_ARMOR);
            case PIGLIN -> piglin(ctx, d, ModelLayers.PIGLIN, ModelLayers.PIGLIN_ARMOR);
            case PIGLIN_BRUTE -> piglin(ctx, d, ModelLayers.PIGLIN_BRUTE, ModelLayers.PIGLIN_BRUTE_ARMOR);
            case MIMIC, HUMAN -> new AwSkinRenderer(ctx, d); // never AvatarRenderState: see SkinnedMobRenderer
            case ENDERMAN -> new AwMobRenderer<EndermanRenderState, EndermanModel<EndermanRenderState>>(ctx, d, new EndermanModel<>(ctx.bakeLayer(ModelLayers.ENDERMAN)), 0.5F,
                    EndermanRenderState::new, (m, s, pt, l) -> s.isCreepy = m.getTarget() != null || l.telegraph > 0);
            case VINDICATOR -> illager(ctx, d, ModelLayers.VINDICATOR);
            case EVOKER -> illager(ctx, d, ModelLayers.EVOKER);
            case PILLAGER -> illager(ctx, d, ModelLayers.PILLAGER);
            case ILLUSIONER -> illager(ctx, d, ModelLayers.ILLUSIONER);
            case WITCH -> new AwMobRenderer<WitchRenderState, WitchModel>(ctx, d, new WitchModel(ctx.bakeLayer(ModelLayers.WITCH)), 0.5F, WitchRenderState::new,
                    (m, s, pt, l) -> s.entityId = m.getId());
            case SPIDER -> new AwMobRenderer<LivingEntityRenderState, SpiderModel>(ctx, d, new SpiderModel(ctx.bakeLayer(ModelLayers.SPIDER)), 0.8F, LivingEntityRenderState::new, null);
            case CAVE_SPIDER -> new AwMobRenderer<LivingEntityRenderState, SpiderModel>(ctx, d, new SpiderModel(ctx.bakeLayer(ModelLayers.CAVE_SPIDER)), 0.6F, LivingEntityRenderState::new, null);
            case WOLF, SNOW_WOLF -> new AwMobRenderer<WolfRenderState, AdultWolfModel>(ctx, d, new AdultWolfModel(ctx.bakeLayer(ModelLayers.WOLF)), 0.5F, WolfRenderState::new,
                    (m, s, pt, l) -> {
                        s.isAngry = true;
                        s.tailAngle = 1.5393804F;
                    });
            case HOGLIN -> hoglin(ctx, d, ModelLayers.HOGLIN);
            case ZOGLIN -> hoglin(ctx, d, ModelLayers.ZOGLIN);
            case POLAR_BEAR -> new AwMobRenderer<PolarBearRenderState, PolarBearModel>(ctx, d, new PolarBearModel(ctx.bakeLayer(ModelLayers.POLAR_BEAR)), 0.9F, PolarBearRenderState::new,
                    (m, s, pt, l) -> s.standScale = l.telegraph);
            case RAVAGER -> new AwMobRenderer<RavagerRenderState, RavagerModel>(ctx, d, new RavagerModel(ctx.bakeLayer(ModelLayers.RAVAGER)), 1.1F, RavagerRenderState::new,
                    (m, s, pt, l) -> {
                        s.roarAnimation = l.telegraph > 0 ? 1.0F - l.telegraph : 0.0F;
                        s.attackTicksRemaining = swing(m, pt) * 10.0F;
                    });
            case IRON_GOLEM -> new AwMobRenderer<IronGolemRenderState, IronGolemModel>(ctx, d, new IronGolemModel(ctx.bakeLayer(ModelLayers.IRON_GOLEM)), 0.7F, IronGolemRenderState::new,
                    (m, s, pt, l) -> s.attackTicksRemaining = Math.max(swing(m, pt) * 10.0F, l.telegraph * 8.0F));
            case WARDEN -> new AwMobRenderer<WardenRenderState, WardenModel>(ctx, d, new WardenModel(ctx.bakeLayer(ModelLayers.WARDEN)), 0.9F, WardenRenderState::new,
                    (m, s, pt, l) -> {
                        s.tendrilAnimation = l.telegraph;
                        s.heartAnimation = 0.5F + 0.5F * l.glow;
                    });
            case CREAKING -> new AwMobRenderer<CreakingRenderState, CreakingModel>(ctx, d, new CreakingModel(ctx.bakeLayer(ModelLayers.CREAKING)), 0.6F, CreakingRenderState::new,
                    (m, s, pt, l) -> {
                        s.canMove = !m.isWatchedNow();
                        s.eyesGlowing = true;
                    });
            case CREEPER -> new AwMobRenderer<CreeperRenderState, CreeperModel>(ctx, d, new CreeperModel(ctx.bakeLayer(ModelLayers.CREEPER)), 0.5F, CreeperRenderState::new,
                    (m, s, pt, l) -> {
                        s.swelling = l.swell;
                        s.isPowered = m.flag(AwakenMob.F_CHARGED);
                    }).scaler((s, pose, l) -> {
                        float g = l.swell * l.swell;
                        pose.scale(1.0F + g * 0.4F, 1.0F + g * 0.1F, 1.0F + g * 0.4F);
                    });
            case SILVERFISH -> new AwMobRenderer<LivingEntityRenderState, SilverfishModel>(ctx, d, new SilverfishModel(ctx.bakeLayer(ModelLayers.SILVERFISH)), 0.3F, LivingEntityRenderState::new, null);
            case ENDERMITE -> new AwMobRenderer<LivingEntityRenderState, EndermiteModel>(ctx, d, new EndermiteModel(ctx.bakeLayer(ModelLayers.ENDERMITE)), 0.3F, LivingEntityRenderState::new, null);
            case SLIME -> slime(ctx, d);
            case MAGMA -> new AwMobRenderer<SlimeRenderState, MagmaCubeModel>(ctx, d, new MagmaCubeModel(ctx.bakeLayer(ModelLayers.MAGMA_CUBE)), 0.5F, SlimeRenderState::new,
                    (m, s, pt, l) -> {
                        s.squish = l.squish;
                        s.size = 2;
                    }).scaler(AwRenderers::cube).fullBright();
            case BREEZE -> breeze(ctx, d);
            case VEX -> vex(ctx, d);
            case BLAZE -> new AwMobRenderer<LivingEntityRenderState, BlazeModel>(ctx, d, new BlazeModel(ctx.bakeLayer(ModelLayers.BLAZE)), 0.5F, LivingEntityRenderState::new, null).fullBright();
            case PHANTOM -> new AwMobRenderer<PhantomRenderState, PhantomModel>(ctx, d, new PhantomModel(ctx.bakeLayer(ModelLayers.PHANTOM)), 0.75F, PhantomRenderState::new,
                    (m, s, pt, l) -> {
                        s.flapTime = m.getId() * 3 + s.ageInTicks;
                        s.size = 0;
                    }).scaler((s, pose, l) -> pose.translate(0.0F, 1.3125F, 0.1875F));
            case BEE -> new AwMobRenderer<BeeRenderState, AdultBeeModel>(ctx, d, new AdultBeeModel(ctx.bakeLayer(ModelLayers.BEE)), 0.4F, BeeRenderState::new,
                    (m, s, pt, l) -> {
                        // Gate bees are angry and armed; the overworld's bogwicks bob gently, stingless.
                        s.isAngry = !d.isFauna();
                        s.isOnGround = false;
                        s.hasStinger = !d.isFauna();
                    });
            case GUARDIAN -> guardian(ctx, d, ModelLayers.GUARDIAN, 0.5F);
            case ELDER_GUARDIAN -> guardian(ctx, d, ModelLayers.ELDER_GUARDIAN, 1.2F);
            case GHAST -> new AwMobRenderer<GhastRenderState, GhastModel>(ctx, d, new GhastModel(ctx.bakeLayer(ModelLayers.GHAST)), 1.5F, GhastRenderState::new,
                    (m, s, pt, l) -> s.isCharging = l.telegraph > 0);
            case WITHER -> new AwMobRenderer<WitherRenderState, WitherBossModel>(ctx, d, new WitherBossModel(ctx.bakeLayer(ModelLayers.WITHER)), 1.0F, WitherRenderState::new,
                    (m, s, pt, l) -> s.isPowered = m.flag(AwakenMob.F_CHARGED)).scaler((s, pose, l) -> pose.scale(2.0F, 2.0F, 2.0F)).fullBright();
            case SERPENT -> new AwMobRenderer<LivingEntityRenderState, AwModels.Serpent>(ctx, d, new AwModels.Serpent(ctx.bakeLayer(AwModels.SERPENT)), 0.6F, LivingEntityRenderState::new, null);
            case LISTENER -> new AwMobRenderer<LivingEntityRenderState, AwModels.Listener>(ctx, d, new AwModels.Listener(ctx.bakeLayer(AwModels.LISTENER)), 0.6F, LivingEntityRenderState::new, null);
            case BELL -> new AwMobRenderer<LivingEntityRenderState, AwModels.Bell>(ctx, d, new AwModels.Bell(ctx.bakeLayer(AwModels.BELL)), 1.0F, LivingEntityRenderState::new, null);
            case LANTERN -> new AwMobRenderer<LivingEntityRenderState, AwModels.Lantern>(ctx, d, new AwModels.Lantern(ctx.bakeLayer(AwModels.LANTERN)), 0.6F, LivingEntityRenderState::new, null);
            case SHADE -> new AwMobRenderer<LivingEntityRenderState, AwModels.Shade>(ctx, d, new AwModels.Shade(ctx.bakeLayer(AwModels.SHADE)), 0.7F, LivingEntityRenderState::new, null);
            // the Awakened layer's overworld creatures
            case MOTH -> new AwMobRenderer<LivingEntityRenderState, FaunaModels.Moth>(ctx, d, new FaunaModels.Moth(ctx.bakeLayer(FaunaModels.MOTH)), 0.2F, LivingEntityRenderState::new, null);
            case STAG -> new AwMobRenderer<LivingEntityRenderState, FaunaModels.Stag>(ctx, d, new FaunaModels.Stag(ctx.bakeLayer(FaunaModels.STAG)), 0.6F, LivingEntityRenderState::new, null);
            case TORTOISE -> new AwMobRenderer<LivingEntityRenderState, FaunaModels.Tortoise>(ctx, d, new FaunaModels.Tortoise(ctx.bakeLayer(FaunaModels.TORTOISE)), 0.7F, LivingEntityRenderState::new, null);
            case RAY -> new AwMobRenderer<LivingEntityRenderState, FaunaModels.Ray>(ctx, d, new FaunaModels.Ray(ctx.bakeLayer(FaunaModels.RAY)), 0.6F, LivingEntityRenderState::new, null);
            case SCARAB -> new AwMobRenderer<LivingEntityRenderState, FaunaModels.Scarab>(ctx, d, new FaunaModels.Scarab(ctx.bakeLayer(FaunaModels.SCARAB)), 0.25F, LivingEntityRenderState::new, null);
            case ALLAY -> new AwMobRenderer<AllayRenderState, AllayModel>(ctx, d, new AllayModel(ctx.bakeLayer(ModelLayers.ALLAY)), 0.3F, AllayRenderState::new,
                    (m, s, pt, l) -> {
                        s.isDancing = false;
                        s.isSpinning = false;
                        s.holdingAnimationProgress = 0.0F;
                    });
            case PARROT -> new AwMobRenderer<ParrotRenderState, ParrotModel>(ctx, d, new ParrotModel(ctx.bakeLayer(ModelLayers.PARROT)), 0.3F, ParrotRenderState::new,
                    (m, s, pt, l) -> {
                        boolean air = (l.fauna & FaunaMob.P_AIR) != 0;
                        s.pose = air ? ParrotModel.Pose.FLYING : ParrotModel.Pose.STANDING;
                        s.flapAngle = air ? (Mth.sin(s.ageInTicks * 1.3F) + 1.0F) * 0.6F : 0.0F;
                    });
            case FOX -> new AwMobRenderer<FoxRenderState, AdultFoxModel>(ctx, d, new AdultFoxModel(ctx.bakeLayer(ModelLayers.FOX)), 0.4F, FoxRenderState::new,
                    (m, s, pt, l) -> {
                        boolean crouch = (l.fauna & FaunaMob.P_STALK) != 0 || l.telegraph > 0;
                        s.isCrouching = crouch;
                        s.crouchAmount = crouch ? 2.5F : 0.0F;
                        s.isPouncing = (l.fauna & FaunaMob.P_AIR) != 0 && (l.fauna & FaunaMob.P_ANGRY) != 0;
                        s.isSitting = (l.fauna & FaunaMob.P_REST) != 0;
                        s.isSleeping = false;
                        s.isFaceplanted = false;
                        s.headRollAngle = 0.0F;
                    });
            // P2: our own bodies...
            case LIZARD -> new AwMobRenderer<LivingEntityRenderState, WildModels.Lizard>(ctx, d, new WildModels.Lizard(ctx.bakeLayer(WildModels.LIZARD)), 0.4F, LivingEntityRenderState::new, null);
            case SNAIL -> new AwMobRenderer<LivingEntityRenderState, WildModels.Snail>(ctx, d, new WildModels.Snail(ctx.bakeLayer(WildModels.SNAIL)), 0.35F, LivingEntityRenderState::new, null);
            case MUSHROOM -> new AwMobRenderer<LivingEntityRenderState, WildModels.Mushroom>(ctx, d, new WildModels.Mushroom(ctx.bakeLayer(WildModels.MUSHROOM)), 0.4F, LivingEntityRenderState::new, null);
            case MARMOT -> new AwMobRenderer<LivingEntityRenderState, WildModels.Marmot>(ctx, d, new WildModels.Marmot(ctx.bakeLayer(WildModels.MARMOT)), 0.3F, LivingEntityRenderState::new, null);
            // ...and three vanilla animal bodies
            case FROG -> new AwMobRenderer<FrogRenderState, FrogModel>(ctx, d, new FrogModel(ctx.bakeLayer(ModelLayers.FROG)), 0.3F, FrogRenderState::new,
                    (m, s, pt, l) -> {
                        s.isSwimming = m.isInWater();
                        if (m instanceof FaunaMob f) {
                            s.jumpAnimationState.copyFrom(f.jumpAnim);
                            s.croakAnimationState.copyFrom(f.croakAnim);
                            s.tongueAnimationState.copyFrom(f.tongueAnim);
                        }
                    });
            case GOAT -> new AwMobRenderer<GoatRenderState, GoatModel>(ctx, d, new GoatModel(ctx.bakeLayer(ModelLayers.GOAT)), 0.7F, GoatRenderState::new,
                    (m, s, pt, l) -> {
                        s.hasLeftHorn = true;
                        s.hasRightHorn = true;
                        // Head down to graze, or lowered to ram while a charge winds up.
                        s.rammingXHeadRot = l.telegraph > 0 ? 0.6F + 0.5F * l.telegraph : (l.fauna & FaunaMob.P_GRAZE) != 0 ? 1.1F : 0.0F;
                    });
            case OCELOT -> new AwMobRenderer<FelineRenderState, AdultOcelotModel>(ctx, d, new AdultOcelotModel(ctx.bakeLayer(ModelLayers.OCELOT)), 0.4F, FelineRenderState::new,
                    (m, s, pt, l) -> {
                        boolean crouch = (l.fauna & FaunaMob.P_STALK) != 0 || l.telegraph > 0;
                        s.isCrouching = crouch;
                        s.isSprinting = !crouch && (l.fauna & FaunaMob.P_ANGRY) != 0 && s.walkAnimationSpeed > 0.4F;
                        s.isSitting = (l.fauna & FaunaMob.P_REST) != 0;
                        s.lieDownAmount = 0.0F;
                        s.lieDownAmountTail = 0.0F;
                        s.relaxStateOneAmount = 0.0F;
                    });
            // hero bodies (rigs, clips and textures from tools/art/specs/heroes.py)
            case BRIAR_SOW, DUNE_WYRM, OLD_ANTLER, MIRE_MOTHER, PLUMED_COIL, WEAVER_QUEEN, EMPRESS_MOTH, STONEHORN, MYCELORD, OWL, FIREFLY ->
                    HeroRenderer.create(ctx, d);
            // P3: the water and cave bodies (rigs from tools/art/specs/fauna3.py)
            case GLIMMERFIN, BROOK_OTTER, BREATHBELL, TIDESTEED, LANTERN_MAW, CHIME_CRICKET, WICKLING, DRIP_LURKER, HUSHLING, THOUSANDFOOT ->
                    dev.awaken.mobs.client.anim.WildRenderer.create(ctx, d);
        };
    }

    private static float swing(AwakenMob m, float pt) {
        float s = m.getSwingAnimation(pt);
        return s > 0 ? 1.0F - s : 0.0F;
    }

    private static void cube(SlimeRenderState s, PoseStack pose, AwLook l) {
        float size = s.size;
        float ss = s.squish / (size * 0.5F + 1.0F);
        float w = 1.0F / (ss + 1.0F);
        pose.scale(w * size, 1.0F / w * size, w * size);
    }

    private static AwHumanoidRenderer<ZombieRenderState, ZombieModel<ZombieRenderState>> zombie(EntityRendererProvider.Context ctx, MobDef d, ModelLayerLocation body,
                                                                                             net.minecraft.client.renderer.entity.ArmorModelSet<ModelLayerLocation> armor) {
        return new AwHumanoidRenderer<>(ctx, d, new ZombieModel<>(ctx.bakeLayer(body)), ArmorModelSet.bake(armor, ctx.getModelSet(), ZombieModel::new),
                ZombieRenderState::new, s -> AwRender.base(d), (m, s, pt, l) -> s.isAggressive = m.isAggressive() || l.telegraph > 0);
    }

    private static AwHumanoidRenderer<SkeletonRenderState, SkeletonModel<SkeletonRenderState>> skeleton(EntityRendererProvider.Context ctx, MobDef d, ModelLayerLocation body,
                                                                                                     net.minecraft.client.renderer.entity.ArmorModelSet<ModelLayerLocation> armor) {
        return new AwHumanoidRenderer<>(ctx, d, new SkeletonModel<>(ctx.bakeLayer(body)), ArmorModelSet.bake(armor, ctx.getModelSet(), SkeletonModel::new),
                SkeletonRenderState::new, s -> AwRender.base(d), (m, s, pt, l) -> {
                    s.isAggressive = m.isAggressive() || l.telegraph > 0;
                    s.isHoldingBow = m.getMainHandItem().is(Items.BOW);
                    if (s.isHoldingBow && s.isAggressive) s.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.BOW_AND_ARROW;
                });
    }

    private static AwHumanoidRenderer<PiglinRenderState, AdultPiglinModel> piglin(EntityRendererProvider.Context ctx, MobDef d, ModelLayerLocation body,
                                                                                 net.minecraft.client.renderer.entity.ArmorModelSet<ModelLayerLocation> armor) {
        return new AwHumanoidRenderer<>(ctx, d, new AdultPiglinModel(ctx.bakeLayer(body)), ArmorModelSet.bake(armor, ctx.getModelSet(), AdultPiglinModel::new),
                PiglinRenderState::new, s -> AwRender.base(d), (m, s, pt, l) -> {
                    s.isBrute = d.model == dev.awaken.mobs.def.Model.PIGLIN_BRUTE;
                    s.armPose = m.isAggressive() && !m.getMainHandItem().isEmpty() ? PiglinArmPose.ATTACKING_WITH_MELEE_WEAPON : PiglinArmPose.DEFAULT;
                });
    }

    private static AwMobRenderer<IllagerRenderState, IllagerModel<IllagerRenderState>> illager(EntityRendererProvider.Context ctx, MobDef d, ModelLayerLocation layer) {
        AwMobRenderer<IllagerRenderState, IllagerModel<IllagerRenderState>> r = new AwMobRenderer<>(ctx, d, new IllagerModel<>(ctx.bakeLayer(layer)), 0.5F, IllagerRenderState::new,
                (m, s, pt, l) -> {
                    s.isAggressive = m.isAggressive();
                    s.mainArm = m.getMainArm();
                    boolean holds = !m.getMainHandItem().isEmpty();
                    if (l.telegraph > 0 && !holds) s.armPose = AbstractIllager.IllagerArmPose.SPELLCASTING;
                    else if (m.getMainHandItem().is(Items.CROSSBOW)) s.armPose = AbstractIllager.IllagerArmPose.CROSSBOW_HOLD;
                    else if (m.getMainHandItem().is(Items.BOW)) s.armPose = AbstractIllager.IllagerArmPose.BOW_AND_ARROW;
                    else if (holds && (m.isAggressive() || l.telegraph > 0)) s.armPose = AbstractIllager.IllagerArmPose.ATTACKING;
                    else s.armPose = holds ? AbstractIllager.IllagerArmPose.NEUTRAL : AbstractIllager.IllagerArmPose.CROSSED;
                });
        r.addLayer(new ItemInHandLayer<>(r));
        return r;
    }

    private static AwMobRenderer<HoglinRenderState, HoglinModel> hoglin(EntityRendererProvider.Context ctx, MobDef d, ModelLayerLocation layer) {
        return new AwMobRenderer<>(ctx, d, new HoglinModel(ctx.bakeLayer(layer)), 0.7F, HoglinRenderState::new,
                (m, s, pt, l) -> s.attackAnimationRemainingTicks = (int) (swing(m, pt) * 10.0F));
    }

    private static AwMobRenderer<GuardianRenderState, GuardianModel> guardian(EntityRendererProvider.Context ctx, MobDef d, ModelLayerLocation layer, float shadow) {
        return new AwMobRenderer<>(ctx, d, new GuardianModel(ctx.bakeLayer(layer)), shadow, GuardianRenderState::new, (m, s, pt, l) -> {
            s.spikesAnimation = 0.5F + 0.5F * l.telegraph;
            s.tailAnimation = s.ageInTicks * 0.15F;
            s.eyePosition = m.getEyePosition(pt);
            if (m.getTarget() != null) {
                s.lookDirection = m.getViewVector(pt);
                s.lookAtPosition = m.getTarget().getEyePosition(pt);
            }
        });
    }

    private static AwMobRenderer<VexRenderState, VexModel> vex(EntityRendererProvider.Context ctx, MobDef d) {
        AwMobRenderer<VexRenderState, VexModel> r = new AwMobRenderer<>(ctx, d, new VexModel(ctx.bakeLayer(ModelLayers.VEX)), 0.3F, VexRenderState::new,
                (m, s, pt, l) -> s.isCharging = m.flag(AwakenMob.F_CHARGING) || l.telegraph > 0);
        r.addLayer(new ItemInHandLayer<>(r));
        return r.fullBright();
    }

    private static AwMobRenderer<BreezeRenderState, BreezeModel> breeze(EntityRendererProvider.Context ctx, MobDef d) {
        AwMobRenderer<BreezeRenderState, BreezeModel> r = new AwMobRenderer<>(ctx, d, new BreezeModel(ctx.bakeLayer(ModelLayers.BREEZE)), 0.5F, BreezeRenderState::new,
                (m, s, pt, l) -> s.idle.copyFrom(m.idleAnim));
        r.addLayer(new BreezeWindLayer(r, ctx.getModelSet()));
        return r;
    }

    private static AwMobRenderer<SlimeRenderState, SlimeModel> slime(EntityRendererProvider.Context ctx, MobDef d) {
        AwMobRenderer<SlimeRenderState, SlimeModel> r = new AwMobRenderer<>(ctx, d, new SlimeModel(ctx.bakeLayer(ModelLayers.SLIME)), 0.5F, SlimeRenderState::new,
                (m, s, pt, l) -> {
                    s.squish = l.squish;
                    s.size = 2;
                });
        r.scaler(AwRenderers::cube);
        SlimeModel outer = new SlimeModel(ctx.bakeLayer(ModelLayers.SLIME_OUTER));
        Identifier tex = AwRender.base(d);
        r.addLayer(new RenderLayer<>(r) {
            @Override
            public void submit(PoseStack pose, SubmitNodeCollector collector, int light, SlimeRenderState state, float yRot, float xRot) {
                if (state.isInvisible) return;
                AwLook l = AwRender.look(state);
                int tint = ARGB.color(Math.min(200, ARGB.alpha(l.tint)), l.tint & 0xFFFFFF);
                collector.order(1).submitModel(outer, state, pose, RenderTypes.entityTranslucent(tex), light,
                        LivingEntityRenderer.getOverlayCoords(state, 0.0F), tint, null, state.outlineColor);
            }
        });
        return r;
    }
}
