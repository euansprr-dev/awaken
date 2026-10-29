package dev.awaken.armour;

/** The four ways to fight a set is made for, and the Lord-forged sets (SPEC §44.2). */
public enum Archetype {
    BULWARK("Bulwark"),
    STRIDER("Strider"),
    CHANNELER("Channeler"),
    DUELIST("Duelist"),
    LORD("Lord-forged");

    public final String label;

    Archetype(String label) {
        this.label = label;
    }
}
