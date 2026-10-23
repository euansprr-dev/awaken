package dev.awaken.descent.entity;

import dev.awaken.descent.DescentContent;
import dev.awaken.descent.DescentSync;
import dev.awaken.descent.player.DeepPacks;
import dev.awaken.descent.player.DescentData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What you leave where you fell in the Descent: a ghost of light holding your Deep Pack. It never despawns, can't be
 * hurt, and only its owner can take it back (walk into it or use it). Shown on the owner's screen.
 */
public class GraveEcho extends Entity {
    public static final int SLOTS = 27;
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(GraveEcho.class, EntityDataSerializers.STRING);

    private @Nullable UUID owner;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public GraveEcho(EntityType<? extends GraveEcho> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static @Nullable GraveEcho create(ServerLevel level, BlockPos at, ServerPlayer owner, List<ItemStack> contents) {
        GraveEcho e = DescentContent.GRAVE_ECHO.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (e == null) return null;
        e.snapTo(at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, owner.getYRot(), 0);
        e.owner = owner.getUUID();
        e.entityData.set(OWNER_NAME, owner.getGameProfile().name());
        for (int i = 0; i < Math.min(SLOTS, contents.size()); i++) e.items.set(i, contents.get(i).copy());
        e.setCustomName(Component.literal(owner.getGameProfile().name() + "'s Grave Echo"));
        e.setCustomNameVisible(true);
        level.addFreshEntity(e);
        level.playSound(null, at, DescentContent.SND_GRAVE.get(), SoundSource.PLAYERS, 1.0F, 0.8F);
        return e;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return entityData.get(OWNER_NAME);
    }

    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : items) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public List<ItemStack> takeItems() {
        List<ItemStack> out = contents();
        for (int i = 0; i < SLOTS; i++) items.set(i, ItemStack.EMPTY);
        discard();
        return out;
    }

    /** The owner died again on a difficulty that keeps one echo: this one is gone, and its loot with it. */
    public void crumble() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.9, getZ(), 30, 0.3, 0.6, 0.3, 0.02);
        }
        for (int i = 0; i < SLOTS; i++) items.set(i, ItemStack.EMPTY);
        discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(OWNER_NAME, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - 0.5) * 0.6,
                        getY() + random.nextDouble() * 1.6, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
            return;
        }
        if (tickCount % 10 != 0 || !(level() instanceof ServerLevel sl) || owner == null) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(owner);
        if (p == null) return;
        DescentData d = DescentSync.data(p);
        if (d.graves.stream().noneMatch(g -> g.id().equals(getUUID()))) {
            crumble();
            return;
        }
        if (p.level() == sl && p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 2.4 * 2.4) {
            DeepPacks.recover(p, this);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player instanceof ServerPlayer sp && owner != null && owner.equals(sp.getUUID()) && !isRemoved()) {
            DeepPacks.recover(sp, this);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal("This Grave Echo belongs to " + ownerName() + "."));
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        owner = in.read("owner", UUIDUtil.CODEC).orElse(null);
        entityData.set(OWNER_NAME, in.getStringOr("owner_name", ""));
        ContainerHelper.loadAllItems(in, items);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.storeNullable("owner", UUIDUtil.CODEC, owner);
        out.putString("owner_name", ownerName());
        ContainerHelper.saveAllItems(out, items);
    }
}
