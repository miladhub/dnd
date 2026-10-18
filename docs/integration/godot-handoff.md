# Godot client handoff

## First implementation slice

Build the Godot client in this order:

1. Create a typed protocol layer for `State`, `Character`, `Narrative`, `Combat`, `Combatant`, `Target`, `Action`, and `Event`.
2. Connect to `ws://localhost:8080/api/v1/events`.
3. Store the latest `state` from the initial `state.snapshot` event.
4. Render a debug screen showing mode, location, player HP, narrative text, and combatants.
5. Add buttons that send the `Action` commands described in [api-v1.md](api-v1.md).
6. Add scene transitions and animation only after state updates are reliable.

## Suggested Godot boundaries

- `DndApiClient`: HTTP and WebSocket transport only.
- `DndProtocol`: JSON parsing and typed dictionaries/resources.
- `GameStore`: latest authoritative state and sequence number.
- `GamePresenter`: converts state into scene/UI updates.
- Combat and narrative scenes: presentation and input only; they do not calculate rules.

## Reconnection

When the socket closes:

1. Reconnect to the WebSocket.
2. Replace local state with the new `state.snapshot`.
3. Ignore any local pending animation that no longer matches the snapshot.

There is no event replay API yet.

## Ready-to-send Codex prompt

```text
We are building a Godot 4 desktop client for the Java D&D engine.

Read:
- docs/integration/README.md
- docs/integration/api-v1.md
- docs/integration/godot-handoff.md

The Java server is authoritative. Do not reimplement combat, quests, movement, spell slots, or validation in Godot.

Implement the client in vertical slices:
1. typed protocol models;
2. HTTP/WebSocket client;
3. GameStore containing the latest State;
4. debug rendering of narrative and combat state;
5. commands driven by the server-provided actions.

Connect to ws://localhost:8080/api/v1/events and use the exact JSON contract in docs/integration/api-v1.md.
Keep networking, protocol parsing, state storage, and presentation in separate scripts.
Do not change the Java server unless a concrete contract mismatch is found; report mismatches first.
```
