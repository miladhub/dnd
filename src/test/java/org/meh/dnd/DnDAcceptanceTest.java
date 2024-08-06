package org.meh.dnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.meh.dnd.GameMode.*;

class DnDAcceptanceTest
{
    public static final String GAME_ID = "42";
    private final DMChannel dmChannel = new InMemoryDMChannel();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel);
    private final PlayerOutput exploring = new ExploreOutput(
            "You are exploring the Dark Forest, what do you do?",
            List.of(new Explore(""), new Rest()));
    private final List<PlayerOutput> playerOutputs = new ArrayList<>();
    private final PlayerOutput seeGoblin = new ExploreOutput(
            "You see a goblin, what do you do?",
            List.of(new Attack("goblin"), new Dialogue("goblin")));
    private final GameChar goblin = new GameChar("goblin", List.of(new Weapon("sword")), List.of());
    private final CombatOutput combatGoblin = new CombatOutput(true, goblin, "");
    private final RestOutput rest = new RestOutput();
    private final DialogueOutput speakWithGoblin =
            new DialogueOutput("hey there", List.of(new Say("hi"), new Say("what?")));
    private final DialogueOutput answerByGoblin =
            new DialogueOutput("I said, hey there", List.of(new Say("hi")));
    private final CombatOutput meleeOutput = new CombatOutput(
            false,
            goblin,
            "Foo: melee attack with sword");

    @BeforeEach
    void setUp() {
        playersChannel.subscribe(GAME_ID, playerOutputs::add);
    }

    @Test
    void player_sees_last_output_when_entering() {
        startWith(exploring, new Peace(), EXPLORING);
        PlayerOutput output = dnd.enter(GAME_ID);
        assertEquals(exploring, output);
    }

    @Test
    void explore_continue_exploring() {
        startWith(exploring, new Peace(), EXPLORING);

        dmOutcome(seeGoblin);
        dnd.playTurn(GAME_ID, new Explore(""));

        assertThat(playerOutputs, contains(seeGoblin));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_then_rest() {
        startWith(exploring, new Peace(), EXPLORING);

        dnd.playTurn(GAME_ID, new Rest());

        assertThat(playerOutputs, contains(rest));

        assertEquals(
                Optional.of(RESTING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_dialogue() {
        startWith(seeGoblin, new Peace(), EXPLORING);
        dmOutcome(speakWithGoblin);

        dnd.playTurn(GAME_ID, new Dialogue("goblin"));

        assertThat(playerOutputs, contains(speakWithGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void dialogue_say() {
        startWith(speakWithGoblin, new Peace(), EXPLORING);
        dmOutcome(answerByGoblin);

        dnd.playTurn(GAME_ID, new Say("goblin"));

        assertThat(playerOutputs, contains(answerByGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void dialogue_end_dialogue() {
        startWith(answerByGoblin, new Peace(), EXPLORING);
        dmOutcome(exploring);

        dnd.playTurn(GAME_ID, new EndDialogue());

        assertThat(playerOutputs, contains(exploring));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_attack() {
        startWith(seeGoblin, new Peace(), EXPLORING);

        dnd.playTurn(GAME_ID, new Attack("goblin"));

        assertThat(playerOutputs, contains(combatGoblin));

        assertEquals(
                new Fight(true, goblin, ""),
                game().combatStatus());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_melee_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, ""), COMBAT);

        dnd.combatTurn(GAME_ID, new MeleeAttack("sword"));

        assertEquals(
                new Fight(false, goblin, meleeOutput.lastAction()),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, goblin, "Foo: melee attack with sword")));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    private void dmOutcome(
            PlayerOutput output
    ) {
        dmChannel.subscribe(GAME_ID, pi -> playersChannel.post(GAME_ID, output));
    }

    private Game game() {
        return gameRepository.gameById(GAME_ID).orElseThrow();
    }

    private void startWith(
            PlayerOutput lastOutput,
            CombatStatus combatStatus,
            GameMode gameMode
    ) {
        Game game = new Game(
                GAME_ID,
                gameMode,
                lastOutput,
                new GameChar("Foo", List.of(new Weapon("sword")), List.of()),
                combatStatus
        );
        gameRepository.save(game);
    }
}