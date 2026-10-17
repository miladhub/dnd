# DnD — Agent Guide

## Principles

- Keep it simple, no overengineering.
- Think first, and only then implement.
- Architecture style: hexagonal.
- Use pure functions as much as possible (imperative shell, functional core style).
- Use TDD as much as possible especially for inner layer.
- Create integration tests for outer layers.
- Create acceptance tests on middle layers, these should mostly be ECB "Control" classes (use cases).
- Ensure type safety all the way.
- When finding a bug, create a non-regression automated test first.

## Stack

- Java 21 or higher, Quarkus, Maven.
- LangChain4j (`quarkus-langchain4j-openai`) for AI DM.
- Mustache (`_template.html`, `_combat.html`, `_levelup.html`) for server-side HTML.
- Htmx + SSE (`/updates`) for real-time UI.
- Tailwind CSS (inline in templates).
- Any custom UI element that cannot be done via standard HTML must be placed into its own self-contained web component.
- Jackson for JSON save/load.
- In-memory state only — no database.

## Structure

- Single module, single package: `org.meh.dnd` (90 source files, flat).
- Entrypoint: `RestServer.java` — JAX-RS resource, manual wiring (no `@Inject`).
- Game loop: `DnD.java` orchestrates actions/combat.
- AI narrative: `AiDM.java` via LangChain4j `AiServices`.
- Persistence: `GameSaveLoad.java` — JSON export/import via `/save` and `/upload`.

## Commands
```sh
# build + test (requires JDK 21)
mvn clean install

# dev mode with hot reload (requires OPENAI_API_KEY)
export OPENAI_API_KEY=...
mvn quarkus:dev

# run production jar
java -jar target/quarkus-app/quarkus-run.jar

# run a single test class
mvn test -Dtest=DnDAcceptanceTest
```

## Testing

- `DnDAcceptanceTest` — main test suite (1165 lines), pure unit tests with `InMemoryChannel`/`InMemoryDMChannel`/`InMemoryPlayerChannel`. No env vars needed.
- `RestServerTest` — `@QuarkusTest` using REST Assured on `/hello`
- `RestServerIT` — `@QuarkusIntegrationTest` (packaged mode)
- `DiceTest`, `DndCombatTest`, `AiEntitiesTest`, `GameSaveLoadTest`
- Surefire for unit tests, Failsafe for integration tests (`*IT.java`)

## Quirks

- `OPENAI_API_KEY` env var required for AI DM to work (dev mode, production). Tests with mock channels don't need it.
- Debug port `5005` in dev mode
- Default bind: `localhost:8080`. Use `-Dquarkus.http.host=0.0.0.0` for all interfaces.
- `-parameters` compiler flag enabled (method parameter names retained)
- Quarkus profiles: `native` (activated via `-Dnative`)
- `.drawio.*` files are gitignored
- No CI/CD, no pre-commit hooks, no linter/formatter config
