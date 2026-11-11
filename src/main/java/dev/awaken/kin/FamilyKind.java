package dev.awaken.kin;

/** The two of the Hearth's family (SPEC §69): Aya the cat and Dubu the dog. */
public enum FamilyKind {
    AYA("aya", "Aya", "Cat", "her", "she"),
    DUBU("dubu", "Dubu", "Dog", "him", "he");

    /** Its word in commands, textures and saves; the name it comes with; what it is (its entity's name). */
    public final String id, name, sort, them, they;

    FamilyKind(String id, String name, String sort, String them, String they) {
        this.id = id;
        this.name = name;
        this.sort = sort;
        this.them = them;
        this.they = they;
    }

    public static FamilyKind of(String id) {
        return DUBU.id.equals(id) ? DUBU : AYA;
    }
}
