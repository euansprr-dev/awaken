package dev.awaken.citizens.client;

/** What a citizen works with in hand (M4 B3b): which prop, its model's render state (held on the body, reused). */
final class HeldProp {
    static final int NONE = 0, ROPE = 1, NET = 2, PARCEL = 3;
    final CritterRenderState state = new CritterRenderState();
    int kind;
}
