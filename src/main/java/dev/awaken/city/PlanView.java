package dev.awaken.city;

/**
 * What the building grammar reads of the ground round a plot (SPEC §28.6): the kind of each cell (CityPlan's ground
 * kinds), its height over the street level, which plot stands on it, and where trees and lamps stand. Carillon's plan
 * is one ({@link CityPlan}); a settlement's planned raster is another (the village module's plans), so the same
 * grammar paints a village's houses with their faces, holds and roofs. Plan coordinates: blocks from the origin, x east,
 * z south; heights relative to the origin's street level. Pure, and safe from any thread.
 */
public interface PlanView {
    /** The cell's ground kind ({@link CityPlan#VOID} outside). */
    byte kind(int x, int z);

    /** The ground's top block at a cell, over the street level (signed). */
    int lift(int x, int z);

    /** The id of the plot on this cell, or -1. */
    int plotAt(int x, int z);

    /** A plot by its id (ids are the plots' indices). */
    CityPlan.Plot plot(int id);

    /** Whether a tree's trunk stands in this cell (a facade keeps its lines clear of it). */
    boolean treeAt(int x, int z);

    /** Whether a street lamp stands within a block of this cell (a sign bracket keeps clear of it). */
    default boolean lampNear(int x, int z) {
        return false;
    }
}
