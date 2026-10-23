package dev.awaken.progression;

import dev.awaken.core.PlayDays;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Detectors for Latents that come from the ordinary world (lightning, lava, the noon sun...). They set flag counters;
 * the Latent definitions in data decide what those flags unlock. Nothing here is ever hinted in the UI.
 */
final class WorldLatents {
    private WorldLatents() {}

    private static final class Track {
        int hungrySprintTicks;
        int lavaTicks;
        boolean wasDrowning;
        boolean wasFullyFrozen;
        int sunStareTicks;
        double fallStartY = Double.NaN;
        boolean creeperArmed;
        long lastNightChecked = -1;
        boolean sleptThisNight;
    }

    private static final Map<UUID, Track> TRACK = new HashMap<>();
    private static final Set<UUID> SLEPT = new HashSet<>();

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(WorldLatents::onTick);
        bus.addListener(WorldLatents::onLightning);
        bus.addListener(WorldLatents::onDamage);
        bus.addListener(WorldLatents::onDeath);
        bus.addListener(WorldLatents::onWake);
        PlayDays.onMorning((server, day) -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!SLEPT.remove(p.getUUID()) && Progression.profile(p).awakened()) {
                    Progression.count(p, "awaken:nights_awake");
                    if (p.level().dimension() == Level.OVERWORLD && p.getY() < 0) {
                        Progression.count(p, "awaken:latent/nights_awake_underground");
                    }
                }
            }
        });
    }

    private static Track track(ServerPlayer p) {
        return TRACK.computeIfAbsent(p.getUUID(), k -> new Track());
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !Progression.profile(p).awakened()) return;
        Track t = track(p);

        // Second Wind: sprint for two straight minutes while hungry.
        if (p.isSprinting() && p.getFoodData().getFoodLevel() <= 8) {
            if (++t.hungrySprintTicks == 2400) flag(p, "sprint_starving");
        } else {
            t.hungrySprintTicks = 0;
        }

        // Ember Heart: three seconds in lava, then out alive.
        if (p.isInLava()) {
            t.lavaTicks++;
        } else {
            if (t.lavaTicks >= 60 && p.isAlive()) flag(p, "survived_lava");
            t.lavaTicks = 0;
        }

        // Iron Lungs: run out of air, then surface alive.
        if (p.getAirSupply() <= 0) {
            t.wasDrowning = true;
        } else if (t.wasDrowning && p.getAirSupply() >= p.getMaxAirSupply() - 20) {
            t.wasDrowning = false;
            Progression.count(p, "awaken:latent/near_drowned");
        }

        // Frost Blood: freeze solid, then climb out.
        if (p.isFullyFrozen()) {
            t.wasFullyFrozen = true;
        } else if (t.wasFullyFrozen && p.getTicksFrozen() == 0) {
            t.wasFullyFrozen = false;
            flag(p, "escaped_freezing");
        }

        // Featherfall: fall from near the build limit into water.
        if (!p.onGround() && p.getDeltaMovement().y < -0.5) {
            if (Double.isNaN(t.fallStartY)) t.fallStartY = p.getY();
            if (p.isInWater() && t.fallStartY >= p.level().getMaxY() - 24 && p.level().dimension() == Level.OVERWORLD) {
                flag(p, "fell_from_height_limit_into_water");
            }
        } else if (p.onGround() || p.isInWater()) {
            t.fallStartY = Double.NaN;
        }

        // Sun Eyes: stare at the noon sun from a mountaintop for a minute.
        long timeOfDay = p.level().getOverworldClockTime() % PlayDays.DAY;
        boolean noon = timeOfDay > 5000 && timeOfDay < 7000;
        if (noon && p.getXRot() < -75 && p.getY() > 150 && p.level().canSeeSky(p.blockPosition()) && p.level().dimension() == Level.OVERWORLD) {
            if (++t.sunStareTicks == 1200) flag(p, "stared_at_sun");
        } else {
            t.sunStareTicks = 0;
        }
    }

    private static void onLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Progression.profile(p).awakened()
                && (p.getMainHandItem().is(Items.COPPER_INGOT) || Items.LIGHTNING_ROD.asList().contains(p.getMainHandItem().getItem()))) {
            flag(p, "struck_holding_copper");
        }
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        // Blast Skin: a creeper explodes on you at full health and you live.
        if (event.getSource().getDirectEntity() instanceof Creeper && p.getHealth() >= p.getMaxHealth() - 0.01f) {
            track(p).creeperArmed = true;
            p.level().getServer().execute(() -> {
                if (p.isAlive() && track(p).creeperArmed) flag(p, "creeper_point_blank_survived");
                track(p).creeperArmed = false;
            });
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        // Beast Tongue: your wolf dies to a monster while protecting you.
        if (dead instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner
                && event.getSource().getEntity() instanceof Enemy && pet.distanceTo(owner) < 16) {
            flag(owner, "wolf_died_protecting");
        }
        // Night kills for directives.
        if (event.getSource().getEntity() instanceof ServerPlayer p && dead instanceof Enemy
                && p.level().dimension() == Level.OVERWORLD) {
            long tod = p.level().getOverworldClockTime() % PlayDays.DAY;
            if (tod > 13000 && tod < 23000) Progression.count(p, "awaken:kills_overworld_night");
        }
        if (dead instanceof ServerPlayer p) track(p).creeperArmed = false;
    }

    private static void onWake(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) SLEPT.add(p.getUUID());
    }

    static void flag(ServerPlayer p, String latent) {
        String id = "awaken:latent/" + latent;
        if (Progression.profile(p).counter(id) == 0) Progression.count(p, id);
    }
}
