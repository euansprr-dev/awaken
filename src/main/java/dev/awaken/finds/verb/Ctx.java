package dev.awaken.finds.verb;

import dev.awaken.finds.FindHeartBlockEntity;
import dev.awaken.finds.Kind;
import dev.awaken.finds.Reward;
import dev.awaken.finds.Scene;
import dev.awaken.finds.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A script's hands (SPEC §58.5): the site it's at, its parts as places in the world, its saved numbers, and the few
 * things it may do there (change a part, make a sound, pay out). One per call; nothing is kept between calls but what
 * is written to the heart.
 */
public final class Ctx {
    public final ServerLevel level;
    public final FindHeartBlockEntity heart;
    public final Site site;
    public final Scene scene;
    private final Script root;
    /** Which link of a chain is speaking: its numbers are {@code slot*4..slot*4+3}, its clock is {@code slot}. */
    int slot;

    public Ctx(ServerLevel level, FindHeartBlockEntity heart, Site site, Scene scene, Script root) {
        this.level = level;
        this.heart = heart;
        this.site = site;
        this.scene = scene;
        this.root = root;
    }

    public Kind kind() {
        return site.kind();
    }

    // ---- parts ------------------------------------------------------------------------------------------------------

    public int count(String part) {
        return scene.part(part).size();
    }

    public BlockPos pos(String part, int i) {
        return site.world(scene.part(part).get(i));
    }

    public boolean has(String part) {
        return !scene.part(part).isEmpty();
    }

    public BlockState at(String part, int i) {
        return level.getBlockState(pos(part, i));
    }

    /** What the world made at a part (air where the scene put nothing). */
    public BlockState made(String part, int i) {
        BlockPos r = scene.part(part).get(i);
        BlockState s = scene.blocks().get(BlockPos.asLong(r.getX(), r.getY(), r.getZ()));
        return s == null ? Blocks.AIR.defaultBlockState() : s;
    }

    public void set(String part, int i, BlockState s) {
        set(pos(part, i), s);
    }

    public void set(BlockPos p, BlockState s) {
        if (level.getBlockState(p) == s) return;
        level.setBlock(p, s, Block.UPDATE_ALL);
        heart.touch();
    }

    /** Every part back to what the world made. */
    public void restoreAll() {
        for (var e : scene.parts().entrySet())
            for (int i = 0; i < e.getValue().size(); i++) {
                BlockPos p = site.world(e.getValue().get(i));
                BlockState made = made(e.getKey(), i);
                if (level.getBlockState(p) != made) level.setBlock(p, made, Block.UPDATE_ALL);
            }
    }

    // ---- memory -----------------------------------------------------------------------------------------------------

    /** Which link of the kind's script is running: 0 at the start, the script's length when it's all done. */
    public int stage() {
        return heart.stage();
    }

    public boolean done() {
        return heart.stage() >= root.length();
    }

    public int v(int i) {
        return heart.var(slot * 4 + (i & 3));
    }

    public void v(int i, int val) {
        heart.var(slot * 4 + (i & 3), val);
    }

    public long clock() {
        return heart.clock(slot);
    }

    public void clock(long t) {
        heart.clock(slot, t);
    }

    public long now() {
        return level.getGameTime();
    }

    public long seed() {
        return site.seed();
    }

    /**
     * This link's work is done. The next link takes over; after the last, everyone near who hasn't been paid here is
     * paid ({@link Reward}), {@code by} first.
     */
    public void complete(@Nullable ServerPlayer by) {
        if (done()) return;
        heart.stage(heart.stage() + 1);
        if (done()) Reward.pay(this, by);
    }

    // ---- the world --------------------------------------------------------------------------------------------------

    public void sound(BlockPos p, SoundEvent e, float volume, float pitch) {
        level.playSound(null, p, e, SoundSource.BLOCKS, volume, pitch);
    }

    public void puff(BlockPos p, ParticleOptions o, int n, double spread, double speed) {
        level.sendParticles(o, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, n, spread, spread, spread, speed);
    }

    public List<ServerPlayer> players(double r) {
        List<ServerPlayer> out = new ArrayList<>(2);
        double cx = site.x() + 0.5, cy = site.y() + 1, cz = site.z() + 0.5;
        for (ServerPlayer p : level.players()) if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(cx, cy, cz) <= r * r) out.add(p);
        return out;
    }

    /** What lives at this site with a role (the scene's spawns), still alive and loaded. */
    public List<Entity> life(String role) {
        int r = kind().reach() + 24;
        AABB box = new AABB(site.x() - r, site.y() - 16, site.z() - r, site.x() + r, site.y() + 64, site.z() + r);
        String tag = FindHeartBlockEntity.tagOf(site), want = "find_role:" + role;
        return level.getEntities((Entity) null, box, e -> e.isAlive() && e.entityTags().contains(tag) && e.entityTags().contains(want));
    }

    /** Tosses a stack out of a place, upward and a little toward nobody in particular. */
    public void pop(BlockPos from, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity e = new ItemEntity(level, from.getX() + 0.5, from.getY() + 0.6, from.getZ() + 0.5, stack);
        e.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.12, 0.26, (level.random.nextDouble() - 0.5) * 0.12);
        e.setPickUpDelay(12);
        level.addFreshEntity(e);
    }

    public boolean night() {
        long t = level.getOverworldClockTime() % 24000;
        return t >= 13000 && t < 23000;
    }

    /** The hour of the game day, 0..23999 (0 is sunrise). */
    public long hour() {
        return level.getOverworldClockTime() % 24000;
    }

    public boolean raining() {
        return level.isRainingAt(site.ground().above(2)) || level.isRaining();
    }

    public boolean storm() {
        return level.isThundering();
    }
}
