package dev.awaken.citizens.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * A settlement as other modules see it (SPEC §28.4): Carillon is id 0, the others are what W3 registers. Read on the
 * server thread; nothing here allocates per call except the station lists.
 */
public interface Settlement {
    int id();

    String key();

    String name();

    BlockPos origin();

    /** Whether a position belongs to it (Carillon: inside the wall; others: within their radius). */
    boolean inside(BlockPos pos);

    /** Its citizens (event actors not counted). */
    int people();

    /** Its people with a body near a player now (out of the world pool all settlements share). */
    int bodies();

    /** Ticking its day now (Carillon always; others while a player is near). */
    boolean awake();

    /**
     * How it knows a player: {@code RenownHooks.STRANGER}, {@code FAMILIAR} or {@code KNOWN} (its own memory: each
     * citizen's familiarity, and how many of its people know them; a small place needs fewer).
     */
    int standing(Player player);

    /**
     * Where its places of a kind are (world positions, feet): a place kind ({@code door}, {@code anvil}, {@code wash},
     * {@code stall_keeper}, {@code bell}, {@code tavern_out}, ...; Roster.PLACE_KINDS) or a lot tag ({@code house},
     * {@code fire}, ...: the lot's door).
     */
    List<BlockPos> stations(String kind);

    /** What people call the part of it a position lies in (its own district names). */
    String district(BlockPos pos);

    /** How many records it has (its citizens, then its event actors). */
    int records();

    /** A citizen's trade (Roster.JOBS: "smith", "farmer", ...), "" for an event actor. */
    String job(int rec);

    /** A citizen's name ("given family"). */
    String personName(int rec);

    /** A citizen's age class: 0 a child, 1 young, 2 grown, 3 old. */
    int age(int rec);

    /** The citizen's body near a player now, or null. */
    net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity body(int rec);

    /** A citizen's body says a line to one player (over its head, as a greeting is). */
    void say(net.minecraft.server.level.ServerPlayer to, int rec, String line, int ticks);

    /**
     * Every one of its people knows a player a little better, up to a ceiling (a request filled, a trade: SPEC §28.6). Their
     * greetings follow (familiar at 20, known at 55).
     */
    void acquaint(net.minecraft.server.level.ServerPlayer p, int amount, int ceiling);

    // ---- (SPEC §28.7, W4) a village's troubles and festivals stage its people -------------------------------------

    /**
     * A citizen staged for a trouble or a festival: walks to {@code at} (a body far from it, or none yet, is put there)
     * and stands facing {@code yaw} (NaN: as they stand), doing {@code act} once there (a {@code citizens.Act} name in
     * lower case: "point_shout", "bucket_pass", "cheer", "dance", "embrace", "halberd_stand", "hammer", "sit", ...) with
     * {@code prop} in hand ("lantern", "bucket", "halberd", "hammer", "trowel", "bow", "pick", "hoe", ""). Call again to
     * move them; {@code at} null hands them back to their day. Lapses if not renewed for 30 s (a stopped trouble never
     * leaves anyone standing).
     */
    void stage(int rec, net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 at, float yaw, String act, String prop);

    /**
     * A citizen away from the settlement's life ({@code away} true: taken, gone to Carillon, out on the land; no body, no
     * day) or back in it (false: where this hour wants them). Saved with the settlement.
     */
    void away(int rec, boolean away);

    boolean isAway(int rec);

    /** The alarm on the bell nearest a place: fast strokes heard to 256 blocks, its ringer at the rope. */
    void alarm(BlockPos near, int strokes);

    /** Where a citizen is now (their body, else where the ledger has them out of doors), or null (inside, away). */
    net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 where(int rec);

    /** The skin a citizen wears (a texture path under {@code awaken}, and whether it is the slim build). */
    record Look(net.minecraft.resources.Identifier texture, boolean slim) {}

    Look look(int rec);
}
