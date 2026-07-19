# Prayer Overheads Reworked

A RuneLite plugin that gets the large overhead protection-prayer bubbles out of the way —
in big group content (8-man raids) they can cover the ground tiles you need to see.

Planned modes:

- **Bubble:** vanilla / hidden / mini-icon (redrawn smaller by the plugin)
- **Highlight:** none / outline / underfoot tile / hull tint, colored by protection style

## How it works

The client loads overhead prayer icons from sprite group `HEADICONS_PRAYER` (440).
`Client.getSpriteOverrides()` intercepts the client's sprite loader by sprite ID, so
overriding 440 with a transparent sprite removes the bubbles without touching health
bars, hitsplats, skulls, or overhead chat (unlike Entity Hider's all-or-nothing 2D flags).

## Running / developing

```
./gradlew run
```

launches the full RuneLite client in developer mode with this plugin loaded.
Requires JDK 11+.

## Spike protocol (current state)

The plugin currently ships the sprite-override spike. With the plugin enabled,
config `Overhead bubbles` = `Magenta test`, log in and activate any protection prayer:

1. **Mechanism** — if the bubble renders as a magenta square, the override reaches the
   overhead icon draw path. If the bubble is unchanged, headicons load outside the
   overridable path and we fall back to a core-RuneLite contribution.
2. **Timing** — flip the config to `Vanilla` mid-session. If the bubble reverts
   immediately (or after switching prayers), overrides apply live; if not, note whether
   a relog or client restart is needed.
3. **Granularity** — cycle Protect from Melee/Missiles/Magic. If every prayer shows the
   same magenta square, the override is group-level (one image for all six icons) —
   meaning "shrink in place" would lose per-prayer identity, and the mini-icon mode
   should be: hide vanilla + redraw our own sized/positioned icons in an overlay.

## Spike results (2026-07-19)

**NEGATIVE.** Sprite override on group 440 has no effect on overhead prayer
bubbles, including on a cold boot with the override registered before login.
The headicon draw path does not consult `getSpriteOverrides()` (it presumably
loads the group through a bulk loader the hook does not wrap). Sprite
replacement is a dead end for this feature.

**New direction:** `Hooks.registerRenderableDrawListener` — the mechanism the
core Entity Hider uses. Returning false for an actor's `drawingUI` pass hides
its whole 2D block (overhead chat, health bar, prayer bubble) per actor, per
frame. The plugin hides the 2D block for configured actor categories and
selectively redraws the elements worth keeping (chat text, health bar, skull),
replacing the prayer bubble with mini-icons / underfoot highlights.
