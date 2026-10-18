# Combat redesign decision record

This records the combat direction established on the `ai` branch. Read it before changing combat rules, turn progression, combat views, or combat AI.

## Direction

The redesign is a staged migration from the original one-player/one-opponent distance combat to multi-combat on a small grid. The old path remains supported. `Fight.isMultiCombat()` selects the new path when `Fight.combatants()` is non-empty.

## Domain decisions

- A multi-combat `Fight` owns the complete encounter state: combatants, each combatant's `AvailableActions`, `activeCombatant`, combat log, delayed effects, and outcome. `Combatant` keeps an id, ally/enemy flag, initiative, character, and `GridPosition`.
- The battlefield is fixed at 12 × 8 cells. Each cell is 5 feet. `GridPosition.distanceInFeet()` uses Chebyshev distance, matching grid adjacency and diagonal movement.
- Movement is one 5-foot cardinal step. Destinations must be inside the grid and unoccupied by a living combatant.
- Legacy scalar positions and relative directions remain accepted for compatibility with older tests and callers. New UI movement is cardinal (`UP`, `DOWN`, `LEFT`, `RIGHT`) and is filtered at the edges before rendering.
- Turns advance through living combatants in list order, wrapping around. A combatant keeps its turn while actions remain; when the player becomes active, living combatants receive fresh actions from their character state.
- A dead player ends the fight even if an ally remains alive.
- Player multi-combat actions validate turn ownership, action/bonus-action availability, spell slots, target identity, and attack range before mutating state. Movement and attacks are saved and broadcast through the existing `PlayerChannel`/SSE flow.
- Delayed effects, such as Melf's Magic Arrow, are stored on the `Fight`, resolve at end turn, decrement their remaining turns, and are removed once applied. Spell slots are consumed when the spell is cast.

## AI behavior

NPC behavior is intentionally simple and random for now:

- Choose an available weapon attack, spell, or 5-foot move.
- Target the nearest living opponent.
- Move toward a target when an attack is out of range.
- Normalize stale attacks to `EndTurn` when the main action is exhausted.
- End the turn when an out-of-range attacker has no movement left, rather than producing invalid negative actions.

This is a heuristic, not a strategic planner. Keep action normalization and range/movement validation at the domain boundary when improving it.

## UI decisions

The combat UI presents target-first attack cards, a battlefield with portraits and initiative, HP progress bars, and movement/turn controls. View encoding maps domain output to Mustache view models; `_combat.html` remains presentation-focused.

## Compatibility seams

- The legacy single-combat path still uses scalar distance and separate enemy-turn logic. Do not assume a grid `Fight` and a legacy `Fight` have identical invariants.
- `Fight` retains legacy fields (`opponent`, player/opponent actions, distance) even for multi-combat because existing output and persistence code still reads them. Any consolidation should be incremental and covered by acceptance tests.

## Executable specification

`MultiCombatAcceptanceTest` covers round action reset, delayed damage and spell-slot consumption, AI action exhaustion and movement limits, target-card rendering, grid rendering, edge-safe movement controls, movement logging, and player-death outcome handling. Add a regression test there before changing combat behavior.

## Current continuation note

The current working tree includes fixes in `DnD.java`, `ViewEncoderDecoder.java`, `_combat.html`, and `MultiCombatAcceptanceTest.java`: AI no longer tries to move after exhausting speed, AI ends its turn when an out-of-range attack cannot be reached, movement buttons are hidden at battlefield edges, and player/target/initiative HP bars are rendered and tested.
