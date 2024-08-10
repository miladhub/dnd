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
import static org.meh.dnd.DndCombat.STATS_FIGHTER;
import static org.meh.dnd.FightStatus.*;
import static org.meh.dnd.GameMode.*;

class DnDAcceptanceTest
{
    public static final String GAME_ID = "42";
    private static final Weapon SWORD = DndCombat.SWORD;
    private final DMChannel dmChannel = new InMemoryDMChannel();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel,
            new Combat() {
                @Override
                public Fight generateFight(String opponentName) {
                    GameChar opponent = new GameChar(
                            opponentName,
                            3,
                            CharClass.FIGHTER,
                            10,
                            10, 15, 1000, 1500,
                            STATS_FIGHTER,
                            List.of(SWORD),
                            List.of()
                    );
                    return new Fight(true, opponent, "", 5, IN_PROGRESS);
                }
                @Override
                public CombatActions generateAttack(Fight fight) {
                    return new WeaponAttack(fight.opponent().weapons().getFirst().name());
                }
                @Override
                public AttackResult computeAttack(
                        Attacks attack,
                        GameChar attacker,
                        GameChar defender
                ) {
                    return new AttackResult(defender.damage(3), 3);
                }
            });
    private final PlayerOutput exploring = new ExploreOutput(
            "Dark Forest",
            "You are exploring the Dark Forest, what do you do?",
            List.of(new Explore("Dark Forest"), new Rest()));
    private final List<PlayerOutput> playerOutputs = new ArrayList<>();
    private final PlayerOutput seeGoblin = new ExploreOutput(
            "Dark Forest",
            "You see a goblin, what do you do?",
            List.of(new Attack("goblin"), new Dialogue("goblin")));
    private final GameChar goblin = new GameChar(
            "goblin",
            3,
            CharClass.FIGHTER,
            10, 10, 15, 1000, 1500,
            STATS_FIGHTER,
            List.of(SWORD),
            List.of());
    private final CombatOutput combatGoblin = new CombatOutput(true, goblin,
            "", false, false, 5);
    private final RestOutput rest = new RestOutput();
    private final DialogueOutput speakWithGoblin =
            new DialogueOutput("goblin", "hey there", List.of(new Say("hi"), new Say("what?")));
    private final DialogueOutput answerByGoblin =
            new DialogueOutput("goblin", "I said, hey there", List.of(new Say("hi")));
    private final CombatOutput meleeOutput = new CombatOutput(
            false,
            goblin,
            "Foo: melee attack with sword (3 hp damage)",
            false,
            false,
            5);

    @BeforeEach
    void setUp() {
        playersChannel.subscribe(GAME_ID, playerOutputs::add);
    }

    @Test
    void player_sees_last_output_when_entering() {
        startWith(exploring, new Peace(), EXPLORING);
        Optional<PlayerOutput> output = dnd.enter(GAME_ID);
        assertEquals(Optional.of(exploring), output);
    }

    @Test
    void explore_continue_exploring() {
        startWith(exploring, new Peace(), EXPLORING);

        dmOutcome(seeGoblin);
        dnd.doAction(GAME_ID, new Explore("Dark Forest"));

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
                        c.opponent().name().equals("goblin") &&
                        (c.lastAction().isEmpty() || c.lastAction().startsWith("goblin: "))));
        assertTrue(game().combatStatus() instanceof Fight);
        assertEquals(
                "goblin",
                ((Fight) game().combatStatus()).opponent().name());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_melee_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, "", 5, IN_PROGRESS),
                COMBAT);

        dnd.playCombatTurn(GAME_ID, new WeaponAttack("sword"));

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                7, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(SWORD),
                List.of());

        assertEquals(
                new Fight(false, newGoblin, meleeOutput.lastAction(), 5, IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, newGoblin,
                        "Foo: melee attack with sword (3 hp damage)", false, false, 5)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_spell_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, "", 5, IN_PROGRESS),
                COMBAT);

        dnd.playCombatTurn(GAME_ID, new SpellAttack(DndCombat.MAGIC_MISSILE.name()));

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                7, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(SWORD),
                List.of());

        assertEquals(
                new Fight(false, newGoblin, "Foo: cast Magic Missile (3 hp damage)", 5, IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, newGoblin, "Foo: cast Magic Missile (3 hp damage)",
                        false, false, 5)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_movement_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, "", 10, IN_PROGRESS), COMBAT);

        dnd.playCombatTurn(GAME_ID, new Move(Dir.TOWARDS_ENEMY, 5));

        assertEquals(
                new Fight(false, goblin, "Foo: move 5 feet towards goblin", 5, IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, goblin, "Foo: move 5 feet towards goblin",
                        false, false, 5)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_melee_enemy_turn() {
        startWith(combatGoblin,
                new Fight(false, goblin, "Foo: melee attack with sword",
                        5, IN_PROGRESS),
                COMBAT);

        dnd.enemyCombatTurn(GAME_ID, new WeaponAttack("sword"));

        assertEquals(
                new Fight(true, goblin, "goblin: melee attack with sword (3 hp damage)",
                        5, IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, goblin,
                        "goblin: melee attack with sword (3 hp damage)", false, false, 5)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_movement_enemy_turn() {
        startWith(combatGoblin,
                new Fight(false, goblin, "Foo: melee attack with sword",
                        10, IN_PROGRESS),
                COMBAT);

        dnd.enemyCombatTurn(GAME_ID, new Move(Dir.TOWARDS_ENEMY, 5));

        assertEquals(
                new Fight(true, goblin, "goblin: move 5 feet towards Foo",
                        5, IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, goblin,
                        "goblin: move 5 feet towards Foo", false, false, 5)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void attack_does_damage() {
        startWith(combatGoblin, new Fight(true, goblin, "", 5, IN_PROGRESS),
                COMBAT);

        dnd.playCombatTurn(GAME_ID, new WeaponAttack(SWORD.name()));

        GameChar damaged =
                new GameChar("goblin",
                        3,
                        CharClass.FIGHTER,
                        7, 10, 15, 1000, 1500, STATS_FIGHTER,
                        List.of(SWORD),
                        List.of());
        assertEquals(
                new Fight(false, damaged, "Foo: melee attack with sword (3 hp damage)",
                        5, IN_PROGRESS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(false, damaged,
                        "Foo: melee attack with sword (3 hp damage)", false, false, 5)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void killing_player_won() {
        startWith(combatGoblin, new Fight(true, goblin, "", 5, IN_PROGRESS),
                COMBAT);

        dnd.playCombatTurn(GAME_ID, new WeaponAttack(SWORD.name()));
        dnd.playCombatTurn(GAME_ID, new WeaponAttack(SWORD.name()));
        dnd.playCombatTurn(GAME_ID, new WeaponAttack(SWORD.name()));
        dnd.playCombatTurn(GAME_ID, new WeaponAttack(SWORD.name()));

        GameChar killed =
                new GameChar("goblin", 3,
                        CharClass.FIGHTER,
                        0, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(SWORD),
                        List.of());
        assertEquals(killed, ((Fight) game().combatStatus()).opponent());
        assertEquals("Foo: killed goblin, melee attack with sword (3 hp damage)",
                ((Fight) game().combatStatus()).lastAction());
        assertEquals(PLAYER_WON, ((Fight) game().combatStatus()).outcome());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, killed,
                        "Foo: killed goblin, melee attack with sword (3 hp damage)", true,
                        false, 5)));
        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void killing_enemy_won() {
        startWith(combatGoblin, new Fight(false, goblin, "", 5, IN_PROGRESS),
                COMBAT);

        dnd.enemyCombatTurn(GAME_ID, new WeaponAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new WeaponAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new WeaponAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new WeaponAttack("sword"));

        assertEquals(
                new Fight(false, goblin,
                        "goblin: killed Foo, melee attack with sword (3 hp damage)",
                        5, ENEMY_WON),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(false, goblin,
                        "goblin: killed Foo, melee attack with sword (3 hp damage)", false, true, 5)));
        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.gameById(GAME_ID).map(Game::mode));
    }

    @Test
    void resting_heals() {
        startWith(combatGoblin, new Fight(false, goblin, "", 5, IN_PROGRESS),
                COMBAT);

        dnd.playCombatTurn(GAME_ID, new WeaponAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new WeaponAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new WeaponAttack("sword"));
        dnd.playCombatTurn(GAME_ID, new WeaponAttack("sword"));
        dnd.enemyCombatTurn(GAME_ID, new WeaponAttack("sword"));
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
                List.of(lastOutput),
                new GameChar("Foo", 3,
                        CharClass.FIGHTER,
                        10, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(SWORD),
                        List.of()),
                combatStatus,
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody()
        );
        gameRepository.save(game);
    }
}