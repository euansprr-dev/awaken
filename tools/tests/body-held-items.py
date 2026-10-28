#!/usr/bin/env python3
"""Compile the real third-person stow modifier against tiny render/input doubles; no client or graphics context."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get("JAVA_HOME", "/Volumes/MEDIA/.jdks/jdk-25.0.5+8/Contents/Home")) / "bin"
STUBS = {
    "dev/awaken/body/BodyContent.java": "package dev.awaken.body; public class BodyContent { public static final Object STOWED = new Object(); }",
    "dev/awaken/body/BodyStow.java": "package dev.awaken.body; public class BodyStow { public static boolean climbVault(int kind) { return kind >= 2 && kind <= 4; } }",
    "dev/awaken/body/ascent/client/AscentMoves.java": "package dev.awaken.body.ascent.client; public class AscentMoves { public static class State { public boolean rope; } public static final State STATE = new State(); public static State state() { return STATE; } }",
    "net/minecraft/world/entity/Entity.java": "package net.minecraft.world.entity; public class Entity {}",
    "net/minecraft/world/entity/player/Player.java": """package net.minecraft.world.entity.player;
public class Player extends net.minecraft.world.entity.Entity {
    public boolean stowed, alive = true, spectator;
    public final net.minecraft.world.item.ItemStack inventory = new net.minecraft.world.item.ItemStack();
    public Boolean getExistingDataOrNull(Object key) { return stowed; }
    public boolean isAlive() { return alive; }
    public boolean isSpectator() { return spectator; }
}""",
    "net/minecraft/client/Minecraft.java": """package net.minecraft.client;
public class Minecraft {
    public static final Minecraft INSTANCE = new Minecraft();
    public net.minecraft.world.entity.player.Player player;
    public static Minecraft getInstance() { return INSTANCE; }
}""",
    "net/minecraft/world/item/ItemStack.java": "package net.minecraft.world.item; public class ItemStack { public static final ItemStack EMPTY = new ItemStack(); }",
    "net/minecraft/client/renderer/entity/state/EntityRenderState.java": "package net.minecraft.client.renderer.entity.state; public class EntityRenderState {}",
    "net/minecraft/client/renderer/entity/state/ArmedEntityRenderState.java": """package net.minecraft.client.renderer.entity.state;
import net.minecraft.world.item.ItemStack;
public class ArmedEntityRenderState extends EntityRenderState {
    public static class ItemState { public boolean populated; public void clear() { populated = false; } }
    public final ItemState rightHandItemState = new ItemState(), leftHandItemState = new ItemState();
    public ItemStack rightHandItemStack = ItemStack.EMPTY, leftHandItemStack = ItemStack.EMPTY;
}""",
    "dev/awaken/body/client/ClientMoves.java": """package dev.awaken.body.client;
class ClientMoves {
    static boolean mantling, vaulting;
    record Vault(int kind) {}
    static Vault vault;
}""",
    "dev/awaken/body/client/RunnerMoves.java": """package dev.awaken.body.client;
class RunnerMoves {
    enum State { NONE, RUN, HANG, PATH, GRIP }
    static State state = State.NONE;
    static boolean runClimb;
}""",
    "dev/awaken/body/client/BodyHeldItemsCheck.java": """package dev.awaken.body.client;
import dev.awaken.body.ascent.client.AscentMoves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class BodyHeldItemsCheck {
    static final ArmedEntityRenderState state = new ArmedEntityRenderState();
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    // Vanilla re-extracts both hands every frame. Arms may then inject a pair's synthetic off-hand item.
    static void extract(Player player) {
        state.rightHandItemStack = player.inventory;
        state.leftHandItemStack = new ItemStack();
        state.rightHandItemState.populated = state.leftHandItemState.populated = true;
    }
    static void frame(Player player, boolean hidden, String why) {
        extract(player);
        BodyHeldItems.modify(player, state);
        check(state.rightHandItemState.populated != hidden && state.leftHandItemState.populated != hidden, why + ": both meshes");
        check((state.rightHandItemStack == ItemStack.EMPTY) == hidden && (state.leftHandItemStack == ItemStack.EMPTY) == hidden, why + ": both stacks/trails");
        check(player.inventory != ItemStack.EMPTY, why + ": inventory is untouched");
    }
    public static void main(String[] args) {
        Player local = new Player(), climber = new Player(), idle = new Player();
        Minecraft.INSTANCE.player = local;
        climber.stowed = true;
        frame(climber, true, "remote climb and synthetic twin");
        frame(idle, false, "next player's reused render state");
        climber.stowed = false;
        frame(climber, false, "remote release restores next extraction");
        for (RunnerMoves.State move : RunnerMoves.State.values()) {
            RunnerMoves.state = move;
            RunnerMoves.runClimb = false;
            frame(local, move == RunnerMoves.State.HANG || move == RunnerMoves.State.PATH || move == RunnerMoves.State.GRIP, "predicted " + move);
        }
        RunnerMoves.state = RunnerMoves.State.RUN;
        RunnerMoves.runClimb = true;
        frame(local, true, "wall climb");
        RunnerMoves.state = RunnerMoves.State.NONE;
        ClientMoves.mantling = true;
        frame(local, true, "mantle");
        ClientMoves.mantling = false;
        ClientMoves.vaulting = true;
        for (int kind = 0; kind < 5; kind++) {
            ClientMoves.vault = new ClientMoves.Vault(kind);
            frame(local, kind >= 2, "vault " + kind);
        }
        ClientMoves.vaulting = false;
        AscentMoves.STATE.rope = true;
        frame(local, true, "rope");
        AscentMoves.STATE.rope = false;
        local.stowed = true;
        frame(local, false, "release ignores stale server echo");
        climber.stowed = true;
        climber.alive = false;
        frame(climber, false, "dead body");
        climber.alive = true;
        climber.spectator = true;
        frame(climber, false, "spectator");
        BodyHeldItems.modify(new net.minecraft.world.entity.Entity(), state);
        BodyHeldItems.modify(idle, new net.minecraft.client.renderer.entity.state.EntityRenderState());
        System.out.println("PASS: third-person held-item stow, restoration, per-player isolation, paired weapons and predicted movement");
    }
}""",
}

(ROOT / "logs").mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix="body-held-items-", dir=ROOT / "logs") as directory:
    work = Path(directory)
    for name, source in STUBS.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
    source = ROOT / "src/main/java/dev/awaken/body/client/BodyHeldItems.java"
    subprocess.run([str(JAVA / "javac"), "-d", str(work), *map(str, work.rglob("*.java")), str(source)], check=True)
    subprocess.run([str(JAVA / "java"), "-cp", str(work), "dev.awaken.body.client.BodyHeldItemsCheck"], check=True)
