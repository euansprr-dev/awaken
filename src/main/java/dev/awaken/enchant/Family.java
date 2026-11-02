package dev.awaken.enchant;

/**
 * What an enchantment may add (SPEC §45.3): each family has a colour, a kind of mark and a voice, used wherever the
 * enchantment shows (the page, the book, the item, the Journal, its work in the world).
 */
public enum Family {
    /** What armour guards against: closed shapes and rings; each Force's own colour, steel for the rest. */
    WARD("Wards", 0xB9C3D2),
    /** Which kind of foe a weapon is deadly to: points and spears, blood red. */
    BANE("Banes", 0xFF5F63),
    /** What you get from what you break or kill: open cups, gold. */
    SPOILS("Work and spoils", 0xF2C45A),
    /** Where it carries you: waves, sea blue. */
    CARRYING("Carrying", 0x4FB5EA),
    /** How it keeps itself: knots, warm white. */
    KEEPING("Keeping", 0xF3EAD6);

    public final String label;
    public final int colour;

    Family(String label, int colour) {
        this.label = label;
        this.colour = colour;
    }
}
