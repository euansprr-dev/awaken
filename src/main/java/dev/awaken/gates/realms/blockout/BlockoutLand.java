package dev.awaken.gates.realms.blockout;

import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.SheetLand;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A blockout drawn as it is, before a gate has any detail (SPEC §50.7): each role in one plain, mid-toned block that
 * can't be mistaken for another, so the shapes and the light on them can be judged from the painter's camera. Land
 * program {@code awaken:blockout}; a blueprint that uses it has no ranks, so the game never rolls it.
 */
public final class BlockoutLand extends SheetLand<Mats> {
    /** One block per role, in {@link Blockout.Role}'s order: moss, stone, pale sandstone, terracotta, purpur, planks, water, shroomlight, leaves. */
    private static final BlockState[] BLOCK = {
            null,
            Blocks.MOSS_BLOCK.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.TERRACOTTA.defaultBlockState(),
            Blocks.PURPUR_BLOCK.defaultBlockState(),
            Blocks.OAK_PLANKS.defaultBlockState(),
            Blocks.WATER.defaultBlockState(),
            Blocks.SHROOMLIGHT.defaultBlockState(),
            // persistent, or the game would wither a crown that has no log in it
            Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)};

    private static final ThreadLocal<byte[]> COLUMN = ThreadLocal.withInitial(() -> new byte[V.H]);
    private Mats mats;

    /** The blockout named by the blueprint's id: {@code awaken:blockout_quiet_barrow} (or {@code awaken:quiet_barrow}) draws {@code quiet_barrow}. */
    public BlockoutLand(Ctx ctx) {
        this(ctx, Blockout.load(nameFor(ctx.id())));
    }

    /** Any blockout, for the bench: one read from a file is drawn without a build. */
    public BlockoutLand(Ctx ctx, Blockout blockout) {
        super(ctx, 0xB10C0FFL, blockout);
    }

    /**
     * A gate's real blueprint takes its own id once its land is written, so the blueprint that shows its blockout sits
     * beside it under {@code blockout_<name>}; both read the one blockout file.
     */
    public static String nameFor(String realmId) {
        String path = realmId.substring(realmId.indexOf(':') + 1);
        return path.startsWith("blockout_") ? path.substring("blockout_".length()) : path;
    }

    /** The block a role is drawn in. */
    public static BlockState block(Blockout.Role role) {
        return BLOCK[role.ordinal() + 1];
    }

    @Override
    protected Mats mats() {
        // nothing here is drawn from a land's own materials; a kit piece asked to draw gets amber's
        if (mats == null) mats = new Mats("amber", new Mats.Accents(null, null, null, null, null, null, null, null, null)) {};
        return mats;
    }

    @Override
    protected void draw(V v) {
        // only the masses that reach this chunk are asked about its columns
        Blockout.Mass[] near = blockout.near(v.x0 - 1, v.z0 - 1, v.x0 + 16, v.z0 + 16);
        if (near.length == 0) return;
        byte[] col = COLUMN.get();
        for (int x = v.x0 - 1; x <= v.x0 + 16; x++) for (int z = v.z0 - 1; z <= v.z0 + 16; z++) {
            int span = blockout.column(x, z, near, col);
            if (span < 0) continue;
            for (int y = span >> 16, hi = span & 0xFFFF; y <= hi; y++) if (col[y] != 0) v.set(x, y, z, BLOCK[col[y]]);
        }
    }

    @Override
    public int biome(int x, int y, int z) {
        return 0;
    }
}
