package org.meh.dnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.smallrye.common.constraint.Assert.assertTrue;
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
        startWith(exploring);
        PlayerOutput output = dnd.enter(GAME_ID);
        assertEquals(exploring, output);
    }

    @Test
    void explore_continue_exploring() {
        startWith(exploring);

        dmOutcome(seeGoblin);
        dnd.playTurn(GAME_ID, new Explore(""));

        assertThat(playerOutputs, contains(seeGoblin));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_then_rest() {
        startWith(exploring);

        dnd.playTurn(GAME_ID, new Rest());

        assertThat(playerOutputs, contains(rest));

        assertEquals(
                Optional.of(RESTING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_attack() {
        startWith(seeGoblin);

        dnd.playTurn(GAME_ID, new Attack("goblin"));

        assertThat(playerOutputs, contains(combatGoblin));

        assertEquals(
                new Fight(true, goblin, ""),
                game().fightStatus());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_melee() {
        startWith(combatGoblin);

        dnd.playTurn(GAME_ID, new Attack("goblin"));
        dnd.combatTurn(GAME_ID, new MeleeAttack("sword"));

        assertEquals(
                new Fight(false, goblin, meleeOutput.lastAction()),
                game().fightStatus());
        assertThat(playerOutputs, contains(
                combatGoblin,
                new CombatOutput(false, goblin, "Foo: melee attack with sword")));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    private Game game() {
        return gameRepository.gameById(GAME_ID).orElseThrow();
    }

    @Test
    void explore_dialogue() {
        startWith(seeGoblin);
        dmOutcome(speakWithGoblin);

        dnd.playTurn(GAME_ID, new Dialogue("goblin"));

        assertThat(playerOutputs, contains(speakWithGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void dialogue_say() {
        startWith(speakWithGoblin);
        dmOutcome(answerByGoblin);

        dnd.playTurn(GAME_ID, new Say("goblin"));

        assertThat(playerOutputs, contains(answerByGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void dialogue_end_dialogue() {
        startWith(answerByGoblin);
        dmOutcome(exploring);

        dnd.playTurn(GAME_ID, new EndDialogue());

        assertThat(playerOutputs, contains(exploring));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    private void dmOutcome(
            PlayerOutput output
    ) {
        dmChannel.subscribe(GAME_ID, pi -> playersChannel.post(GAME_ID, output));
    }

    private void startWith(PlayerOutput lastOutput) {
        startWith(lastOutput, new Peace());
    }

    private void startWith(PlayerOutput lastOutput, FightStatus fightStatus) {
        Game game = new Game(
                GAME_ID,
                GameMode.EXPLORING,
                lastOutput,
                new GameChar("Foo", List.of(new Weapon("sword")), List.of()),
                fightStatus
        );
        gameRepository.save(game);
    }
}