package dev.awaken.hearth.room;

/** Rooms the Hearth recognises from what an enclosed space holds. No menus: build it and it counts. */
public enum RoomType {
    SANCTUARY("Sanctuary", "a bed and a Meditation Mat", "better dreams, faster healing"),
    ARMORY("Armory", "an anvil, a Forge and an armour stand", "the Forge can temper higher"),
    TROPHY_HALL("Trophy Hall", "a mounted trophy", "a bonus for every different boss"),
    WORKSHOP("Workshop", "a Precursor machine", "a place for the Above's machines"),
    BARRACKS("Barracks", "a role pad", "Echoes on duty rest and train better");

    public final String label;
    public final String needs;
    public final String gives;

    RoomType(String label, String needs, String gives) {
        this.label = label;
        this.needs = needs;
        this.gives = gives;
    }
}
