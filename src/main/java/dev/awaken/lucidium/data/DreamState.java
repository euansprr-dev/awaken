package dev.awaken.lucidium.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

/**
 * The player's current dream, if any (attachment {@code awaken:dream}). Everything needed to wake them safely is
 * here and saved with the player, so a logout, crash or restart mid-dream always ends in their own bed on the next
 * login. Runtime-only things (mobs, room swaps) live in {@code ActiveDream} and are simply dropped.
 */
public final class DreamState implements ValueIOSerializable {
    public boolean active;
    public long dreamId;
    public int cell = -1;
    public String host = "";
    public long startTick;
    public long endTick;
    public float lucidity;
    public float peak;

    // where to wake up
    public String returnDim = "minecraft:overworld";
    public double rx, ry, rz;
    public float ryaw, rpitch;
    public BlockPos bed;
    public boolean fromSleep;

    // what we changed on the player
    public String prevMode = "";
    public boolean prevMayfly;
    public boolean grantedFly;

    // progress inside the dream
    public int spotted;
    public int falls;
    public boolean lucidCounted;
    public boolean masteryShown;
    public long lastManifest = -100000;
    public long lastRealityCheck = -100000;
    public int realityChecks;
    public long powerBaseline = -1;
    public double xpGranted;
    public boolean summoned;
    public boolean doorFound;
    public long lastDoor = -100000;

    public void reset() {
        DreamState fresh = new DreamState();
        copyFrom(fresh);
    }

    private void copyFrom(DreamState o) {
        active = o.active; dreamId = o.dreamId; cell = o.cell; host = o.host; startTick = o.startTick; endTick = o.endTick;
        lucidity = o.lucidity; peak = o.peak; returnDim = o.returnDim; rx = o.rx; ry = o.ry; rz = o.rz; ryaw = o.ryaw;
        rpitch = o.rpitch; bed = o.bed; fromSleep = o.fromSleep; prevMode = o.prevMode; prevMayfly = o.prevMayfly;
        grantedFly = o.grantedFly; spotted = o.spotted; falls = o.falls; lucidCounted = o.lucidCounted;
        masteryShown = o.masteryShown; lastManifest = o.lastManifest; lastRealityCheck = o.lastRealityCheck;
        realityChecks = o.realityChecks; powerBaseline = o.powerBaseline; xpGranted = o.xpGranted; summoned = o.summoned;
        doorFound = o.doorFound; lastDoor = o.lastDoor;
    }

    @Override
    public void serialize(ValueOutput out) {
        out.putBoolean("active", active);
        if (!active) return;
        out.putLong("dream", dreamId);
        out.putInt("cell", cell);
        out.putString("host", host);
        out.putLong("start", startTick);
        out.putLong("end", endTick);
        out.putFloat("lucidity", lucidity);
        out.putFloat("peak", peak);
        out.putString("return_dim", returnDim);
        out.putDouble("rx", rx);
        out.putDouble("ry", ry);
        out.putDouble("rz", rz);
        out.putFloat("ryaw", ryaw);
        out.putFloat("rpitch", rpitch);
        if (bed != null) out.store("bed", BlockPos.CODEC, bed);
        out.putBoolean("from_sleep", fromSleep);
        out.putString("prev_mode", prevMode);
        out.putBoolean("prev_mayfly", prevMayfly);
        out.putBoolean("granted_fly", grantedFly);
        out.putInt("spotted", spotted);
        out.putInt("falls", falls);
        out.putBoolean("lucid_counted", lucidCounted);
        out.putLong("last_manifest", lastManifest);
        out.putDouble("xp_granted", xpGranted);
    }

    @Override
    public void deserialize(ValueInput in) {
        reset();
        active = in.getBooleanOr("active", false);
        if (!active) return;
        dreamId = in.getLongOr("dream", 0);
        cell = in.getIntOr("cell", -1);
        host = in.getStringOr("host", "");
        startTick = in.getLongOr("start", 0);
        endTick = in.getLongOr("end", 0);
        lucidity = in.getFloatOr("lucidity", 0);
        peak = in.getFloatOr("peak", 0);
        returnDim = in.getStringOr("return_dim", "minecraft:overworld");
        rx = in.getDoubleOr("rx", 0);
        ry = in.getDoubleOr("ry", 64);
        rz = in.getDoubleOr("rz", 0);
        ryaw = in.getFloatOr("ryaw", 0);
        rpitch = in.getFloatOr("rpitch", 0);
        bed = in.read("bed", BlockPos.CODEC).orElse(null);
        fromSleep = in.getBooleanOr("from_sleep", false);
        prevMode = in.getStringOr("prev_mode", "");
        prevMayfly = in.getBooleanOr("prev_mayfly", false);
        grantedFly = in.getBooleanOr("granted_fly", false);
        spotted = in.getIntOr("spotted", 0);
        falls = in.getIntOr("falls", 0);
        lucidCounted = in.getBooleanOr("lucid_counted", false);
        lastManifest = in.getLongOr("last_manifest", -100000);
        xpGranted = in.getDoubleOr("xp_granted", 0);
    }
}
