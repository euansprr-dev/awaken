package dev.awaken.village;

import dev.awaken.city.VillageStyles;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The {@link Villages} API over the running settlements and the world's records (SPEC §12, §28.6). */
final class VillagesAccess implements Villages.Access {
    static Villages.View view(VillageService.Live l) {
        VillagePlan p = l.plan();
        return new Villages.View(p.key, p.name, p.kind.name().toLowerCase(Locale.ROOT), VillageStyles.FAMILIES[p.family], new BlockPos(p.ox, p.baseY, p.oz),
                p.radius(), l.settlement().id(), l.settlement().people());
    }

    @Override
    public Optional<Villages.View> settlementAt(Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        VillageService.Live l = VillageService.at(level, pos);
        return l == null ? Optional.empty() : Optional.of(view(l));
    }

    @Override
    public Optional<Villages.View> nearest(Level level, BlockPos pos, int within) {
        if (level.dimension() != Level.OVERWORLD || level.getServer() == null) return Optional.empty();
        Villages.View best = null;
        double bd = within;
        for (VillageService.Live l : VillageService.all()) {
            double d = Math.hypot(l.plan().ox - pos.getX(), l.plan().oz - pos.getZ());
            if (d < bd) {
                bd = d;
                best = view(l);
            }
        }
        for (VillageState.Village v : VillageState.get(level.getServer()).villages.values()) {
            BlockPos o = BlockPos.of(v.origin);
            double d = Math.hypot(o.getX() - pos.getX(), o.getZ() - pos.getZ());
            if (d < bd) {
                bd = d;
                best = new Villages.View(v.key, v.name, v.kind == 1 ? "village" : "hamlet", VillageStyles.FAMILIES[Math.clamp(v.family, 0, 9)], o, v.radius, -1, 0);
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public List<Villages.View> all(Level level) {
        List<Villages.View> out = new ArrayList<>();
        for (VillageService.Live l : VillageService.all()) out.add(view(l));
        return out;
    }

    @Override
    public int standing(ServerPlayer p, String key) {
        VillageService.Live l = VillageService.LIVE.get(key);
        if (l != null) return VillageService.tier(p, l);
        VillageState.Village v = VillageState.get(p.level().getServer()).villages.get(key);
        return v == null ? Villages.STRANGER : VillageState.tier(v.points(p.getUUID()));
    }

    @Override
    public void addStanding(ServerPlayer p, String key, int points, String why) {
        VillageService.Live l = VillageService.LIVE.get(key);
        if (l != null) VillageService.standing(p, l, points, why);
    }

    @Override
    public List<Villages.Damage> damage(MinecraftServer server, String key) {
        VillageState.Village v = VillageState.get(server).villages.get(key);
        if (v == null) return List.of();
        List<Villages.Damage> out = new ArrayList<>();
        for (VillageState.Damage d : v.damage) out.add(new Villages.Damage(BlockPos.of(d.pos()), d.original(), d.day()));
        return out;
    }

    @Override
    public void repaired(MinecraftServer server, String key, BlockPos pos) {
        VillageState st = VillageState.get(server);
        VillageState.Village v = st.villages.get(key);
        if (v != null && v.damage.removeIf(d -> d.pos() == pos.asLong())) st.setDirty();
    }

    @Override
    public List<String> history(MinecraftServer server, String key) {
        VillageState.Village v = VillageState.get(server).villages.get(key);
        if (v == null) return List.of();
        List<String> out = new ArrayList<>();
        for (VillageState.History h : v.history) out.add("Day " + h.day() + ": " + h.text());
        return out;
    }

    @Override
    public void history(MinecraftServer server, String key, String line) {
        VillageState st = VillageState.get(server);
        VillageState.Village v = st.villages.get(key);
        if (v == null) return;
        v.history.add(new VillageState.History(VillageService.today(server), line));
        while (v.history.size() > 32) v.history.removeFirst();
        st.setDirty();
    }

    @Override
    public int prosperity(MinecraftServer server, String key) {
        VillageState.Village v = VillageState.get(server).villages.get(key);
        return v == null ? 0 : v.prosperity;
    }

    @Override
    public void addProsperity(MinecraftServer server, String key, int delta) {
        VillageState st = VillageState.get(server);
        VillageState.Village v = st.villages.get(key);
        if (v == null) return;
        v.prosperity = Math.clamp(v.prosperity + delta, 0, 100);
        st.setDirty();
    }

    @Override
    public int mood(MinecraftServer server, String key) {
        VillageState.Village v = VillageState.get(server).villages.get(key);
        return v == null ? 0 : v.mood;
    }

    @Override
    public void addMood(MinecraftServer server, String key, int delta) {
        VillageState st = VillageState.get(server);
        VillageState.Village v = st.villages.get(key);
        if (v == null) return;
        v.mood = Math.clamp(v.mood + delta, 0, 100);
        st.setDirty();
    }

    /**
     * Planned or met settlements' ground (radius plus margin). A settlement not yet planned (none near any player: the
     * service plans every anchor within 420 of one) isn't reserved; a gate that opens by one never carves into its
     * built ground anyway (gates' own rule).
     */
    @Override
    public boolean reserved(Level level, BlockPos pos, int margin) {
        if (level.dimension() != Level.OVERWORLD || level.getServer() == null || !VillageService.enabled(level.getServer())) return false;
        long seed = level.getServer().overworld().getSeed();
        // a town or wayhouse whose site isn't found yet has no plan yet either: never waits for the search
        for (SettlementGrid.Anchor a : SettlementGrid.near(seed, pos.getX(), pos.getZ(), VillagePlannerReach.FAR + margin, SettlementGrid.centre(), false)) {
            VillagePlan p = Plans.cached(a.key());
            if (p != null) {
                if (Math.hypot(p.ox - pos.getX(), p.oz - pos.getZ()) <= p.radius() + margin) return true;
            }
        }
        return false;
    }

    @Override
    public List<Villages.Empty> emptyNear(net.minecraft.server.level.ServerLevel level, BlockPos pos, int within) {
        if (level.dimension() != Level.OVERWORLD || !VillageService.enabled(level.getServer())) return List.of();
        List<SettlementGrid.Anchor> near = new ArrayList<>(SettlementGrid.near(level.getSeed(), pos.getX(), pos.getZ(), within, SettlementGrid.centre(), false));
        near.removeIf(a -> dev.awaken.village.road.Holdfasts.isKey(a.key()));
        near.sort(java.util.Comparator.comparingDouble(a -> Math.hypot(a.x() - pos.getX(), a.z() - pos.getZ())));
        List<Villages.Empty> out = new ArrayList<>(near.size());
        for (SettlementGrid.Anchor a : near) {
            VillagePlan p = Plans.cached(a.key());
            if (p == null && !Plans.known(a.key())) VillageService.planSoon(level, a);
            out.add(new Villages.Empty(a.key(), new BlockPos(a.x(), 0, a.z()), p == null ? null : p.name, p == null ? null : p.ground(p.well[0], p.well[1])));
        }
        return out;
    }

    /** The furthest a settlement's ground reaches from its anchor (search 32, then up to 88 out from its middle). */
    static final class VillagePlannerReach {
        static final int FAR = 124;
    }
}
