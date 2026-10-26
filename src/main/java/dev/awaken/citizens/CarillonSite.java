package dev.awaken.citizens;

import dev.awaken.city.CityPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Carillon's ground, read from its plan placed at (cx, cz) with the street at baseY (the world's city, or the plan
 * borrowed by a test). Its districts are the plan's (1 market .. 7 wallring) and map to the event tags: fires break out
 * in the Works' houses, carts spill on the Quay's lanes, pickpockets work the Market (SPEC §16.5, as before).
 */
final class CarillonSite implements Site {
    private final int cx, cz, baseY;
    private final CityPlan plan = CityPlan.get();
    private List<Lot> fireLots;

    CarillonSite(int cx, int cz, int baseY) {
        this.cx = cx;
        this.cz = cz;
        this.baseY = baseY;
    }

    @Override
    public int cx() {
        return cx;
    }

    @Override
    public int cz() {
        return cz;
    }

    @Override
    public int baseY() {
        return baseY;
    }

    @Override
    public boolean carillon() {
        return true;
    }

    @Override
    public boolean inside(int x, int z) {
        return plan.inside(x - cx, z - cz);
    }

    @Override
    public int district(int x, int z) {
        return plan.district(x - cx, z - cz);
    }

    @Override
    public int districts() {
        return CityPlan.DISTRICTS.length;
    }

    @Override
    public String districtName(int id) {
        return Recognition.districtName(id);
    }

    @Override
    public int districtFor(String tag, int x, int z) {
        return switch (tag) {
            case FIRE -> CityPlan.WORKS;
            case CART -> CityPlan.QUAY_D;
            case STALL -> CityPlan.MARKET;
            default -> district(x, z);
        };
    }

    @Override
    public int ground(int x, int z) {
        return baseY + plan.lift(x - cx, z - cz);
    }

    @Override
    public boolean walkable(int x, int z) {
        return CityPlan.walkable(plan.kind(x - cx, z - cz));
    }

    @Override
    public boolean water(int x, int z) {
        return CityPlan.water(plan.kind(x - cx, z - cz));
    }

    @Override
    public List<Lot> lots(String tag) {
        if (!FIRE.equals(tag)) return List.of();
        if (fireLots == null) {
            // a Works house with a door nobody walks into (the enterable ones are the run's and the shops')
            List<Lot> out = new ArrayList<>();
            for (CityPlan.Plot p : plan.plots) {
                if (p.district() != CityPlan.WORKS || !p.use().equals("house") || !p.hasDoor() || p.enter()) continue;
                out.add(new Lot(p.id(), p.x0(), p.z0(), p.x1(), p.z1(), p.top(), p.doorX(), p.doorZ()));
            }
            fireLots = List.copyOf(out);
        }
        return fireLots;
    }

    @Override
    public boolean tagged(String tag, double x, double z) {
        return CART.equals(tag) && plan.district((int) Math.floor(x - cx), (int) Math.floor(z - cz)) == CityPlan.QUAY_D;
    }
}
