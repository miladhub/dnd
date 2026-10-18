package org.meh.dnd;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Application interactor: the protocol adapters all call the game through here. */
@Singleton
public class GameEngine {
    private static final Logger LOG = Logger.getLogger(GameEngine.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DMChannel dmChannel = new InMemoryThreadedDMChannel(executor);
    private final PlayerChannel playersChannel = new InMemoryThreadedPlayerChannel(executor);
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel, new DndCombat());
    private final Set<Consumer<String>> apiSubscribers = new CopyOnWriteArraySet<>();
    private final DM dm = System.getenv("OPENAI_API_KEY") != null
            && !System.getenv("OPENAI_API_KEY").isBlank()
            ? new AiDM(playersChannel, gameRepository)
            : new SimpleDM(playersChannel, gameRepository);

    @PostConstruct
    void initialize() {
        dmChannel.subscribe(action -> {
            try { dm.process(action); }
            catch (Exception e) { LOG.error(e); }
        });
        playersChannel.subscribe(output -> notifyUpdate(output instanceof CombatOutput
                ? "combat.updated" : "narrative.updated"));
    }

    @PreDestroy
    void shutDown() throws InterruptedException {
        executor.shutdown();
        assert executor.awaitTermination(3, TimeUnit.SECONDS);
    }

    public java.util.Optional<Game> currentGame() { return gameRepository.game(); }
    public ApiModels.State apiState() { return currentGame().map(ApiMapper::state).orElse(null); }
    public void subscribePlayer(Consumer<PlayerOutput> subscriber) { playersChannel.subscribe(subscriber); }
    public void unsubscribePlayer(Consumer<PlayerOutput> subscriber) { playersChannel.unsubscribe(subscriber); }

    public void subscribeApiUpdates(Consumer<String> subscriber) { apiSubscribers.add(subscriber); }
    public void unsubscribeApiUpdates(Consumer<String> subscriber) { apiSubscribers.remove(subscriber); }
    public void notifyUpdate(String type) { for (Consumer<String> subscriber : apiSubscribers) subscriber.accept(type); }

    public String saveGame() throws IOException {
        return currentGame().map(game -> {
            try { return GameSaveLoad.save(game); }
            catch (IOException e) { throw new IllegalStateException(e); }
        }).orElse("");
    }
    public void loadGame(String json) throws IOException { gameRepository.save(GameSaveLoad.load(new StringReader(json))); }
    public void executeApiAction(Actions action) { dnd.doAction(action); }
    public void executeApiCombat(CombatActions action, boolean bonusAction) { dnd.playCombatAction(action, bonusAction); }

    public void createApiCharacter(JsonNode node) {
        String name = node.path("name").asText();
        CharClass charClass = CharClass.valueOf(node.path("class").asText("FIGHTER").toUpperCase());
        int level = node.path("level").asInt(1);
        int maxHp = node.path("maxHp").asInt(10);
        GameChar character = new GameChar(name, level, charClass, maxHp, maxHp,
                node.path("armorClass").asInt(10), DndCombat.xpAtLevel(level), DndCombat.xpAtLevel(level + 1),
                new Stats(node.path("strength").asInt(10), node.path("dexterity").asInt(10),
                        node.path("constitution").asInt(10), node.path("intelligence").asInt(10),
                        node.path("wisdom").asInt(10), node.path("charisma").asInt(10)),
                charClass == CharClass.FIGHTER ? DndCombat.FIGHTER_WEAPONS : DndCombat.WIZARD_WEAPONS,
                charClass == CharClass.FIGHTER ? List.of() : DndCombat.WIZARD_SPELLS,
                new AvailableActions(node.path("actions").asInt(1), node.path("bonusActions").asInt(1), node.path("speed").asInt(30)),
                DndCombat.spellSlots(charClass, level));
        gameRepository.save(GameSaveLoad.createGameFrom(node.path("background").asText(""), node.path("place").asText(""), character));
    }
    public void createApiQuest(JsonNode node) {
        Game game = currentGame().orElseThrow(() -> new IllegalStateException("No character has been created"));
        gameRepository.save(GameSaveLoad.createGameFrom(node.path("background").asText(""), node.path("place").asText(game.place()), game.playerChar()));
    }
    public void apiLevelUp() { gameRepository.save(g -> g.withPlayerChar(DndCombat.levelUp(g.playerChar()))); }
    public void save(Game game) { gameRepository.save(game); }
    void clearGame() { gameRepository.save((Game) null); }
    public void update(java.util.function.UnaryOperator<Game> update) { gameRepository.save(update); }
    public java.util.Optional<PlayerOutput> enter() { return dnd.enter(); }
    public void doAction(Actions action) { dnd.doAction(action); }
    public void playCombatAction(CombatActions action, boolean bonusAction) { dnd.playCombatAction(action, bonusAction); }
}
