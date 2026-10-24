package dev.awaken.veil;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.DreamHooks;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spirit projection, server-authoritative. The real player becomes the spirit (spectator mode under our rules) while a
 * vanilla Mannequin with their skin and gear stays behind as the Body. The saved {@link ProjectionState} is the safety
 * net: whatever happens (logout, crash, restart, death), the player ends up back in their body in their old game mode.
 */
public final class Projection {
    private Projection() {}

    public static final Identifier SPIRIT_EFFECT = Awaken.id("veil_spirit");
    public static final Identifier RIPPLE_EFFECT = Awaken.id("veil_ripple");

    /** Live spirits by player. Never saved. */
    static final Map<UUID, Spirit> LIVE = new HashMap<>();
    /** Players the Veil itself is moving right now (our own game mode and dimension changes are allowed through). */
    static final Set<UUID> TRANSIT = new HashSet<>();

    public enum EndReason { RETURNED, TIME, CORD_CUT, BODY_DIED, BODY_LOST, LOGOUT, DEATH, COMMAND, DISABLED }

    /** The live half of a projection. */
    public static final class Spirit {
        final UUID owner;
        final UUID bodyId;
        @Nullable Mannequin body;
        final ResourceKey<Level> bodyDim;
        Vec3 bodyPos;
        /** The point the cord is measured from: the Body, or the doorway when leaving through the Stillpoint's Out door. */
        ResourceKey<Level> anchorDim;
        Vec3 anchor;
        boolean anchorIsBody = true;
        int ticksLeft;
        final int maxTicks;
        final int radius;
        int awareness;
        Vec3 lastGood;
        int age;
        int warnStage;
        long lastWarning = -1000;
        long lastPullToast = -1000;
        public int warningsSent;
        long lastBodyHurt = -1000;
        boolean bodyHurtFlag;
        @Nullable DamageSource bodyDiedSource;
        int pendingVeilTicks;
        boolean dirty = true;
        // possession
        @Nullable Mob possessed;
        boolean possessedHadNoGravity;
        VeilPayloads.PossessInput input = new VeilPayloads.PossessInput(0, 0, false, false, 0, 0);
        int actCooldown;
        boolean ignited;
        // Cord-Eaters
        final List<LivingEntity> eaters = new ArrayList<>();
        int outsideTicks;
        int nextEater = -1;
        // Veil Gates
        @Nullable String pocket;
        ResourceKey<Level> riftReturnDim = Level.OVERWORLD;
        Vec3 riftReturn = Vec3.ZERO;
        int riftCooldown;
        ResourceKey<Level> savedAnchorDim = Level.OVERWORLD;
        Vec3 savedAnchor = Vec3.ZERO;
        boolean savedAnchorIsBody = true;
        // sight
        final SpiritSight.Scanner scanner = new SpiritSight.Scanner();

        Spirit(UUID owner, UUID bodyId, ResourceKey<Level> bodyDim, Vec3 bodyPos, int maxTicks, int radius, int awareness) {
            this.owner = owner;
            this.bodyId = bodyId;
            this.bodyDim = bodyDim;
            this.bodyPos = bodyPos;
            this.anchorDim = bodyDim;
            this.anchor = bodyPos;
            this.maxTicks = maxTicks;
            this.ticksLeft = maxTicks;
            this.radius = radius;
            this.awareness = awareness;
            this.lastGood = bodyPos;
        }

        public int ticksLeft() { return ticksLeft; }
        public int radius() { return radius; }
        public @Nullable Mannequin body() { return body; }
        public @Nullable Mob possessed() { return possessed; }
        public Vec3 anchor() { return anchor; }
        public ResourceKey<Level> anchorDim() { return anchorDim; }
        public boolean inPocket() { return pocket != null; }
    }

    public static @Nullable Spirit live(ServerPlayer player) {
        return LIVE.get(player.getUUID());
    }

    static boolean inTransit(ServerPlayer player) {
        return TRANSIT.contains(player.getUUID());
    }

    static void withTransit(ServerPlayer player, Runnable r) {
        boolean added = TRANSIT.add(player.getUUID());
        try {
            r.run();
        } finally {
            if (added) TRANSIT.remove(player.getUUID());
        }
    }

    // ---- numbers ----------------------------------------------------------------------------------------------------

    public static int cordRadius(ServerPlayer p) {
        return Cord.radius(Progression.stat(p, Stat.SPI), Progression.perk(p, Perks.CORD_LENGTH));
    }

    public static int veilTicks(ServerPlayer p) {
        return Cord.timeTicks(Progression.stat(p, Stat.SPI));
    }

    public static int awareness(ServerPlayer p) {
        return Cord.awareness(Progression.stat(p, Stat.PER), Progression.stat(p, Stat.SPI), Progression.perk(p, Perks.SECRET_GLOW_RANGE));
    }

    static int possessLevel(ServerPlayer p) {
        Rank r = Progression.rank(p);
        if (r.ordinal() >= Rank.A.ordinal()) return 2;
        if (r.ordinal() >= Rank.C.ordinal()) return 1;
        return 0;
    }

    // ---- start ------------------------------------------------------------------------------------------------------

    /**
     * Leave the body. {@code outTo} sends the spirit somewhere else at once (the Stillpoint's Out door): the cord is then
     * measured from there and the Body waits where it is. {@code force} skips the rank, shock and feature checks (admin).
     */
    public static boolean start(ServerPlayer sp, boolean force, VeilMemory.@Nullable ReturnPoint outTo) {
        if (LIVE.containsKey(sp.getUUID()) || !sp.isAlive() || sp.isSleeping()) return false;
        GameType mode = sp.gameMode.getGameModeForPlayer();
        if (mode == GameType.SPECTATOR) return false;
        if (!force) {
            if (!Veil.enabled()) return false;
            if (!Progression.profile(sp).awakened()) return false;
            if (sp.hasEffect(VeilContent.SPIRIT_SHOCK)) {
                Signal.toast(sp, Component.literal("Your spirit is still shaking. Not yet."));
                return false;
            }
        }
        Meditation.stop(sp);
        sp.stopRiding();
        ServerLevel level = sp.level();
        Mannequin body = createBody(sp);
        if (body == null) return false;

        Spirit s = new Spirit(sp.getUUID(), body.getUUID(), level.dimension(), body.position(), veilTicks(sp), cordRadius(sp), awareness(sp));
        s.body = body;
        LIVE.put(sp.getUUID(), s);
        if (!level.addFreshEntity(body)) {
            LIVE.remove(sp.getUUID());
            return false;
        }
        sp.setData(VeilContent.PROJECTION.get(), new ProjectionState(ProjectionState.VERSION, true, mode, level.dimension(), body.position(),
                body.getYRot(), Optional.of(body.getUUID()), sp.getHealth(), level.getGameTime()));
        refreshTicket(body);

        withTransit(sp, () -> {
            sp.setGameMode(GameType.SPECTATOR);
            if (outTo != null) {
                ServerLevel dest = level.getServer().getLevel(outTo.pos().dimension());
                if (dest != null) {
                    Vec3 p = Vec3.atBottomCenterOf(outTo.pos().pos()).add(0, 0.6, 0);
                    teleport(sp, dest, p, outTo.yaw(), outTo.pitch());
                    s.anchorDim = dest.dimension();
                    s.anchor = p;
                    s.anchorIsBody = false;
                    s.lastGood = p;
                }
            } else {
                Vec3 up = sp.position().add(0, 0.8, 0);
                sp.teleportTo(level, up.x, up.y, up.z, Set.of(), sp.getYRot(), sp.getXRot(), true);
                s.lastGood = up;
            }
        });
        sp.removePostEffect(RIPPLE_EFFECT);
        sp.addPostEffect(SPIRIT_EFFECT);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), VeilContent.RISE.get(), SoundSource.PLAYERS, 0.9F, 1.0F);

        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        mem.projections++;
        if (mem.tipsShown < TIPS) Signal.toast(sp, tip(mem.tipsShown++));
        DirectorLog.log(sp, "veil", "projected: cord " + s.radius + ", time " + s.maxTicks / 20 + "s, awareness " + s.awareness);
        dev.awaken.core.Playtest.log(sp, "veil_project", "cord", s.radius);
        sendState(sp, s);
        return true;
    }

    private static final int TIPS = 3;

    /** First-projection tips; keys are shown as the player bound them. */
    private static Component tip(int i) {
        return switch (i) {
            case 0 -> Component.literal("You can pass through anything. You can only touch what glows blue.");
            case 1 -> Component.literal("[").append(Component.keybind("key.awaken.focus")).append("] speak to the dead · [")
                    .append(Component.keybind("key.awaken.accept")).append("] go back to your body");
            default -> Component.literal("Your body stays behind. Somewhere safe would be good.");
        };
    }

    static @Nullable Mannequin createBody(ServerPlayer sp) {
        ServerLevel level = sp.level();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("immovable", true);
        tag.putBoolean("hide_description", true);
        tag.putString("pose", "crouching");
        float yaw = sp.getYRot();
        Entity e = EntityType.loadEntityRecursive(EntityTypes.MANNEQUIN, tag, level, EntitySpawnReason.TRIGGERED, ent -> {
            ent.snapTo(sp.getX(), sp.getY(), sp.getZ(), yaw, 0F);
            return ent;
        });
        if (!(e instanceof Mannequin body)) return null;
        body.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(sp.getGameProfile()));
        body.setMainArm(sp.getMainArm());
        body.setYBodyRot(yaw);
        body.setYHeadRot(yaw);
        body.setCustomName(Component.translatable("entity.awaken.veil_body", sp.getDisplayName()));
        body.setCustomNameVisible(false);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            body.setItemSlot(slot, sp.getItemBySlot(slot).copy()); // for looks and armour only: a Body never drops anything
        }
        AttributeInstance hp = body.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(sp.getMaxHealth());
        AttributeInstance kb = body.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1.0);
        body.setHealth(sp.getHealth());
        body.setData(VeilContent.BODY_TAG.get(), new BodyTag(sp.getUUID()));
        return body;
    }

    static void refreshTicket(Mannequin body) {
        if (body.level() instanceof ServerLevel sl) sl.getChunkSource().addTicketWithRadius(VeilContent.BODY_TICKET.get(), body.chunkPosition(), 2);
    }

    // ---- tick -------------------------------------------------------------------------------------------------------

    static void tick(ServerPlayer sp, Spirit s) {
        s.age++;
        MinecraftServer server = sp.level().getServer();
        if (s.bodyDiedSource != null) {
            bodyDied(sp, s);
            return;
        }
        if (!Veil.enabled()) {
            end(sp, EndReason.DISABLED);
            return;
        }
        // the Body
        if (s.body == null || s.body.isRemoved()) {
            ServerLevel bl = server.getLevel(s.bodyDim);
            Entity found = bl == null ? null : bl.getEntity(s.bodyId);
            if (found instanceof Mannequin m && m.isAlive()) s.body = m;
            else if (s.age > 40) {
                end(sp, EndReason.BODY_LOST);
                return;
            }
        }
        if (s.body != null && s.age % 20 == 0) {
            refreshTicket(s.body);
            s.bodyPos = s.body.position();
            if (s.anchorIsBody) s.anchor = s.bodyPos;
            provokeMobs(s.body);
        }
        // possession keeps the player glued to the creature; check it before the movement rules
        if (s.possessed != null) Possession.check(sp, s);
        Entity cam = sp.getCamera();
        if (cam != sp && cam != s.possessed) sp.setCamera(sp); // no riding along inside other players or mobs

        ServerLevel level = sp.level();
        if (level.dimension() != s.anchorDim && !inTransit(sp)) {
            ServerLevel home = server.getLevel(s.anchorDim);
            if (home != null) withTransit(sp, () -> teleport(sp, home, s.lastGood, sp.getYRot(), sp.getXRot()));
            return;
        }
        Vec3 pos = sp.position();
        if (s.possessed == null && pos.distanceToSqr(s.lastGood) > 24 * 24) {
            // a spectator-menu teleport or similar jump: not allowed
            sp.teleportTo(level, s.lastGood.x, s.lastGood.y, s.lastGood.z, Set.of(), sp.getYRot(), sp.getXRot(), true);
            pos = s.lastGood;
        } else if (s.possessed == null && VeilSolid.blocked(level, s.lastGood, pos)) {
            sp.teleportTo(level, s.lastGood.x, s.lastGood.y, s.lastGood.z, Set.of(), sp.getYRot(), sp.getXRot(), false);
            pos = s.lastGood;
        } else {
            s.lastGood = pos;
        }
        // the cord
        double tension = Cord.tension(pos.distanceTo(s.anchor), s.radius);
        if (tension > 1.0) {
            if (s.possessed != null) Possession.release(sp, s, "The cord won't stretch any further.");
            Vec3 dir = pos.subtract(s.anchor).normalize();
            Vec3 back = s.anchor.add(dir.scale(Cord.pullBackDistance(s.radius)));
            sp.teleportTo(level, back.x, back.y, back.z, Set.of(), sp.getYRot(), sp.getXRot(), true);
            s.lastGood = back;
            if (s.age - s.lastPullToast > 40) {
                Signal.toast(sp, Component.literal("The cord pulls you back.").withStyle(ChatFormatting.RED));
                s.lastPullToast = s.age;
            }
            s.warnStage = 2;
        } else if (tension >= Cord.DANGER && s.warnStage < 2) {
            s.warnStage = 2;
            Signal.toast(sp, Component.literal("The cord is at its limit.").withStyle(ChatFormatting.RED));
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(VeilContent.CORD_SNAP, SoundSource.PLAYERS,
                    pos.x, pos.y, pos.z, 0.25F, 1.6F, sp.getRandom().nextLong()));
        } else if (tension >= Cord.WARN && s.warnStage < 1) {
            s.warnStage = 1;
            Signal.toast(sp, Component.literal("The cord is getting thin.").withStyle(ChatFormatting.GOLD));
        } else if (tension < Cord.WARN - 0.05) {
            s.warnStage = 0;
        }
        // focus runs out
        if (--s.ticksLeft <= 0) {
            end(sp, EndReason.TIME);
            return;
        }
        if (++s.pendingVeilTicks >= 20) {
            Progression.count(sp, Counters.VEIL_TICKS, s.pendingVeilTicks);
            s.pendingVeilTicks = 0;
        }
        if (s.riftCooldown > 0) s.riftCooldown--;

        SpiritSight.tick(sp, s);
        if (!LIVE.containsKey(sp.getUUID())) return;
        Spirits.tickSpirit(sp, s);
        CordEaters.tick(sp, s);
        if (!LIVE.containsKey(sp.getUUID())) return;
        VeilGates.tick(sp, s);
        if (!LIVE.containsKey(sp.getUUID())) return;
        if (s.dirty || s.age % 20 == 0) sendState(sp, s);
    }

    /** Hostile mobs near an empty Body notice it. A Body is not a Player, so we point them at it ourselves. */
    static void provokeMobs(Mannequin body) {
        if (!(body.level() instanceof ServerLevel sl)) return;
        AABB box = body.getBoundingBox().inflate(16, 6, 16);
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive() && m.getTarget() == null)) {
            if (CordEaters.isEater(mob) || mob.isNoAi()) continue;
            if (mob.getSensing().hasLineOfSight(body)) mob.setTarget(body);
        }
    }

    // ---- body events ------------------------------------------------------------------------------------------------

    static @Nullable Spirit spiritOfBody(LivingEntity body) {
        if (!(body instanceof Mannequin)) return null;
        BodyTag tag = body.getExistingData(VeilContent.BODY_TAG.get()).orElse(BodyTag.NONE);
        if (!tag.isBody()) return null;
        Spirit s = LIVE.get(tag.owner());
        return s != null && s.bodyId.equals(body.getUUID()) ? s : null;
    }

    /** The Body took damage: warn its owner and offer the way back (Y). */
    static void onBodyHurt(LivingEntity body, DamageSource source, float amount) {
        Spirit s = spiritOfBody(body);
        if (s == null || !(body.level() instanceof ServerLevel sl)) return;
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(s.owner);
        if (owner == null) return;
        s.lastBodyHurt = s.age;
        s.bodyHurtFlag = true;
        s.dirty = true;
        if (s.age - s.lastWarning < 60 && s.warningsSent > 0) return;
        s.lastWarning = s.age;
        s.warningsSent++;
        Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Your body is under attack."))
                .body(Component.literal(culprit(source.getEntity()) + " " + heartsLost(amount) + " lost."))
                .row(Component.literal("Body"), Component.literal(hearts(body.getHealth()) + " / " + hearts(body.getMaxHealth()) + " ♥"))
                .choice("veil:return", "Return to your body", "Stay out").priority(9).ttl(20 * 8).build());
        owner.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(dev.awaken.core.CoreModule.SIGNAL_WARNING,
                SoundSource.PLAYERS, owner.getX(), owner.getY(), owner.getZ(), 0.8F, 0.8F, owner.getRandom().nextLong()));
    }

    /** "A zombie found you meditating." / "Vael found you meditating." / "Something is hurting it." */
    static String culprit(@Nullable Entity attacker) {
        if (attacker == null) return "Something is hurting it.";
        String name = attacker.getDisplayName().getString();
        if (attacker instanceof net.minecraft.world.entity.player.Player || attacker.hasCustomName()) return name + " found you meditating.";
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String article = !lower.isEmpty() && "aeiou".indexOf(lower.charAt(0)) >= 0 ? "An " : "A ";
        return article + lower + " found you meditating.";
    }

    private static String hearts(float hp) {
        float h = Math.round(hp) / 2F;
        return h == Math.floor(h) ? String.valueOf((int) h) : String.valueOf(h);
    }

    static String heartsLost(float damage) {
        float h = Math.max(0.5F, Math.round(damage) / 2F);
        if (h == 0.5F) return "Half a heart";
        String[] words = {"", "One heart", "Two hearts", "Three hearts", "Four hearts", "Five hearts", "Six hearts", "Seven hearts",
                "Eight hearts", "Nine hearts", "Ten hearts"};
        if (h == Math.floor(h) && h <= 10) return words[(int) h];
        return hearts(damage) + " hearts";
    }

    /** The Body is dying. Its owner dies with it, where it lies, as a normal death (handled on the owner's next tick). */
    static void onBodyDeath(LivingEntity body, DamageSource source) {
        Spirit s = spiritOfBody(body);
        if (s != null) s.bodyDiedSource = source;
    }

    private static void bodyDied(ServerPlayer sp, Spirit s) {
        DamageSource source = s.bodyDiedSource;
        end(sp, EndReason.BODY_DIED);
        if (source == null || !sp.isAlive()) return;
        // a Body felled by a player who may not hurt it (a friend, or anyone with PvP off) never takes its owner along
        if (source.getEntity() instanceof ServerPlayer by && !playerMayHurtBody(by, sp.getUUID())) return;
        sp.setHealth(1F);
        PENDING_DEATH.put(sp.getUUID(), new PendingDeath(source, 100));
        tryPendingDeath(sp);
    }

    /** Whether {@code attacker} may hurt the Body of {@code owner}: PvP on, not its own spirit, not in the owner's party. */
    static boolean playerMayHurtBody(ServerPlayer attacker, UUID owner) {
        if (attacker.getUUID().equals(owner)) return false;
        if (!attacker.level().isPvpAllowed()) return false;
        ServerPlayer o = attacker.level().getServer().getPlayerList().getPlayer(owner);
        return o == null || !dev.awaken.core.api.PartyHooks.sameParty(attacker, o);
    }

    /** Server stop: deaths owed by Bodies belong to the world that stopped. */
    static void clearPendingDeaths() {
        PENDING_DEATH.clear();
    }

    /** A death owed by a Body: applied as soon as the player can be hurt (not mid-teleport). */
    private record PendingDeath(DamageSource source, int ttl) {}

    private static final Map<UUID, PendingDeath> PENDING_DEATH = new HashMap<>();

    static void tryPendingDeath(ServerPlayer sp) {
        PendingDeath d = PENDING_DEATH.get(sp.getUUID());
        if (d == null) return;
        if (!sp.isAlive() || d.ttl() <= 0) {
            PENDING_DEATH.remove(sp.getUUID());
            return;
        }
        ServerLevel level = sp.level();
        if (!sp.isInvulnerableTo(level, d.source())) {
            if (!sp.hurtServer(level, d.source(), Float.MAX_VALUE) && sp.isAlive()) sp.kill(level);
        } else if (!sp.isInvulnerableTo(level, level.damageSources().genericKill())) {
            sp.kill(level);
        }
        if (!sp.isAlive()) PENDING_DEATH.remove(sp.getUUID());
        else PENDING_DEATH.put(sp.getUUID(), new PendingDeath(d.source(), d.ttl() - 1));
    }

    static boolean owesDeath(ServerPlayer sp) {
        return PENDING_DEATH.containsKey(sp.getUUID());
    }

    // ---- end --------------------------------------------------------------------------------------------------------

    /** Back into the body. Always restores the old game mode, clears the spirit look and removes the Body. */
    public static void end(ServerPlayer sp, EndReason reason) {
        Spirit s = LIVE.remove(sp.getUUID());
        if (s == null) {
            restoreFromState(sp, reason == EndReason.LOGOUT);
            return;
        }
        Possession.release(sp, s, null);
        CordEaters.clear(s);
        Spirits.forget(sp);
        ProjectionState st = sp.getData(VeilContent.PROJECTION.get());
        MinecraftServer server = sp.level().getServer();
        ServerLevel bodyLevel = server.getLevel(s.bodyDim);
        if (bodyLevel == null) bodyLevel = server.overworld();
        Vec3 pos;
        float yaw;
        float health;
        Mannequin body = s.body;
        if (body != null && !body.isRemoved()) {
            pos = body.position();
            yaw = body.getYRot();
            health = body.isAlive() ? body.getHealth() : 0F;
        } else {
            pos = st.bodyPos();
            yaw = st.bodyYaw();
            health = st.health();
        }
        if (body != null) {
            body.setData(VeilContent.BODY_TAG.get(), BodyTag.NONE);
            body.discard();
        }
        if (bodyLevel != null && body != null) bodyLevel.getChunkSource().removeTicketWithRadius(VeilContent.BODY_TICKET.get(), body.chunkPosition(), 2);

        if (reason == EndReason.LOGOUT && sp.level().dimension() != bodyLevel.dimension()) {
            // Never move a player between dimensions while they are being saved: the next login finishes the job.
            sp.setData(VeilContent.PROJECTION.get(), st.withBody(pos, yaw, health));
            return;
        }
        GameType back = st.active() ? st.previousMode() : GameType.SURVIVAL;
        if (back == GameType.SPECTATOR) back = GameType.SURVIVAL;
        final ServerLevel dest = bodyLevel;
        final GameType mode = back;
        withTransit(sp, () -> {
            sp.setCamera(sp);
            if (reason != EndReason.DEATH) teleport(sp, dest, pos, yaw, sp.getXRot());
            sp.setGameMode(mode);
        });
        if (reason != EndReason.DEATH) {
            float hp = Math.min(sp.getMaxHealth(), Math.max(reason == EndReason.BODY_DIED ? 1F : 1F, health));
            sp.setHealth(hp);
        }
        sp.resetFallDistance();
        sp.removePostEffect(SPIRIT_EFFECT);
        sp.removePostEffect(RIPPLE_EFFECT);
        sp.setData(VeilContent.PROJECTION.get(), ProjectionState.NONE);
        if (s.pendingVeilTicks > 0) Progression.count(sp, Counters.VEIL_TICKS, s.pendingVeilTicks);
        AwakenNet.send(sp, VeilPayloads.State.NONE);
        AwakenNet.send(sp, VeilPayloads.Sight.EMPTY);
        if (reason != EndReason.LOGOUT && reason != EndReason.DEATH) {
            dest.playSound(null, pos.x, pos.y, pos.z, reason == EndReason.CORD_CUT ? VeilContent.CORD_SNAP.get() : VeilContent.RETURN.get(),
                    SoundSource.PLAYERS, 0.9F, reason == EndReason.CORD_CUT ? 0.6F : 1.0F);
        }
        switch (reason) {
            case TIME -> Signal.toast(sp, Component.literal("Your focus thins. You fall back into yourself."));
            case CORD_CUT -> {
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(VeilContent.SPIRIT_SHOCK, 20 * 30, 0));
                Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Spirit Shock"))
                        .body(Component.literal("Something bit through the cord. You slam back into yourself."))
                        .footer(Component.literal("The Veil stays shut for a while.")).ttl(20 * 6).priority(8).build());
            }
            case BODY_LOST -> Signal.toast(sp, Component.literal("Your body was moved. You followed it home."));
            case DISABLED -> Signal.toast(sp, Component.literal("The Veil closed."));
            default -> {}
        }
        DirectorLog.log(sp, "veil", "returned: " + reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Puts a player back from the saved state alone (after a crash or a cross-dimension logout). */
    static void restoreFromState(ServerPlayer sp, boolean duringLogout) {
        ProjectionState st = sp.getData(VeilContent.PROJECTION.get());
        if (!st.active()) {
            sp.removePostEffect(SPIRIT_EFFECT);
            sp.removePostEffect(RIPPLE_EFFECT);
            return;
        }
        if (duringLogout) return;
        MinecraftServer server = sp.level().getServer();
        ServerLevel level = server.getLevel(st.bodyDim());
        if (level == null) level = server.overworld();
        GameType mode = st.previousMode() == GameType.SPECTATOR ? GameType.SURVIVAL : st.previousMode();
        final ServerLevel dest = level;
        withTransit(sp, () -> {
            sp.setCamera(sp);
            teleport(sp, dest, st.bodyPos(), st.bodyYaw(), sp.getXRot());
            sp.setGameMode(mode);
        });
        sp.setHealth(Math.max(1F, Math.min(sp.getMaxHealth(), st.health())));
        sp.resetFallDistance();
        sp.removePostEffect(SPIRIT_EFFECT);
        sp.removePostEffect(RIPPLE_EFFECT);
        st.bodyId().ifPresent(id -> {
            Entity e = dest.getEntity(id);
            if (e != null) e.discard();
        });
        sp.setData(VeilContent.PROJECTION.get(), ProjectionState.NONE);
        AwakenNet.send(sp, VeilPayloads.State.NONE);
        Signal.toast(sp, Component.literal("You woke up in your body."));
        DirectorLog.log(sp, "veil", "restored from saved state");
    }

    /** Same-dimension or cross-dimension move. */
    static void teleport(ServerPlayer sp, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        if (sp.level() == level) {
            sp.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true);
        } else {
            sp.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
        }
    }

    static void sendState(ServerPlayer sp, Spirit s) {
        s.dirty = false;
        boolean here = s.body != null && s.body.level() == sp.level();
        Vec3 bodyPos = here ? s.body.position().add(0, 0.9, 0) : Vec3.ZERO;
        AwakenNet.send(sp, new VeilPayloads.State(true, s.ticksLeft, s.maxTicks, s.radius, s.awareness, s.anchor, here, bodyPos,
                here ? s.body.getId() : -1, s.possessed != null ? s.possessed.getId() : -1, s.pocket != null, DreamHooks.isDreaming(sp),
                s.bodyHurtFlag, possessLevel(sp)));
        s.bodyHurtFlag = false;
    }

    /** Ends every live projection (server stopping). */
    static void endAll(MinecraftServer server) {
        for (UUID id : List.copyOf(LIVE.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) end(p, EndReason.LOGOUT);
            else LIVE.remove(id);
        }
    }
}
