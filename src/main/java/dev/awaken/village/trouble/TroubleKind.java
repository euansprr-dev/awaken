package dev.awaken.village.trouble;

import org.jspecify.annotations.Nullable;

/** The launch set (SPEC §28.7, PLAN 6.3), the Deep Mine waiting for W7's mining towns. The festival runs through the same director. */
public enum TroubleKind {
    TAKEN("taken", "Taken"),
    BURNING("burning", "The Burning"),
    REBUILD("rebuild", "The Rebuild"),
    BELL("bell", "The Bell"),
    MISSING("missing", "Gone Missing"),
    GREY_FIELDS("grey_fields", "The Grey Fields"),
    FESTIVAL("festival", "The Festival");

    public final String id, title;

    TroubleKind(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public static @Nullable TroubleKind of(String id) {
        for (TroubleKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
