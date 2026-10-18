# Godot integration

This folder contains the machine-client contract between the Java D&D engine and
external renderers such as Godot 4.

The Java process is authoritative: it owns game state, rules, validation,
combat, AI, quests, and persistence. Godot is a client and renderer. The
existing browser UI remains supported through the original HTML and SSE
endpoints.

Start with [api-v1.md](api-v1.md). The shortest Godot onboarding instructions
are in [godot-handoff.md](godot-handoff.md).

## Local server

```sh
mvn quarkus:dev
```

The default base URL is `http://localhost:8080`.

OpenAI is optional for integration work. Without `OPENAI_API_KEY`, the server
uses the deterministic/simple DM implementation.

## cURL example

With the server running on `localhost:8080`, the following creates a character,
reads the current state, starts the adventure, explores a location, and saves
the game:

```sh
base=http://localhost:8080

# Create a character and game.
curl -sS "$base/api/v1/character" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Aria",
    "class": "fighter",
    "place": "The forest",
    "background": "A lost adventurer",
    "maxHp": 12,
    "armorClass": 15,
    "strength": 16,
    "dexterity": 12,
    "constitution": 14,
    "actions": 1,
    "bonusActions": 1,
    "speed": 30
  }' | jq

# Fetch the complete authoritative renderer state.
curl -sS "$base/api/v1/state" | jq

# Start the adventure.
curl -sS "$base/api/v1/commands" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "action",
    "action": "start",
    "place": "The forest"
  }' | jq

# Explore a new location.
curl -sS "$base/api/v1/commands" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "action",
    "action": "explore",
    "place": "The ruined tower"
  }' | jq

# Download the current save document.
curl -sS "$base/api/v1/save" -o game.json

# Load a previously saved game document.
curl -sS "$base/api/v1/load" \
  -H 'Content-Type: application/json' \
  --data-binary @game.json | jq
```

`jq` is optional; remove `| jq` if it is not installed. A successful command
returns `accepted: true` and the resulting `state`. Invalid commands return HTTP
`400` with `accepted: false` and an error message.

For realtime updates, use a WebSocket-capable client such as Godot, `websocat`,
or `wscat`; standard cURL is used here for the request/response API and cannot
maintain the WebSocket event stream.

### Connecting to the WebSocket from a terminal

`websocat` is a convenient terminal WebSocket client:

```sh
brew install websocat
websocat ws://localhost:8080/api/v1/events
```

Alternatively, use `wscat` through Node.js:

```sh
npm install -g wscat
wscat -c ws://localhost:8080/api/v1/events
```

After connecting, the server sends a `state.snapshot` event. You can paste JSON
commands into the interactive client, for example:

```json
{
  "type": "action",
  "action": "rest"
}
```

Create a character first if the server has no active game; the character
creation cURL example above can be used for that.

### Complete WebSocket client flow

The WebSocket is the gameplay connection. Character creation is currently an
HTTP operation, so a new client should use this sequence:

1. Start the Java server.
2. Create a character with `POST /api/v1/character`.
3. Connect to `ws://localhost:8080/api/v1/events`.
4. Read the initial `state.snapshot` event and use its `state` as the complete
   authoritative client state.
5. Send an `action/start` command to begin the adventure.
6. Render each subsequent event and send further actions selected from the
   current state.

Create the character before opening the WebSocket:

```sh
curl -sS http://localhost:8080/api/v1/character \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Aria",
    "class": "fighter",
    "place": "The forest",
    "background": "A lost adventurer",
    "maxHp": 12,
    "armorClass": 15,
    "strength": 16,
    "dexterity": 12,
    "constitution": 14,
    "actions": 1,
    "bonusActions": 1,
    "speed": 30
  }'
```

After connecting, the first frame is an event envelope similar to:

```json
{
  "protocol": "dnd",
  "version": 1,
  "sequence": 1,
  "type": "state.snapshot",
  "state": { "mode": "EXPLORING", "player": { "name": "Aria" } }
}
```

Send this command to start the adventure. The `place` value should be the
character's current place or another place selected by the client:

```json
{
  "type": "action",
  "action": "start",
  "place": "The forest"
}
```

The requesting connection receives a `command.accepted` event, followed by a
`narrative.updated` event when the DM produces the result. The client should
replace its local state with the `state` from each event rather than trying to
apply game rules locally.

Gameplay command examples:

```json
{"type":"action","action":"explore","place":"The ruined tower"}
{"type":"action","action":"dialogue","info":"Eldric"}
{"type":"action","action":"say","info":"I need information."}
{"type":"action","action":"end_dialogue"}
{"type":"action","action":"rest"}
```

Combat commands use the `combat` type:

```json
{"type":"combat","action":"melee","target":"wolf-id"}
{"type":"combat","action":"spell","info":"Fire Bolt","target":"wolf-id"}
{"type":"combat","action":"move","direction":"UP","amount":5}
{"type":"combat","action":"end_turn"}
```

For an accepted command, wait for the `command.accepted` event and then the
resulting `narrative.updated` or `combat.updated` event. Invalid commands are
returned as a JSON object with `accepted: false`, `error`, and the current
`state`; they are not event envelopes. A WebSocket client should display the
error and keep its previous state unless the response contains a newer state.

If the connection drops, reconnect and use the new `state.snapshot` as the
authoritative state. Events are snapshots, not a replayable event log, so do
not attempt to recover missing events from sequence numbers. Save and load are
currently HTTP operations at `/api/v1/save` and `/api/v1/load`.

### SSE and WebSocket are not interchangeable

Both transports push server updates, but they serve different client contracts:

|                 | SSE                                 | WebSocket                                 |
|-----------------|-------------------------------------|-------------------------------------------|
| Direction       | Server to client                    | Client to server and server to client     |
| Endpoint        | `/updates`                          | `/api/v1/events`                          |
| Current payload | Browser-oriented HTML fragments     | JSON state/event envelopes                |
| Commands        | Separate browser HTTP form requests | JSON commands can use the same connection |
| Intended client | Existing browser UI                 | Godot and other machine clients           |

The browser SSE stream is therefore not the Godot integration contract. The
browser receives rendered HTML and submits actions through the existing form
endpoints; Godot receives structured JSON and sends structured commands.

### Observing the browser SSE stream with cURL

The existing browser UI receives HTML fragments from `/updates` using
Server-Sent Events. You can observe that stream with cURL:

```sh
base=http://localhost:8080

# Keep the connection open and print streamed chunks immediately.
curl -N -sS \
  -H 'Accept: text/event-stream' \
  "$base/updates"
```

While this command is running, submit a browser action or an HTTP command from
another terminal. The response will receive the rendered HTML fragment generated
for the browser UI. This stream is not the renderer-neutral JSON protocol and
should not be used as the Godot integration contract.

## Multiple clients connected to one game

A browser and a Godot instance can connect to the same Java process and observe
the same in-memory game:

```text
Browser ── SSE /updates ───────────┐
                                   ├── Java game engine ── one shared Game
Godot ── WebSocket /api/v1/events ─┘
```

Both clients receive gameplay updates from the same `PlayerChannel`. Therefore,
exploration, dialogue, rest, combat actions, enemy turns, and AI narrative
updates produced by either client are visible to the other client.

Both clients may also issue commands. The Java engine remains authoritative and
validates every command before changing the game state. The clients should never
assume that a locally displayed action has succeeded until a resulting state
event is received.

### Current limitations

- There is one active game per Java process.
- There are no client identities, permissions, or multiplayer session IDs.
- Simultaneous commands from browser and Godot compete for the same game state
  and may be processed in sequence order determined by the server.
- Gameplay output is broadcast to both clients, but some lifecycle
  operations—such as browser form-based character creation, browser level-up,
  and browser save/upload—do not currently publish a machine-client event.
- A reconnecting client receives a fresh snapshot, but there is no durable event
  replay.

For the current prototype, it is safest to use one client as the controller and
the other as a viewer. The next synchronization improvement would be to route
every state-changing operation through one event gateway, including browser
forms and save/load, and to add explicit client/session ownership if
simultaneous control is required.

## Design rules

- Treat every state snapshot as authoritative.
- Render only what is present in `state` and what is allowed by `actions`.
- Do not reproduce Java combat or quest rules in Godot.
- Use `sequence` to detect ordering and reconnect with a fresh snapshot when
  needed.
- Keep protocol parsing independent from scene and animation code.
