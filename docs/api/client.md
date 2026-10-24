# Client API cheat sheet — Minecraft 26.3 / NeoForge 26.3.0.95

Every signature below was read from the real sources on 2026-10-23. Path prefixes:
`src/` = `/Volumes/MEDIA/Awaken/.ref/src/`, `neo/` = `/Volumes/MEDIA/Awaken/.ref/neo/`,
`assets/` = `/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/`, `data/` = `/Volumes/MEDIA/Awaken/.ref/src/data/minecraft/`.

## 0. Renames that break 1.21.x code (read first)

| 1.21.x | 26.3 |
|---|---|
| `ResourceLocation` | `net.minecraft.resources.Identifier` (`fromNamespaceAndPath`, `withDefaultNamespace`, `parse`) |
| `GuiGraphics` | `net.minecraft.client.gui.GuiGraphicsExtractor` (it records render state; it does not draw) |
| `drawString` / `drawCenteredString` / `renderOutline` / `renderItem` | `text` / `centeredText` / `outline` / `item` |
| `guiGraphics.pose()` = `PoseStack` | `Matrix3x2fStack` (JOML): `pushMatrix()`, `popMatrix()`, `translate(x,y)`, `scale(x,y)` |
| `Screen.render(...)`, `renderBackground(...)` | `extractRenderState(...)`, `extractBackground(...)` (background is called for you) |
| `Minecraft.setScreen(s)`, `mc.screen` | `mc.gui.setScreen(s)`, `mc.gui.screen()`. `mc.gui` is now `net.minecraft.client.gui.Gui` (screens); the HUD is `mc.gui.hud` (`Hud`) |
| `options.hideGui` | `mc.gui.hud.isHidden()` |
| `keyPressed(int,int,int)`, `mouseClicked(double,double,int)` | `keyPressed(KeyEvent)`, `mouseClicked(MouseButtonEvent, boolean doubleClick)` |
| GLFW key codes, mouse button 0 = left | SDL3 scancodes (`InputConstants.KEY_*`), mouse buttons 1-based: LEFT=1, MIDDLE=2, RIGHT=3 |
| `KeyMapping(String, int, String category)` | `KeyMapping(String, int, KeyMapping.Category)` |
| `EntityRenderer.render(entity, ..., MultiBufferSource, light)` | `extractRenderState(entity, state, pt)` + `submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)`. `MultiBufferSource` no longer exists |
| `RenderType` (`net.minecraft.client.renderer`) | `net.minecraft.client.renderer.rendertype.RenderType`; factories in `...rendertype.RenderTypes` |
| `com.mojang.blaze3d.pipeline.RenderPipeline` | `com.mojang.renderpearl.api.pipeline.RenderPipeline` |
| `PoseStack.mulPose(Quaternionf)` | `rotate(Quaternionfc)`, `rotate(Axis, rad)`, `rotateDegrees(Axis, deg)` (`com.mojang.math.Axis`) |
| `TextureSheetParticle`, `ParticleRenderType.PARTICLE_SHEET_*` | `SingleQuadParticle` + `SingleQuadParticle.Layer` |
| `EntityType.ZOMBIE` constants | `net.minecraft.world.entity.EntityTypes.ZOMBIE` (EntityType has 0 constants now) |
| `Entity.moveTo(...)` | `Entity.snapTo(...)` |
| `Camera.getPosition()` | `Camera.position()`; the camera is `mc.gameRenderer.mainCamera()` |
| `RenderLevelStageEvent.Stage` enum | one subclass per stage; custom geometry goes through `SubmitCustomGeometryEvent` |
| biome `effects.fog_color` / `sky_color` | environment attributes `minecraft:visual/fog_color`, `minecraft:visual/sky_color` |
| `GameRenderer.loadEffect/shutdownEffect` | `ServerPlayer.addPostEffect/removePostEffect/clearPostEffects` (synced), client `LocalPlayer.setActivePostEffects` |

Colours are always ARGB ints. `text()` skips anything with alpha 0, so `0xFFFFFF` draws **nothing**; use `0xFFFFFFFF`.

Buses: `IModBusEvent`s go on the mod bus (`RegisterGuiLayersEvent`, `RegisterKeyMappingsEvent`, `EntityRenderersEvent.*`,
`RegisterParticleProvidersEvent`, `RegisterClientPayloadHandlersEvent`, `RegisterColorHandlersEvent.*`, `RegisterRenderStateModifiersEvent`,
`RegisterCustomEnvironmentEffectRendererEvent`, `RegisterRenderPipelinesEvent`); everything else here (`ClientTickEvent`,
`ClientPlayerNetworkEvent`, `ViewportEvent.*`, `RenderLevelStageEvent.*`, `SubmitCustomGeometryEvent`, `ExtractLevelRenderStateEvent`,
`InputEvent.*`, `ScreenEvent.*`) on `NeoForge.EVENT_BUS`. Verified style (`neo/net/neoforged/neoforge/client/ClientNeoForgeMod.java`):

```java
@Mod(value = "awaken", dist = Dist.CLIENT)
public final class AwakenClient {
    public AwakenClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterGuiLayersEvent.class, StatusHud::onRegisterLayers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, AwakenKeys::onClientTick);
    }
}
```
`@EventBusSubscriber(modid = "awaken")` (`net.neoforged.fml.common.EventBusSubscriber`) also works and picks the bus per event
type (NeoForge's own `NetworkInitialization` subscribes a mod-bus event with no `bus` argument).

---

## 1. HUD overlays

- `net.neoforged.neoforge.client.event.RegisterGuiLayersEvent` (mod bus):
  `registerBelowAll(Identifier, GuiLayer)`, `registerBelow(Identifier other, Identifier id, GuiLayer)`,
  `registerAbove(Identifier other, Identifier id, GuiLayer)`, `registerAboveAll(Identifier, GuiLayer)`,
  `replaceLayer(Identifier, GuiLayer)`, `wrapLayer(Identifier, UnaryOperator<GuiLayer>)`.
- `net.neoforged.neoforge.client.gui.GuiLayer`: `void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker)`.
- `net.neoforged.neoforge.client.gui.VanillaGuiLayers`: `CAMERA_OVERLAYS, CROSSHAIR, AFTER_CAMERA_DECORATIONS, HOTBAR,
  PLAYER_HEALTH, ARMOR_LEVEL, FOOD_LEVEL, VEHICLE_HEALTH, AIR_LEVEL, CONTEXTUAL_INFO_BAR_BACKGROUND, EXPERIENCE_LEVEL,
  CONTEXTUAL_INFO_BAR, SELECTED_ITEM_NAME, SPECTATOR_TOOLTIP, EFFECTS, BOSS_OVERLAY, SLEEP_OVERLAY, DEMO_OVERLAY,
  SCOREBOARD_SIDEBAR, OVERLAY_MESSAGE, TITLE, CHAT, TAB_LIST, SUBTITLE_OVERLAY`.
- `DeltaTracker`: `getGameTimeDeltaPartialTick(boolean ignoreFrozenGame)`, `getGameTimeDeltaTicks()`, `getRealtimeDeltaTicks()`.

`GuiGraphicsExtractor` (all `int` coordinates, colours ARGB):

| Method | Notes |
|---|---|
| `fill(int x0,int y0,int x1,int y1,int col)` | semi-transparent = alpha < 0xFF, e.g. `0x80000000` |
| `fill(RenderPipeline, x0,y0,x1,y1,col)` / `fillGradient(x0,y0,x1,y1,col1,col2)` | vertical gradient |
| `outline(int x,int y,int width,int height,int color)` | 1 px border; note width/height, not x1/y1 |
| `horizontalLine(x0,x1,y,col)`, `verticalLine(x,y0,y1,col)` | |
| `text(Font, String\|Component\|FormattedCharSequence, int x, int y, int color[, boolean dropShadow])` | shadow defaults to `true` |
| `centeredText(Font, String\|Component\|FormattedCharSequence, int x, int y, int color)` | |
| `textWithWordWrap(Font, FormattedText, x, y, width, col[, shadow])` | returns next y (line = 9 px) |
| `blit(RenderPipeline, Identifier texture, x, y, float u, float v, w, h, texW, texH[, color])` | full texture path `awaken:textures/gui/x.png` |
| `blitSprite(RenderPipeline, Identifier sprite, x, y, w, h[, float alpha \| int color])` | GUI atlas: file `textures/gui/sprites/<path>.png`, id `awaken:<path>`; honours `.mcmeta` nine-slice/tile |
| `item(ItemStack, x, y)`, `itemDecorations(Font, ItemStack, x, y)` | |
| `enableScissor(x0,y0,x1,y1)` / `disableScissor()` | stack; transformed by current pose |
| `pose()` → `Matrix3x2fStack` | `pushMatrix/popMatrix/translate/scale/rotate(rad)` |
| `nextStratum()`, `blurBeforeThisStratum()` | layering / menu blur |
| `guiWidth()`, `guiHeight()` | scaled size |
| `setTooltipForNextFrame(Font, Component, x, y)` | |

Pipelines: `net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED` (blit/sprites), `GUI` (fills).
`Font` (`mc.font`): `int width(String|FormattedText|FormattedCharSequence)`, `lineHeight` (= 9), `split(FormattedText, int)`.

```java
public final class StatusHud {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("awaken", "status_hud");
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("awaken", "hud/mana_frame"); // textures/gui/sprites/hud/mana_frame.png

    static void onRegisterLayers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, ID, StatusHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;   // modded layers are NOT auto-hidden by F1
        Font font = mc.font;
        int x = 8, y = 8, w = 120, h = 30;
        g.fill(x, y, x + w, y + h, 0x80000000);                   // 50% black panel
        g.outline(x, y, w, h, 0xFF4FC3F7);
        g.text(font, Component.literal("LV 12"), x + 4, y + 4, 0xFFFFFFFF);
        int labelW = font.width("MP");
        g.enableScissor(x + 6 + labelW, y + 16, x + w - 4, y + 24);   // clip the bar
        g.fill(x + 6 + labelW, y + 16, x + 6 + labelW + 70, y + 24, 0xFF2196F3);
        g.disableScissor();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y + h + 2, 81, 5);
        g.pose().pushMatrix();
        g.pose().translate(x, y + h + 10);
        g.pose().scale(0.75F, 0.75F);
        g.text(font, "small text", 0, 0, 0xFFAAAAAA, false);
        g.pose().popMatrix();
    }
}
```
Gotchas: nothing is drawn immediately (state is collected then rendered), so no `RenderSystem` calls here. Cancel/peek vanilla
layers with `RenderGuiLayerEvent.Pre/Post` (game bus).
Verified: `neo/.../client/event/RegisterGuiLayersEvent.java`, `neo/.../client/gui/{GuiLayer,VanillaGuiLayers,GuiLayerManager}.java`,
`src/net/minecraft/client/gui/{GuiGraphicsExtractor,Font,Hud}.java`, `assets/atlases/gui.json`.

## 2. Screens

`net.minecraft.client.gui.screens.Screen`:
- ctor `protected Screen(Component title)`; fields `width`, `height`, `font`, `minecraft`, `title`.
- `protected void init()`, `public void tick()`, `public void removed()`, `public void onClose()` (→ `minecraft.gui.popScreenLayer()`).
- `public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)` — super draws the widgets.
- `public void extractBackground(GuiGraphicsExtractor, int, int, float)` — called automatically before `extractRenderState`
  (blur + menu texture unless `isInGameUi()` returns true, then a dark gradient). Override with an empty body for none.
- `public boolean keyPressed(KeyEvent event)`, `keyReleased(KeyEvent)`, `charTyped(CharacterEvent)`,
  `mouseClicked(MouseButtonEvent event, boolean doubleClick)`, `mouseReleased(MouseButtonEvent)`,
  `mouseDragged(MouseButtonEvent, double dx, double dy)`, `mouseScrolled(double x, double y, double scrollX, double scrollY)`.
- `public boolean isPauseScreen()` (default `true`), `isInGameUi()` (default `false`), `shouldCloseOnEsc()`.
- `addRenderableWidget(T)`, `addRenderableOnly(T)`, `addWidget(T)`, `removeWidget`, `clearWidgets`, `rebuildWidgets`.

Input records (`net.minecraft.client.input`): `KeyEvent(int key, int keycode, int modifiers)` (`key` = SDL scancode,
`keycode` = SDL keycode); `MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo)` with `button()`, `x()`, `y()`.
Both implement `InputWithModifiers`: `isEscape()`, `isConfirmation()`, `isSelection()`, `hasShiftDown()`, `hasControlDown()`, `hasAltDown()`.

`net.minecraft.client.gui.components.Button.builder(Component, Button.OnPress)` → `.pos(x,y)`, `.width(w)`, `.size(w,h)`,
`.bounds(x,y,w,h)`, `.tooltip(Tooltip)`, `.build()`. `Button.OnPress`: `void onPress(Button)`. `Tooltip.create(Component)`.

```java
public final class StatusScreen extends Screen {
    public StatusScreen() { super(Component.translatable("screen.awaken.status")); }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.STATUS.matches(event)) { onClose(); return true; }   // same key toggles it shut
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
}
// open (client thread): Minecraft.getInstance().gui.setScreen(new StatusScreen());
// stack on top of the current one (NeoForge): mc.gui.pushScreenLayer(screen); onClose() pops back.
```
Verified: `src/net/minecraft/client/gui/screens/Screen.java`, `src/net/minecraft/client/gui/Gui.java` (`setScreen`, `screen()`,
`pushScreenLayer`, `popScreenLayer`), `src/net/minecraft/client/input/{KeyEvent,MouseButtonEvent,InputWithModifiers}.java`,
`src/net/minecraft/client/gui/components/{Button,Renderable,GuiEventListener}.java`.

## 3. Key mappings

- `net.minecraft.client.KeyMapping` ctors: `(String name, int keysym, Category)`, `(String, InputConstants.Type, int, Category)`,
  `(String, Type, int, Category, int order)`, NeoForge: `(String, IKeyConflictContext, Type, int, Category)`,
  `(String, IKeyConflictContext, KeyModifier, Type, int, Category)` (+ `InputConstants.Key` variants).
- `public record KeyMapping.Category(Identifier id)`; vanilla `MOVEMENT, MISC, MULTIPLAYER, GAMEPLAY, INVENTORY, CREATIVE,
  SPECTATOR, DEBUG`; `label()` = `Component.translatable("key.category.<ns>.<path>")`. `Category.register(Identifier)` is deprecated.
- `RegisterKeyMappingsEvent` (mod bus): `register(KeyMapping)`, `registerCategory(KeyMapping.Category)`.
- Query: `consumeClick()`, `isDown()`, `matches(KeyEvent)`, `matchesMouse(MouseButtonEvent)`, `getKey()`.
- `com.mojang.blaze3d.platform.InputConstants`: SDL scancodes, e.g. `KEY_A=4 … KEY_Z=29`, `KEY_1=30 … KEY_0=39`, `KEY_ESCAPE=41`,
  `KEY_TAB=43`, `KEY_GRAVE=53`, `KEY_F1=58`, `KEY_LSHIFT=225`, `KEY_LALT=226`, `KEY_UP=82`; `MOUSE_BUTTON_LEFT=1, MIDDLE=2, RIGHT=3`;
  `Type.KEYBOARD`, `Type.MOUSE`; `UNKNOWN`; `isKeyDown(int scancode)` (no Window arg any more).
- Context: `net.neoforged.neoforge.client.settings.KeyConflictContext.{UNIVERSAL, GUI, IN_GAME}`.

```java
public final class AwakenKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("awaken", "main"));
    public static final KeyMapping STATUS = new KeyMapping("key.awaken.status", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYBOARD, InputConstants.KEY_G, CATEGORY);

    static void onRegisterKeys(RegisterKeyMappingsEvent e) {   // mod bus
        e.registerCategory(CATEGORY);                          // required: Controls sorting uses the registered list
        e.register(STATUS);
    }

    static void onClientTick(ClientTickEvent.Post e) {          // game bus
        Minecraft mc = Minecraft.getInstance();
        while (STATUS.consumeClick()) {
            if (mc.player != null && mc.gui.screen() == null) mc.gui.setScreen(new StatusScreen());
        }
    }
}
```
Verified: `src/net/minecraft/client/KeyMapping.java`, `src/com/mojang/blaze3d/platform/InputConstants.java`,
`neo/.../client/event/RegisterKeyMappingsEvent.java`, `neo/.../client/settings/KeyConflictContext.java`.

## 4. Client tick, login/logout, client → server payloads

- `net.neoforged.neoforge.client.event.ClientTickEvent.Pre` / `.Post` (game bus, no fields).
- `net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn` (`getPlayer()`, `getMultiPlayerGameMode()`,
  `getConnection()`), `.LoggingOut` (all three `@Nullable`), `.Clone` (`getOldPlayer()`, `getNewPlayer()`) — respawn/dimension change.
- Send: `net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(CustomPacketPayload payload, CustomPacketPayload... more)`.
- Register (common, mod bus): `RegisterPayloadHandlersEvent.registrar(String version)` →
  `playToServer(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, IPayloadHandler<T>)`,
  `playToClient(Type, codec)` (handler registered client-side), `playBidirectional(...)`. Default handler thread: `HandlerThread.MAIN`.
- Client handlers: `net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent.register(Type<T>, IPayloadHandler<T>)` (mod bus).

```java
public record OpenGatePayload(int gateId) implements CustomPacketPayload {
    public static final Type<OpenGatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("awaken", "open_gate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGatePayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenGatePayload::gateId, OpenGatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
// common:  e.registrar("1").playToServer(OpenGatePayload.TYPE, OpenGatePayload.STREAM_CODEC,
//              (p, ctx) -> GateService.open((ServerPlayer) ctx.player(), p.gateId()));
// client:  ClientPacketDistributor.sendToServer(new OpenGatePayload(7));
```
Verified: `neo/.../client/event/{ClientTickEvent,ClientPlayerNetworkEvent}.java`, `neo/.../client/network/ClientPacketDistributor.java`,
`neo/.../client/network/event/RegisterClientPayloadHandlersEvent.java`, `neo/.../network/registration/PayloadRegistrar.java`.

## 5. Entity rendering

### 5.1 Registration and the 26.3 renderer contract
- `EntityRenderersEvent.RegisterRenderers.registerEntityRenderer(EntityType<? extends T>, EntityRendererProvider<T>)`
  and `registerBlockEntityRenderer(...)`; `EntityRenderersEvent.RegisterLayerDefinitions.registerLayerDefinition(ModelLayerLocation, Supplier<LayerDefinition>)`;
  `EntityRenderersEvent.AddLayers` (`getRenderer(EntityType)`, `getPlayerRenderer(PlayerModelType)`, `getMannequinRenderer(PlayerModelType)`).
- `net.minecraft.client.renderer.entity.EntityRenderer<T extends Entity, S extends EntityRenderState>`: `public abstract S createRenderState()`;
  `public void extractRenderState(T entity, S state, float partialTicks)` (copy everything you need from the entity here);
  `public void submit(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera)` (no entity access).
- `LivingEntityRenderer<T, S extends LivingEntityRenderState, M extends EntityModel<? super S>>`: `addLayer(RenderLayer)`,
  `public abstract Identifier getTextureLocation(S state)`, `getModelTint(S)`, `scale(S, PoseStack)`. `MobRenderer(Context, M, float shadow)`.
- `RenderLayer.submit(PoseStack, SubmitNodeCollector, int lightCoords, S state, float yRot, float xRot)`.
- `SubmitNodeCollector` (`net.minecraft.client.renderer`) extends `OrderedSubmitNodeCollector`; `order(int)` returns an ordered
  sub-collector (higher draws later, e.g. `EyesLayer` uses `order(1)`). Key methods:
  `submitModel(Model<? super S>, S, PoseStack, RenderType, int light, int overlay, int tint, @Nullable UvMapping, int outlineColor)`
  (+ shorter overloads), `submitModelPart(...)`, `submitCustomGeometry(PoseStack, RenderType, CustomGeometryRenderer)`,
  `submitShapeOutline(PoseStack, VoxelShape, RenderType, int color, float width, boolean afterTerrain)`, `submitText(...)`,
  `submitNameTag(...)`, `submitItem(...)`, `submitBlockModel(...)`. `CustomGeometryRenderer`: `void render(PoseStack.Pose, VertexConsumer)`.
- `EntityModel<T extends EntityRenderState>`: ctors `(ModelPart root)` (renders with `RenderTypes::entityCutout`) and
  `(ModelPart root, Function<Identifier, RenderType>)`; `setupAnim(T)` — call `super.setupAnim` first (it resets the pose).

### 5.2 Custom mob reusing a vanilla model + custom texture
`net.minecraft.client.model.monster.zombie.ZombieModel<S extends ZombieRenderState>`, `...monster.skeleton.SkeletonModel<S extends SkeletonRenderState>`.
`AbstractZombieRenderer` / `AbstractSkeletonRenderer` require vanilla `Zombie` / `AbstractSkeleton` subclasses, so for your own
`Monster` subclass extend `HumanoidMobRenderer<T extends Mob, S extends HumanoidRenderState, M extends HumanoidModel<S>>`
(ctor `(Context, M model, float shadow)`, adds head/wings/item-in-hand layers):

```java
public final class GateZombieRenderer extends HumanoidMobRenderer<GateZombie, ZombieRenderState, ZombieModel<ZombieRenderState>> {
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("awaken", "textures/entity/gate_zombie.png");

    public GateZombieRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);   // 64x64 zombie UV layout
    }
    @Override public ZombieRenderState createRenderState() { return new ZombieRenderState(); }
    @Override public void extractRenderState(GateZombie e, ZombieRenderState s, float pt) {
        super.extractRenderState(e, s, pt);
        s.isAggressive = e.isAggressive();          // arms-forward pose
    }
    @Override public Identifier getTextureLocation(ZombieRenderState s) { return TEX; }
}
// mod bus: e.registerEntityRenderer(AwakenEntities.GATE_ZOMBIE.get(), GateZombieRenderer::new);
```
Armour: `addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, ctx.getModelSet(), ZombieModel::new), ctx.getEquipmentRenderer()))`
(pattern from `ZombieRenderer`/`AbstractSkeletonRenderer`).

### 5.3 Fully custom model + vanilla keyframe animations (Breeze/Warden pattern)
`AnimationDefinition.Builder.withLength(float).looping().addAnimation(String bone, AnimationChannel).build()`;
`new AnimationChannel(AnimationChannel.Targets.{POSITION,ROTATION,SCALE}, Keyframe...)`;
`new Keyframe(float seconds, Vector3f, AnimationChannel.Interpolations.{LINEAR,CATMULLROM})`;
`KeyframeAnimations.posVec(x,y,z)`, `degreeVec(x,y,z)`, `scaleVec(x,y,z)`.
`AnimationDefinition.bake(ModelPart root)` → `KeyframeAnimation` with `apply(AnimationState, float ageInTicks[, float speed])`,
`applyWalk(float pos, float speed, float speedFactor, float scaleFactor)`, `applyStatic()`.
`net.minecraft.world.entity.AnimationState` (common): `start(int tickCount)`, `startIfStopped(int)`, `animateWhen(boolean, int)`,
`stop()`, `isStarted()`, `copyFrom(AnimationState)`.

```java
public final class ShadeAnimations {
    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(2.0F).looping()
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
            new Keyframe(0.0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(1.0F, KeyframeAnimations.posVec(0F, 1F, 0F), AnimationChannel.Interpolations.CATMULLROM),
            new Keyframe(2.0F, KeyframeAnimations.posVec(0F, 0F, 0F), AnimationChannel.Interpolations.CATMULLROM)))
        .build();
}

public final class ShadeRenderState extends LivingEntityRenderState {
    public final AnimationState idle = new AnimationState();
    public final AnimationState attack = new AnimationState();
}

public final class ShadeModel extends EntityModel<ShadeRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("awaken", "shade"), "main");
    private final ModelPart head;
    private final KeyframeAnimation idle;

    public ShadeModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);          // translucent body; default is entityCutout
        this.head = root.getChild("body").getChild("head");
        this.idle = ShadeAnimations.IDLE.bake(root);           // throws if a bone name is missing
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body",
            CubeListBuilder.create().texOffs(0, 16).addBox(-4F, -12F, -2F, 8F, 12F, 4F), PartPose.offset(0F, 24F, 0F));
        body.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F), PartPose.offset(0F, -12F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override public void setupAnim(ShadeRenderState s) {
        super.setupAnim(s);
        head.yRot = s.yRot * Mth.DEG_TO_RAD;
        head.xRot = s.xRot * Mth.DEG_TO_RAD;
        idle.apply(s.idle, s.ageInTicks);
    }
}

public final class ShadeRenderer extends MobRenderer<Shade, ShadeRenderState, ShadeModel> {
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("awaken", "textures/entity/shade.png");
    private static final RenderType EYES = RenderTypes.eyes(Identifier.fromNamespaceAndPath("awaken", "textures/entity/shade_eyes.png"));

    public ShadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ShadeModel(ctx.bakeLayer(ShadeModel.LAYER)), 0.4F);
        addLayer(new EyesLayer<>(this) { @Override public RenderType renderType() { return EYES; } });   // full-bright eyes
    }
    @Override public ShadeRenderState createRenderState() { return new ShadeRenderState(); }
    @Override public void extractRenderState(Shade e, ShadeRenderState s, float pt) {
        super.extractRenderState(e, s, pt);
        s.idle.copyFrom(e.idle);
        s.attack.copyFrom(e.attack);
    }
    @Override public Identifier getTextureLocation(ShadeRenderState s) { return TEX; }
}
// mod bus: e.registerLayerDefinition(ShadeModel.LAYER, ShadeModel::createBodyLayer);
```
Entity side (common class): `public final AnimationState idle = new AnimationState();` then in `tick()`:
`if (level().isClientSide()) idle.animateWhen(!walkAnimation.isMoving(), tickCount);`. One-shot animations: server calls
`level().broadcastEntityEvent(this, EntityEvent.START_ATTACKING)` (byte 4; vanilla ids go up to 72), client overrides
`handleEntityEvent(byte id)` → `attack.start(tickCount)` (exactly what `Warden` does).
NeoForge alternative: JSON keyframe files in `assets/<ns>/neoforge/animations/entity/*.json`, loaded via
`Model.getAnimation(Identifier)` → `AnimationHolder.get()`.
Verified: `src/net/minecraft/client/renderer/entity/{EntityRenderer,LivingEntityRenderer,MobRenderer,HumanoidMobRenderer,ZombieRenderer,SkeletonRenderer,BreezeRenderer}.java`,
`src/net/minecraft/client/renderer/{SubmitNodeCollector,OrderedSubmitNodeCollector}.java`, `src/net/minecraft/client/model/{EntityModel,Model}.java`,
`src/net/minecraft/client/model/monster/{breeze/BreezeModel,warden/WardenModel}.java`, `src/net/minecraft/client/animation/*.java`,
`src/net/minecraft/world/entity/monster/warden/Warden.java`, `neo/.../client/event/EntityRenderersEvent.java`.

### 5.4 Player-skinned stand-in body: vanilla Mannequin
- Entity: `net.minecraft.world.entity.decoration.Mannequin extends Avatar extends LivingEntity` (not a `Mob`: no AI/pathing).
  Type `EntityTypes.MANNEQUIN` (0.6×1.8). Profile is synced data `DATA_PROFILE` (`ResolvableProfile`); `setProfile` is private but
  `Entity.setComponent(DataComponents.PROFILE, profile)` routes to it (`applyImplicitComponent`). NBT: `profile`, `hidden_layers`,
  `main_hand`, `pose` (STANDING/CROUCHING/SWIMMING/FALL_FLYING/SLEEPING), `immovable`, `description`, `hide_description`.
- Client: `net.minecraft.client.entity.ClientMannequin` (installed by `Mannequin.constructor` when the level is a `ClientLevel`),
  looks the skin up through `PlayerSkinRenderCache` and renders with `AvatarRenderer<ClientMannequin>` (dispatcher
  switches on `ClientMannequin`, not on the entity type).
- Yes, a mod can spawn one with any player's skin (server side):

```java
Mannequin body = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.TRIGGERED);
if (body != null) {
    body.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
    // or ResolvableProfile.createUnresolved(String name) / createUnresolved(UUID id)
    body.snapTo(x, y, z, yaw, 0F);
    level.addFreshEntity(body);
}
```
- A subclass of `Mannequin` with its own `EntityType` gets a plain object on the client, not `ClientMannequin`, so it will not use
  the avatar renderer. For a *moving* player-skinned mob, use your own `Mob` + `dev.awaken.core.client.skin.SkinnedMobRenderer`
  (SPEC §12), sync a `ResolvableProfile` (`EntityDataSerializers.RESOLVABLE_PROFILE`), and in `extractRenderState` call
  `state.wear(ctx.getPlayerSkinRenderCache().getOrDefault(profile).playerSkin())` (texture and slim or wide).
- **Never give a non-player renderer an `AvatarRenderState`** (e.g. `HumanoidMobRenderer<YourMob, AvatarRenderState, PlayerModel>`):
  `EntityRenderDispatcher.submit` looks the renderer up from the state (`getRenderer(S)`), and any `AvatarRenderState` goes to the
  vanilla player `AvatarRenderer` picked by `state.skin.model()`. Your renderer's `extractRenderState` runs (it is picked by entity
  type) but its `submit`, `getTextureLocation`, `getRenderType`, `getModelTint`, `scale` and layers never do; the body draws
  `state.skin` (default slim Steve unless set). A plain `HumanoidModel` on `ModelLayers.PLAYER`/`PLAYER_SLIM` renders the hat,
  jacket, sleeves and pants (children of head, body, arms and legs in `PlayerModel.createMesh`). To pick wide or slim per state,
  set `this.model` in `submit` before `super.submit` (as `AgeableMobRenderer` does); that needs a `MobRenderer` parent, because
  `HumanoidMobRenderer`'s parent `AgeableMobRenderer.submit` resets the model to the adult one.
Verified: `src/net/minecraft/world/entity/decoration/Mannequin.java`, `src/net/minecraft/client/entity/ClientMannequin.java`,
`src/net/minecraft/client/renderer/entity/{EntityRenderDispatcher,player/AvatarRenderer}.java`,
`src/net/minecraft/client/renderer/entity/{AgeableMobRenderer,HumanoidMobRenderer,MobRenderer,LivingEntityRenderer}.java`,
`src/net/minecraft/client/model/player/PlayerModel.java`, `src/net/minecraft/client/model/geom/LayerDefinitions.java`,
`src/net/minecraft/client/renderer/PlayerSkinRenderCache.java`, `src/net/minecraft/world/item/component/ResolvableProfile.java`,
`src/net/minecraft/world/entity/{Entity,EntityTypes,EntityType}.java`.

## 6. World-space rendering (beams, quads, lines)

Frame order: extract (main thread reads the world) → submit → render. Events:
- `ExtractLevelRenderStateEvent` (game bus): `getLevel()`, `getCamera()`, `getFrustum()`, `getDeltaTracker()`, `getRenderState()`
  (`LevelRenderState`). Store per-frame data with `getRenderState().setRenderData(ContextKey<T>, T)`.
- `SubmitCustomGeometryEvent` (game bus): `getLevelRenderState()`, `getSubmitNodeCollector()`, `getPoseStack()` (identity — you
  translate camera-relative), `getRenderableSections()`. **Use this for custom geometry.**
- `RenderLevelStageEvent.{AfterSky, AfterOpaqueBlocks, AfterOpaqueFeatures, AfterTranslucentFeatures, AfterTranslucentBlocks,
  AfterTranslucentParticles, AfterWeather, OitTranslucent, AfterLevel}`: low-level, give an active `RenderPass` (`getRenderPass()`,
  never close it); buffer uploads must happen in `PrepareRenderBuffersEvent`. Only for raw GPU work.
- Camera: `levelRenderState.cameraRenderState.pos` (`Vec3`), `.orientation` (`Quaternionf`), `.depthFar`; time: `levelRenderState.gameTime`,
  `.worldPartialTicks`; main thread: `mc.gameRenderer.mainCamera().position()`.

Beacon code (`BeaconRenderer`, reusable): `public static void submitBeaconBeam(PoseStack, SubmitNodeCollector, Identifier beamTexture,
float scale, float animationTime, int beamStart, int height, int color, float solidBeamRadius, float beamGlowRadius)`;
`BEAM_LOCATION = minecraft:textures/entity/beacon/beacon_beam.png`; it translates `(0.5, 0, 0.5)` itself and draws with
`RenderTypes.beaconBeam(texture, false)` (core) and `beaconBeam(texture, true)` (glow, `ARGB.color(32, color)`).
Vertex format used: `buffer.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0)`.

```java
public final class GateBeams {
    private static final ContextKey<List<Vec3>> BEAMS = new ContextKey<>(Identifier.fromNamespaceAndPath("awaken", "gate_beams"));

    static void onExtract(ExtractLevelRenderStateEvent e) {             // main thread: read game state here
        e.getRenderState().setRenderData(BEAMS, ClientGateCache.visibleBeamBases());
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        List<Vec3> beams = e.getLevelRenderState().getRenderData(BEAMS);
        if (beams == null) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        PoseStack ps = e.getPoseStack();
        float time = (e.getLevelRenderState().gameTime % 40) + e.getLevelRenderState().worldPartialTicks;
        for (Vec3 base : beams) {
            ps.pushPose();
            ps.translate(base.x - cam.x - 0.5, base.y - cam.y, base.z - cam.z - 0.5);   // beam adds +0.5 itself
            BeaconRenderer.submitBeaconBeam(ps, e.getSubmitNodeCollector(), BeaconRenderer.BEAM_LOCATION,
                1.0F, time, 0, 320, 0xFF7C4DFF, 0.2F, 0.25F);
            ps.popPose();
        }
    }

    // a textured quad in the pose's XY plane, centred on the pose origin (translate to target - cam first)
    static void quad(SubmitNodeCollector out, PoseStack ps, Identifier tex, float half, int argb) {
        out.submitCustomGeometry(ps, RenderTypes.entityTranslucentEmissive(tex), (pose, vc) -> {
            vc.addVertex(pose, -half, -half, 0).setColor(argb).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose,  half, -half, 0).setColor(argb).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose,  half,  half, 0).setColor(argb).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
            vc.addVertex(pose, -half,  half, 0).setColor(argb).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        });
    }
}
```
Billboard: `ps.rotate(e.getLevelRenderState().cameraRenderState.orientation)` before `quad(...)`.

**Lines / boxes — easiest path: gizmos** (`net.minecraft.gizmos.Gizmos`, rendered every frame, not debug-gated):
`line(Vec3 a, Vec3 b, int argb[, float width])`, `arrow(...)`, `cuboid(AABB|BlockPos, [float padding,] GizmoStyle)`,
`circle(Vec3, float r, GizmoStyle)`, `rect(...)`, `point(Vec3, int argb, float size)`, `billboardText(...)`;
returns `GizmoProperties` → `setAlwaysOnTop()`, `persistForMillis(int)`, `fadeOut()`. `GizmoStyle.stroke(argb[, width])`,
`fill(argb)`, `strokeAndFill(stroke, width, fill)`. Must be called inside a collector: `ClientTickEvent` handlers run inside
`mc.collectPerTickGizmos()` (lives one tick — emit every tick). Outside a collector it throws `IllegalStateException`.
Raw lines: `RenderTypes.lines()` / `linesTranslucent()` need `.setColor(..).setNormal(..).setLineWidth(w)` per vertex.

Far away: the far plane is `max(renderDistanceBlocks * 4, cloudRange * 16)` (`Camera.update`). Beyond that, draw at a clamped
distance along `(target - cam).normalize()` and scale up linearly (the beacon widens its radius by `distance / 96` for the same reason).
Custom pipelines: `RegisterRenderPipelinesEvent.registerPipeline(RenderPipeline)` + `RenderType.create(String, RenderSetup.builder(pipeline).withTexture("Sampler0", tex).createRenderSetup())`.
Verified: `neo/.../client/event/{RenderLevelStageEvent,SubmitCustomGeometryEvent,ExtractLevelRenderStateEvent}.java`,
`src/net/minecraft/client/renderer/{LevelRenderer,extract/LevelExtractor}.java`, `src/net/minecraft/client/renderer/blockentity/BeaconRenderer.java`,
`src/net/minecraft/gizmos/{Gizmos,GizmoProperties,GizmoStyle}.java`, `src/net/minecraft/client/Camera.java`, `src/com/mojang/blaze3d/vertex/{PoseStack,VertexConsumer}.java`.

## 7. Post effects

Files: `assets/<ns>/post_effect/<name>.json` (id `<ns>:<name>`), shaders `assets/<ns>/shaders/post/<name>.fsh|.vsh`
(referenced as `<ns>:post/<name>`); the full-screen vertex shader is `minecraft:core/screenquad`. Vanilla post effects: `blur`,
`creeper`, `entity_outline`, `invert`, `spider`.

`assets/post_effect/invert.json` (verbatim):
```json
{
    "targets": {
        "swap": {}
    },
    "passes": [
        {
            "vertex_shader": "minecraft:core/screenquad",
            "fragment_shader": "minecraft:post/invert",
            "inputs": [
                {
                    "sampler_name": "In",
                    "target": "minecraft:main"
                }
            ],
            "output": "swap",
            "uniforms": {
                "InvertConfig": [
                    {
                        "name": "InverseAmount",
                        "type": "float",
                        "value": 0.8
                    }
                ]
            }
        },
        {
            "vertex_shader": "minecraft:core/screenquad",
            "fragment_shader": "minecraft:post/blit",
            "inputs": [
                {
                    "sampler_name": "In",
                    "target": "swap"
                }
            ],
            "uniforms": {
                "BlitConfig": [
                    {
                        "name": "ColorModulate",
                        "type": "vec4",
                        "value": [ 1.0, 1.0, 1.0, 1.0 ]
                    }
                ]
            },
            "output": "minecraft:main"
        }
    ]
}
```
`assets/post_effect/blur.json` (content verbatim, whitespace compacted to one pass per line; 6 passes = 3× horizontal+vertical):
```json
{
  "targets": {"swap": {}},
  "passes": [
    {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/box_blur", "inputs": [{"sampler_name": "In", "target": "minecraft:main", "bilinear": true}], "output": "swap", "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": [1.0, 0.0]}, {"name": "Radius", "type": "float", "value": 0.0}]}},
    {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/box_blur", "inputs": [{"sampler_name": "In", "target": "swap", "bilinear": true}], "output": "minecraft:main", "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": [0.0, 1.0]}, {"name": "Radius", "type": "float", "value": 0.0}]}},
    {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/box_blur", "inputs": [{"sampler_name": "In", "target": "minecraft:main", "bilinear": true}], "output": "swap", "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": [1.0, 0.0]}, {"name": "Radius", "type": "float", "value": 0.0}]}},
    {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/box_blur", "inputs": [{"sampler_name": "In", "target": "swap", "bilinear": true}], "output": "minecraft:main", "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": [0.0, 1.0]}, {"name": "Radius", "type": "float", "value": 0.0}]}},
    {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/box_blur", "inputs": [{"sampler_name": "In", "target": "minecraft:main", "bilinear": true}], "output": "swap", "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": [1.0, 0.0]}, {"name": "Radius", "type": "float", "value": 0.0}]}},
    {"vertex_shader": "minecraft:core/screenquad", "fragment_shader": "minecraft:post/box_blur", "inputs": [{"sampler_name": "In", "target": "swap", "bilinear": true}], "output": "minecraft:main", "uniforms": {"BlurConfig": [{"name": "BlurDir", "type": "vec2", "value": [0.0, 1.0]}, {"name": "Radius", "type": "float", "value": 0.0}]}}
  ]
}
```
JSON schema (`PostChainConfig`): `targets` map → `{width?, height?, persistent=false, clear_color=0}`; pass =
`vertex_shader`, `fragment_shader`, `inputs` (target input `{sampler_name, target, use_depth_buffer=false, bilinear=false}` or
texture input `{sampler_name, location, width, height, bilinear}`), `output`, `uniforms` (block name → list of
`{name, type: int|ivec3|float|vec2|vec3|vec4|matrix4x4, value}`).

Shader format (`assets/shaders/post/invert.fsh`, GLSL 330 + explicit locations, std140 blocks; the JSON `"In"` sampler becomes
`InSampler`, sizes arrive in the `SamplerInfo` block as `OutSize`/`InSize`):
```glsl
#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;
layout(location = 0) in vec2 texCoord;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform InvertConfig { float InverseAmount; };   // block name == JSON "uniforms" key
layout(location = 0) out vec4 fragColor;

void main(){
    vec4 diffuseColor = texture(InSampler, texCoord);
    fragColor = vec4(mix(diffuseColor, 1.0 - diffuseColor, InverseAmount).rgb, 1.0);
}
```
`core/screenquad.vsh` uses `gl_VertexIndex` (Vulkan-style) and writes `layout(location = 0) out vec2 texCoord`.

Applying from code (what `/posteffect add|remove|clear|list` does — `src/net/minecraft/server/commands/PostEffectCommand.java`):
- Server, authoritative, synced via `ClientboundPostEffectsPacket`, **saved in player NBT (`post_effects`)**:
  `boolean ServerPlayer.addPostEffect(Identifier)`, `removePostEffect(Identifier)`, `clearPostEffects()`, `List<Identifier> getPostEffects()`.
- Client only: `LocalPlayer.setActivePostEffects(List<Identifier>)` / `getActivePostEffects()` — overwritten by the next server sync.
  `GameRenderer.update()` re-reads this list every frame; effects only apply while a player is rendered.
- Spectator slot: `mc.gameRenderer.setSpectatedEntityPostEffect(Identifier)` / `clearSpectatedEntityPostEffect()` — cleared by F5 and
  `setCameraEntity`; avoid for gameplay. Map an entity type to a spectator shader: `RegisterEntitySpectatorShadersEvent.register(EntityType<?>, Identifier)`.
- Clear: `removePostEffect(id)` (server) / remove from the client list. Effects that fail to load are blacklisted until resource reload.

Uniforms are baked into a GPU buffer when the `PostPass` is built (`PostPass` ctor) — there is **no API to change a uniform per
frame**. For time, `#include <minecraft:globals.glsl>` and read `GameTime` (also `ScreenSize`, `CameraOffset`, `MenuBlurRadius`).
For intensity, ship several JSON variants (`awaken:gate_tint_25/50/75/100`) and swap ids, or feed a `TextureInput`.
Verified: `assets/post_effect/*.json`, `assets/shaders/post/{invert,blit,box_blur}.fsh`, `assets/shaders/core/screenquad.vsh`,
`assets/shaders/include/globals.glsl`, `src/net/minecraft/client/renderer/{GameRenderer,PostChainConfig,PostPass,ShaderManager,UniformValue}.java`,
`src/net/minecraft/server/level/ServerPlayer.java`, `src/net/minecraft/client/player/LocalPlayer.java`.

## 8. Fog and sky colour

Events (game bus, `net.neoforged.neoforge.client.event.ViewportEvent`):
- `ViewportEvent.ComputeFogColor`: `getRed/Green/Blue()`, `setRed/Green/Blue(float)` (0–1), `getCamera()`, `getPartialTick()`.
- `ViewportEvent.RenderFog`: `getEnvironment()` (`@Nullable FogEnvironment`), `getType()` (`FogType`), `getFogData()`,
  `get/setNearPlaneDistance` (= `FogData.environmentalStart`), `get/setFarPlaneDistance` (= `environmentalEnd`),
  `scaleNear/FarPlaneDistance(float)`. `FogData` also has `renderDistanceStart/End`, `skyEnd`, `cloudEnd`, `color`.

```java
static void onFogColor(ViewportEvent.ComputeFogColor e) { if (InGate.active()) { e.setRed(0.15F); e.setGreen(0.05F); e.setBlue(0.25F); } }
static void onFog(ViewportEvent.RenderFog e) {
    if (InGate.active()) { e.setNearPlaneDistance(0F); e.setFarPlaneDistance(24F); e.getFogData().skyEnd = 24F; }
}
// client-only sky tint: the sky state is extracted before this event fires
private static final Vector3f GATE_SKY = new Vector3f(0.2F, 0.05F, 0.35F);
static void onExtract(ExtractLevelRenderStateEvent e) { if (InGate.active()) e.getRenderState().skyRenderState.skyColor = GATE_SKY; }
```
There is no sky-colour event. Data-driven route: environment attributes on a dimension type and/or biome (biome layers on top
of dimension). Ids (`net.minecraft.world.attribute.EnvironmentAttributes`): `minecraft:visual/fog_color`, `fog_start_distance`,
`fog_end_distance`, `sky_fog_end_distance`, `cloud_fog_end_distance`, `water_fog_color`, `water_fog_start_distance`,
`water_fog_end_distance`, `sky_color`, `sunrise_sunset_color`, `cloud_color`, `cloud_height`, `sun_angle`, `moon_angle`,
`star_angle`, `moon_phase`, `star_brightness`, `block_light_tint`, `sky_light_color`, `sky_light_factor`, `night_vision_color`,
`ambient_light_color`, `ambient_particles`, `default_dripstone_particle`; `minecraft:audio/background_music|ambient_sounds|music_volume`;
NeoForge adds `neoforge:custom_skybox|custom_clouds|custom_weather_effects` (renderers via
`RegisterCustomEnvironmentEffectRendererEvent.registerSkyboxRenderer(Identifier, CustomSkyboxRenderer)`;
`renderSky(LevelRenderState, SkyRenderState, Matrix4fc, GpuBufferSlice)` returns `true` to replace vanilla).
A value is either a plain value or `{"argument": ..., "modifier": "override|add|subtract|multiply|minimum|maximum|alpha_blend|blend_to_gray|and|or|...|append|overlay"}`.

Vanilla biome JSONs ARE in the jar: `data/worldgen/biome/*.json` (keys: `attributes, carvers, downfall, effects, features,
has_precipitation, temperature`; mob spawns are now the attribute `minecraft:gameplay/natural_mob_spawns`). `basalt_deltas.json`, visual part:
```json
"attributes": {
  "minecraft:visual/ambient_particles": { "argument": [ { "particle": { "type": "minecraft:white_ash" }, "probability": 0.118093334 } ], "modifier": "append" },
  "minecraft:visual/fog_color": "#685f70"
},
"effects": { "water_color": "#3f76e4" }
```
`swamp.json`: `"minecraft:visual/sky_color": "#78a7ff"`, `"minecraft:visual/water_fog_end_distance": {"argument": 0.85, "modifier": "multiply"}`.
`dimension_type/the_nether.json`: `"minecraft:visual/fog_start_distance": 10.0`, `"minecraft:visual/fog_end_distance": 96.0`.
Read on the client: `camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, partialTicks)`.
Verified: `neo/.../client/event/ViewportEvent.java`, `neo/.../client/ClientHooks.java`, `src/net/minecraft/client/renderer/fog/{FogData,FogRenderer}.java`,
`src/net/minecraft/world/attribute/{EnvironmentAttributes,modifier/AttributeModifier}.java`, `src/net/minecraft/client/renderer/{SkyRenderer,LevelRenderer}.java`,
`src/net/minecraft/client/renderer/state/level/SkyRenderState.java`, `neo/.../common/world/NeoForgeEnvironmentAttributes.java`, `data/worldgen/biome/`, `data/dimension_type/`.

## 9. Particles

- Type (common): `DeferredRegister.create(Registries.PARTICLE_TYPE, "awaken")`, `register("mana_spark", () -> new SimpleParticleType(false))`
  (`boolean overrideLimiter`).
- `RegisterParticleProvidersEvent` (mod bus): `registerSpriteSet(ParticleType<T>, ParticleResources.SpriteParticleRegistration<T>)`
  (`ParticleProvider<T> create(SpriteSet)`), `registerSpecial(ParticleType<T>, ParticleProvider<T>)` (no JSON/sprites).
- `ParticleProvider<T>`: `@Nullable Particle createParticle(T options, ClientLevel level, double x, double y, double z,
  double xAux, double yAux, double zAux, RandomSource random)` — `RandomSource` is new.
- `TextureSheetParticle` no longer exists → `net.minecraft.client.particle.SingleQuadParticle`: ctors
  `(ClientLevel, x, y, z, TextureAtlasSprite)` / `(ClientLevel, x, y, z, xa, ya, za, TextureAtlasSprite)`;
  `protected abstract SingleQuadParticle.Layer getLayer()` (`OPAQUE`, `TRANSLUCENT`, `OPAQUE_TERRAIN`, `TRANSLUCENT_TERRAIN`,
  `OPAQUE_ITEMS`, `TRANSLUCENT_ITEMS`); `setSpriteFromAge(SpriteSet)`, `setColor(r,g,b)`, `setAlpha(a)`, fields `quadSize, rCol, gCol,
  bCol, alpha, roll`. `Particle`: fields `xd yd zd age lifetime gravity friction hasPhysics`, `setLifetime(int)`,
  `setParticleSpeed(...)`, `scale(float)`, `tick()`, `protected int getLightCoords(float a)` (was `getLightColor`).
- JSON: `assets/<ns>/particles/<type_path>.json` = `{"textures": ["awaken:mana_spark_0", "awaken:mana_spark_1"]}` →
  files `assets/<ns>/textures/particle/<name>.png` (particle atlas, `assets/atlases/particles.json`). Vanilla `glow.json`: `{"textures": ["minecraft:glow"]}`.

```java
public final class ManaSparkParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    ManaSparkParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, xa, ya, za, sprites.first());
        this.sprites = sprites;
        this.friction = 0.96F; this.gravity = -0.01F; this.hasPhysics = false;
        this.lifetime = 20 + this.random.nextInt(10);
        this.setSpriteFromAge(sprites);
    }
    @Override protected SingleQuadParticle.Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }
    @Override protected int getLightCoords(float a) { return LightCoordsUtil.FULL_BRIGHT; }
    @Override public void tick() { super.tick(); this.setSpriteFromAge(this.sprites); this.alpha = 1F - (float) this.age / this.lifetime; }
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType o, ClientLevel level, double x, double y, double z,
                                                 double xa, double ya, double za, RandomSource random) {
            return new ManaSparkParticle(level, x, y, z, xa, ya, za, sprites);
        }
    }
}
// mod bus: e.registerSpriteSet(AwakenParticles.MANA_SPARK.get(), ManaSparkParticle.Provider::new);
```
Server spawn (`ServerLevel`, returns players reached): `<T extends ParticleOptions> int sendParticles(T particle, double x, double y,
double z, int count, double xDist, double yDist, double zDist, double speed)`; long range:
`sendParticles(T, boolean overrideLimiter, boolean alwaysShow, x, y, z, count, xDist, yDist, zDist, speed)` (overrideLimiter → 512 blocks,
else 32); one player only: `boolean sendParticles(ServerPlayer, T, boolean overrideLimiter, boolean alwaysShow, x, y, z, count, xDist, yDist, zDist, speed)`.
Client spawn: `ClientLevel.addParticle(ParticleOptions, x, y, z, xd, yd, zd)` / `addAlwaysVisibleParticle(...)`.
Verified: `neo/.../client/event/RegisterParticleProvidersEvent.java`, `src/net/minecraft/client/particle/{Particle,SingleQuadParticle,GlowParticle,ParticleProvider,ParticleResources}.java`,
`src/net/minecraft/server/level/ServerLevel.java`, `src/net/minecraft/core/particles/SimpleParticleType.java`, `assets/particles/glow.json`.

## 10. Item and block client assets

- Client item definition (required for every item): `assets/<ns>/items/<item>.json`
  ```json
  { "model": { "type": "minecraft:model", "model": "awaken:item/mana_crystal" } }
  ```
- Item model `assets/<ns>/models/item/<name>.json`: `{"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/mana_crystal"}}`
  → `assets/<ns>/textures/item/mana_crystal.png` (items atlas, `item/` prefix — separate from the blocks atlas).
- Block item: `items/<block>.json` with `"model": "awaken:block/<block>"`. Blockstate `assets/<ns>/blockstates/<block>.json`:
  `{"variants": {"": {"model": "awaken:block/gate_stone"}}}`; model `{"parent": "minecraft:block/cube_all", "textures": {"all": "awaken:block/gate_stone"}}`
  → `textures/block/gate_stone.png`.
- Tint: `"tints"` array in the item definition, index = `tintindex` / generated layer number. Sources (`ItemTintSources`):
  `minecraft:constant` (`"value"`: int, or `[r,g,b]` floats — not a hex string), `dye` (`default`), `potion` (`default`), `grass`,
  `firework`, `team`, `custom_model_data`. Vanilla `items/potion.json`:
  ```json
  { "model": { "type": "minecraft:model", "model": "minecraft:item/potion", "tints": [ { "type": "minecraft:potion", "default": -13083194 } ] } }
  ```
  Code-driven: `RegisterColorHandlersEvent.ItemTintSources.register(Identifier, MapCodec<? extends ItemTintSource>)`;
  blocks: `RegisterColorHandlersEvent.BlockTintSources.register(List<BlockTintSource>, Block...)`.
- Emissive: element-level `"light_emission": 15` in a model's `elements` (vanilla `models/block/cross_emissive.json`); NeoForge per
  generated-item layer:
  ```json
  { "parent": "minecraft:item/generated",
    "textures": { "layer0": "awaken:item/rune",
                  "layer1": { "sprite": "awaken:item/rune_glow", "neoforge_data": { "light_emission": 15 } } } }
  ```
  (`neoforge_data` = `ExtraFaceData{color, light_emission 0..15, ambient_occlusion}`; texture objects use `sprite` + optional `force_translucent`).
Verified: `assets/items/{diamond,potion,diamond_block}.json`, `assets/models/item/{diamond,generated}.json`, `assets/blockstates/diamond_block.json`,
`assets/models/block/{diamond_block,cross_emissive}.json`, `assets/atlases/{items,blocks}.json`, `src/net/minecraft/client/color/item/{ItemTintSources,Constant}.java`,
`src/net/minecraft/util/ExtraCodecs.java`, `neo/.../client/model/{ExtraFaceData,NeoForgeModelProperties}.java`, `src/net/minecraft/client/resources/model/sprite/Material.java`,
`neo/.../client/event/RegisterColorHandlersEvent.java`.

## 11. Sounds

- `net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier)` / `createFixedRangeEvent(Identifier, float range)`.
  Register: `DeferredRegister.create(Registries.SOUND_EVENT, "awaken").register("gate.open", SoundEvent::createVariableRangeEvent)`
  (`register(String, Function<Identifier, ? extends I>)`).
- `assets/<ns>/sounds.json`, keyed by the event path. Event fields: `replace` (bool), `subtitle`, `sounds`. Sound entry: string or
  `{name, volume=1 (>0), pitch=1 (>0), weight=1, preload=false, stream=false, attenuation_distance=16, type="file"|"event"}`.
  `type: file` → `name` is `assets/<ns>/sounds/<name>.ogg`; `type: event` → `name` is another sound event id.
  ```json
  {
    "gate.open": {
      "subtitle": "subtitles.awaken.gate.open",
      "sounds": [
        { "name": "minecraft:block/beacon/activate", "pitch": 0.5, "volume": 0.9 },
        { "name": "minecraft:mob/warden/sonic_boom1", "pitch": 1.4, "volume": 0.4, "attenuation_distance": 48 },
        { "name": "minecraft:entity.elder_guardian.curse", "type": "event", "pitch": 0.7 }
      ]
    },
    "system.ping": { "subtitle": "subtitles.awaken.system.ping", "sounds": [ { "name": "awaken:system/ping", "stream": false } ] }
  }
  ```
  The vanilla `sounds.json` is not in the client jar (it lives in the launcher asset index). File names above
  (`block/beacon/activate`, `mob/warden/sonic_boom1..4`, `mob/guardian/curse`) were checked in the local launcher index
  `~/Library/Application Support/minecraft/assets/indexes/34.json`, which is an older version; vanilla sound paths rarely change, but listen once in game.
- Play: server `level.playSound(@Nullable Entity except, double x, double y, double z, SoundEvent|Holder<SoundEvent>, SoundSource, float vol, float pitch)`;
  client local `ClientLevel.playLocalSound(double x, double y, double z, SoundEvent, SoundSource, float, float, boolean distanceDelay)`;
  UI: `mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent, float pitch[, float volume]))`.
Verified: `src/net/minecraft/sounds/SoundEvent.java`, `src/net/minecraft/client/resources/sounds/{SoundEventRegistrationSerializer,Sound,SimpleSoundInstance}.java`,
`src/net/minecraft/world/level/Level.java`, `src/net/minecraft/client/multiplayer/ClientLevel.java`, `neo/.../registries/DeferredRegister.java`.

## 12. Outlines / glowing for one client

- Vanilla rule: `Minecraft.shouldEntityAppearGlowing(Entity)` = `entity.isCurrentlyGlowing()` (server-synced shared flag 6) or
  spectator outline key. `EntityRenderer.extractRenderState` sets `state.outlineColor = appearsGlowing ? ARGB.opaque(entity.getTeamColor()) : 0`;
  `EntityRenderState.appearsGlowing()` = `outlineColor != 0`. The outline pass runs whenever any submitted model has an outline colour.
- Client-only glow: modify the render state after extraction with NeoForge's modifier hook (applies to subclasses of the given renderer):

```java
// mod bus
static void onModifiers(RegisterRenderStateModifiersEvent e) {
    e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {},   // com.google.common.reflect.TypeToken
        (entity, state) -> { if (ClientMarks.isMarked(entity.getId())) state.outlineColor = 0xFFFF3355; });
}
```
  (The `Class` overload needs a raw-type cast; the `TypeToken` form type-checks.) Invisible living entities still get an outline-only
  pass (`LivingEntityRenderer.getRenderType` → `RenderTypes.outline(texture)`).
- Blocks through walls: per-tick gizmo, always on top:

```java
static void onClientTick(ClientTickEvent.Post e) {          // runs inside the per-tick gizmo collector
    for (BlockPos pos : ClientMarks.blocks())
        Gizmos.cuboid(pos, 0.002F, GizmoStyle.stroke(0xFF00E5FF, 2.0F)).setAlwaysOnTop();
}
```
  Hovered-block outline only: `ExtractBlockOutlineRenderStateEvent` (cancel, or `addCustomRenderer(CustomBlockOutlineRenderer)`).
  Depth-tested outline instead: `SubmitCustomGeometryEvent` + `submitShapeOutline(PoseStack, VoxelShape, RenderTypes.lines(), color, width, afterTerrain)`
  (pose translated to `pos - cameraPos`).
Verified: `src/net/minecraft/client/Minecraft.java`, `src/net/minecraft/client/renderer/entity/{EntityRenderer,LivingEntityRenderer}.java`,
`src/net/minecraft/client/renderer/entity/state/EntityRenderState.java`, `neo/.../client/renderstate/{RegisterRenderStateModifiersEvent,RenderStateExtensions}.java`,
`neo/.../client/event/ExtractBlockOutlineRenderStateEvent.java`, `src/net/minecraft/gizmos/Gizmos.java`.

## 13. Camera tricks

- `Minecraft.setCameraEntity(@Nullable Entity)` (sets `mainCamera().setEntity` and runs `checkEntityPostEffect`, so creeper/spider/
  enderman shaders apply), `getCameraEntity()`; restore with `mc.setCameraEntity(mc.player)`. Input still drives `mc.player`.
  Perspective: `mc.options.getCameraType()` / `setCameraType(CameraType)`.
- `ViewportEvent.ComputeCameraAngles`: `get/setYaw`, `get/setPitch`, `get/setRoll` (degrees), `getPartialTick()`. Not fired while riding a
  minecart with the new minecart behaviour (see `Camera.alignWithEntity`).
- `ViewportEvent.ComputeFov`: `getFOV()`, `setFOV(float)` (degrees, final value). `ComputeFovModifierEvent` (`getFovModifier()`,
  `setNewFovModifier(float)`) for speed-style multipliers. Third-person distance: `CalculateDetachedCameraDistanceEvent`.

```java
public final class ScreenShake {
    private static int ticks; private static float strength;
    public static void kick(float deg, int duration) { strength = Math.max(strength, deg); ticks = Math.max(ticks, duration); }

    static void onClientTick(ClientTickEvent.Post e) { if (ticks > 0 && --ticks == 0) strength = 0F; }

    static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (ticks <= 0) return;
        float t = (ticks - e.getPartialTick()) / 20F;              // fade out over the last second
        float amp = strength * Math.min(1F, t);
        double time = (Minecraft.getInstance().level.getGameTime() + e.getPartialTick()) * 0.9;
        e.setYaw(e.getYaw() + amp * (float) Math.sin(time * 3.1));
        e.setPitch(e.getPitch() + amp * (float) Math.sin(time * 4.7 + 1.3));
        e.setRoll(e.getRoll() + amp * 0.5F * (float) Math.sin(time * 6.3 + 2.1));
    }
}
```
Verified: `src/net/minecraft/client/{Minecraft,Camera}.java`, `src/net/minecraft/client/renderer/GameRenderer.java`,
`neo/.../client/event/{ViewportEvent,ComputeFovModifierEvent,CalculateDetachedCameraDistanceEvent,RegisterEntitySpectatorShadersEvent}.java`.

## 14. Language keys (`assets/awaken/lang/en_us.json`)

| Thing | Key | Vanilla example (`assets/lang/en_us.json`) |
|---|---|---|
| Item | `item.awaken.<path>` | `item.minecraft.diamond` |
| Block (and its BlockItem) | `block.awaken.<path>` | `block.minecraft.diamond_block` |
| Entity | `entity.awaken.<path>` | `entity.minecraft.warden` |
| Key mapping | the `name` string passed to `KeyMapping`, convention `key.awaken.<action>` | `key.jump` |
| Key category | `key.category.awaken.<path>` (from `Category.label()` = `id.toLanguageKey("key.category")`) | `key.category.minecraft.movement` (legacy `key.categories.*` still present) |
| Creative tab | whatever `CreativeModeTab.builder().title(Component.translatable(...))` gets, convention `itemGroup.awaken[.<tab>]` | `itemGroup.combat` |
| Sound subtitle | the `subtitle` in sounds.json, convention `subtitles.awaken.<event>` | `subtitles.entity.warden.sonic_boom` |
| Mob effect | `effect.awaken.<path>` | `effect.minecraft.speed` |
| Biome | `biome.awaken.<path>` | `biome.minecraft.plains` |
| Death message | `death.attack.<msgId>` (+ `.player`) | `death.attack.sonic_boom` |
| Screens / HUD | free-form, convention `screen.awaken.<name>`, `hud.awaken.<name>` | — |

Verified: `assets/lang/en_us.json`, `src/net/minecraft/client/KeyMapping.java` (`Category.label`), `src/net/minecraft/resources/Identifier.java`
(`toLanguageKey(prefix)` = `prefix + "." + ns + "." + path`), `src/net/minecraft/world/item/CreativeModeTab.java` (`Builder.title(Component)`).
