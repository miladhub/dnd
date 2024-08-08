package org.meh.dnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.meh.dnd.FightStatus.*;
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
    private final GameChar goblin = new GameChar(
            "goblin",
            10, 10,
            List.of(new Weapon("sword")),
            List.of());
    private final CombatOutput combatGoblin = new CombatOutput(true, goblin,
            "", false, false);
    private final RestOutput rest = new RestOutput();
    private final DialogueOutput speakWithGoblin =
            new DialogueOutput("hey there", List.of(new Say("hi"), new Say("what?")));
    private final DialogueOutput answerByGoblin =
            new DialogueOutput("I said, hey there", List.of(new Say("hi")));
    private final CombatOutput meleeOutput = new CombatOutput(
            false,
            goblin,
            "Foo: melee attack with sword",
            false,
            false);

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
        dnd.doAction(GAME_ID, new Explore(""));

        assertThat(playerOutputs, contains(seeGoblin));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_then_rest() {
        startWith(exploring, new Peace(), EXPLORING);

        dnd.doAction(GAME_ID, new Rest());

        assertThat(playerOutputs, contains(rest));

        assertEquals(
                Optional.of(RESTING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_dialogue() {
        startWith(seeGoblin, new Peace(), EXPLORING);
        dmOutcome(speakWithGoblin);

        dnd.doAction(GAME_ID, new Dialogue("goblin"));

        assertThat(playerOutputs, contains(speakWithGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void dialogue_say() {
        startWith(speakWithGoblin, new Peace(), EXPLORING);
        dmOutcome(answerByGoblin);

        dnd.doAction(GAME_ID, new Say("goblin"));

        assertThat(playerOutputs, contains(answerByGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void dialogue_end_dialogue() {
        startWith(answerByGoblin, new Peace(), EXPLORING);
        dmOutcome(exploring);

        dnd.doAction(GAME_ID, new EndDialogue());

        assertThat(playerOutputs, contains(exploring));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void explore_attack() {
        startWith(seeGoblin, new Peace(), EXPLORING);

        dnd.doAction(GAME_ID, new Attack("goblin"));

        assertTrue(playerOutputs.stream().anyMatch(
                o -> o instanceof CombatOutput c &&
                        c.opponent().equals(goblin) &&
                        (c.lastAction().isEmpty() || c.lastAction().startsWith("goblin: "))));
        assertTrue(game().combatStatus() instanceof Fight);
        assertEquals(
                goblin,
                ((Fight) game().combatStatus()).opponent());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_melee_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, "", IN_PROGRESS), COMBAT);

        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));

        GameChar newGoblin = new GameChar(
                "goblin",
                7, 10,
                List.of(new Weapon("sword")),
                List.of());

        assertEquals(
                new Fight(false, newGoblin, meleeOutput.lastAction(), IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, newGoblin, "Foo: melee attack with sword", false, false)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_spell_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, "", IN_PROGRESS), COMBAT);

        dnd.playCombatTurn(GAME_ID, new SpellAttack("magic missile"));

        GameChar newGoblin = new GameChar(
                "goblin",
                7, 10,
                List.of(new Weapon("sword")),
                List.of());

        assertEquals(
                new Fight(false, newGoblin, "Foo: cast magic missile", IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, newGoblin, "Foo: cast magic missile", false, false)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_melee_enemy_turn() {
        startWith(combatGoblin,
                new Fight(false, goblin, "Foo: melee attack with sword", IN_PROGRESS),
                COMBAT);

        dnd.enemyCombatTurn(GAME_ID, new MeleeAttack("sword"));

        assertEquals(
                new Fight(true, goblin, "goblin: melee attack with sword", IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, goblin, "goblin: melee attack with sword", false, false)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_does_damage() {
        startWith(combatGoblin, new Fight(true, goblin, "", IN_PROGRESS), COMBAT);

        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));

        GameChar damaged =
                new GameChar("goblin", 7, 10, List.of(new Weapon("sword")),
                        List.of());
        assertEquals(
                new Fight(false, damaged, "Foo: melee attack with sword", IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, damaged,
                        "Foo: melee attack with sword", false, false)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void killing_player_won() {
        startWith(combatGoblin, new Fight(true, goblin, "", IN_PROGRESS), COMBAT);

        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));

        GameChar killed =
                new GameChar("goblin", 0, 10, List.of(new Weapon("sword")),
                        List.of());
        assertEquals(killed, ((Fight) game().combatStatus()).opponent());
        assertEquals("Foo: killed goblin", ((Fight) game().combatStatus()).lastAction());
        assertEquals(PLAYER_WON, ((Fight) game().combatStatus()).outcome());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, killed, "Foo: killed goblin", true, false)));
        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void killing_enemy_won() {
        startWith(combatGoblin, new Fight(false, goblin, "", IN_PROGRESS), COMBAT);

        dnd.enemyCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new MeleeAttack("sword"));

        assertEquals(
                new Fight(false, goblin, "goblin: killed Foo", ENEMY_WON),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(false, goblin, "goblin: killed Foo", false, true)));
        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void resting_heals() {
        startWith(combatGoblin, new Fight(false, goblin, "", IN_PROGRESS), COMBAT);

        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new MeleeAttack("sword"));
        dnd.doAction(GAME_ID, new Rest());

        assertEquals(
                10,
                gameRepository.gameById(GAME_ID).orElseThrow().playerChar().hp()
        );
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
                new GameChar("Foo", 10, 10, List.of(new Weapon("sword")),
                        List.of()),
                combatStatus,
                new Chat(List.of())
        );
        gameRepository.save(game);
    }
}