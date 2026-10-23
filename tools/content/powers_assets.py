"""Writes the powers module's JSON assets (run from the repo root: python3 tools/content/powers_assets.py).

Sounds point at vanilla sound events with pitch/volume changes. Post effects share one fragment shader
(awaken:post/power_tint) with a different tint per power. Re-run after changing anything below; the output is
committed so the build never depends on Python.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
RES = os.path.join(ROOT, "src/main/resources/assets/awaken")
MERGE = os.path.join(ROOT, "src/main/merge/powers")


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        if isinstance(data, str):
            f.write(data)
        else:
            json.dump(data, f, indent=2)
            f.write("\n")


def ev(name, pitch=1.0, volume=1.0):
    return {"name": "minecraft:" + name, "type": "event", "pitch": pitch, "volume": volume}


# name -> (subtitle, [sound entries])
SOUNDS = {
    "fail": ("Power refused", [ev("block.note_block.bass", 0.5, 0.5)]),
    "learn": ("Power learned", [ev("block.beacon.power_select", 1.3, 0.9), ev("block.amethyst_block.resonate", 1.2, 0.8)]),
    "slam": ("Ground slams", [ev("item.mace.smash_ground_heavy", 0.9, 1.0)]),
    "whoosh": ("Rush of air", [ev("entity.breeze.wind_burst", 1.5, 0.6), ev("item.trident.riptide_1", 1.6, 0.4)]),
    "punch": ("Heavy punch", [ev("entity.player.attack.strong", 0.7, 1.0), ev("item.mace.smash_ground", 1.2, 0.6)]),
    "charge_up": ("Power gathers", [ev("block.respawn_anchor.charge", 1.4, 0.7)]),
    "rip": ("Earth tears", [ev("block.rooted_dirt.break", 0.5, 1.0), ev("block.gravel.break", 0.6, 1.0)]),
    "impact": ("Impact", [ev("item.mace.smash_ground", 1.0, 0.9)]),
    "shockwave": ("Shockwave", [ev("entity.wind_charge.wind_burst", 0.6, 1.0)]),
    "brace": ("Bracing", [ev("item.armor.equip_iron", 0.7, 1.0), ev("block.anvil.land", 1.6, 0.3)]),
    "thunder": ("Thunder", [ev("entity.lightning_bolt.thunder", 1.0, 0.6)]),
    "zap": ("Crackle", [ev("entity.lightning_bolt.impact", 1.6, 0.7)]),
    "flow_start": ("Time slows", [ev("block.beacon.activate", 2.0, 0.8), ev("item.trident.riptide_2", 0.6, 0.5)]),
    "flow_end": ("Time resumes", [ev("block.beacon.deactivate", 2.0, 0.7)]),
    "hover": ("Hovering", [ev("entity.breeze.jump", 1.2, 0.7)]),
    "gravity": ("Gravity bends", [ev("entity.warden.heartbeat", 0.6, 1.0), ev("block.beacon.ambient", 0.5, 0.8)]),
    "throw": ("Thrown", [ev("entity.breeze.shoot", 0.8, 1.0)]),
    "beam": ("Beam hums", [ev("block.beacon.ambient", 1.8, 0.8), ev("block.conduit.ambient.short", 1.6, 0.6)]),
    "nova": ("Nova", [ev("entity.generic.explode", 1.4, 0.8), ev("block.beacon.power_select", 2.0, 0.6)]),
    "barrier": ("Barrier rises", [ev("block.beacon.power_select", 1.6, 0.7), ev("block.amethyst_block.chime", 1.2, 1.0)]),
    "barrier_break": ("Barrier shatters", [ev("block.glass.break", 0.8, 1.0)]),
    "shot": ("Bolt fired", [ev("entity.shulker.shoot", 1.2, 0.9), ev("entity.blaze.shoot", 1.6, 0.4)]),
    "overload": ("Overload builds", [ev("block.respawn_anchor.charge", 0.6, 1.0), ev("entity.warden.sonic_charge", 1.4, 0.6)]),
    "shadow": ("Shadow shifts", [ev("entity.enderman.teleport", 0.7, 0.8)]),
    "cloak": ("Fades from sight", [ev("entity.illusioner.prepare_blindness", 1.2, 0.9)]),
    "summon": ("Something answers", [ev("entity.evoker.prepare_summon", 0.8, 0.9)]),
    "mind": ("A mind bends", [ev("entity.illusioner.cast_spell", 1.2, 0.9)]),
    "pop": ("Decoy bursts", [ev("entity.illusioner.mirror_move", 1.2, 1.0), ev("entity.firework_rocket.blast", 0.8, 0.5)]),
    "build": ("Hard light forms", [ev("block.amethyst_block.place", 0.8, 1.0), ev("block.stone.place", 1.2, 0.6)]),
    "collapse": ("Constructs collapse", [ev("block.heavy_core.fall", 0.6, 1.0), ev("entity.zombie.attack_iron_door", 0.6, 0.6)]),
    "construct_fade": ("Construct fades", [ev("block.amethyst_block.break", 1.6, 0.5)]),
    "grapple": ("Hook fires", [ev("item.crossbow.shoot", 1.4, 0.9), ev("entity.fishing_bobber.retrieve", 0.8, 0.8)]),
    "jet": ("Jets roar", [ev("entity.firework_rocket.launch", 0.8, 1.0)]),
    "drone_shot": ("Drone fires", [ev("entity.blaze.shoot", 2.0, 0.4)]),
    "rail": ("Railgun fires", [ev("entity.warden.sonic_boom", 1.4, 0.8)]),
    "nanite": ("Nanites harden", [ev("item.armor.equip_netherite", 1.2, 1.0), ev("block.copper.place", 1.4, 0.6)]),
    "spirit": ("Spirit strikes", [ev("particle.soul_escape", 1.0, 1.0), ev("block.amethyst_block.chime", 0.6, 0.8)]),
    "heal": ("Mending", [ev("block.amethyst_block.chime", 1.4, 1.0), ev("entity.player.levelup", 2.0, 0.2)]),
    "eye": ("Third eye opens", [ev("block.beacon.activate", 1.6, 0.6), ev("entity.ender_eye.launch", 0.8, 0.6)]),
    "lash": ("Cord lashes", [ev("item.trident.throw", 1.4, 0.8), ev("entity.fishing_bobber.retrieve", 1.2, 0.8)]),
    "ward": ("Ward rises", [ev("block.conduit.activate", 1.2, 1.0)]),
    "void": ("The void hungers", [ev("entity.warden.heartbeat", 0.5, 1.0), ev("particle.soul_escape", 0.5, 1.0)]),
    "dream": ("The dream shifts", [ev("block.amethyst_block.resonate", 1.4, 1.0), ev("block.chorus_flower.grow", 1.2, 0.8)]),
    "frost": ("Frost spreads", [ev("block.powder_snow.break", 0.8, 1.0), ev("block.glass.break", 1.6, 0.4)]),
    "ember": ("Embers flare", [ev("item.firecharge.use", 1.2, 0.8), ev("block.fire.ambient", 1.0, 1.0)]),
    "steam": ("Steam hisses", [ev("block.fire.extinguish", 0.8, 1.0)]),
    "blast": ("Blast", [ev("entity.generic.explode", 1.0, 0.9)]),
    "stun": ("Stunned", [ev("entity.lightning_bolt.impact", 2.0, 0.5)]),
    "shatter": ("Mirror shatters", [ev("block.glass.break", 1.0, 1.0)]),
    "danger": ("Danger sensed", [ev("block.note_block.bit", 1.6, 0.4)]),
}

# post effect id -> (tint rgb, strength, desaturate, vignette, pulse)
POST = {
    "flow": ((0.55, 0.78, 1.0), 0.35, 0.55, 0.55, 0.0),
    "foresight": ((1.0, 0.82, 0.45), 0.35, 0.75, 0.6, 0.0),
    "overload": ((1.0, 0.45, 0.25), 0.22, 0.0, 0.45, 0.6),
    "third_eye": ((0.7, 0.55, 1.0), 0.3, 0.35, 0.5, 0.3),
    "cloak": ((0.35, 0.3, 0.5), 0.35, 0.6, 0.7, 0.0),
    "void": ((0.18, 0.1, 0.3), 0.4, 0.5, 0.85, 0.4),
}

SHADER = """#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>

// AWAKEN power tint: desaturate, tint, vignette and an optional slow pulse. One shader, many post effects.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform TintConfig {
    vec4 Tint;    // rgb tint, a = tint strength
    vec4 Params;  // x = desaturate, y = vignette, z = pulse, w = unused
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(c, vec3(luma), Params.x);
    float pulse = 1.0 + Params.z * 0.25 * sin(GameTime * 2400.0);
    c = mix(c, c * Tint.rgb * 1.6, Tint.a * pulse);
    vec2 d = texCoord - vec2(0.5);
    float v = smoothstep(0.85, 0.25, length(d) * (1.0 + Params.y));
    c *= mix(1.0, v, Params.y);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""


def post_effect(tint, strength, desat, vignette, pulse):
    return {
        "targets": {"swap": {}},
        "passes": [
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "awaken:post/power_tint",
                "inputs": [{"sampler_name": "In", "target": "minecraft:main"}],
                "output": "swap",
                "uniforms": {
                    "TintConfig": [
                        {"name": "Tint", "type": "vec4", "value": [tint[0], tint[1], tint[2], strength]},
                        {"name": "Params", "type": "vec4", "value": [desat, vignette, pulse, 0.0]},
                    ]
                },
            },
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "minecraft:post/blit",
                "inputs": [{"sampler_name": "In", "target": "swap"}],
                "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]},
                "output": "minecraft:main",
            },
        ],
    }


def main():
    sounds = {}
    lang = {
        "item.awaken.power_core": "Power Core",
        "item.awaken.power_core.named": "Power Core · %s",
        "block.awaken.construct": "Construct",
        "block.awaken.dream_construct": "Dream Construct",
        "entity.awaken.shadow_soldier": "Shadow Soldier",
        "entity.awaken.power_drone": "Drone",
        "effect.awaken.stunned": "Stunned",
    }
    for name, (subtitle, entries) in SOUNDS.items():
        sounds["power." + name] = {"subtitle": "subtitles.awaken.power." + name, "sounds": entries}
        lang["subtitles.awaken.power." + name] = subtitle
    write(os.path.join(MERGE, "assets/awaken/sounds.json"), sounds)
    write(os.path.join(MERGE, "assets/awaken/lang/en_us.json"), lang)

    # constructs: four ages, full block or bottom slab
    for block in ("construct", "dream_construct"):
        variants = {}
        for age in range(4):
            tex = f"awaken:block/{block}_{age}"
            write(os.path.join(RES, f"models/block/{block}_{age}.json"),
                  {"parent": "minecraft:block/cube_all", "textures": {"all": {"sprite": tex, "force_translucent": True}}})
            write(os.path.join(RES, f"models/block/{block}_slab_{age}.json"),
                  {"parent": "minecraft:block/slab", "textures": {
                      "bottom": {"sprite": tex, "force_translucent": True},
                      "top": {"sprite": tex, "force_translucent": True},
                      "side": {"sprite": tex, "force_translucent": True}}})
            variants[f"age={age},slab=false"] = {"model": f"awaken:block/{block}_{age}"}
            variants[f"age={age},slab=true"] = {"model": f"awaken:block/{block}_slab_{age}"}
        write(os.path.join(RES, f"blockstates/{block}.json"), {"variants": variants})

    # the Power Core: a shell plus an inner glow tinted by the power's family
    write(os.path.join(RES, "models/item/power_core.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "awaken:item/power_core",
                     "layer1": {"sprite": "awaken:item/power_core_glow", "neoforge_data": {"light_emission": 12}}}})
    write(os.path.join(RES, "items/power_core.json"), {"model": {
        "type": "minecraft:model", "model": "awaken:item/power_core",
        "tints": [{"type": "minecraft:constant", "value": -1}, {"type": "awaken:power_family", "default": -8404993}]}})

    for name, args in POST.items():
        write(os.path.join(RES, f"post_effect/{name}.json"), post_effect(*args))
    write(os.path.join(RES, "shaders/post/power_tint.fsh"), SHADER)

    # Spirit meets Mirror: blocks a spirit step walks through. Gates add their mirror blocks to this tag.
    write(os.path.join(MERGE, "data/awaken/tags/block/mirror.json"), {"values": ["minecraft:tinted_glass"]})
    print("powers assets written")


if __name__ == "__main__":
    main()
