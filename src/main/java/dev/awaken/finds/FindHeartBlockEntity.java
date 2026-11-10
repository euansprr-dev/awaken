package dev.awaken.finds;

import com.mojang.serialization.Codec;
import dev.awaken.finds.verb.Ctx;
import dev.awaken.finds.verb.Script;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The heart of one find (SPEC §58.5): everything about a site that isn't a pure function of the seed. Its stage, its
 * sixteen numbers and four clocks (a script's whole memory), who has been paid here, and whether what lives here has
 * been put out yet. It runs the kind's script every five ticks while a player is near, and lets a site that nobody
 * finished go back to how the world made it once everyone has left.
 */
public final class FindHeartBlockEntity extends BlockEntity {
    /** A script runs while a player is this close; beyond it the site sleeps. */
    public static final double NEAR = 56;
    /** A site left part-done goes back after two minutes with nobody near; a finished one that doesn't persist, after ten. */
    static final int FORGET = 20 * 120, REDO = 20 * 600;

    private int stage;
    private final int[] vars = new int[16];
    private final long[] clocks = new long[4];
    private final List<UUID> claimed = new ArrayList<>();
    private boolean woke, touched;
    private long lastNear;
    // a site made by hand (the gallery, a test) says what it is; a site the world made is found from the seed
    private String forcedKind = "";
    private int forcedRot;
    private long forcedSeed;
    // client: what to draw
    private int tell;
    private long tellPos;

    private @Nullable Site site;
    private @Nullable Script script;
    private boolean resolved;

    public FindHeartBlockEntity(BlockPos pos, BlockState state) {
        super(FindsContent.HEART_BE.get(), pos, state);
    }

    // ---- what it is -------------------------------------------------------------------------------------------------

    public void force(Kind kind, int rot, long seed) {
        forcedKind = kind.id();
        forcedRot = rot;
        forcedSeed = seed;
        resolved = false;
        setChanged();
    }

    public @Nullable Site site() {
        if (!resolved && level instanceof ServerLevel sl) {
            resolved = true;
            if (!forcedKind.isEmpty()) {
                Kind k = Kinds.byId(forcedKind);
                site = k == null ? null : new Site(k, 2, worldPosition.getX(), worldPosition.getY() + 2, worldPosition.getZ(), forcedSeed, forcedRot,
                        dev.awaken.living.region.RegionMap.Group.PLAINS, k.rank());
            } else site = Sites.ofHeart(sl, worldPosition);
            script = site == null ? null : site.kind().script().get();
        }
        return site;
    }

    public @Nullable Ctx ctx() {
        Site s = site();
        if (s == null || script == null || !(level instanceof ServerLevel sl)) return null;
        return new Ctx(sl, this, s, Sites.scene(sl, s), script);
    }

    public @Nullable Script script() {
        site();
        return script;
    }

    // ---- state, for Ctx ---------------------------------------------------------------------------------------------

    public int stage() { return stage; }

    public void stage(int s) {
        stage = s;
        touch();
    }

    public int var(int i) { return vars[i & 15]; }

    public void var(int i, int v) {
        if (vars[i & 15] != v) {
            vars[i & 15] = v;
            touch();
        }
    }

    public long clock(int i) { return clocks[i & 3]; }

    public void clock(int i, long t) {
        clocks[i & 3] = t;
        touch();
    }

    public boolean claimed(UUID id) { return claimed.contains(id); }

    public void claim(UUID id) {
        if (!claimed.contains(id)) claimed.add(id);
        setChanged();
    }

    /** Something here is no longer as the world made it. */
    public void touch() {
        touched = true;
        setChanged();
    }

    // ---- running ----------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, FindHeartBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        Site s = be.site();
        if (s == null || be.script == null) return;
        long now = level.getGameTime();
        // on the beat, not staggered: a script that counts in seconds (now % 20) has to land on them
        if (now % be.script.every() != 0) return;
        if (!level.hasNearbyAlivePlayer(s.x() + 0.5, s.y() + 1, s.z() + 0.5, NEAR)) {
            if (be.touched && !s.kind().persists() && now - be.lastNear > (be.done() ? REDO : FORGET)) be.forget(sl);
            return;
        }
        be.lastNear = now;
        Ctx c = be.ctx();
        if (c == null) return;
        if (!be.woke) {
            be.woke = true;
            be.life(sl, s, c.scene);
            be.syncTell(c);
            be.script.wake(c);
            be.setChanged();
        }
        be.script.tick(c);
        // a find that wants a move you may not have yet is kept for you as a promise (SPEC §58.7), asked every two seconds
        if (!s.kind().move().isEmpty() && now % 40 == 0 && !be.done())
            for (var p : c.players(20))
                if (!be.claimed(p.getUUID()))
                    dev.awaken.core.api.Promises.seen(p, "find/" + s.key(), s.ground(), dev.awaken.core.api.Promises.Need.tier(s.kind().move()), s.kind().name());
    }

    public boolean done() {
        return script != null && stage >= script.length();
    }

    /** Back to how the world made it: parts restored, numbers cleared. What lives here stays. */
    void forget(ServerLevel sl) {
        Ctx c = ctx();
        if (c == null) return;
        script.reset(c);
        c.restoreAll();
        stage = 0;
        java.util.Arrays.fill(vars, 0);
        java.util.Arrays.fill(clocks, 0);
        touched = false;
        setChanged();
    }

    /** Puts out what lives here, once. Each is tagged with its site and its role so a script can find it again. */
    private void life(ServerLevel sl, Site s, Scene scene) {
        for (Scene.Spawn sp : scene.spawns()) {
            Entity e = sp.type().create(sl, EntitySpawnReason.STRUCTURE);
            if (e == null) continue;
            BlockPos at = s.world(sp.rel());
            e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, sl.getRandom().nextFloat() * 360, 0);
            e.addTag(tagOf(s));
            e.addTag("find_role:" + sp.role());
            if (e instanceof Mob m) {
                m.setPersistenceRequired();
                if ((sp.flags() & Scene.Spawn.NO_AI) != 0) m.setNoAi(true);
                if ((sp.flags() & Scene.Spawn.QUIET) != 0) m.setSilent(true);
            }
            if (e instanceof AgeableMob a && (sp.flags() & Scene.Spawn.BABY) != 0) a.setBaby(true);
            if (e instanceof Cat cat && (sp.flags() & Scene.Spawn.LIE) != 0) cat.setLying(true);
            sl.addFreshEntity(e);
        }
    }

    public static String tagOf(Site s) {
        return "find:" + s.key();
    }

    private void syncTell(Ctx c) {
        tell = c.kind().tell().ordinal();
        BlockPos t = c.scene.tell != null ? c.site.world(c.scene.tell) : c.site.ground().above(2);
        tellPos = t.asLong();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- client -----------------------------------------------------------------------------------------------------

    /** Installed by the client: draws a tell (a few particles, a quiet sound) for a heart near the camera. */
    public interface TellDrawer {
        void draw(Level level, BlockPos at, Kind.Tell tell, boolean done);
    }

    public static TellDrawer drawer = (l, p, t, d) -> {};
    private boolean clientDone;

    public static void clientTick(Level level, BlockPos pos, BlockState state, FindHeartBlockEntity be) {
        if (be.tell <= 0 || be.tell >= Kind.Tell.values().length) return;
        drawer.draw(level, BlockPos.of(be.tellPos), Kind.Tell.values()[be.tell], be.clientDone);
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schemaVersion", 1);
        out.putInt("stage", stage);
        out.putIntArray("vars", vars);
        for (int i = 0; i < 4; i++) out.putLong("clock" + i, clocks[i]);
        out.store("claimed", Codec.STRING.listOf(), claimed.stream().map(UUID::toString).toList());
        out.putBoolean("woke", woke);
        out.putBoolean("touched", touched);
        out.putLong("lastNear", lastNear);
        if (!forcedKind.isEmpty()) {
            out.putString("kind", forcedKind);
            out.putInt("rot", forcedRot);
            out.putLong("seed", forcedSeed);
        }
        out.putInt("tell", tell);
        out.putLong("tellPos", tellPos);
        out.putBoolean("done", done());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        stage = in.getIntOr("stage", 0);
        int[] v = in.getIntArray("vars").orElse(null);
        if (v != null) System.arraycopy(v, 0, vars, 0, Math.min(16, v.length));
        for (int i = 0; i < 4; i++) clocks[i] = in.getLongOr("clock" + i, 0);
        claimed.clear();
        for (String s : in.read("claimed", Codec.STRING.listOf()).orElse(List.of())) {
            try {
                claimed.add(UUID.fromString(s));
            } catch (IllegalArgumentException skip) {
                // a malformed entry is dropped
            }
        }
        woke = in.getBooleanOr("woke", false);
        touched = in.getBooleanOr("touched", false);
        lastNear = in.getLongOr("lastNear", 0);
        forcedKind = in.getStringOr("kind", "");
        forcedRot = in.getIntOr("rot", 0);
        forcedSeed = in.getLongOr("seed", 0);
        tell = in.getIntOr("tell", 0);
        tellPos = in.getLongOr("tellPos", 0);
        clientDone = in.getBooleanOr("done", false);
        resolved = false;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        return saveWithoutMetadata(regs);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) Hearts.add(sl, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) Hearts.remove(sl, this);
    }
}
