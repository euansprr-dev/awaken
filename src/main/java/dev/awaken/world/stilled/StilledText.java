package dev.awaken.world.stilled;

import java.util.Locale;

/**
 * What the System says of the Stilled and what they carry (SPEC §43.3), all in plain speech, read aloud: the poses'
 * readings, the letters, the keepsakes and what a family in Carillon says when shown one.
 */
public final class StilledText {
    private StilledText() {}

    /** Who a figure is: their look's texture, how the System names them, and their pronouns. */
    public enum Look {
        MAN("man", "a man", "he", "his", "him"),
        WOMAN("woman", "a woman", "she", "her", "her"),
        OLD_MAN("old_man", "an old man", "he", "his", "him"),
        OLD_WOMAN("old_woman", "an old woman", "she", "her", "her"),
        YOUNG("young", "a girl", "she", "her", "her"),
        CHILD("child", "a child", "they", "their", "them");

        final String texture, who, he, his, him;

        Look(String texture, String who, String he, String his, String him) {
            this.texture = texture;
            this.who = who;
            this.he = he;
            this.his = his;
            this.him = him;
        }

        boolean male() {
            return this == MAN || this == OLD_MAN;
        }

        /** Its texture's name under textures/entity/stilled/. */
        public String texture() {
            return texture;
        }
    }

    /**
     * The poses (twelve, each posed by hand in tools/art/stilled.py and drawn from the rig): what the System sees. {@code {who}} is the
     * figure ("a man"), {@code {he}}/{@code {his}}/{@code {him}} their pronouns.
     */
    public enum Pose {
        MILESTONE("{Who} sitting on a milestone with {his} pack beside {him}. {He} stopped for a rest and never got up."),
        WALKING("{Who} caught mid-step, leaning into the walk with a pack on {his} back. {He} was in a hurry to get somewhere."),
        GRAVE("{Who} kneeling at a grave with one hand on the stone. {He} came back to say goodbye."),
        PAIR("Two of them sitting side by side on the tail of a cart, her head on his shoulder. They went back together."),
        LEAD("{Who} slumped back against {his} pack with a dog's lead still in {his} hand. There's no dog on the end of it."),
        REACHING("{Who} with one hand held out, reaching for something just ahead. {He} nearly made it."),
        LOOKING_BACK("{Who} standing in the road, looking back over {his} shoulder toward the city."),
        WAITING("{Who} sitting with {his} arms round {his} knees. {He} sat down to wait for someone."),
        LANTERN("{Who} holding up a lantern. {He} went out looking for someone after dark."),
        CHILD("{Who} holding a child's hand. They were going back for the child's things."),
        CURLED("{Who} lying curled on {his} side, as if {he}'d only lain down for a minute."),
        BUNDLE("{Who} sitting on a bundle of {his} things with {his} chin in {his} hands. {He} couldn't carry it any further.");

        final String reading;

        Pose(String reading) {
            this.reading = reading;
        }

        /** Two figures in one (a pair on a cart's tail, an adult and a child). */
        public boolean two() {
            return this == PAIR || this == CHILD;
        }
    }

    static String fill(String s, Look look) {
        return s.replace("{Who}", cap(look.who)).replace("{who}", look.who).replace("{He}", cap(look.he)).replace("{he}", look.he)
                .replace("{His}", cap(look.his)).replace("{his}", look.his).replace("{him}", look.him);
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- letters ------------------------------------------------------------------------------------------------

    /** A letter: who it's from (the book's author), and its words ({@code {place}}: the settlement it names). */
    record Letter(String from, String text) {}

    /** Aldo's note to Ilse: always the first letter on the north road. */
    static final int ALDO = 0;

    static final Letter[] LETTERS = {
            new Letter("Aldo", "Ilse,\n\nI've gone back for Bess. She wouldn't come when I whistled, and I can't leave her out there on her own.\n\n"
                    + "I'll be back before the kettle boils.\n\nAldo"),
            new Letter("Meg", "Tom,\n\nThe chime at {place} stopped ringing on Tuesday. Mam says we're going to the city in the morning.\n\n"
                    + "I've left your good boots by the door in case you come back for them.\n\nYour sister,\nMeg"),
            new Letter("Annie", "To whoever finds this,\n\nI'm going back to {place} for my mother's ring. It's in the blue jug on the shelf.\n\n"
                    + "If I don't come back, please tell my husband I'm sorry. His name is Will. He's in the city.\n\nAnnie"),
            new Letter("Ness", "Dear Robin,\n\nI know you said not to go back. But the goats are still in the top field at {place}, "
                    + "and nobody let them out. I'll be quick.\n\nLove,\nNess"),
            new Letter("Jen", "Pa,\n\nWe got to the city. It's loud, and the bells ring all night, but we're all right.\n\n"
                    + "Please come. Don't stay at {place} on your own.\n\nJen"),
            new Letter("Dad", "Harry,\n\nThe last cart for the city goes from {place} at first light. If you're reading this, you've missed it.\n\n"
                    + "Follow the road and keep walking. Whatever you do, don't sit down.\n\nDad"),
            new Letter("Hob", "To the elder at {place},\n\nThe chime's cracked and I can't mend it before winter. People are starting to "
                    + "fall asleep in the daytime.\n\nI think we should go while we still can.\n\nOld Hob, the bellwright"),
            new Letter("Ma", "Kit,\n\nI'm going back for the seed potatoes. We'll need them in the spring, wherever we end up.\n\n"
                    + "Wait for me at the last fire.\n\nMa"),
            new Letter("Ellis", "My love,\n\nI've left your father's watch on the windowsill at {place}. I'll be there and back by dark.\n\n"
                    + "Keep the children close to the bells.\n\nEllis"),
            new Letter("your brother", "Sam,\n\nThe bell at {place} was still ringing on Sundays when I left. If it's still ringing, "
                    + "tell them I'm coming home.\n\nYour brother"),
            new Letter("Rosie", "To anyone,\n\nMy dog Pip ran off towards {place} when the carts left. She's brown with one white ear.\n\n"
                    + "If you find her, she likes cheese.\n\nRosie, age 9"),
            new Letter("George", "Martha,\n\nI couldn't leave the hives. Somebody has to take them their sugar for the winter.\n\n"
                    + "Don't be cross with me.\n\nGeorge"),
            new Letter("Annie", "Dear Ruth,\n\nWe buried Grandad by the chapel at {place}. I'm going back to put flowers on him before "
                    + "the snow comes. I won't be long.\n\nYour Annie"),
            new Letter("your old man", "Lad,\n\nIf you're reading this, you came looking for me. Go back to the city.\n\n"
                    + "There's nothing out here worth sitting down for.\n\nYour old man"),
    };

    static String letter(int n, String place) {
        return LETTERS[Math.floorMod(n, LETTERS.length)].text.replace("{place}", place.isEmpty() ? "home" : place);
    }

    // ---- keepsakes ----------------------------------------------------------------------------------------------

    /** A keepsake: what it is, and why its owner went back (said by their family; {@code {they}}/{@code {their}}). */
    record Keepsake(String thing, String why) {}

    static final Keepsake[] KEEPSAKES = {
            new Keepsake("dog whistle", "{They} went back for the dog. I used to hear {them} whistling her in from the top field every night."),
            new Keepsake("wedding ring", "{They} went back for the other one. {They} never could stand the two of them being apart."),
            new Keepsake("christening spoon", "That was mine, when I was a baby. {They} went back for the rest of the silver."),
            new Keepsake("carved wooden horse", "{They} carved that for me when I was small. {They} went back for the rest of my toys, "
                    + "because I cried the whole way here."),
            new Keepsake("pocket watch", "{They} wound it every night at nine. {They} went back for the clock that matched it."),
            new Keepsake("hair ribbon", "{They} wore a ribbon like that every Sunday. I'd know it anywhere."),
            new Keepsake("tobacco tin", "I'd know that tin anywhere. {They} went back for {their} pipe, of all things."),
            new Keepsake("bone comb", "{They} combed my hair with that every morning before school."),
            new Keepsake("brass button", "That's off {their} good coat. {They} went back for the coat because it was cold."),
            new Keepsake("tin soldier", "That's from my set. {They} went back for the rest of them. I told {them} not to."),
    };

    /** Who the keepsake's owner was to the family, by the figure's look: their word and their pronouns. */
    static String[] relation(Look look, long seed) {
        String[][] men = {{"dad", "he", "his", "him"}, {"brother", "he", "his", "him"}, {"uncle", "he", "his", "him"}};
        String[][] women = {{"mam", "she", "her", "her"}, {"sister", "she", "her", "her"}, {"aunt", "she", "her", "her"}};
        if (look == Look.OLD_MAN) return new String[]{"grandad", "he", "his", "him"};
        if (look == Look.OLD_WOMAN) return new String[]{"gran", "she", "her", "her"};
        String[][] pool = look.male() ? men : women;
        return pool[(int) Math.floorMod(seed, (long) pool.length)];
    }

    /** A relation's word back to its pronouns ("dad": he, his, him). */
    static String[] relationWord(String word) {
        boolean she = switch (word) {
            case "mam", "sister", "aunt", "gran" -> true;
            default -> false;
        };
        return she ? new String[]{word, "she", "her", "her"} : new String[]{word, "he", "his", "him"};
    }

    /** "to the north-east": the way along (dx, dz) (north is -z). */
    static String bearing(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        int k = (int) Math.floorMod(Math.round(a / 45.0), 8L);
        return new String[]{"to the north", "to the north-east", "to the east", "to the south-east", "to the south", "to the south-west",
                "to the west", "to the north-west"}[k];
    }

    /** What a family member says, shown the keepsake: once, in plain words; you keep it. */
    static String reaction(String given, int keepsake, String[] rel) {
        Keepsake k = KEEPSAKES[Math.floorMod(keepsake, KEEPSAKES.length)];
        String why = k.why.replace("{They}", cap(rel[1])).replace("{they}", rel[1]).replace("{their}", rel[2]).replace("{them}", rel[3]);
        return given + ": Where did you find that? That's my " + rel[0] + "'s " + k.thing + ". " + why
                + " Thank you for bringing it this far. You keep it. It should be out there with someone who's still walking.";
    }

    static String family(String surname) {
        return surname.toUpperCase(Locale.ROOT);
    }

    /** "the Underhills" (the family, said plainly). */
    static String plural(String surname) {
        if (surname.endsWith("s") || surname.endsWith("x") || surname.endsWith("ch") || surname.endsWith("sh")) return "the " + surname + "es";
        return "the " + surname + "s";
    }
}
