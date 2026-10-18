DnD™-like app using ChatGPT as the Dungeon Master
===

*Disclaimer* - This is a hobby-time pet project, run it at your own risk.

# Pre-requisites

* [JDK 25 or newer](https://www.oracle.com/java/technologies/downloads/)
* [OpenAI](http://openai.com) key (optional; only needed for the OpenAI-backed DM)

# Building

Building requires [Maven](https://maven.apache.org):

```shell
mvn clean install
```

# Distribution

To give someone a runnable copy of the backend, build the project and provide
the complete distribution archive:

```shell
mvn clean package
```

The archive is created at:

```text
target/dnd-1.0-SNAPSHOT-dist.zip
```

It contains the runnable JAR and all of the Quarkus application files it needs.
Do not send `quarkus-run.jar` by itself; keep the extracted `app`, `lib`, and
`quarkus` folders alongside it.

The recipient needs a [JDK 25 or newer](https://www.oracle.com/java/technologies/downloads/).
They do not need Maven or the project source code.

## Windows

Extract the ZIP to a folder, open PowerShell in that folder, and run:

```powershell
java -version
java -jar .\quarkus-run.jar
```

The backend listens on port `8080` and is available at
<http://localhost:8080/>. A Godot client running on the same computer should
connect to `ws://localhost:8080/api/v1/events`.

An OpenAI API key is only needed when using the OpenAI-backed DM. Without one,
the backend uses the deterministic/simple DM implementation.

# Running on a server

To install and run the game on a server, expand the contents of file
`target/dnd-*-dist.zip` to a folder of your choice - e.g., `~/dnd`:

```shell
unzip target/dnd-*-dist.zip -d ~
mv ~/dnd-*-SNAPSHOT ~/dnd
```

To start the game, issue:

```shell
cd ~/dnd
export OPENAI_API_KEY=...
java -jar quarkus-run.jar
```

The game is accessible at <http://localhost:8080/>.

# Running in dev mode

This will re-build the project, run the tests and execute the game engine:

```shell
export OPENAI_API_KEY=...
mvn quarkus:dev
```

The game server will listen on port `8080` and the debug port is `5005`.

Note that this will listen only on `localhost`. To listen on all IPs, add
`-Dquarkus.http.host=0.0.0.0` the the above command, see
<https://quarkus.io/guides/all-config#quarkus-vertx-http_quarkus.http.host>.

# Running in production mode

```shell
export OPENAI_API_KEY=...
java -jar target/quarkus-app/quarkus-run.jar
```

The game server will listen on port `8080` on all IPs (i.e., on `0.0.0.0`).

This runs it in debug:

```bash
export OPENAI_API_KEY=...
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar target/quarkus-app/quarkus-run.jar
```

# One session, two clients: web and Godot

## Integration architecture

`ApiServer` is the REST/HTTP inbound adapter and `WebSocketServer` is the WebSocket
inbound adapter. Both delegate through the shared `GameEngine` interactor to the
authoritative game engine:

```text
Browser ──────> HypermediaServer ──┐
REST clients ─> ApiServer ─────────┼──> GameEngine ──> DnD
Godot WS ─────> WebSocketServer ───┘
```

The browser and Godot are two renderers over the same authoritative Java game
engine. They follow the same gameplay sequence, but use different protocols:

| Session step     | Browser                                            | Godot                                                              |
|------------------|----------------------------------------------------|--------------------------------------------------------------------|
| Create character | `POST /character` with an HTML form                | `POST /api/v1/character` with JSON                                 |
| Observe state    | `GET /game` returns rendered HTML                  | `GET /api/v1/state` or the initial WebSocket snapshot returns JSON |
| Send a command   | `POST /actions` or `POST /combat` with form fields | JSON command over `POST /api/v1/commands` or the WebSocket         |
| Receive updates  | SSE `GET /updates` sends HTML fragments            | WebSocket `/api/v1/events` sends JSON event envelopes              |
| Render           | Browser swaps the HTML fragment into the page      | Godot updates its scene from the latest `state`                    |

The game rules are not duplicated in either client. The Java server validates
commands, changes the in-memory game, and publishes the resulting state. A
client should wait for the resulting update before treating a command as
successful.

## Example: start and explore

In the browser, character creation is an HTML form. A simplified request looks
like this:

```sh
curl -X POST http://localhost:8080/character \
  -d 'name=Aria' -d 'class=fighter' -d 'level=1' \
  -d 'max_hp=12' -d 'armor_class=15' \
  -d 'strength=16' -d 'dexterity=12' -d 'constitution=14' \
  -d 'intelligence=10' -d 'wisdom=10' -d 'charisma=10' \
  -d 'actions=1' -d 'bonus_actions=1' -d 'speed=30' \
  -d 'background=A lost adventurer' -d 'place=The forest'
```

The browser then opens `/game` and keeps `/updates` open as an SSE stream.
When the player clicks an exploration choice, the browser submits a form such
as:

```http
POST /actions
Content-Type: application/x-www-form-urlencoded

action=Explore&info=The+ruined+tower
```

The response is not the new game screen. The server publishes an HTML fragment
on the already-open `/updates` stream, and HTMX replaces the current game view.

Starting the adventure follows the same browser pattern:

```http
POST /actions
Content-Type: application/x-www-form-urlencoded

action=Start&info=The+forest
```

Godot performs the same steps with the machine-client API. It creates the
character using JSON, then connects to the WebSocket:

```json
POST /api/v1/character
Content-Type: application/json

{
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
}
```

```text
WebSocket: ws://localhost:8080/api/v1/events
```

The first frame is a complete `state.snapshot`. Godot sends the exploration
command on that same connection. Starting the adventure would be:

```json
{
  "type": "action",
  "action": "start",
  "place": "The forest"
}
```

After the resulting state event, Godot can explore:

```json
{
  "type": "action",
  "action": "explore",
  "place": "The ruined tower"
}
```

The server replies with a JSON event containing the complete authoritative
state, for example:

```json
{
  "protocol": "dnd",
  "version": 1,
  "sequence": 4,
  "type": "narrative.updated",
  "state": {
    "mode": "EXPLORING",
    "place": "The ruined tower",
    "narrative": {
      "type": "explore",
      "text": "...",
      "choices": []
    }
  }
}
```

Godot stores `state`, updates its scene, and uses the `choices` supplied by the
server to decide which buttons to show. It does not calculate quests or
narrative transitions locally.

## Example: combat

The combat sequence is also equivalent. In the browser, an attack card submits
the target and action as form fields:

```http
POST /combat
Content-Type: application/x-www-form-urlencoded

action=Melee&info=Longsword&target=goblin-1&bonus=false
```

The browser receives the updated combat HTML through `/updates`, including the
battlefield, initiative list, HP bars, legal targets, and movement controls.
To move, it submits another form:

```http
POST /combat
Content-Type: application/x-www-form-urlencoded

action=MoveUp&info=5&target=&bonus=false
```

Godot sends the renderer-neutral equivalent over WebSocket:

```json
{
  "type": "combat",
  "action": "melee",
  "info": "Longsword",
  "target": "goblin-1",
  "bonusAction": false
}
```

For movement:

```json
{
  "type": "combat",
  "action": "move",
  "direction": "UP",
  "amount": 5,
  "bonusAction": false
}
```

The resulting `combat.updated` event contains the complete combat snapshot:
active combatant, available actions, combat log, combatants and grid positions,
legal targets, and the new HP values. If the command is illegal—for example,
the target is out of range—the server returns `accepted: false` with an error;
the client must keep displaying the last accepted state.

The browser protocol is therefore HTML-over-SSE plus form POSTs, while the
Godot protocol is JSON-over-WebSocket (with equivalent JSON HTTP endpoints).
They are different client contracts over the same game state, not two separate
implementations of the game.
See [docs/integration/api-v1.md](docs/integration/api-v1.md)
for the complete Godot contract
and [docs/integration/godot-handoff.md](docs/integration/godot-handoff.md)
for client architecture guidance.
