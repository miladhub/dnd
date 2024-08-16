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
import static org.meh.dnd.AvailableActionType.*;
import static org.meh.dnd.DndCombat.*;
import static org.meh.dnd.FightOutcome.*;
import static org.meh.dnd.GameMode.*;

class DnDAcceptanceTest
{
    private static final Weapon SWORD = DndCombat.SWORD;
    private static final List<AvailableAction> AVAILABLE_ACTIONS = List.of(
            new AvailableAction(WEAPON, "sword", false),
            new AvailableAction(WEAPON, "sword", true),
            new AvailableAction(MOVE, "5", false),
            new AvailableAction(END_TURN, "", false));
    public static final AvailableActions STANDARD_ACTIONS = new AvailableActions(1, 1, 30);
    private final DMChannel dmChannel = new InMemoryDMChannel();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final GameRepository gameRepository = new InMemoryGameRepository();
    private final DnD dnd = new DnD(gameRepository, dmChannel, playersChannel,
            new MockCombat(true, 3));
    private final PlayerOutput exploring = new ExploreOutput(
            "Dark Forest",
            "You are exploring the Dark Forest, what do you do?",
            List.of(new Explore("Dark Forest"), new Rest()),
            "Something happened");
    private final List<PlayerOutput> playerOutputs = new ArrayList<>();
    private final PlayerOutput seeGoblin = new ExploreOutput(
            "Dark Forest",
            "You see a goblin, what do you do?",
            List.of(new Attack("goblin", NpcType.WARRIOR), new Dialogue(
                    "goblin", NpcType.WARRIOR)),
            "Something happened");
    private final GameChar goblin = new GameChar(
            "goblin",
            3,
            CharClass.FIGHTER,
            10, 10, 15, 1000, 1500,
            STATS_FIGHTER,
            List.of(SWORD),
            List.of(),
            STANDARD_ACTIONS);
    private final CombatOutput combatGoblin =
            new CombatOutput(true, STANDARD_ACTIONS, STANDARD_ACTIONS, goblin, List.of(), false, false, 5, AVAILABLE_ACTIONS);
    private final RestOutput rest = new RestOutput();
    private final DialogueOutput speakWithGoblin =
            new DialogueOutput("hey there", List.of(new Say("hi"), new Say("what?")));
    private final DialogueOutput answerByGoblin =
            new DialogueOutput("I said, hey there", List.of(new Say("hi")));
    private final CombatOutput meleeOutput = new CombatOutput(
            false,
            STANDARD_ACTIONS, STANDARD_ACTIONS, goblin,
            List.of("Foo: melee attack with sword (3 hp damage)"),
            false,
            false,
            5,
            AVAILABLE_ACTIONS);
    private final GameChar foo = new GameChar("Foo", 3,
            CharClass.FIGHTER,
            10, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(SWORD),
            List.of(), STANDARD_ACTIONS);

    @BeforeEach
    void setUp() {
        playersChannel.subscribe(playerOutputs::add);
    }

    @Test
    void restarting_heals() {
        startWith(exploring, new Peace(), EXPLORING, new GameChar("Foo", 3,
                CharClass.FIGHTER,
                1, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(SWORD),
                List.of(), STANDARD_ACTIONS));

        dmOutcome(seeGoblin);
        dnd.doAction(new Start("forest"));

        assertEquals(
                10,
                gameRepository.game().orElseThrow().playerChar().hp());
        assertEquals(
                List.of(),
                gameRepository.game().orElseThrow().diary());
        assertThat(playerOutputs, contains(seeGoblin));
    }

    @Test
    void player_sees_last_output_when_entering() {
        startWith(exploring, new Peace(), EXPLORING, foo);
        Optional<PlayerOutput> output = dnd.enter();
        assertEquals(Optional.of(exploring), output);
    }

    @Test
    void explore_continue_exploring() {
        startWith(exploring, new Peace(), EXPLORING, foo);

        dmOutcome(seeGoblin);
        dnd.doAction(new Explore("Dark Forest"));

        assertThat(playerOutputs, contains(seeGoblin));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void explore_then_rest() {
        startWith(exploring, new Peace(), EXPLORING, foo);

        dnd.doAction(new Rest());

        assertThat(playerOutputs, contains(rest));

        assertEquals(
                Optional.of(RESTING),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void explore_dialogue() {
        startWith(seeGoblin, new Peace(), EXPLORING, foo);
        dmOutcome(speakWithGoblin);

        dnd.doAction(new Dialogue("goblin", NpcType.WARRIOR));

        assertThat(playerOutputs, contains(speakWithGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void dialogue_say() {
        startWith(speakWithGoblin, new Peace(), EXPLORING, foo);
        dmOutcome(answerByGoblin);

        dnd.doAction(new Say("goblin"));

        assertThat(playerOutputs, contains(answerByGoblin));

        assertEquals(
                Optional.of(DIALOGUE),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void dialogue_end_dialogue() {
        startWith(answerByGoblin, new Peace(), EXPLORING, foo);
        dmOutcome(exploring);

        dnd.doAction(new EndDialogue("Bye", new KillGoal(NpcType.BEAST, "Wolf", false)));

        assertThat(playerOutputs, contains(exploring));

        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void explore_attack_player_first() {
        startWith(seeGoblin, new Peace(), EXPLORING, foo);

        dnd.doAction(new Attack("goblin", NpcType.WARRIOR));

        assertTrue(playerOutputs.stream().anyMatch(o ->
                o instanceof CombatOutput c &&
                        c.opponent().name().equals("goblin") &&
                        c.log().isEmpty()));
        assertTrue(game().combatStatus() instanceof Fight);
        Fight fight = (Fight) game().combatStatus();
        assertEquals("goblin", fight.opponent().name());
        assertEquals(STANDARD_ACTIONS, fight.playerActions());
        assertEquals(STANDARD_ACTIONS, fight.opponentActions());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void explore_attack_opponent_first() {
        DnD dnd = new DnD(gameRepository, dmChannel, playersChannel,
                new MockCombat(false, 3));

        startWith(seeGoblin, new Peace(), EXPLORING, foo);

        dnd.doAction(new Attack("goblin", NpcType.WARRIOR));

        assertTrue(playerOutputs.stream().anyMatch(o ->
                o instanceof CombatOutput c &&
                        c.opponent().name().equals("goblin") &&
                        !c.log().isEmpty() &&
                        c.log().getLast().startsWith("goblin: ")));
        assertTrue(game().combatStatus() instanceof Fight);
        Fight fight = (Fight) game().combatStatus();
        assertEquals("goblin", fight.opponent().name());
        assertEquals(STANDARD_ACTIONS, fight.playerActions());
        assertEquals(new AvailableActions(0, 1, 30), fight.opponentActions());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_melee_move_and_2nd_hand_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS),
                COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack("sword"), false);
        dnd.playCombatAction(new WeaponAttack("dagger"), true);
        dnd.playCombatAction(new Move(Dir.AWAY_FROM_ENEMY, 5), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                4, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(SWORD),
                List.of(),
                STANDARD_ACTIONS);

        assertEquals(
                new Fight(true, newGoblin, List.of(
                        "Foo: melee attack with sword (3 hp damage)",
                        "Foo: melee attack with dagger (3 hp damage)",
                        "Foo: move 5 feet away from goblin"
                ), 10, IN_PROGRESS,
                        new AvailableActions(0, 0, 25),
                        STANDARD_ACTIONS),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, new AvailableActions(0, 0, 25), STANDARD_ACTIONS, newGoblin,
                        List.of(
                                "Foo: melee attack with sword (3 hp damage)",
                                "Foo: melee attack with dagger (3 hp damage)",
                                "Foo: move 5 feet away from goblin"
                        ),
                        false, false, 10, List.of(
                        new AvailableAction(MOVE, "5", false),
                        new AvailableAction(END_TURN, "", false)))));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_melee_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                STANDARD_ACTIONS,
                STANDARD_ACTIONS), COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack("sword"), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                7, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(SWORD),
                List.of(),
                STANDARD_ACTIONS);

        assertEquals(
                new Fight(true, newGoblin, meleeOutput.log(), 5, IN_PROGRESS,
                        new AvailableActions(0, 1, 30),
                        STANDARD_ACTIONS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, new AvailableActions(0, 1, 30), STANDARD_ACTIONS, newGoblin,
                        List.of("Foo: melee attack with sword (3 hp damage)"),
                        false,
                        false, 5, List.of(
                                new AvailableAction(WEAPON, "sword", true),
                                new AvailableAction(MOVE, "5", false),
                                new AvailableAction(END_TURN, "", false)
                )))
        );
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void player_uses_action_bonus_and_movement() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                STANDARD_ACTIONS,
                STANDARD_ACTIONS), COMBAT, foo);

        dnd.playCombatAction(new SpellAttack(MAGIC_MISSILE.name()), false);
        dnd.playCombatAction(new WeaponAttack(DAGGER.name()), true);
        dnd.playCombatAction(new Move(Dir.AWAY_FROM_ENEMY, 5), false);
        dnd.playCombatAction(new EndTurn(), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                4, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(SWORD),
                List.of(),
                STANDARD_ACTIONS);

        assertEquals(
                new Fight(true, newGoblin,
                        List.of(
                                "Foo: cast Magic Missile (3 hp damage)",
                                "Foo: melee attack with dagger (3 hp damage)",
                                "Foo: move 5 feet away from goblin",
                                "goblin: melee attack with sword (3 hp damage)"),
                        10,
                        IN_PROGRESS,
                        new AvailableActions(1, 1, 30),
                        new AvailableActions(0, 1, 30)),
                game().combatStatus());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_spell_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS),
                COMBAT, foo);

        dnd.playCombatAction(new SpellAttack(MAGIC_MISSILE.name()), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                7, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(SWORD),
                List.of(),
                STANDARD_ACTIONS);

        assertEquals(
                new Fight(true, newGoblin,
                        List.of("Foo: cast Magic Missile (3 hp damage)"), 5,
                        IN_PROGRESS,
                        new AvailableActions(0, 1, 30),
                        STANDARD_ACTIONS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, new AvailableActions(0, 1, 30), STANDARD_ACTIONS, newGoblin,
                        List.of("Foo: cast Magic Missile (3 hp damage)"),
                        false, false, 5, List.of(
                        new AvailableAction(WEAPON, "sword", true),
                        new AvailableAction(MOVE, "5", false),
                        new AvailableAction(END_TURN, "", false)))));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_movement_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 10, IN_PROGRESS,
                STANDARD_ACTIONS,
                STANDARD_ACTIONS), COMBAT, foo);

        dnd.playCombatAction(new Move(Dir.TOWARDS_ENEMY, 5), false);

        assertEquals(
                new Fight(true, goblin, List.of("Foo: move 5 feet towards " +
                        "goblin"),
                        5, IN_PROGRESS,
                        new AvailableActions(1, 1, 25),
                        STANDARD_ACTIONS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, new AvailableActions(1, 1, 25),
                        new AvailableActions(1, 1, 30), goblin,
                        List.of("Foo: move 5 feet towards goblin"),
                        false, false, 5, AVAILABLE_ACTIONS)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_melee_enemy_turn() {
        startWith(combatGoblin,
                new Fight(false, goblin, List.of("Foo: melee attack with sword"),
                        5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS),
                COMBAT, foo);

        dnd.playCombatAction(new EndTurn(), false);

        assertEquals(
                new Fight(true, goblin,
                        List.of(
                                "Foo: melee attack with sword",
                                "goblin: melee attack with sword (3 hp damage)"),
                        5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        new AvailableActions(0, 1, 30)),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, STANDARD_ACTIONS, new AvailableActions(0, 1, 30), goblin,
                        List.of(
                                "Foo: melee attack with sword",
                                "goblin: melee attack with sword (3 hp damage)"),
                        false, false, 5, AVAILABLE_ACTIONS)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_does_damage() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS),
                COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack(SWORD.name()), false);

        GameChar damaged =
                new GameChar("goblin",
                        3,
                        CharClass.FIGHTER,
                        7, 10, 15, 1000, 1500, STATS_FIGHTER,
                        List.of(SWORD),
                        List.of(),
                        STANDARD_ACTIONS);
        assertEquals(
                new Fight(true, damaged, List.of("Foo: melee attack with " +
                        "sword (3 hp damage)"),
                        5, IN_PROGRESS,
                        new AvailableActions(0, 1, 30),
                        STANDARD_ACTIONS),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, new AvailableActions(0, 1, 30),
                        new AvailableActions(1, 1, 30), damaged,
                        List.of("Foo: melee attack with sword (3 hp damage)"),
                        false, false, 5, List.of(
                        new AvailableAction(WEAPON, "sword", true),
                        new AvailableAction(MOVE, "5", false),
                        new AvailableAction(END_TURN, "", false)))));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void killing_player_won() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        new AvailableActions(3, 1, 30),
                        STANDARD_ACTIONS),
                COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack(SWORD.name()), false);
        dnd.playCombatAction(new WeaponAttack(SWORD.name()), false);
        dnd.playCombatAction(new WeaponAttack(SWORD.name()), false);
        dnd.playCombatAction(new WeaponAttack(SWORD.name()), true);

        GameChar killed =
                new GameChar("goblin", 3,
                        CharClass.FIGHTER,
                        0, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(SWORD),
                        List.of(), STANDARD_ACTIONS);
        assertEquals(killed, ((Fight) game().combatStatus()).opponent());
        assertEquals(
                new Fight(true, killed, List.of(
                        "Foo: melee attack with sword (3 hp damage)",
                        "Foo: melee attack with sword (3 hp damage)",
                        "Foo: melee attack with sword (3 hp damage)",
                        "Foo: killed goblin, melee attack with sword (3 hp damage)"),
                        5, PLAYER_WON,
                        new AvailableActions(0, 0, 30),
                        STANDARD_ACTIONS),
                game().combatStatus());
        assertEquals(PLAYER_WON, ((Fight) game().combatStatus()).outcome());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, new AvailableActions(0, 0, 30), STANDARD_ACTIONS, killed,
                        List.of(
                                "Foo: melee attack with sword (3 hp damage)",
                                "Foo: melee attack with sword (3 hp damage)",
                                "Foo: melee attack with sword (3 hp damage)",
                                "Foo: killed goblin, melee attack with sword (3 hp damage)"),
                        true, false, 5, List.of(
                        new AvailableAction(MOVE, "5", false),
                        new AvailableAction(END_TURN, "", false)))));
        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void killing_enemy_won() {
        GameChar goblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                10, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(SWORD),
                List.of(),
                new AvailableActions(4, 1, 30));
        startWith(combatGoblin, new Fight(false, goblin, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        new AvailableActions(4, 1, 30)),
                COMBAT, foo);

        dnd.playCombatAction(new EndTurn(), false);

        assertEquals(
                new Fight(false, goblin,
                        List.of(
                                "goblin: melee attack with sword (3 hp damage)",
                                "goblin: melee attack with sword (3 hp damage)",
                                "goblin: melee attack with sword (3 hp damage)",
                                "goblin: killed Foo, melee attack with sword (3 hp damage)"
                        ),
                        5, ENEMY_WON,
                        STANDARD_ACTIONS,
                        new AvailableActions(0, 1, 30)),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(false, STANDARD_ACTIONS, new AvailableActions(0, 1, 30), goblin,
                        List.of(
                                "goblin: melee attack with sword (3 hp damage)",
                                "goblin: melee attack with sword (3 hp damage)",
                                "goblin: melee attack with sword (3 hp damage)",
                                "goblin: killed Foo, melee attack with sword (3 hp damage)"
                        ),
                        false, true, 5, AVAILABLE_ACTIONS)));
        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void resting_heals() {
        startWith(exploring, new Peace(), EXPLORING, new GameChar("Foo", 3,
                CharClass.FIGHTER,
                1, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(SWORD),
                List.of(), STANDARD_ACTIONS));

        dnd.doAction(new Rest());

        assertEquals(
                10,
                gameRepository.game().orElseThrow().playerChar().hp()
        );
    }

    @Test
    void killing_solves_quest_goal() {
        gameRepository.save(new Game(
                COMBAT,
                List.of(combatGoblin),
                foo,
                new Fight(
                        true, goblin, List.of(), 5, IN_PROGRESS,
                        new AvailableActions(3, 1, 30),
                        STANDARD_ACTIONS),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new KillGoal(NpcType.WARRIOR, "goblin", false))
        ));

        dnd.playCombatAction(new WeaponAttack(SWORD.name()), false);
        dnd.playCombatAction(new WeaponAttack(SWORD.name()), false);
        dnd.playCombatAction(new WeaponAttack(SWORD.name()), false);
        dnd.playCombatAction(new WeaponAttack(SWORD.name()), true);

        assertEquals(
                List.of(new KillGoal(NpcType.WARRIOR, "goblin", true)),
                game().quest());
    }

    @Test
    void explore_solves_quest_goal() {
        gameRepository.save(new Game(
                EXPLORING,
                List.of(exploring),
                foo,
                new Peace(),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new ExploreGoal("Dungeon", false))
        ));

        dnd.doAction(new Explore("The Dungeon"));

        assertEquals(
                List.of(new ExploreGoal("Dungeon", true)),
                game().quest());
    }

    @Test
    void starting_can_solve_quest_goal() {
        gameRepository.save(new Game(
                EXPLORING,
                List.of(exploring),
                foo,
                new Peace(),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new ExploreGoal("Dark Forest", false))
        ));

        dnd.doAction(new Start("Dark Forest"));

        assertEquals(
                List.of(new ExploreGoal("Dark Forest", true)),
                game().quest());
    }

    @Test
    void talking_can_solve_quest_goal() {
        gameRepository.save(new Game(
                EXPLORING,
                List.of(exploring),
                foo,
                new Peace(),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new TalkGoal(NpcType.MAGIC, "Elf Sage", false))
        ));

        dnd.doAction(new Dialogue("Elf Sage", NpcType.MAGIC));

        assertEquals(
                List.of(new TalkGoal(NpcType.MAGIC, "Elf Sage", true)),
                game().quest());
    }

    private void dmOutcome(
            PlayerOutput output
    ) {
        dmChannel.subscribe(pi -> playersChannel.post(output));
    }

    private Game game() {
        return gameRepository.game().orElseThrow();
    }

    private void startWith(
            PlayerOutput lastOutput,
            CombatStatus combatStatus,
            GameMode gameMode,
            GameChar playerChar
    ) {
        Game game = new Game(
                gameMode,
                List.of(lastOutput),
                playerChar,
                combatStatus,
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of()
        );
        gameRepository.save(game);
    }

    private record MockCombat(
            boolean playerActsFirst,
            int damage
    ) implements Combat {
        @Override
        public Fight generateFight(
                GameChar gameChar,
                Attack attack
        ) {
            GameChar opponent = new GameChar(
                    attack.target(),
                    3,
                    CharClass.FIGHTER,
                    10,
                    10, 15, 1000, 1500,
                    STATS_FIGHTER,
                    List.of(SWORD, DAGGER),
                    List.of(),
                    STANDARD_ACTIONS
            );
            return new Fight(playerActsFirst, opponent, List.of(), 5, IN_PROGRESS,
                    STANDARD_ACTIONS,
                    STANDARD_ACTIONS);
        }

        @Override
        public GeneratedCombatAction generateAttack(Fight fight) {
            if (fight.opponentActions().actions() > 0)
                return new GeneratedCombatAction(
                    new WeaponAttack(fight.opponent().weapons().getFirst().name()),
                    false);
            else return new GeneratedCombatAction(
                    new EndTurn(),
                    false
            );
        }

        @Override
        public AttackResult computeAttack(
                Attacks attack,
                GameChar attacker,
                GameChar defender
        ) {
            return new Hit(defender.damage(damage), damage);
        }
    }
}