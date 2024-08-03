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
            List.of(new Explore(), new Rest()));
    private final List<PlayerOutput> x_outputs = new ArrayList<>();
    private final PlayerOutput seeGoblin = new ExploreOutput(
            "You see a goblin, what do you do?",
            List.of(new Attack("goblin"), new Dialogue()));
    private final CombatOutput combatGoblin = new CombatOutput("goblin");
    private final RestOutput rest = new RestOutput();

    @BeforeEach
    void setUp() {
        playersChannel.subscribe(GAME_ID, x_outputs::add);
    }

    @Test
    void player_sees_last_output_when_entering() {
        startWith(exploring);
        PlayerOutput output = dnd.enter(GAME_ID);
        assertEquals(exploring, output);
    }

    @Test
    void play_turn_explore_continue_exploring() {
        startWith(exploring);

        dmOutcome(seeGoblin);
        dnd.playTurn(GAME_ID, new Explore());

        assertThat(x_outputs, contains(seeGoblin));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
        assertEquals(
                Optional.of(seeGoblin),
                gameRepository.gameById(GAME_ID).map(Game::lastOutput));
    }

    @Test
    void play_turn_explore_then_rest() {
        startWith(exploring);

        dnd.playTurn(GAME_ID, new Rest());

        assertThat(x_outputs, contains(rest));

        assertEquals(
                Optional.of(RESTING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
        assertEquals(
                Optional.of(rest),
                gameRepository.gameById(GAME_ID).map(Game::lastOutput));
    }

    @Test
    void play_turn_explore_attack() {
        startWith(seeGoblin);

        dnd.playTurn(GAME_ID, new Attack("goblin"));

        assertThat(x_outputs, contains(combatGoblin));

        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
        assertEquals(
                Optional.of(combatGoblin),
                gameRepository.gameById(GAME_ID).map(Game::lastOutput));
    }

    private void dmOutcome(
            PlayerOutput output
    ) {
        dmChannel.subscribe(GAME_ID, pi -> {
            gameRepository.save(GAME_ID, g -> g.withLastOutput(output));
            playersChannel.post(GAME_ID, output);
        });
    }

    private void startWith(PlayerOutput lastOutput) {
        Game game = new Game(
                GAME_ID,
                GameMode.EXPLORING,
                lastOutput
        );
        gameRepository.save(game);
    }
}