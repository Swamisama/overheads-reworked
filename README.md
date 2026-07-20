# Overheads Reworked

A RuneLite plugin that gets the large overhead protection-prayer bubbles out of the way —
in big group content (8-man raids) they can cover the ground tiles you need to see.

The plugin suppresses the vanilla 2D overhead block per player and replaces the prayer
bubble with something subtle — by default an underfoot tile tint coloured by protection
style — while redrawing the 2D elements you still want.

It is **player-only by design**: NPCs keep their vanilla overheads completely untouched.

## Configuration

**Hide vanilla overheads** — independent toggles for your own player, party members,
and other players. `Only while praying` (default on) limits the hiding to players who
currently have an overhead icon, so players who aren't praying keep their vanilla
chat and health bar untouched. Smite, Redemption, and Retribution each have a separate
`Replace` checkbox; these are off by default, leaving their original vanilla icons and
2D elements intact unless the user explicitly opts into replacing them.

**Prayer display** — independently enable an underfoot tile (default), character
outline, and compact copy of the original overhead prayer. The displays can be combined,
including tile + outline. Highlight colours, overall opacity, border/outline widths, and
compact-overhead size are configurable.

**Redraw** — overhead chat text, health bar, hitsplats, and PK skull, each individually
toggleable, with a height offset.

## How it works

A `RenderCallback` registered with `RenderCallbackManager` is consulted for every
renderable each frame. Returning `false` from `addEntity` on a player's `drawingUI` pass
hides that player's entire 2D block — overhead chat, health bar, hitsplats, and the
prayer bubble together, since the API exposes no per-element control. An `ABOVE_SCENE`
overlay then redraws the wanted parts for exactly those players. Non-player renderables
are passed through untouched before any prayer state is even read.

Multiple plugins' render callbacks AND together, so this coexists with core Entity Hider.
The replacement overlay also mirrors Entity Hider's **player** categories: if Entity Hider
hides a player's model or 2D elements, this plugin draws no tile, outline, icon, or other
replacement element for that player. It mirrors those player categories only — Entity
Hider's NPC handling is irrelevant here, because this plugin never touches NPCs.

The plugin only ever reflects prayers that are *already active and visible* — it never
indicates which prayer to use. Redrawn hitsplats reuse the active cache's own hitmark
component sprites, colours, movement, and fade timing. If a future cache revision cannot
be decoded safely, that hitsplat falls back to a simple coloured marker.

## Running / developing

```
./gradlew run
```

launches the full RuneLite client in developer mode with this plugin loaded.
Requires JDK 11+.

## Spike results (2026-07-19)

**Sprite override: NEGATIVE.** Overriding sprite group 440 (`HEADICONS_PRAYER`) via
`Client.getSpriteOverrides()` has no effect on overhead prayer bubbles, including on a
cold boot with the override registered before login. The headicon draw path does not
consult the override map (it presumably loads the group through a bulk loader the hook
does not wrap). Sprite replacement is a dead end for this feature; the draw-listener
approach above replaced it.

**2D draw mask: NEGATIVE.** The renderable listener is a before-draw predicate, not a
before/after pair. Changing `Client#setDraw2DMask` there cannot be restored after one
actor's 2D pass: restoring immediately makes the change ineffective, while restoring
on the next callback leaks the mask across the current actor and potentially later
actors or UI. This also cannot safely compose with Entity Hider or multiple actors in a
frame. The plugin therefore never changes the global mask and keeps its manual redraw.

## Redraw fidelity and API limits

Overhead chat uses RuneLite's RuneScape bold font, vanilla-style yellow text with a
one-pixel black shadow, logical-height projection, and cross-actor collision stacking.
The public `Actor` API does not expose the original chat colour or animation effect, so
redrawn text remains yellow and static. It also exposes no public-chat visibility mode.
To avoid revealing text vanilla may have filtered, remote-player chat is not manually
redrawn; only the local player's own overhead speech is redrawn.

The ordinary health-bar fallback is redrawn as a 30-by-5-pixel pure red/green bar with
safe ratio clamping and a one-pixel minimum for positive health. Cache-defined sprite
bars, interpolation, fading, multiple simultaneous bars, and the definition-to-actor
association are not public API and are intentionally not guessed.
