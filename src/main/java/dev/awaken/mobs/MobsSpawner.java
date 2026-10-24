package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Rosters;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.def.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The real {@link MobHooks.Spawner}: theme ids to rosters, boss ids to bosses. Roles pick from the roster (a normal at
 * random, the theme's elite, its Warden, its Lord); a roster without a designed elite or Warden upgrades a normal
 * creature into that role (bigger, tougher, a bar for Wardens). Unknown ids never fail: they fall back to a
 * deterministic gate theme or Gate Lord.
 */
public final class MobsSpawner implements MobHooks.Spawner {
    private static final List<MobDef> LORDS = new ArrayList<>();

    static {
        for (MobDef d : Bestiary.all()) if (d.tier == Tier.LORD) LORDS.add(d);
    }

    @Override
    public @Nullable LivingEntity spawnThemed(ServerLevel level, String theme, MobRole.Role role, BlockPos pos, int contentLevel, Set<Forces.Tag> tags, String gateId) {
        Rosters.Roster r = Rosters.forTheme(theme);
        if (Rosters.exact(theme) == null) Awaken.LOG.debug("[mobs] unknown theme {} -> {}", theme, r.id());
        // Nightmare templates are always nightmares, whatever role the caller passed.
        Pick pick = pick(r, r.theme() == Theme.FEAR ? MobRole.Role.NIGHTMARE : role, level.getRandom());
        EnumSet<Forces.Tag> all = EnumSet.noneOf(Forces.Tag.class);
        all.addAll(tags);
        all.addAll(r.tags());
        String themeId = theme == null || theme.isEmpty() ? r.id() : theme;
        MobRole mr = new MobRole(pick.role, contentLevel, themeId, pick.role == MobRole.Role.BOSS ? Optional.of(pick.def.bossId()) : Optional.empty(), gateId == null ? "" : gateId);
        return Mobs.spawn(level, pick.def, Vec3.atBottomCenterOf(pos), contentLevel, mr, all);
    }

    @Override
    public java.util.Optional<java.util.UUID> bondedTo(net.minecraft.world.entity.Entity e) {
        return e instanceof dev.awaken.mobs.entity.WildMount m ? java.util.Optional.ofNullable(m.owner()) : java.util.Optional.empty();
    }

    @Override
    public boolean peaceful(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof dev.awaken.mobs.entity.FaunaMob f)) return false;
        var r = f.faunaRole();
        return (r == dev.awaken.mobs.def.FaunaRole.AMBIENT || r == dev.awaken.mobs.def.FaunaRole.NEUTRAL) && f.getTarget() == null;
    }

    @Override
    public @Nullable LivingEntity spawnBoss(ServerLevel level, String bossId, BlockPos pos, int contentLevel, String gateId) {
        MobDef d = bossDef(bossId);
        MobRole.Role role = d.tier.role == MobRole.Role.NORMAL || d.tier.role == MobRole.Role.ELITE ? MobRole.Role.BOSS : d.tier.role;
        MobRole mr = new MobRole(role, contentLevel, d.theme.id, Optional.of(bossId == null ? d.bossId() : bossId), gateId == null ? "" : gateId);
        return Mobs.spawn(level, d, Vec3.atBottomCenterOf(pos), contentLevel, mr, Set.of());
    }

    /** Resolves a boss id ("awaken:bell_warden", "bell_warden", "awaken:nightmare/falling"); unknown ids map to a Lord. */
    public static MobDef bossDef(@Nullable String bossId) {
        if (bossId != null && bossId.startsWith("awaken:nightmare/")) {
            MobDef d = Bestiary.get("nightmare_" + bossId.substring("awaken:nightmare/".length()));
            return d != null ? d : Bestiary.get("nightmare_generic");
        }
        MobDef d = Bestiary.byAnyId(bossId);
        if (d != null) return d;
        Awaken.LOG.warn("[mobs] unknown boss id {}, using a Gate Lord", bossId);
        return LORDS.get(Math.floorMod(bossId == null ? 0 : bossId.hashCode(), LORDS.size()));
    }

    record Pick(MobDef def, MobRole.Role role) {}

    static Pick pick(Rosters.Roster r, MobRole.Role role, RandomSource random) {
        List<MobDef> normals = r.normals();
        MobDef normal = normals.get(random.nextInt(normals.size()));
        return switch (role) {
            case ELITE -> new Pick(r.elite() != null ? r.elite() : normal, MobRole.Role.ELITE);
            case WARDEN -> new Pick(r.warden() != null ? r.warden() : r.elite() != null ? r.elite() : normal, MobRole.Role.WARDEN);
            case BOSS -> new Pick(r.boss() != null ? r.boss() : LORDS.get(Math.floorMod(r.id().hashCode(), LORDS.size())), MobRole.Role.BOSS);
            // a broken gate's Lord walks out: the theme's Lord body, with the gates' boss bar instead of its own
            case BREAK -> new Pick(r.boss() != null ? r.boss() : LORDS.get(Math.floorMod(r.id().hashCode(), LORDS.size())), MobRole.Role.BREAK);
            case NIGHTMARE -> {
                MobDef n = r.theme() == Theme.FEAR ? normal : Bestiary.get("nightmare_generic");
                yield new Pick(n, MobRole.Role.NIGHTMARE);
            }
            case NONE -> new Pick(normal, MobRole.Role.NORMAL);
            default -> new Pick(normal, role);
        };
    }
}
