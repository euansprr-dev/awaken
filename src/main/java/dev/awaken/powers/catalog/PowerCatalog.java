package dev.awaken.powers.catalog;

/**
 * Registers every power. The authoritative launch list (SPEC section 6): 63 powers, numbers below. Cost is energy
 * (channels/toggles add a per-second drain), cooldown in seconds, damage is the base before scaling
 * (x (1 + 0.075 x level) x (1 + 1.5% per point of the family stat above 5) x (1 + 12% per power level) x variant).
 * Hidden = path signature or latent: never rolled from Power Cores. Full descriptions: docs/modules/powers.md.
 *
 * <pre>
 * id               family     slot      rank  cost      cd    dmg   forces
 * ground_slam      Kinetic    active    E     22        6     6
 * leap             Kinetic    movement  E     15        4     3
 * haymaker         Kinetic    active    E     18 (hold) 5     7+9
 * terrain_rip      Kinetic    active    C     30        10    14
 * shockwave        Kinetic    active    D     20        7     5
 * brace            Kinetic    active    D     15        12    3
 * thunderclap      Kinetic    active    C*    40        14    12    charge
 * dash             Velocity   movement  E     10        0.7   2
 * flow             Velocity   active    E     35        20    -
 * afterimage       Velocity   active    D     20        12    6
 * wall_run         Velocity   passive   D     -         -     -
 * water_run        Velocity   passive   D     -         -     -
 * lightning_step   Velocity   movement  C*    22        3     6     charge
 * hover            Flight     active    E     15        10    -
 * flight           Flight     movement  A     10+6/s    2     -
 * gravity_well     Flight     active    D     28        14    2/0.5s gravity
 * telekinesis      Flight     active    C     20+6/s    8     9     gravity
 * meteor_dive      Flight     active    C     30        12    10+   gravity
 * gravity_flip     Flight     active    B*    30        15    8     gravity
 * beam             Energy     active    E     6+18/s    3     2.2/0.2s heat
 * nova             Energy     active    D     30        10    9     heat
 * barrier          Energy     active    E     25        18    4     charge
 * charged_shot     Energy     active    C     15 (hold) 4     6+14  charge
 * overload         Energy     ultimate  C     60        60    28    heat+charge
 * prism_lance      Energy     ultimate  C*    40+10/s   45    5/0.2s heat
 * shadow_step      Shadow     active    E     15        6     -
 * cloak            Shadow     active    D     25        20    5
 * echo_swap        Shadow     active    C     18        8     5     spirit
 * legion           Shadow     ultimate  C*    60        60    3.5   spirit
 * danger_sense     Mind       passive   D     -         -     -
 * dominate         Mind       active    C     35        30    -
 * read_intent      Mind       active    D     20        20    -
 * decoy            Mind       active    E     18        12    4     spirit
 * foresight        Mind       active    C*    35        30    -
 * wall             Architect  active    E     12        2     -
 * ramp             Architect  active    E     12        2     -
 * floor            Architect  active    E     12        2     -
 * cone             Architect  active    D     14        2     -
 * rampart          Architect  active    C     30        15    -
 * spire            Architect  active    C     22        8     6
 * collapse         Architect  active    B     25        10    4/block
 * citadel          Architect  ultimate  C*    50        60    -
 * grapple          Tech       movement  E     10        2.5   3
 * jet_burst        Tech       movement  D     14        2     2     heat
 * drone            Tech       active    D     25        15    2.5/shot charge
 * turret           Tech       active    C     30        20    3.5/shot charge
 * railgun          Tech       active    B     35 (hold) 12    18+10 charge
 * nanite_skin      Tech       active    C     25        25    -
 * drone_swarm      Tech       ultimate  C*    60        60    2.5/shot charge
 * aura             Spirit     passive   E     -         -     1.2/2s spirit
 * spirit_palm      Spirit     active    E     16        5     8     spirit
 * mend             Spirit     active    E     25        20    -
 * third_eye        Spirit     active    D     15        20    -
 * cord_lash        Spirit     active    D     18        6     6     spirit
 * bulwark          Spirit     active    C*    40        30    -     spirit
 * hundred_palms    Spirit     ultimate  C*    50        45    1.6x20 spirit
 * void_maw         Spirit     ultimate  C*    55        60    3/0.5s gravity+spirit
 * waking_dream     Spirit     active    C*    25        10    -
 * storm_call       Spirit     active    D*    45        30    14    charge
 * ember_step       Spirit     movement  D*    14        1.5   2     heat
 * frost_nova       Spirit     active    D*    28        12    7     frost
 * ghost_step       Spirit     movement  D*    14        2.5   3     spirit
 * twin_strike      Spirit     active    C*    25        20    60% echo spirit
 * (* hidden)
 * </pre>
 */
public final class PowerCatalog {
    private PowerCatalog() {}

    private static boolean done;

    public static synchronized void bootstrap() {
        if (done) return;
        done = true;
        KineticPowers.register();
        VelocityPowers.register();
        FlightPowers.register();
        EnergyPowers.register();
        ShadowPowers.register();
        MindPowers.register();
        ArchitectPowers.register();
        TechPowers.register();
        SpiritPowers.register();
    }
}
