# Entities, AI & Combat: API cheat sheet (MC 26.3 / NeoForge 26.3.0.95)

Every signature below was checked against the decompiled sources. Path prefixes:
`src/` = `/Volumes/MEDIA/Awaken/.ref/src` (MC 26.3 with NeoForge patches), `neo/` = `/Volumes/MEDIA/Awaken/.ref/neo`,
`data/` = `/Volumes/MEDIA/Awaken/.ref/assets/data/minecraft`. `MODID` = our mod id. Nullability annotations are `org.jspecify.annotations.Nullable`.

## 0. Breaking changes since 1.21.x (read first)

| 1.21.x | 26.3 | Verified in |
|---|---|---|
| `ResourceLocation` | `net.minecraft.resources.Identifier` (`fromNamespaceAndPath`, `withDefaultNamespace`, `parse`) | `src/net/minecraft/resources/Identifier.java` |
| `EntityType.ZOMBIE` | `net.minecraft.world.entity.EntityTypes.ZOMBIE`; keys in `EntityTypeIds.ZOMBIE` (no constants left in `EntityType`) | `src/.../world/entity/EntityTypes.java` |
| `MobSpawnType` | `EntitySpawnReason` (`NATURAL, SPAWNER, MOB_SUMMONED, TRIGGERED, COMMAND, SPAWN_ITEM_USE, CONVERSION, …`) | `src/.../entity/EntitySpawnReason.java` |
| `boolean hurt(src, amt)` | `boolean hurtServer(ServerLevel, DamageSource, float)` (abstract on `Entity`). `hurt` is now `@Deprecated public final void` | `src/.../entity/Entity.java:1968,1980` |
| `entity.hurtMarked = true` | **removed.** `needsSync` (send to trackers) / `syncVelocity` (send to trackers **and self**) | `Entity.java:282-284`, `server/level/ServerEntity.java:130-210` |
| `invulnerableTime` (i-frames) | `LivingEntity.damageCooldownTime` (public int). `Entity.invulnerableTime` still exists but now means *temporary full invulnerability* | `LivingEntity.java:242,1272`, `Entity.java:3150` |
| `setInvulnerable(boolean)` | `setPermanentlyInvulnerable(boolean)` / `isPermanentlyInvulnerable()` | `Entity.java:3158` |
| NBT `CompoundTag` save/load | `addAdditionalSaveData(ValueOutput)` / `readAdditionalSaveData(ValueInput)` | `Entity.java:2288-2290` |
| `moveTo(x,y,z,yRot,xRot)` | `snapTo(...)` (same overloads + `snapTo(BlockPos,float,float)`) | `Entity.java:1831-1847` |
| `restrictTo(pos, r)` / `getRestrictCenter()` / `isWithinRestriction()` | `setHomeTo(BlockPos,int)` / `getHomePosition()` / `isWithinHome()` / `hasHome()` / `clearHome()` | `Mob.java:1231-1262` |
| override `isAlliedTo(Entity)` | `isAlliedTo(Entity)` is **final**; override `protected boolean considersEntityAsAlly(Entity)` | `Entity.java:2879-2885` |
| `interact(Player, Hand)` | `interact(Player, InteractionHand, Vec3 location)`; mobs still override `mobInteract(Player, InteractionHand)` | `Entity.java:2339`, `Mob.java:1215` |
| `customServerAiStep()` | `customServerAiStep(ServerLevel)` | `Mob.java:784` |
| `kill()` | `kill(ServerLevel)` | `Entity.java:419` |
| `level.isClientSide` field | `level.isClientSide()` method (field private) | `world/level/Level.java:169` |
| `Explosion level.explode(...)` | returns **void** | `Level.java:556-660` |
| `new ServerBossEvent(name, color, overlay)` | `new ServerBossEvent(UUID, Component, BossBarColor, BossBarOverlay)` | `server/level/ServerBossEvent.java:20` |
| `InteractionResultHolder<ItemStack>` | gone; `Item#use` returns `InteractionResult` | `world/item/Item.java:226` |
| `displayClientMessage(c, overlay)` | `sendSystemMessage(Component)` / `sendOverlayMessage(Component)` | `world/entity/player/Player.java:1411-1414` |
| `DeferredSpawnEggItem` / egg colours | gone; `SpawnEggItem(Properties)` + `Properties#spawnEgg(type)`; per-egg texture | `world/item/SpawnEggItem.java:37`, `Item.java:605` |
| `GameRules.RULE_MOBGRIEFING` | `net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING`; `level.getGameRules().get(rule)` | `world/level/gamerules/GameRules.java:61,124` |
| `CompoundTag#getInt` → int | `getInt` → `Optional<Integer>`; use `getIntOr(key, def)` | `nbt/CompoundTag.java:317-321` |
| biome `spawners` JSON | spawns are an environment attribute `minecraft:gameplay/natural_mob_spawns`; mods use `neoforge:add_spawns` | `data/worldgen/biome/forest.json` |
| `Mob#getTarget()` raw | validated (drops creative/spectator/un-attackable); raw = `getTargetUnchecked()` | `Mob.java:237-251` |
| goals registered on both sides | `registerGoals()` runs **only when `level instanceof ServerLevel`** | `Mob.java:164` |

Event subscription: `@EventBusSubscriber(modid = MODID)` (`net.neoforged.fml.common.EventBusSubscriber`) with `@SubscribeEvent` (`net.neoforged.bus.api.SubscribeEvent`) routes `IModBusEvent`s to the mod bus and the rest to `NeoForge.EVENT_BUS` automatically. There's no `bus =` argument. Verified: `neo/.../network/ConfigurationInitialization.java:31,46`.

---

## 1. Registering an EntityType, attributes, spawn placement, spawn egg

**Builder** (`net.minecraft.world.entity.EntityType.Builder`, `src/.../entity/EntityType.java:512-708`):
`static <T extends Entity> Builder<T> of(EntityType.EntityFactory<T> factory, MobCategory category)`, `sized(float w, float h)`, `eyeHeight(float)`,
`clientTrackingRange(int chunks)`, `updateInterval(int)`, `fireImmune()`, `notInPeaceful()`, `noSummon()`, `noSave()`, `noLootTable()`,
`canSpawnFarFromPlayer()`, `immuneTo(TagKey<Block>)`, `passengerAttachments(float...)`, `ridingOffset(float)`, `nameTagOffset(float)`,
`EntityType<T> build(ResourceKey<EntityType<?>> name)`. Factory: `@Nullable T create(EntityType<T> type, Level level)`.

**NeoForge helper** (`neo/.../registries/DeferredRegister.java:185,813-845`): `DeferredRegister.createEntities(MODID)` returns `DeferredRegister.Entities` with
`registerEntityType(String name, EntityFactory<E>, MobCategory, UnaryOperator<EntityType.Builder<E>>)`. It builds the `ResourceKey` for you.

```java
public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Ghoul>> GHOUL = ENTITIES.registerEntityType(
        "ghoul", Ghoul::new, MobCategory.MONSTER,
        b -> b.sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).notInPeaceful());

    public static final DeferredHolder<EntityType<?>, EntityType<ShadowBolt>> SHADOW_BOLT = ENTITIES.registerEntityType(
        "shadow_bolt", ShadowBolt::new, MobCategory.MISC,
        b -> b.sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).noLootTable());
}
// Without the helper (vanilla style):
// EntityType.Builder.of(Ghoul::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
//     .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "ghoul")));
// In the mod constructor: ModEntities.ENTITIES.register(modBus);
```

**Attributes** (`neo/.../event/entity/EntityAttributeCreationEvent.java:30`): `void put(EntityType<? extends LivingEntity>, AttributeSupplier)`. It's a mod-bus event. Without it `DefaultAttributes.getSupplier` returns null and the mob NPEs when it's created (`ai/attributes/DefaultAttributes.java:192`). To add attributes to existing types (e.g. players), use `EntityAttributeModificationEvent#add(EntityType<? extends LivingEntity>, Holder<Attribute>[, double])`.

**Spawn placement** (`neo/.../event/entity/RegisterSpawnPlacementsEvent.java:53-70`, mod bus):
`register(EntityType<T>, @Nullable SpawnPlacementType, Heightmap.@Nullable Types, SpawnPlacements.SpawnPredicate<T>, Operation)`. A *new* type needs non-null placement and heightmap.
`SpawnPredicate#test(EntityType<T>, ServerLevelAccessor, EntitySpawnReason, BlockPos, RandomSource)` (`src/.../entity/SpawnPlacements.java:208`).

```java
@EventBusSubscriber(modid = MODID)
public final class ModEntityEvents {
    @SubscribeEvent
    static void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.GHOUL.get(), Ghoul.createAttributes().build());
    }
    @SubscribeEvent
    static void placements(RegisterSpawnPlacementsEvent e) {
        e.register(ModEntities.GHOUL.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
```
Classes: `net.minecraft.world.entity.SpawnPlacementTypes` (`ON_GROUND, IN_WATER, IN_LAVA, NO_RESTRICTIONS`) and `net.minecraft.world.level.levelgen.Heightmap`.
`Monster.checkMonsterSpawnRules / checkAnyLightMonsterSpawnRules / checkSurfaceMonstersSpawnRules` (`src/.../entity/monster/Monster.java:99-118`).

**Natural spawns**: add `data/<ns>/neoforge/biome_modifier/ghoul.json`. `SpawnerData` is now `(EntityType type, IntProvider count)` and the weight sits on the `Weighted` wrapper (`src/.../level/biome/MobSpawnSettings.java:157`, `neo/.../common/world/BiomeModifiers.java:148`):
```json
{ "type": "neoforge:add_spawns", "biomes": "#minecraft:is_overworld",
  "spawners": [ { "type": "MODID:ghoul", "count": 2, "weight": 40 } ] }
```
`count` is an IntProvider (int or `{"type":"minecraft:uniform","min_inclusive":1,"max_inclusive":3}`). A `MobCategory.MISC` type is silently replaced with a pig.

**Spawn egg** (`src/.../world/item/Item.java:605`, `Items.java:2958`): `Item.Properties#spawnEgg(EntityType<?>)` stores `DataComponents.ENTITY_DATA = TypedEntityData.of(type, new CompoundTag())`.
NeoForge registers `ENTITY_TYPE` before `ITEM` (`neo/.../registries/GameData.java:127-134`), so `.get()` inside the item supplier is safe:
```java
public static final DeferredItem<SpawnEggItem> GHOUL_EGG = ITEMS.registerItem("ghoul_spawn_egg",
    SpawnEggItem::new, p -> p.spawnEgg(ModEntities.GHOUL.get()));
```
Assets: `assets/<ns>/items/ghoul_spawn_egg.json` (`{"model":{"type":"minecraft:model","model":"<ns>:item/ghoul_spawn_egg"}}`),
`models/item/ghoul_spawn_egg.json` (`parent: minecraft:item/generated`, `layer0`), and `textures/item/ghoul_spawn_egg.png`. There are no tint colours any more.

**Renderer** (client, mod bus, `neo/.../client/event/EntityRenderersEvent.java:103`): `registerEntityRenderer(EntityType<? extends T>, EntityRendererProvider<T>)`.
E.g. `e.registerEntityRenderer(ModEntities.GHOUL.get(), ZombieRenderer::new)` (a Zombie subclass) or `ThrownItemRenderer::new` for item projectiles.

**Spawning from code** (`EntityType.java:152-252,343-347`):
```java
Ghoul g = ModEntities.GHOUL.get().spawn(serverLevel, pos, EntitySpawnReason.MOB_SUMMONED); // create + finalizeSpawn + addFreshEntityWithPassengers
// manual:
Ghoul g2 = ModEntities.GHOUL.get().create(serverLevel, EntitySpawnReason.MOB_SUMMONED); // null in Peaceful if notInPeaceful()
if (g2 != null) {
    g2.snapTo(pos, yRot, 0F);
    EventHooks.finalizeMobSpawn(g2, serverLevel, serverLevel.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
    serverLevel.addFreshEntity(g2);
}
// skip the peaceful/feature checks: type.create(level, new EntitySpawnRequest(reason, true))
```
`EventHooks` = `net.neoforged.neoforge.event.EventHooks` (`neo/.../event/EventHooks.java:336`). `Mob#finalizeSpawn` is `@OverrideOnly`, so external code calls the hook instead.

---

## 2. Custom hostile mob (Monster / Zombie / Skeleton / Spider)

Constructors (all `(EntityType<? extends X> type, Level level)`):
`protected Monster(...)` (`monster/Monster.java:31`), `public Zombie(...)` (`monster/zombie/Zombie.java:115`),
`public Skeleton(...)` (`monster/skeleton/Skeleton.java:23`; `AbstractSkeleton` needs `protected abstract SoundEvent getStepSound()`),
`public Spider(...)` (`monster/spider/Spider.java:52`). Packages: `net.minecraft.world.entity.monster.{zombie,skeleton,spider}`.

Override points (exact):
- `protected void registerGoals()` (`Mob.java:169`)
- `public static AttributeSupplier.Builder createAttributes()` is a convention. The base is `Monster.createMonsterAttributes()` = `Mob.createMobAttributes().add(ATTACK_DAMAGE)` (FOLLOW_RANGE 16 included)
- `protected void defineSynchedData(SynchedEntityData.Builder entityData)`. Call `super`, then `entityData.define(ACCESSOR, default)` (`network/syncher/SynchedEntityData.java:148`)
- `public void tick()`, `public void aiStep()`, `protected void customServerAiStep(ServerLevel level)` (server AI tick, after goals)
- `protected void addAdditionalSaveData(ValueOutput output)` / `protected void readAdditionalSaveData(ValueInput input)`
- `public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData)` (`Mob.java:1117`)
- sounds: `protected @Nullable SoundEvent getAmbientSound()`, `protected @Nullable SoundEvent getHurtSound(DamageSource)`, `protected @Nullable SoundEvent getDeathSound()`, `protected void playStepSound(BlockPos, BlockState)`, `public int getAmbientSoundInterval()`
- `public boolean doHurtTarget(ServerLevel level, Entity target)` (melee hit; uses ATTACK_DAMAGE + weapon) (`Mob.java:1416`)
- client sync callback: `public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)` (`Entity.java:3505`)

`ValueOutput` (`world/level/storage/ValueOutput.java`): `putInt/putLong/putFloat/putDouble/putBoolean/putString/putByte/putShort/putIntArray(String, v)`,
`<T> store(String, Codec<T>, T)`, `storeNullable(...)`, `child(String)`, `list(String, Codec<T>)`, `discard(String)`.
`ValueInput`: `getIntOr/getLongOr/getFloatOr/getDoubleOr/getBooleanOr/getStringOr(String, def)`, `getInt(String)→Optional<Integer>`, `getString→Optional<String>`,
`<T> Optional<T> read(String, Codec<T>)`, `child(String)→Optional<ValueInput>`, `childOrEmpty`, `listOrEmpty(String, Codec<T>)`, `lookup()`.
UUIDs: `store("x", UUIDUtil.CODEC, uuid)`. Entity links: `EntityReference.store(ref, output, "Owner")` / `EntityReference.read(input, "Owner")`.
NeoForge free-form NBT is still available: `entity.getPersistentData()` (saved under `NeoForgeData`).

`EntityDataSerializers` (`network/syncher/EntityDataSerializers.java`): `BYTE, INT, LONG, FLOAT, STRING, COMPONENT, OPTIONAL_COMPONENT, ITEM_STACK, BLOCK_STATE, BOOLEAN, PARTICLE, BLOCK_POS, OPTIONAL_BLOCK_POS, DIRECTION, OPTIONAL_LIVING_ENTITY_REFERENCE, OPTIONAL_GLOBAL_POS, POSE, VECTOR3, QUATERNION, RESOLVABLE_PROFILE, …`.

```java
public class Ghoul extends Zombie {
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED =
        SynchedEntityData.defineId(Ghoul.class, EntityDataSerializers.BOOLEAN);
    private int hunger; // server-only, persisted

    public Ghoul(EntityType<? extends Ghoul> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    @Override protected void registerGoals() {           // replaces Zombie's goals entirely
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ENRAGED, false);
    }
    public boolean isEnraged() { return this.entityData.get(DATA_ENRAGED); }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!isEnraged() && getHealth() < getMaxHealth() * 0.3F) this.entityData.set(DATA_ENRAGED, true);
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("hunger", hunger);
        out.putBoolean("enraged", isEnraged());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        hunger = in.getIntOr("hunger", 0);
        this.entityData.set(DATA_ENRAGED, in.getBooleanOr("enraged", false));
    }

    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance diff,
                                                            EntitySpawnReason reason, @Nullable SpawnGroupData data) {
        data = super.finalizeSpawn(level, diff, reason, data);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return data;
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ZOMBIE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ZOMBIE_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_DEATH; }
}
```
Gotchas: `registerGoals()` runs inside the `Mob` constructor, before subclass field initialisers, so don't touch your own fields there. Zombie's `finalizeSpawn` also rolls baby, chicken jockey and armour; extend `Monster` if you don't want that.
Custom sounds: `SoundEvent.createVariableRangeEvent(Identifier)` registered to `Registries.SOUND_EVENT`.

---

## 3. Goals (`net.minecraft.world.entity.ai.goal`, targets in `.goal.target`)

| Goal | Constructor(s) (verified) |
|---|---|
| `MeleeAttackGoal` | `(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen)` |
| `RangedAttackGoal` | `(RangedAttackMob mob, double speed, int attackInterval, float attackRadius)`; `(…, int intervalMin, int intervalMax, float attackRadius)` |
| `RangedBowAttackGoal<T extends Mob & RangedAttackMob>` | `(T mob, double speed, int attackIntervalMin, float attackRadius)` |
| `NearestAttackableTargetGoal<T extends LivingEntity>` | `(Mob, Class<T>, boolean mustSee)`; `(Mob, Class<T>, boolean mustSee, TargetingConditions.Selector)`; `(Mob, Class<T>, boolean mustSee, boolean mustReach)`; `(Mob, Class<T>, int randomInterval, boolean mustSee, boolean mustReach, @Nullable Selector)` |
| `HurtByTargetGoal` | `(PathfinderMob, Class<?>... ignoreDamageFrom)` then `.setAlertOthers(Class<?>...)` |
| `LookAtPlayerGoal` | `(Mob, Class<? extends LivingEntity>, float dist)`; `(…, float probability)`; `(…, float probability, boolean onlyHorizontal)` |
| `RandomStrollGoal` | `(PathfinderMob, double speed)`; `(…, int interval)`; `(…, int interval, boolean checkNoActionTime)` |
| `WaterAvoidingRandomStrollGoal` | `(PathfinderMob, double speed)`; `(…, float probability)` |
| `FloatGoal` / `RandomLookAroundGoal` | `(Mob)` |
| `MoveTowardsRestrictionGoal` | `(PathfinderMob, double speed)` (walks back to `getHomePosition()`) |
| `LeapAtTargetGoal` | `(Mob, float yd)` |
| `AvoidEntityGoal<T>` | `(PathfinderMob, Class<T>, float maxDist, double walkSpeed, double sprintSpeed)` |
| `FollowOwnerGoal` | `(TamableAnimal, double speed, float startDistance, float stopDistance)`. **TamableAnimal only** |
| `OwnerHurtByTargetGoal` / `OwnerHurtTargetGoal` | `(TamableAnimal)` |

`TargetingConditions.Selector`: `boolean test(LivingEntity target, ServerLevel level)` (`ai/targeting/TargetingConditions.java:99`).
`RangedAttackMob`: `void performRangedAttack(LivingEntity target, float power)`.

**Custom goal**. Base `Goal` (`ai/goal/Goal.java`): `abstract boolean canUse()`, `canContinueToUse()`, `isInterruptable()`, `start()`, `stop()`, `tick()`,
`requiresUpdateEveryTick()` (default false means tick runs every 2nd tick, so use `adjustedTickDelay(int)`), `setFlags(EnumSet<Goal.Flag>)`; flags are `MOVE, LOOK, JUMP, TARGET`.
Helpers: `protected static int reducedTickDelay(int)`, `protected static ServerLevel getServerLevel(Entity)`.
```java
public class DashAtTargetGoal extends Goal {
    private final PathfinderMob mob; private int cooldown;
    public DashAtTargetGoal(PathfinderMob mob) { this.mob = mob; setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)); }
    @Override public boolean canUse() {
        LivingEntity t = mob.getTarget();
        return --cooldown <= 0 && t != null && mob.distanceToSqr(t) < 64 && mob.getSensing().hasLineOfSight(t);
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        LivingEntity t = mob.getTarget();
        mob.getLookControl().setLookAt(t, 30F, 30F);
        Vec3 d = t.position().subtract(mob.position()).normalize().scale(1.2);
        mob.setDeltaMovement(d.x, 0.3, d.z);
        cooldown = adjustedTickDelay(60);
    }
    @Override public boolean canContinueToUse() { return false; } // one-shot
}
```
Navigation: `mob.getNavigation().moveTo(Entity, double speed)` / `moveTo(x,y,z,speed)` / `stop()` / `isDone()`; look: `getLookControl().setLookAt(Entity, float yMax, float xMax)`.

---

## 4. Boss: boss bar, phases, telegraphs, knockback immunity, no despawn

`net.minecraft.server.level.ServerBossEvent` (`server/level/ServerBossEvent.java`):
`ServerBossEvent(UUID id, Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay)`, `setProgress(float 0..1)`, `setName(Component)`,
`setColor`, `setOverlay`, `setDarkenScreen(boolean)`, `setPlayBossMusic(boolean)`, `setCreateWorldFog(boolean)` (these three return `BossEvent`, so don't chain them as `ServerBossEvent`),
`addPlayer(ServerPlayer)`, `removePlayer(ServerPlayer)`, `removeAllPlayers()`, `setVisible(boolean)`, `getPlayers()`.
Colours `PINK BLUE RED GREEN YELLOW PURPLE WHITE`; overlays `PROGRESS NOTCHED_6 NOTCHED_10 NOTCHED_12 NOTCHED_20` (`world/BossEvent.java`).
Hook into tracking: `public void startSeenByPlayer(ServerPlayer)` / `stopSeenByPlayer(ServerPlayer)` (`Entity.java:3618`). Pattern copied from `boss/wither/WitherBoss.java:75-382`.

Despawn: `Mob#checkDespawn()` (`Mob.java:711`) skips mobs with `isPersistenceRequired() || requiresCustomPersistence()`; `removeWhenFarAway(double distSqr)` is consulted otherwise.
Peaceful still discards if the type was built with `notInPeaceful()`.
Knockback: `LivingEntity#knockback` multiplies by `1 - KNOCKBACK_RESISTANCE` (`LivingEntity.java:1714`); explosions use `EXPLOSION_KNOCKBACK_RESISTANCE`.

```java
public class ShadowMonarch extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ShadowMonarch.class, EntityDataSerializers.INT);
    private static final byte EVENT_SLAM_WINDUP = (byte) 100; // vanilla EntityEvent ids end at 72
    public final AnimationState slamAnim = new AnimationState(); // client
    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
        this.getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int slamTicks = -1; private Vec3 slamPos = Vec3.ZERO;

    public ShadowMonarch(EntityType<? extends ShadowMonarch> t, Level l) {
        super(t, l); this.setPersistenceRequired(); this.xpReward = 200; bossEvent.setDarkenScreen(true);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 600).add(Attributes.ATTACK_DAMAGE, 14)
            .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ARMOR, 12)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(DATA_PHASE, 1); }

    @Override protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        bossEvent.setProgress(getHealth() / getMaxHealth());
        int phase = getHealth() < getMaxHealth() * 0.5F ? 2 : 1;
        if (phase != entityData.get(DATA_PHASE)) { entityData.set(DATA_PHASE, phase); bossEvent.setColor(BossEvent.BossBarColor.RED); }
        LivingEntity target = getTarget();
        if (slamTicks < 0 && target != null && tickCount % (phase == 2 ? 60 : 100) == 0) {
            slamTicks = 30; slamPos = target.position();
            level.broadcastEntityEvent(this, EVENT_SLAM_WINDUP);              // -> handleEntityEvent on clients
        }
        if (slamTicks > 0) {                                                  // telegraph ring
            slamTicks--;
            level.sendParticles(ParticleTypes.CRIT, slamPos.x, slamPos.y + 0.1, slamPos.z, 12, 1.5, 0.0, 1.5, 0.0);
        } else if (slamTicks == 0) {
            slamTicks = -1;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(slamPos, 6, 3, 6), x -> x != this))
                e.hurtServer(level, damageSources().mobAttack(this), 16F);
        }
    }
    @Override public void handleEntityEvent(byte id) {
        if (id == EVENT_SLAM_WINDUP) slamAnim.start(this.tickCount); else super.handleEntityEvent(id);
    }
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bossEvent.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p)  { super.stopSeenByPlayer(p);  bossEvent.removePlayer(p); }
    @Override public void setCustomName(@Nullable Component n) { super.setCustomName(n); bossEvent.setName(getDisplayName()); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canUsePortal(boolean ignorePassenger) { return false; }
    @Override public boolean canBeAffected(MobEffectInstance e) { return !e.is(MobEffects.WITHER) && super.canBeAffected(e); }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in); if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
```
`ServerLevel#sendParticles(T particle, double x, y, z, int count, double xDist, yDist, zDist, double speed)` (`server/level/ServerLevel.java:1355`; overloads with `overrideLimiter, alwaysShow` at 1401). `AnimationState`: `start(int tickCount)`, `stop()`, `animateWhen(boolean, int)`.
Build the type with `.fireImmune().clientTrackingRange(10)`. Persistence is saved by `Mob` as `PersistenceRequired`.

---

## 5. Damage, damage sources, knockback, explosions, i-frames

- Deal damage: `boolean target.hurtServer(ServerLevel level, DamageSource src, float amount)`. Override the same method to intercept (call `super`). Client-side `hurtClient(DamageSource)` only predicts.
- `LivingEntity#hurtServer` order (`LivingEntity.java:1223-1350`): `isInvulnerableTo` → dead check → fire-resistance → **`LivingIncomingDamageEvent`** → blocking → i-frame check (`damageCooldownTime > 10 && !BYPASSES_COOLDOWN`, only the excess over `lastHurt` applies) → `actuallyHurt` → `markHurt()` (sets `syncVelocity`) → `dealDefaultKnockback` → death or hurt sound → `LivingDamageEvent.Post`.
- **I-frames**: `livingEntity.damageCooldownTime = 0;` before `hurtServer` gives rapid multi-hits; tag your damage type `#minecraft:bypasses_cooldown`; or `LivingIncomingDamageEvent#setInvulnerabilityTicks(int)`. Do **not** use `setInvulnerableTime` for this: it makes the entity invulnerable (`Entity.java:3150`).
- `isInvulnerableTo(ServerLevel, DamageSource)` (`LivingEntity.java:4214`).

`DamageSources` (`world/damagesource/DamageSources.java`), via `level.damageSources()` or `entity.damageSources()`:
`mobAttack(LivingEntity)`, `noAggroMobAttack(LivingEntity)`, `playerAttack(Player)`, `magic()`, `indirectMagic(Entity direct, @Nullable Entity owner)`,
`mobProjectile(Entity proj, @Nullable LivingEntity owner)`, `thrown(Entity, @Nullable Entity)`, `explosion(@Nullable Entity direct, @Nullable Entity causer)`, `explosion(@Nullable Explosion)`,
`sonicBoom(Entity)`, `wither()`, `generic()`, `genericKill()`, `fellOutOfWorld()`, `source(ResourceKey<DamageType>[, @Nullable Entity cause])`, `source(key, @Nullable Entity direct, @Nullable Entity causer)`.
`DamageSource` ctors: `(Holder<DamageType>, @Nullable Entity direct, @Nullable Entity causing[, @Nullable Vec3 pos])`, `(Holder, Vec3)`, `(Holder, @Nullable Entity)`, `(Holder)`.
Who did it: `getEntity()` (causer / owner), `getDirectEntity()` (arrow, bolt). Type checks: `is(TagKey<DamageType>)` / `is(ResourceKey<DamageType>)`.

**Custom damage type**: `data/<ns>/damage_type/shadow_strike.json` (`DamageType.java:12`, fields `message_id`, `scaling` (`never|when_caused_by_living_non_player|always`), `exhaustion`, optional `effects` (`hurt|thorns|drowning|burning|poking|freezing`) and `death_message_type` (`default|fall_variants|intentional_game_design`)):
```json
{ "message_id": "MODID.shadow_strike", "scaling": "never", "exhaustion": 0.0 }
```
Lang: `death.attack.MODID.shadow_strike` and `death.attack.MODID.shadow_strike.player` (`DamageSource.java:70`). Tags live in `data/minecraft/tags/damage_type/{bypasses_armor,bypasses_cooldown,no_knockback,is_projectile,…}.json`.
```java
public static final ResourceKey<DamageType> SHADOW_STRIKE =
    ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "shadow_strike"));
DamageSource src = level.damageSources().source(SHADOW_STRIKE, attacker);              // short form
Holder<DamageType> h = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SHADOW_STRIKE);
DamageSource src2 = new DamageSource(h, projectile, attacker);                         // long form
target.hurtServer(serverLevel, src2, 10F);
```

**Knockback** (`LivingEntity.java:1714,1740`): **no 3-arg overload any more.**
`knockback(double power, double xd, double zd, DamageSource source, float damage)` and `(…, boolean comesFromEffect)`. The victim is pushed *opposite* to `(xd, zd)`: pass `attacker - victim` to push it away from the attacker. Power is scaled by `1 - KNOCKBACK_RESISTANCE`, and `y` becomes `min(0.4, y/2 + power)` only when on the ground. It fires `LivingKnockBackEvent` and only sets `needsSync`, so set `syncVelocity = true` for players (see §7).
```java
victim.knockback(1.5, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ(), src, 0F);
victim.syncVelocity = true; // vanilla Ravager does this (monster/Ravager.java:220)
```
Raw impulse: `entity.push(double x, double y, double z)` (sets `needsSync`), `addDeltaMovement(Vec3)`, `setDeltaMovement(Vec3)`.

**Explosions** (`Level.java:556-660`, all return `void`):
`explode(@Nullable Entity source, double x, y, z, float radius, Level.ExplosionInteraction)`, `explode(…, float radius, boolean fire, ExplosionInteraction)`,
`explode(@Nullable Entity, @Nullable DamageSource, @Nullable ExplosionDamageCalculator, double x, y, z, float r, boolean fire, ExplosionInteraction)` (+ `Vec3` variant, + full variant with particles, block particles and `Holder<SoundEvent>`).
`Level.ExplosionInteraction`: `NONE, BLOCK, MOB (respects mob_griefing), TNT, TRIGGER`.
```java
serverLevel.explode(this, damageSources().explosion(this, getOwner()), null, getX(), getY(), getZ(), 3.0F, false, Level.ExplosionInteraction.NONE);
```
Per-entity: `boolean ignoreExplosion(Explosion)` (`Entity.java:3614`).

---

## 6. Projectiles and raycast beams

`net.minecraft.world.entity.projectile.Projectile` (`projectile/Projectile.java`): `protected Projectile(EntityType<? extends Projectile>, Level)`, `setOwner(@Nullable Entity)`, `@Nullable Entity getOwner()`,
`shoot(double xd, double yd, double zd, float pow, float uncertainty)`, `shootFromRotation(Entity source, float xRot, float yRot, float yOffset, float pow, float uncertainty)`,
`protected void onHit(HitResult)`, `protected void onHitEntity(EntityHitResult)`, `protected void onHitBlock(BlockHitResult)`, `protected boolean canHitEntity(Entity)`.
Static spawners: `spawnProjectile(T proj, ServerLevel, ItemStack, Consumer<T> shoot)`, `spawnProjectileFromRotation(ProjectileFactory<T>, ServerLevel, ItemStack, LivingEntity, float yOffset, float pow, float uncertainty)`,
`spawnProjectileUsingShoot(T proj, ServerLevel, ItemStack, double tx, ty, tz, float pow, float uncertainty)`; `ProjectileFactory#create(ServerLevel, LivingEntity, ItemStack)`.
`ThrowableProjectile` (`projectile/ThrowableProjectile.java`): ctors `(type, level)` and `(type, double x, y, z, level)`; gravity is `protected double getDefaultGravity()` (0.03).
`ThrowableItemProjectile` lives in `projectile.throwableitemprojectile` and needs `protected abstract Item getDefaultItem()`. Fireball-like (no gravity, acceleration): `projectile.hurtingprojectile.AbstractHurtingProjectile`.
`Projectile` has no `defineSynchedData`, so **you must implement it** (an empty body is fine).

```java
public class ShadowBolt extends ThrowableProjectile {
    public ShadowBolt(EntityType<? extends ShadowBolt> t, Level l) { super(t, l); }
    public ShadowBolt(Level l, LivingEntity owner) {
        super(ModEntities.SHADOW_BOLT.get(), owner.getX(), owner.getEyeY() - 0.1, owner.getZ(), l);
        setOwner(owner);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected double getDefaultGravity() { return 0.0; }
    @Override protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel sl)
            hit.getEntity().hurtServer(sl, damageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), 6F);
    }
    @Override protected void onHit(HitResult r) { super.onHit(r); if (!level().isClientSide()) discard(); }
}
// fire from a player/mob on the server:
Projectile.spawnProjectileFromRotation((lvl, shooter, st) -> new ShadowBolt(lvl, shooter), serverLevel, ItemStack.EMPTY, player, 0F, 1.5F, 1.0F);
```

**Beam by raycast**: `new ClipContext(Vec3 from, Vec3 to, ClipContext.Block, ClipContext.Fluid, Entity)` (`world/level/ClipContext.java:28`); Block = `COLLIDER | OUTLINE | VISUAL | FALLDAMAGE_RESETTING`, Fluid = `NONE | SOURCE_ONLY | ANY | WATER`.
`BlockHitResult level.clip(ClipContext)` (`BlockGetter.java:67`). `ProjectileUtil.getEntityHitResult(Entity except, Vec3 from, Vec3 to, AABB box, Predicate<Entity>, double maxDistSqr)` (`projectile/ProjectileUtil.java:97`).
For piercing: `getManyEntityHitResult(Level, Entity source, Vec3 from, Vec3 to, AABB area, Predicate<Entity>, boolean includeFromEntity, boolean projectSurfaceHitLocation)` (`:166`). For a quick one: `getHitResultOnViewVector(Entity, Predicate<Entity>, double distance)` (`:56`).
```java
static @Nullable EntityHitResult beam(ServerPlayer p, double range) {
    Vec3 from = p.getEyePosition(), to = from.add(p.getViewVector(1.0F).scale(range));
    BlockHitResult wall = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
    if (wall.getType() != HitResult.Type.MISS) to = wall.getLocation();
    AABB box = p.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0);
    return ProjectileUtil.getEntityHitResult(p, from, to, box, e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator(), from.distanceToSqr(to));
}
```

---

## 7. Moving players from the server; flight; noclip; game mode

- Velocity: `setDeltaMovement(Vec3)` / `(x,y,z)`, `addDeltaMovement(Vec3)` (`Entity.java:3886-3898`). **`hurtMarked` is gone.** A player's own client is authoritative for its motion, so it only moves if the packet reaches **that player**:
  - `player.syncVelocity = true;` → `ServerEntity` sends `ClientboundSetEntityMotionPacket` to trackers **and self** at the next tracker tick (`ServerEntity.java:207-210`), or
  - immediately: `sp.connection.send(new ClientboundSetEntityMotionPacket(sp));` then `sp.syncVelocity = false;` (vanilla `item/enchantment/effects/ApplyEntityImpulse.java:34-37`).
  - `needsSync = true` reaches **trackers only**, so it doesn't work for the moved player.
- Packet: `record ClientboundSetEntityMotionPacket(int id, Vec3 movement)` + `ClientboundSetEntityMotionPacket(Entity)` (`network/protocol/game/ClientboundSetEntityMotionPacket.java`). It still exists; it's just a record now.
- Fall damage: `fallDistance` is a **double**; `resetFallDistance()` (`Entity.java:3023`; ServerPlayer override also clears the advancement start pos). For launches, `livingEntity.setIgnoreFallDamageFromCurrentImpulse(true, sp.position())` (wind-charge style, 40-tick grace) or `applyPostImpulseGraceTime(int)` (`LivingEntity.java:1888-1900`).
- Teleport: `teleportTo(ServerLevel, double x, y, z, Set<Relative>, float yRot, float xRot, boolean resetCamera)` (`ServerPlayer.java:1847`), or `teleport(TeleportTransition)`.
- **Flight**: `Abilities#mayfly` is `@Deprecated` by NeoForge and is reset by game-mode changes. Use the boolean attribute `NeoForgeMod.CREATIVE_FLIGHT` (`neo/.../common/NeoForgeMod.java:213`, read through `player.mayFly()`). `ServerPlayer` auto-disables `flying` when `!mayFly()` (`ServerPlayer.java:763`).
```java
static final Identifier SPIRIT_FLIGHT = Identifier.fromNamespaceAndPath(MODID, "spirit_flight");
AttributeInstance cf = sp.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
cf.addOrUpdateTransientModifier(new AttributeModifier(SPIRIT_FLIGHT, 1.0, AttributeModifier.Operation.ADD_VALUE));
sp.getAbilities().flying = true;  sp.onUpdateAbilities();          // sends ClientboundPlayerAbilitiesPacket
// end: cf.removeModifier(SPIRIT_FLIGHT); (flying is cleared next tick) and sp.getAbilities().setFlyingSpeed(0.05F) if you changed it
```
- **Noclip**: `Player#tick()` sets `this.noPhysics = this.isSpectator()` every tick (`Player.java:243`), so a manual `noPhysics` on players is overwritten. Real noclip needs spectator mode. For non-player entities, `entity.noPhysics = true` + `setNoGravity(true)` works.
- Game mode: `boolean ServerPlayer#setGameMode(GameType)` (`ServerPlayer.java:1893`; fires NeoForge `PlayerEvent.PlayerChangeGameModeEvent`, which may veto). `GameType gameMode()`. `GameType.SURVIVAL / CREATIVE / ADVENTURE / SPECTATOR`.
- Camera (spirit view of another entity): `ServerPlayer#setCamera(@Nullable Entity)` / `getCamera()` (`ServerPlayer.java:2063-2067`).

---

## 8. Effects and attributes

`MobEffectInstance` (`world/effect/MobEffectInstance.java:49-79`): `(Holder<MobEffect>)`, `(Holder, int duration)`, `(Holder, int duration, int amplifier)`,
`(…, boolean ambient, boolean visible)`, `(…, boolean showIcon)`, `(…, @Nullable MobEffectInstance hiddenEffect)`. Apply: `living.addEffect(inst)` / `addEffect(inst, @Nullable Entity source)`,
`hasEffect(Holder<MobEffect>)`, `getEffect(Holder)`, `removeEffect(Holder)`, `forceAddEffect(inst, source)`, `canBeAffected(MobEffectInstance)`.

Custom effect: the `MobEffect` ctor is `protected MobEffect(MobEffectCategory, int color)` (+ particle variants), so subclass it. Hooks (`MobEffect.java:93-116`):
`boolean applyEffectTick(ServerLevel, LivingEntity, int amplifier)` (return false to remove), `boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier)` (default false, so nothing ticks unless you override),
`onEffectStarted(LivingEntity, int)`, `onEffectAdded(LivingEntity, int)`, `onMobHurt(ServerLevel, LivingEntity, int, DamageSource, float)`, `onMobRemoved(ServerLevel, LivingEntity, int, Entity.RemovalReason)`;
`addAttributeModifier(Holder<Attribute>, Identifier id, double amount, AttributeModifier.Operation)` (amount scales with amplifier + 1).
```java
public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
public static final DeferredHolder<MobEffect, MobEffect> SHADOW_FORM = EFFECTS.register("shadow_form", () ->
    new ShadowFormEffect(MobEffectCategory.BENEFICIAL, 0x2B1B47)
        .addAttributeModifier(Attributes.MOVEMENT_SPEED, Identifier.fromNamespaceAndPath(MODID, "effect.shadow_form"), 0.2,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
class ShadowFormEffect extends MobEffect {
    ShadowFormEffect(MobEffectCategory c, int col) { super(c, col); }
    @Override public boolean shouldApplyEffectTickThisTick(int t, int amp) { return t % 20 == 0; }
    @Override public boolean applyEffectTick(ServerLevel l, LivingEntity e, int amp) { e.heal(1F + amp); return true; }
}
player.addEffect(new MobEffectInstance(SHADOW_FORM, 200, 0)); // DeferredHolder IS a Holder<MobEffect>
```
Icon: `assets/<ns>/textures/mob_effect/shadow_form.png`; lang `effect.<ns>.shadow_form`.

`net.minecraft.world.entity.ai.attributes.Attributes` (all `Holder<Attribute>`, no `GENERIC_`/`PLAYER_` prefixes): `MAX_HEALTH, MAX_ABSORPTION, MOVEMENT_SPEED, FLYING_SPEED, ATTACK_DAMAGE, ATTACK_SPEED, ATTACK_KNOCKBACK, ARMOR, ARMOR_TOUGHNESS,
KNOCKBACK_RESISTANCE, EXPLOSION_KNOCKBACK_RESISTANCE, SCALE, GRAVITY, STEP_HEIGHT, SAFE_FALL_DISTANCE, FALL_DAMAGE_MULTIPLIER, JUMP_STRENGTH, BLOCK_INTERACTION_RANGE, ENTITY_INTERACTION_RANGE,
BLOCK_BREAK_SPEED, MINING_EFFICIENCY, SUBMERGED_MINING_SPEED, SNEAKING_SPEED, SWEEPING_DAMAGE_RATIO, FOLLOW_RANGE, SPAWN_REINFORCEMENTS_CHANCE, TEMPT_RANGE, OXYGEN_BONUS, WATER_MOVEMENT_EFFICIENCY,
MOVEMENT_EFFICIENCY, BURNING_TIME, LUCK, CAMERA_DISTANCE, BOUNCINESS, AIR_DRAG_MODIFIER, FRICTION_MODIFIER, NAME_TAG_DISTANCE, BELOW_NAME_DISTANCE, WAYPOINT_TRANSMIT_RANGE, WAYPOINT_RECEIVE_RANGE`.
NeoForge extras in `NeoForgeMod`: `CREATIVE_FLIGHT, GLIDING_FLIGHT, SWIM_SPEED, …`. Custom: `new RangedAttribute(String descId, double def, double min, double max).setSyncable(true)` registered to `Registries.ATTRIBUTE`.

`record AttributeModifier(Identifier id, double amount, AttributeModifier.Operation operation)`; ops `ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL` (`ai/attributes/AttributeModifier.java:14,38`).
`AttributeInstance` (`AttributeInstance.java`): `addTransientModifier(m)` (not saved; throws if the id is present), `addOrUpdateTransientModifier(m)` (preferred for toggles), `addPermanentModifier(m)` (saved with the entity),
`addOrReplacePermanentModifier(m)`, `boolean removeModifier(Identifier)`, `removeModifier(AttributeModifier)`, `hasModifier(Identifier)`, `getModifier(Identifier)`, `setBaseValue(double)`, `getValue()`.
Access: `living.getAttribute(Holder<Attribute>)` (**nullable** if the type lacks it), `getAttributeValue(Holder)`, `getAttributeBaseValue(Holder)`.
```java
AttributeInstance hp = sp.getAttribute(Attributes.MAX_HEALTH);
hp.addOrReplacePermanentModifier(new AttributeModifier(Identifier.fromNamespaceAndPath(MODID, "level_bonus"), 10.0, AttributeModifier.Operation.ADD_VALUE));
sp.getAttribute(Attributes.SCALE).addOrUpdateTransientModifier(new AttributeModifier(GIANT_ID, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
```
Players are re-created on respawn/dimension change, so re-apply transient modifiers in `PlayerEvent.PlayerRespawnEvent` / `Clone`.

---

## 9. Allies: summoned mobs that fight for the player

Vanilla ownership: `interface OwnableEntity { @Nullable EntityReference<LivingEntity> getOwnerReference(); Level level(); default @Nullable LivingEntity getOwner(); default getRootOwner(); }` (`entity/OwnableEntity.java`).
`EntityReference` (`entity/EntityReference.java`): `of(T)`, `of(UUID)`, `getUUID()`, `store(@Nullable ref, ValueOutput, key)`, `read(ValueInput, key)`, `getEntity(Level, Class)`.
`TamableAnimal` (extends `Animal`; must implement `isFood(ItemStack)` and `getBreedOffspring(ServerLevel, AgeableMob)`) gives `setOwner(LivingEntity)`, `tame(Player)`, `isOwnedBy`, `wantsToAttack(target, owner)`,
`canAttack` (never the owner), team/ally inheritance through `considersEntityAsAlly`, and works with `FollowOwnerGoal / OwnerHurtByTargetGoal / OwnerHurtTargetGoal / SitWhenOrderedToGoal` (`entity/TamableAnimal.java:158-225`).

For a humanoid summon (not an animal), use `PathfinderMob` + `OwnableEntity` + your own goals, since the vanilla owner goals require `TamableAnimal`:
```java
public class ShadowSoldier extends PathfinderMob implements OwnableEntity {
    private @Nullable EntityReference<LivingEntity> owner;
    public ShadowSoldier(EntityType<? extends ShadowSoldier> t, Level l) { super(t, l); }
    public static AttributeSupplier.Builder createAttributes() { // MeleeAttackGoal needs ATTACK_DAMAGE
        return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.3);
    }
    public void setOwner(LivingEntity o) { this.owner = EntityReference.of(o); }
    @Override public @Nullable EntityReference<LivingEntity> getOwnerReference() { return owner; }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(3, new FollowLeaderGoal(this, 1.1, 10F, 3F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerTargetGoal(this, true));   // whoever hurt the owner
        targetSelector.addGoal(2, new OwnerTargetGoal(this, false));  // whatever the owner hit
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 5, true, false,
            (t, lvl) -> !(t instanceof Creeper)));
    }
    // allies: makes TargetingConditions.forCombat() skip owner + fellow soldiers (TargetingConditions.java:77)
    @Override protected boolean considersEntityAsAlly(Entity other) {
        LivingEntity o = getOwner();
        return other == o || (other instanceof OwnableEntity oe && o != null && oe.getOwner() == o) || super.considersEntityAsAlly(other);
    }
    @Override public boolean canAttack(LivingEntity t) { return t != getOwner() && !isAlliedTo(t) && super.canAttack(t); }
    // friendly fire: Player#attack only checks isAlliedTo for sweeps (Player.java:1218), so block direct hits here
    @Override public boolean hurtServer(ServerLevel l, DamageSource src, float amt) {
        Entity a = src.getEntity();
        if (a != null && isAlliedTo(a)) return false;
        return super.hurtServer(l, src, amt);
    }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected void addAdditionalSaveData(ValueOutput out) { super.addAdditionalSaveData(out); EntityReference.store(owner, out, "Owner"); }
    @Override protected void readAdditionalSaveData(ValueInput in) { super.readAdditionalSaveData(in); owner = EntityReference.read(in, "Owner"); }

    static class OwnerTargetGoal extends TargetGoal {
        private final ShadowSoldier s; private final boolean defend; private @Nullable LivingEntity pick; private int ts;
        OwnerTargetGoal(ShadowSoldier s, boolean defend) { super(s, false); this.s = s; this.defend = defend; setFlags(EnumSet.of(Goal.Flag.TARGET)); }
        @Override public boolean canUse() {
            LivingEntity o = s.getOwner(); if (o == null) return false;
            pick = defend ? o.getLastHurtByMob() : o.getLastHurtMob();
            int stamp = defend ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            return stamp != ts && canAttack(pick, TargetingConditions.DEFAULT);
        }
        @Override public void start() {
            mob.setTarget(pick); LivingEntity o = s.getOwner();
            if (o != null) ts = defend ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            super.start();
        }
    }
    static class FollowLeaderGoal extends Goal {
        private final ShadowSoldier s; private final double speed; private final float start, stop;
        FollowLeaderGoal(ShadowSoldier s, double speed, float start, float stop) { this.s = s; this.speed = speed; this.start = start; this.stop = stop; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { LivingEntity o = s.getOwner(); return o != null && !o.isSpectator() && s.getTarget() == null && s.distanceToSqr(o) > start * start; }
        @Override public boolean canContinueToUse() { LivingEntity o = s.getOwner(); return o != null && !s.getNavigation().isDone() && s.distanceToSqr(o) > stop * stop; }
        @Override public void tick() {
            LivingEntity o = s.getOwner(); if (o == null) return;
            s.getLookControl().setLookAt(o, 10F, s.getMaxHeadXRot());
            if (s.distanceToSqr(o) > 400) s.snapTo(o.getX(), o.getY(), o.getZ(), s.getYRot(), s.getXRot()); else s.getNavigation().moveTo(o, speed);
        }
        @Override public void stop() { s.getNavigation().stop(); }
    }
}
```
Damage dealt by the summon is credited to it, not the owner. To credit the owner, hit with `damageSources().source(KEY, this, owner)` from `doHurtTarget`. Alternative interception point: `LivingIncomingDamageEvent` (cancel when the attacker and victim are allied).
`Mob#setTarget` fires the cancellable `LivingChangeTargetEvent` (`Mob.java:257`).

---

## 10. Villager-like NPC (name, interact, dialogue/screen, home)

- `protected InteractionResult mobInteract(Player player, InteractionHand hand)` (`Mob.java:1215`). It's reached from `Mob#interact(Player, InteractionHand, Vec3)` after name-tag and spawn-egg handling.
- `InteractionResult` is a sealed interface (`world/InteractionResult.java`): `SUCCESS` (client swing predicted), `SUCCESS_SERVER`, `CONSUME` (no swing), `FAIL`, `PASS`; `Success#heldItemTransformedTo(ItemStack)`. The vanilla pattern is to do the work in `if (!level().isClientSide())` and return `SUCCESS` on both sides (`npc/villager/Villager.java:268-297`).
- Name: `setCustomName(@Nullable Component)`, `setCustomNameVisible(boolean)`. Home: `setHomeTo(BlockPos, int radius)` + `MoveTowardsRestrictionGoal`. Strolls respect `isWithinHome`. Saved by `Mob` as `home_pos` / `home_radius`.
- Messages: `player.sendSystemMessage(Component)` (chat), `sendOverlayMessage(Component)` (action bar). Screens: `ServerPlayer#openMenu(MenuProvider)` / `openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf> extra)` for container screens; `SimpleMenuProvider(MenuConstructor, Component title)`;
  a pure client screen needs a payload: `PacketDistributor.sendToPlayer(ServerPlayer, CustomPacketPayload)` (`neo/.../network/PacketDistributor.java:36`). Vanilla server-driven dialog: `player.openDialog(Holder<Dialog>)` (`ServerPlayer.java:1443`; `server/dialog/NoticeDialog(CommonDialogData, ActionButton)`; data-driven under `data/<ns>/dialog/*.json`).
```java
public class Elder extends PathfinderMob implements Npc {        // net.minecraft.world.entity.npc.Npc
    public Elder(EntityType<? extends Elder> t, Level l) { super(t, l); setCustomName(Component.literal("Elder Kael")); setCustomNameVisible(true); }
    public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.25); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 0.6));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
    @Override public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor l, DifficultyInstance d, EntitySpawnReason r, @Nullable SpawnGroupData g) {
        setHomeTo(blockPosition(), 12); setPersistenceRequired(); return super.finalizeSpawn(l, d, r, g);
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSecondaryUseActive()) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer sp) {
            getLookControl().setLookAt(sp);
            sp.sendSystemMessage(Component.literal("<Elder Kael> The gate opens at dusk, Hunter."));
            // PacketDistributor.sendToPlayer(sp, new OpenDialoguePayload(getId(), "kael_intro"));
        }
        return InteractionResult.SUCCESS;
    }
    @Override public boolean removeWhenFarAway(double d) { return false; }
}
```
Generic alternative without subclassing: `PlayerInteractEvent.EntityInteract` (`getTarget()`, `getLocation()`, `setCancellationResult(InteractionResult)`, cancellable).

---

## 11. Vanilla Mannequin (stand-in body)

- Class: `net.minecraft.world.entity.decoration.Mannequin extends Avatar extends LivingEntity` (**not** a `Player` and **not** a `Mob`). Type: `EntityTypes.MANNEQUIN` (0.6×1.8, eye 1.62, tracking 32 chunks) (`src/.../entity/decoration/Mannequin.java`, `EntityTypes.java:690`).
- Ctors: `public Mannequin(EntityType<Mannequin>, Level)`, `protected Mannequin(Level)`; factory `static @Nullable Mannequin create(EntityType<Mannequin>, Level)` dispatches through the static `constructor`, which the client swaps to `ClientMannequin` (skin lookup + rendering, `client/entity/ClientMannequin.java:20`). **Spawn the vanilla type**: a subclass loses the client renderer path.
- Profile: synced `DATA_PROFILE` (`EntityDataSerializers.RESOLVABLE_PROFILE`, protected static). `setProfile` is **private**, so set it through the component API: `mannequin.setComponent(DataComponents.PROFILE, ResolvableProfile)`, which routes to `applyImplicitComponent` → `setProfile`. Read with `getProfile()`.
  `ResolvableProfile.createResolved(GameProfile)` (full profile incl. skin textures, from `serverPlayer.getGameProfile()`), `createUnresolved(String name)`, `createUnresolved(UUID)` (`world/item/component/ResolvableProfile.java:57-66`). The client resolves the skin when `DATA_PROFILE` changes.
- Saved NBT keys: `profile`, `hidden_layers`, `main_hand`, `pose`, `immovable`, `description`, `hide_description`. Private setters (`immovable`, `hide_description`) can only be set by loading NBT.
- Poses: `setPose(Pose)` is public on `Entity`; valid saved poses are `STANDING, CROUCHING, SWIMMING, FALL_FLYING, SLEEPING` (`Mannequin.java:VALID_POSES`). Nothing in `LivingEntity` ticks the pose back for it. `SLEEPING` without a bed renders lying along `yBodyRot` (`client/renderer/entity/LivingEntityRenderer.java:178-183`) and shrinks the hitbox to 0.2×0.2.
- Damage: yes. Normal `LivingEntity#hurtServer`, attributes = `LivingEntity.createLivingAttributes()` (20 HP) (`ai/attributes/DefaultAttributes.java:140`). It dies, drops loot table `entities/mannequin`, gets knockback and pushes. `immovable` only zeroes AI/input movement (`LivingEntity.java:3177`). Not a `Mob`, so it never despawns. Hostile `NearestAttackableTargetGoal<Player>` ignores it.
- It shows a description label "NPC" (`entity.minecraft.mannequin.label`) under the name unless `hide_description:1b`.
```java
static Mannequin spawnBody(ServerPlayer sp) {
    ServerLevel level = sp.level();
    CompoundTag tag = new CompoundTag();
    tag.putBoolean("immovable", true);
    tag.putBoolean("hide_description", true);
    tag.putString("pose", "sleeping");                                   // or "crouching"
    Entity e = EntityType.loadEntityRecursive(EntityTypes.MANNEQUIN, tag, level, EntitySpawnReason.TRIGGERED, ent -> {
        ent.snapTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0F);
        return ent;
    });                                                                  // EntityType.java:399
    Mannequin body = (Mannequin) e;
    body.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(sp.getGameProfile()));
    body.setYBodyRot(sp.getYRot()); body.setYHeadRot(sp.getYRot());
    body.setCustomName(sp.getDisplayName());
    body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
    level.addFreshEntity(body);
    return body;   // store body.getUUID() on the player; forward damage via LivingIncomingDamageEvent if desired
}
```
Command equivalent: `/summon minecraft:mannequin ~ ~ ~ {profile:"Name",pose:"sleeping",immovable:1b,hide_description:1b}` (the profile codec accepts a bare name).

---

## 12. Common entity events (game bus unless noted)

| Event (FQN) | Key API | Notes |
|---|---|---|
| `net.neoforged.neoforge.event.entity.EntityJoinLevelEvent` | `getEntity()`, `getLevel()`, `loadedFromDisk()` | cancellable; fires on both sides |
| `…event.entity.EntityLeaveLevelEvent` | `getEntity()`, `getLevel()` | |
| `…event.entity.living.LivingDeathEvent` | `getEntity()` (victim), `getSource()` (`.getEntity()` = killer, `.getDirectEntity()` = projectile) | cancellable (restore HP yourself); `victim.getKillCredit()` for assists |
| `…event.entity.living.LivingDropsEvent` | `getDrops()` (`Collection<ItemEntity>`), `getSource()`, `isRecentlyHit()` | cancellable |
| `…event.entity.living.LivingIncomingDamageEvent` | `getSource()`, `getAmount()`, `setAmount(float)`, `getOriginalAmount()`, `setInvulnerabilityTicks(int)`, `addReductionModifier(DamageContainer.Reduction, IReductionFunction)` | cancellable; before armour; earliest point |
| `…event.entity.living.LivingDamageEvent.Pre` / `.Post` | `Pre#setNewDamage(float)`; `Post#getNewDamage()`, `getBlockedDamage()`, `getReduction(Reduction)` | after reductions; not cancellable (set 0) |
| `…event.entity.living.LivingKnockBackEvent` | `getStrength()`, `setStrength`, `getRatioX/Z` | cancellable |
| `…event.entity.living.LivingChangeTargetEvent` | `getNewAboutToBeSetTarget()`, `setNewAboutToBeSetTarget` | cancellable |
| `…event.entity.living.FinalizeSpawnEvent` (extends `MobSpawnEvent`) | `getEntity()` (Mob), `getLevel()`, `getSpawnType()` (`EntitySpawnReason`), `getDifficulty()`, `setSpawnCancelled(true)` | cancel = skip `finalizeSpawn` only; `setSpawnCancelled` blocks the spawn |
| `…event.entity.living.MobSpawnEvent.SpawnPlacementCheck` / `.PositionCheck` | `setResult(Result)` | natural/spawner placement override |
| `…event.entity.living.MobDespawnEvent` | `setResult` | |
| `net.neoforged.neoforge.event.tick.EntityTickEvent.Pre` / `.Post` | `getEntity()` | `Pre` cancellable (skips the tick); fires for **every** entity, so filter fast |
| `…event.entity.player.PlayerInteractEvent.EntityInteract` | `getTarget()`, `getLocation()`, `setCancellationResult(InteractionResult)` | cancellable |
| `…event.entity.ProjectileImpactEvent` | `getProjectile()`, `getRayTraceResult()` | cancellable |
```java
@EventBusSubscriber(modid = MODID)
public final class CombatHooks {
    @SubscribeEvent static void onDeath(LivingDeathEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer killer) { /* grant XP / extract shadow */ }
    }
    @SubscribeEvent static void onIncoming(LivingIncomingDamageEvent e) {
        if (e.getSource().is(DamageTypeTags.IS_FALL) && e.getEntity().hasEffect(ModEffects.SHADOW_FORM)) e.setCanceled(true);
    }
    @SubscribeEvent static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Zombie z && !e.loadedFromDisk()) { /* buff */ }
    }
    @SubscribeEvent static void onTick(EntityTickEvent.Post e) { if (e.getEntity() instanceof ServerPlayer sp) { /* … */ } }
}
```
Sources: `neo/.../event/entity/*.java`, `neo/.../event/entity/living/*.java`, `neo/.../event/tick/EntityTickEvent.java`.

---

## 13. Item use for "power" abilities

- `public InteractionResult use(Level level, Player player, InteractionHand hand)` (`world/item/Item.java:226`). Also `useOn(UseOnContext)`, `interactLivingEntity(ItemStack, Player, LivingEntity, InteractionHand)`,
  `getUseDuration(ItemStack, LivingEntity)`, `releaseUsing(ItemStack, Level, LivingEntity, int remaining)` → **boolean**, `onUseTick(Level, LivingEntity, ItemStack, int remaining)`,
  `inventoryTick(ItemStack, ServerLevel, Entity, @Nullable EquipmentSlot)` (server only now).
- Cooldowns (`world/item/ItemCooldowns.java`, `player.getCooldowns()`; `ServerItemCooldowns` syncs to the client): `isOnCooldown(ItemStack)`, `getCooldownPercent(ItemStack, float partial)`,
  `addCooldown(ItemStack, int ticks)`, `addCooldown(Identifier group, int ticks)`, `removeCooldown(Identifier group)`, `Identifier getCooldownGroup(ItemStack)` (= `USE_COOLDOWN.cooldownGroup` or the item id). There's **no `isOnCooldown(Identifier)`**.
  The item component `DataComponents.USE_COOLDOWN = record UseCooldown(float seconds, Optional<Identifier> cooldownGroup)`; `Item.Properties#useCooldown(float seconds)`. For instant-use items it's applied **automatically** when `use` returns a `Success` (`ItemStack.java:403-433`), and `ServerPlayerGameMode#useItem` refuses to call `use` while cooling down (`:357`).
  Keybind abilities without an item: keep your own tick counters (attachment or capability); `ItemCooldowns` is keyed by stack.
- Look/raycast: `getLookAngle()`, `getViewVector(float partial)`, `getEyePosition()` / `getEyePosition(float)`, `getEyeY()`, `HitResult pick(double range, float partial, boolean withLiquids)` (blocks only, `Entity.java:2054`),
  `public static BlockHitResult Item.getPlayerPOVHitResult(Level, Player, ClipContext.Fluid)` (now public, range = `blockInteractionRange()`), `player.blockInteractionRange()`, `entityInteractionRange()`. Entities: the §6 `beam()`.
```java
public class ShadowStepItem extends Item {
    public ShadowStepItem(Item.Properties p) { super(p); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            Vec3 dash = sp.getLookAngle().scale(1.8);
            sp.setDeltaMovement(dash.x, Math.max(0.2, dash.y), dash.z);
            sp.syncVelocity = true;
            sp.setIgnoreFallDamageFromCurrentImpulse(true, sp.position());
            EntityHitResult hit = beam(sp, 6.0);   // §6
            if (hit != null) hit.getEntity().hurtServer(sp.level(), sp.damageSources().playerAttack(sp), 8F);
        }
        return InteractionResult.SUCCESS;      // triggers USE_COOLDOWN
    }
}
// ITEMS.registerItem("shadow_step", ShadowStepItem::new, p -> p.stacksTo(1).useCooldown(2.0F));
```

---

## 14. ItemStack data components

- `DataComponentType.builder()` → `persistent(Codec<T>)`, `networkSynchronized(StreamCodec<? super RegistryFriendlyByteBuf, T>)`, `cacheEncoding()`, `ignoreSwapAnimation()`, `build()` (`core/component/DataComponentType.java:26-78`).
- NeoForge: `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID)` → `registerComponentType(String, UnaryOperator<DataComponentType.Builder<D>>)` (`DeferredRegister.java:173,789`). Data component types register before items (`GameData.java:130`).
- Stack API: `@Nullable T get(DataComponentType<? extends T>)`, `getOrDefault(type, def)`, `has(type)`, `@Nullable T set(DataComponentType<T>, @Nullable T)` (returns the previous value), `update(type, T default, UnaryOperator<T>)`, `remove(type)`.
  NeoForge adds `Supplier<DataComponentType>` overloads (`set/update/remove/get/has`), so a `DeferredHolder` can be passed directly (`neo/.../common/MutableDataComponentHolder.java`, `core/component/DataComponentGetter.java:20-28`).
- Values **must be immutable with value equality** (records), because stacks compare and merge by `equals`. Never mutate a returned value; `set` a new one.
- Default on every instance of your item: `Item.Properties#component(type, value)`.
```java
public record ShadowCharge(int charges, Optional<Identifier> soul) {
    public static final ShadowCharge EMPTY = new ShadowCharge(0, Optional.empty());
    public static final Codec<ShadowCharge> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("charges").forGetter(ShadowCharge::charges),
        Identifier.CODEC.optionalFieldOf("soul").forGetter(ShadowCharge::soul)
    ).apply(i, ShadowCharge::new));
    public static final StreamCodec<ByteBuf, ShadowCharge> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ShadowCharge::charges,
        Identifier.STREAM_CODEC.apply(ByteBufCodecs::optional), ShadowCharge::soul,
        ShadowCharge::new);
}
public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShadowCharge>> SHADOW_CHARGE =
    COMPONENTS.registerComponentType("shadow_charge", b -> b.persistent(ShadowCharge.CODEC).networkSynchronized(ShadowCharge.STREAM_CODEC));

// usage
ShadowCharge c = stack.getOrDefault(SHADOW_CHARGE, ShadowCharge.EMPTY);
stack.set(SHADOW_CHARGE, new ShadowCharge(c.charges() + 1, c.soul()));
stack.update(SHADOW_CHARGE.get(), ShadowCharge.EMPTY, v -> new ShadowCharge(v.charges() - 1, v.soul()));
```
`CustomData` (`DataComponents.CUSTOM_DATA`, `world/item/component/CustomData.java`) is the untyped escape hatch:
`CustomData.EMPTY`, `CustomData.of(CompoundTag)`, `static update(DataComponentType<CustomData>, ItemStack, Consumer<CompoundTag>)`, `static set(type, stack, CompoundTag)`, `copyTag()`, `contains(String)`, `isEmpty()`. There's no live tag view, so read from `copyTag()`.
```java
CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt("kills", tag.getIntOr("kills", 0) + 1));
int kills = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("kills", 0);
```
Prefer a typed component: `CustomData` isn't validated, costs an NBT copy per read, and is visible in `/data`.
