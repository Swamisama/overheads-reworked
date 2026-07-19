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

Record results in this README before building the real modes.
