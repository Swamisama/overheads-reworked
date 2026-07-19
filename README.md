# Prayer Overheads Reworked

A RuneLite plugin that gets the large overhead protection-prayer bubbles out of the way —
in big group content (8-man raids) they can cover the ground tiles you need to see.

The plugin suppresses the vanilla 2D overhead block per actor and replaces the prayer
bubble with something subtle — by default an underfoot tile tint coloured by protection
style — while redrawing the 2D elements you still want.

## Configuration

**Hide vanilla overheads** — independent toggles for your own player, party members,
other players, and NPCs. `Only while praying` (default on) limits the hiding to actors
that currently have an overhead icon, so actors that aren't praying keep their vanilla
chat and health bar untouched.

**Prayer display** — `Tile` (default), `Outline`, `Mini icon`, or `None`, with
configurable colours for melee / missiles / magic and for the non-protection overheads.

**Redraw** — overhead chat text, health bar, and PK skull, each individually toggleable,
with a height offset.

## How it works

`Hooks.RenderableDrawListener` is called for every renderable each frame. Returning
`false` on an actor's `drawingUI` pass hides its entire 2D block — overhead chat, health
bar, and the prayer bubble together, since the API exposes no per-element control. An
`ABOVE_SCENE` overlay then redraws the wanted parts for exactly those actors.

Multiple plugins' draw listeners AND together, so this coexists with core Entity Hider.

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
