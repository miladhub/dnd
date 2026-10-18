# D&D machine-client API v1

This is the current protocol implemented by `ApiServer`, `WebSocketServer`, `ApiCommand`, `ApiMapper`, and `ApiModels`.

## Transport

HTTP base URL:

```text
http://localhost:8080
```

WebSocket URL:

```text
ws://localhost:8080/api/v1/events
```

All HTTP request and response bodies use JSON except `/api/v1/save`, which returns the JSON save document as a downloadable response. The WebSocket carries text frames containing JSON.

The protocol is currently single-player and single-game per Java process. There are no session IDs, authentication, player IDs, or multiplayer permissions.

## HTTP endpoints

### `GET /api/v1/state`

Returns the complete current renderer state.

- `200`: `State`
- `404`: no character/game has been created

Example response shape:

```json
{
  "mode": "EXPLORING",
  "place": "The forest",
  "background": "A lost adventurer",
  "diary": [],
  "quests": [],
  "player": {},
  "narrative": {},
  "combat": null
}
```

### `POST /api/v1/character`

Creates or replaces the current character and game.

Request:

```json
{
  "name": "Aria",
  "class": "fighter",
  "level": 1,
  "place": "The forest",
  "background": "A lost adventurer",
  "maxHp": 12,
  "armorClass": 15,
  "strength": 16,
  "dexterity": 12,
  "constitution": 14,
  "intelligence": 10,
  "wisdom": 10,
  "charisma": 10,
  "actions": 1,
  "bonusActions": 1,
  "speed": 30
}
```

Only `name`, `class`, `place`, and `background` are semantically required. Defaults are applied for omitted numeric values. `class` is `fighter` or `wizard`.

Returns `200` with `State` and broadcasts a `state.created` event.

### `POST /api/v1/quest`

Starts/replaces a quest using the existing player character.

Request:

```json
{
  "place": "The ruined tower",
  "background": "Find the missing scholar"
}
```

Requires an existing character. Returns `200` with `State`, or `400` if no character exists.

### `POST /api/v1/level-up`

Levels up the current character.

Requires an existing game. Returns `200` with `State` and broadcasts `level-up`.

### `POST /api/v1/commands`

Executes one authoritative game command.

Success response:

```json
{
  "accepted": true,
  "error": "",
  "state": {}
}
```

Invalid or rejected commands return `400`:

```json
{
  "accepted": false,
  "error": "human-readable error",
  "state": {}
}
```

The command is one of the following.

#### Narrative/action commands

```json
{"type":"action","action":"start","place":"The forest"}
{"type":"action","action":"explore","place":"The ruined tower"}
{"type":"action","action":"rest"}
{"type":"action","action":"say","info":"Hello there"}
{"type":"action","action":"end_dialogue","info":""}
```

For dialogue and attack actions, `info` uses the internal encounter identifier format:

```json
{"type":"action","action":"dialogue","info":"HUMANOID_Merchant"}
{"type":"action","action":"attack","info":"GOBLIN_Goblin Chief"}
```

The prefix is an `NpcType` enum value. The suffix is the NPC name.

#### Combat commands

```json
{
  "type":"combat",
  "action":"melee",
  "info":"Longsword",
  "target":"goblin-1",
  "bonusAction":false
}
```

```json
{
  "type":"combat",
  "action":"spell",
  "info":"Fire Bolt",
  "target":"goblin-1",
  "bonusAction":false
}
```

```json
{
  "type":"combat",
  "action":"move",
  "direction":"UP",
  "amount":5,
  "bonusAction":false
}
```

```json
{"type":"combat","action":"end_turn"}
```

Valid movement directions are `UP`, `DOWN`, `LEFT`, and `RIGHT`. Movement is normally one 5-foot step. The server validates boundaries, occupancy, turn ownership, action availability, range, targets, spell slots, and combat state.

### `GET /api/v1/save`

Returns the current JSON save document. If no game exists, the response body is empty.

### `POST /api/v1/load`

Replaces the current game from a JSON save document. The request body is the raw save JSON, not an envelope. Returns `200` with `State` and broadcasts `state.loaded`.

## WebSocket protocol

Connect to `/api/v1/events`. On connection, the server sends one `state.snapshot` event. A client should use this as its initial state.

### Event envelope

```json
{
  "protocol":"dnd",
  "version":1,
  "sequence":12,
  "type":"combat.updated",
  "state": {}
}
```

Fields:

| Field | Type | Meaning |
|---|---|---|
| `protocol` | string | Always `dnd` for this protocol. |
| `version` | integer | Envelope version, currently `1`. |
| `sequence` | integer | Server-wide increasing event number. |
| `type` | string | Event category. |
| `state` | `State` or `null` | Complete current state snapshot. |

Current event types:

| Event | Meaning |
|---|---|
| `state.snapshot` | Initial snapshot after WebSocket connection. |
| `state.created` | Character or quest created. |
| `state.loaded` | Save loaded. |
| `level-up` | Character level increased. |
| `command.accepted` | A WebSocket command was accepted. |
| `combat.updated` | A combat output was produced. |
| `narrative.updated` | Exploration, dialogue, or rest output was produced. |

The current implementation sends complete snapshots rather than replayable event history. After reconnecting, use the new `state.snapshot`; do not attempt to replay missing sequence numbers.

### WebSocket commands

The WebSocket accepts the same command JSON as `POST /api/v1/commands`.

For an accepted command, the requesting connection receives a `command.accepted` event and all connected clients receive the resulting output event when the engine publishes it. Invalid commands receive a `CommandResult` JSON object with `accepted: false` rather than an event envelope.

## State schema

### `State`

| Field | Type | Notes |
|---|---|---|
| `mode` | string | Current mode, such as `EXPLORING`, `DIALOGUE`, `COMBAT`, or `RESTING`. |
| `place` | string | Current location. |
| `background` | string | Character/adventure background. |
| `diary` | string[] | Story-line entries accumulated by the engine. |
| `quests` | `Quest[]` | Current quest goals. |
| `player` | `Character` | Player character. |
| `narrative` | `Narrative|null` | Present for exploration/dialogue/rest output. |
| `combat` | `Combat|null` | Present for combat output. |

`narrative` and `combat` are mutually exclusive in normal output. A client should still tolerate either being null during transitions.

### `Character`

Contains `name`, `level`, `charClass`, `ac`, `hp`, `maxHp`, `xp`, `nextXp`, `stats`, `weapons`, `spells`, `availableActions`, and `spellSlots`.

`charClass` is lowercase (`fighter` or `wizard`). Weapon `damage` values are die names such as `D8`; spell slots use `level1` through `level9`.

### `Narrative`

```json
{
  "type":"explore",
  "text":"A cold wind moves through the trees.",
  "choices": [],
  "storyLine":"",
  "interactionTarget":null
}
```

`type` is `explore`, `dialogue`, or `rest`. Each choice is an `Action` with a renderer-neutral `type`, `info`, `target`, `direction`, `amount`, and `bonusAction`.

### `Combat`

Contains:

- `playerTurn`, `playerWon`, `enemyWon`;
- `activeCombatant`;
- legacy scalar `distance`;
- active `availableActions`;
- newest-first combat `log` entries;
- `combatants` with IDs, characters, ally flags, initiative, grid positions, actions, active/dead flags;
- `targets` with legal attack actions;
- available combat `actions`.

The current multi-combat grid is 12 columns by 8 rows. Coordinates are zero-based and each cell represents 5 feet.

## Error handling

- HTTP command validation errors use status `400` and include a human-readable error string.
- Missing game state uses `404` for `GET /state`.
- Unexpected server failures may use standard Quarkus `500` responses.
- A client should not treat an error response as a state update unless it contains a non-null `state`.

## Current limitations

- One active game per process; no sessions or multiplayer.
- State is in memory except explicit save/load.
- WebSocket updates contain snapshots, not durable event replay.
- Event types identify broad output categories. Detailed attack/damage animation metadata is currently represented through combat state and log text, not a separate effect list.
- The API is versioned as `/api/v1`, but automated JSON Schema generation is not yet included.
