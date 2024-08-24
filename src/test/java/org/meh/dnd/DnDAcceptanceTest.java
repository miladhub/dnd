package org.meh.dnd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.meh.dnd.AvailableActionType.*;
import static org.meh.dnd.DndCombat.*;
import static org.meh.dnd.FightOutcome.*;
import static org.meh.dnd.GameMode.*;

class DnDAcceptanceTest
{
    private static final List<AvailableAction> AVAILABLE_ACTIONS = List.of(
            new AvailableAction(WEAPON, "longsword", false),
            new AvailableAction(WEAPON, "longsword", true),
            new AvailableAction(MOVE, "5", false),
            new AvailableAction(END_TURN, "", false));
    private static final AvailableActions STANDARD_ACTIONS = new AvailableActions(1, 1, 30);
    private static final SpellSlots SPELL_SLOTS = new SpellSlots(4, 3, 0, 0, 0, 0, 0, 0, 0);
    private static final int XP_GAIN = 100;
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
            List.of(DndCombat.LONGSWORD),
            List.of(),
            STANDARD_ACTIONS, SPELL_SLOTS);
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
            List.of("Foo: melee attack with longsword (3 hp damage)"),
            false,
            false,
            5,
            AVAILABLE_ACTIONS);
    private final GameChar foo = new GameChar("Foo", 3,
            CharClass.FIGHTER,
            10, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(DndCombat.LONGSWORD),
            List.of(), STANDARD_ACTIONS, SPELL_SLOTS);

    @BeforeEach
    void setUp() {
        playersChannel.subscribe(playerOutputs::add);
    }

    @Test
    void restarting_heals() {
        startWith(exploring, new Peace(), EXPLORING, new GameChar("Foo", 3,
                CharClass.FIGHTER,
                1, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(DndCombat.LONGSWORD),
                List.of(), STANDARD_ACTIONS, SPELL_SLOTS));

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
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DAGGER), true);
        dnd.playCombatAction(new Move(Dir.AWAY_FROM_ENEMY, 5), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                4, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(DndCombat.LONGSWORD),
                List.of(),
                STANDARD_ACTIONS,
                SPELL_SLOTS);

        assertEquals(
                new Fight(true, newGoblin, List.of(
                        "Foo: melee attack with longsword (3 hp damage)",
                        "Foo: melee attack with dagger (3 hp damage)",
                        "Foo: move 5 feet away from goblin"
                ), 10, IN_PROGRESS,
                        new AvailableActions(0, 0, 25),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, new AvailableActions(0, 0, 25), STANDARD_ACTIONS, newGoblin,
                        List.of(
                                "Foo: melee attack with longsword (3 hp damage)",
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
                STANDARD_ACTIONS,
                XP_GAIN, List.of()), COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                7, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(DndCombat.LONGSWORD),
                List.of(),
                STANDARD_ACTIONS,
                SPELL_SLOTS);

        assertEquals(
                new Fight(true, newGoblin, meleeOutput.log(), 5, IN_PROGRESS,
                        new AvailableActions(0, 1, 30),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, new AvailableActions(0, 1, 30), STANDARD_ACTIONS, newGoblin,
                        List.of("Foo: melee attack with longsword (3 hp damage)"),
                        false,
                        false, 5, List.of(
                                new AvailableAction(WEAPON, "longsword", true),
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
                STANDARD_ACTIONS,
                XP_GAIN, List.of()), COMBAT, foo);

        dnd.playCombatAction(new SpellAttack(MAGIC_MISSILE), false);
        dnd.playCombatAction(new WeaponAttack(DAGGER), true);
        dnd.playCombatAction(new Move(Dir.AWAY_FROM_ENEMY, 5), false);
        dnd.playCombatAction(new EndTurn(), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                4, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(DndCombat.LONGSWORD),
                List.of(),
                STANDARD_ACTIONS,
                SPELL_SLOTS);

        assertEquals(
                new Fight(true, newGoblin,
                        List.of(
                                "Foo: cast Magic Missile (3 hp damage)",
                                "Foo: melee attack with dagger (3 hp damage)",
                                "Foo: move 5 feet away from goblin",
                                "goblin: melee attack with longsword (3 hp damage)"),
                        10,
                        IN_PROGRESS,
                        new AvailableActions(1, 1, 30),
                        new AvailableActions(0, 1, 30),
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_spell_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new SpellAttack(MAGIC_MISSILE), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                7, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(DndCombat.LONGSWORD),
                List.of(),
                STANDARD_ACTIONS,
                SPELL_SLOTS);

        assertEquals(
                new Fight(true, newGoblin,
                        List.of("Foo: cast Magic Missile (3 hp damage)"), 5,
                        IN_PROGRESS,
                        new AvailableActions(0, 1, 30),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, new AvailableActions(0, 1, 30), STANDARD_ACTIONS, newGoblin,
                        List.of("Foo: cast Magic Missile (3 hp damage)"),
                        false, false, 5, List.of(
                        new AvailableAction(WEAPON, "longsword", true),
                        new AvailableAction(MOVE, "5", false),
                        new AvailableAction(END_TURN, "", false)))));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_presents_all_spells_with_full_slots() {
        GameChar wizard = new GameChar("Wiz", 3,
                CharClass.WIZARD,
                10, 10, 15, 1000, 1500, STATS_WIZARD,
                List.of(DAGGER),
                List.of(SHOCKING_GRASP, MAGIC_MISSILE, MELF_ARROW),
                new AvailableActions(1, 1, 30),
                new SpellSlots(4, 3, 0, 0, 0, 0, 0, 0, 0));

        startWith(seeGoblin, new Peace(), EXPLORING, wizard);

        dnd.doAction(new Attack("goblin", NpcType.WARRIOR));

        CombatOutput co = (CombatOutput) playerOutputs.getFirst();

        assertThat(
                co.availableActions().stream()
                        .filter(a -> a.type() == SPELL).toList(),
                containsInAnyOrder(
                        new AvailableAction(SPELL, MAGIC_MISSILE.name(), false),
                        new AvailableAction(SPELL, SHOCKING_GRASP.name(), false),
                        new AvailableAction(SPELL, MELF_ARROW.name(), false)
                ));
    }

    @Test
    void attack_presents_spells_according_to_slots() {
        GameChar wizard = new GameChar("Wiz", 3,
                CharClass.WIZARD,
                10, 10, 15, 1000, 1500, STATS_WIZARD,
                List.of(DAGGER),
                List.of(SHOCKING_GRASP, MAGIC_MISSILE, MELF_ARROW),
                new AvailableActions(1, 1, 30),
                new SpellSlots(1, 0, 0, 0, 0, 0, 0, 0, 0));

        startWith(seeGoblin, new Peace(), EXPLORING, wizard);

        dnd.doAction(new Attack("goblin", NpcType.WARRIOR));

        CombatOutput co = (CombatOutput) playerOutputs.getFirst();

        assertThat(co.availableActions().stream()
                        .filter(a -> a.type() == SPELL).toList(),
                containsInAnyOrder(
                        new AvailableAction(SPELL, SHOCKING_GRASP.name(), false),
                        new AvailableAction(SPELL, MAGIC_MISSILE.name(), false)
                ));
    }

    @Test
    void spells_consume_slots() {
        GameChar wizard = new GameChar("Wiz", 3,
                CharClass.WIZARD,
                10, 10, 15, 1000, 1500, STATS_WIZARD,
                List.of(DAGGER),
                List.of(SHOCKING_GRASP, MAGIC_MISSILE, MELF_ARROW),
                new AvailableActions(1, 1, 30),
                new SpellSlots(4, 3, 0, 0, 0, 0, 0, 0, 0));

        startWith(combatGoblin,
                new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        new AvailableActions(2, 1, 30),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT,
                wizard);

        dnd.playCombatAction(new SpellAttack(MAGIC_MISSILE), false);

        CombatOutput co = (CombatOutput) playerOutputs.getFirst();

        assertThat(
                co.availableActions().stream()
                        .filter(a -> a.type() == SPELL).toList(),
                containsInAnyOrder(
                        new AvailableAction(SPELL, MAGIC_MISSILE.name(), false),
                        new AvailableAction(SPELL, SHOCKING_GRASP.name(), false),
                        new AvailableAction(SPELL, MELF_ARROW.name(), false)
                ));

        assertEquals(
                Optional.of(new SpellSlots(3, 3, 0, 0, 0, 0, 0, 0, 0)),
                gameRepository.game().map(Game::playerChar).map(GameChar::spellSlots));
    }

    @Test
    void attack_movement_players_turn() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 10, IN_PROGRESS,
                STANDARD_ACTIONS,
                STANDARD_ACTIONS,
                XP_GAIN, List.of()), COMBAT, foo);

        dnd.playCombatAction(new Move(Dir.TOWARDS_ENEMY, 5), false);

        assertEquals(
                new Fight(true, goblin, List.of("Foo: move 5 feet towards " +
                        "goblin"),
                        5, IN_PROGRESS,
                        new AvailableActions(1, 1, 25),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
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
                new Fight(false, goblin, List.of("Foo: melee attack with longsword"),
                        5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new EndTurn(), false);

        assertEquals(
                new Fight(true, goblin,
                        List.of(
                                "Foo: melee attack with longsword",
                                "goblin: melee attack with longsword (3 hp damage)"),
                        5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        new AvailableActions(0, 1, 30),
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, STANDARD_ACTIONS, new AvailableActions(0, 1, 30), goblin,
                        List.of(
                                "Foo: melee attack with longsword",
                                "goblin: melee attack with longsword (3 hp damage)"),
                        false, false, 5, AVAILABLE_ACTIONS)));
        assertEquals(
                Optional.of(COMBAT),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void attack_does_damage() {
        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);

        GameChar damaged =
                new GameChar("goblin",
                        3,
                        CharClass.FIGHTER,
                        7, 10, 15, 1000, 1500, STATS_FIGHTER,
                        List.of(DndCombat.LONGSWORD),
                        List.of(),
                        STANDARD_ACTIONS, SPELL_SLOTS);
        assertEquals(
                new Fight(true, damaged, List.of("Foo: melee attack with longsword (3 hp damage)"),
                        5, IN_PROGRESS,
                        new AvailableActions(0, 1, 30),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertThat(playerOutputs, contains(
                new CombatOutput(true, new AvailableActions(0, 1, 30),
                        new AvailableActions(1, 1, 30), damaged,
                        List.of("Foo: melee attack with longsword (3 hp damage)"),
                        false, false, 5, List.of(
                        new AvailableAction(WEAPON, "longsword", true),
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
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), true);

        GameChar killed =
                new GameChar("goblin", 3,
                        CharClass.FIGHTER,
                        0, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(DndCombat.LONGSWORD),
                        List.of(), STANDARD_ACTIONS, SPELL_SLOTS);
        assertEquals(killed, ((Fight) game().combatStatus()).opponent());
        assertEquals(
                new Fight(true, killed, List.of(
                        "Foo: melee attack with longsword (3 hp damage)",
                        "Foo: melee attack with longsword (3 hp damage)",
                        "Foo: melee attack with longsword (3 hp damage)",
                        "Foo: killed goblin, melee attack with longsword (3 hp damage)",
                        "Gained " + XP_GAIN + " xp"),
                        5, PLAYER_WON,
                        new AvailableActions(0, 0, 30),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertEquals(PLAYER_WON, ((Fight) game().combatStatus()).outcome());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(true, new AvailableActions(0, 0, 30), STANDARD_ACTIONS, killed,
                        List.of(
                                "Foo: melee attack with longsword (3 hp damage)",
                                "Foo: melee attack with longsword (3 hp damage)",
                                "Foo: melee attack with longsword (3 hp damage)",
                                "Foo: killed goblin, melee attack with longsword (3 hp damage)",
                                "Gained " + XP_GAIN + " xp"),
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
                List.of(DndCombat.LONGSWORD),
                List.of(),
                new AvailableActions(4, 1, 30), SPELL_SLOTS);
        startWith(combatGoblin, new Fight(false, goblin, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        new AvailableActions(4, 1, 30),
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new EndTurn(), false);

        assertEquals(
                new Fight(false, goblin,
                        List.of(
                                "goblin: melee attack with longsword (3 hp damage)",
                                "goblin: melee attack with longsword (3 hp damage)",
                                "goblin: melee attack with longsword (3 hp damage)",
                                "goblin: killed Foo, melee attack with longsword (3 hp damage)"
                        ),
                        5, ENEMY_WON,
                        STANDARD_ACTIONS,
                        new AvailableActions(0, 1, 30),
                        XP_GAIN, List.of()),
                game().combatStatus());
        assertThat(playerOutputs, hasItem(
                new CombatOutput(false, STANDARD_ACTIONS, new AvailableActions(0, 1, 30), goblin,
                        List.of(
                                "goblin: melee attack with longsword (3 hp damage)",
                                "goblin: melee attack with longsword (3 hp damage)",
                                "goblin: melee attack with longsword (3 hp damage)",
                                "goblin: killed Foo, melee attack with longsword (3 hp damage)"
                        ),
                        false, true, 5, AVAILABLE_ACTIONS)));
        assertEquals(
                Optional.of(EXPLORING),
                gameRepository.game().map(Game::mode));
    }

    @Test
    void resting_restores_spell_slots() {
        GameChar wizard = new GameChar("Wiz", 3,
                CharClass.WIZARD,
                10, 10, 15, 1000, 1500, STATS_WIZARD,
                List.of(DAGGER),
                List.of(SHOCKING_GRASP, MAGIC_MISSILE, MELF_ARROW),
                new AvailableActions(1, 1, 30),
                new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0));

        startWith(exploring, new Peace(), EXPLORING, wizard);

        dnd.doAction(new Rest());

        assertTrue(
                gameRepository.game()
                        .map(Game::playerChar)
                        .map(GameChar::spellSlots)
                        .map(SpellSlots::level1)
                        .orElseThrow() > 0
        );
    }

    @Test
    void resting_heals() {
        startWith(exploring, new Peace(), EXPLORING, new GameChar("Foo", 3,
                CharClass.FIGHTER,
                1, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(DndCombat.LONGSWORD),
                List.of(), STANDARD_ACTIONS, SPELL_SLOTS));

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
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                new NoChat(),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new KillGoal(NpcType.WARRIOR, "goblin", false))
        ));

        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), true);

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
                new NoChat(),
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
                new NoChat(),
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
                new NoChat(),
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

    @Test
    void killing_gives_xp() {
        GameChar gc = new GameChar("Foo", 3,
                CharClass.FIGHTER,
                10, 10, 15,
                1000,
                1500,
                STATS_FIGHTER, List.of(DndCombat.LONGSWORD),
                List.of(), STANDARD_ACTIONS, SPELL_SLOTS);

        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                        new AvailableActions(3, 1, 30),
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, gc);

        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), false);
        dnd.playCombatAction(new WeaponAttack(DndCombat.LONGSWORD), true);

        assertTrue(game().playerChar().xp() > 1000);
    }

    @Test
    void player_damage_lasts_two_turns() {
        DnD dnd = new DnD(gameRepository, dmChannel, playersChannel,
                new MockCombat(true, 3) {
                    @Override
                    public AttackResult computeAttack(
                            Attacks attack,
                            GameChar attacker,
                            GameChar defender
                    ) {
                        if (attack instanceof SpellAttack) {
                            return new Hit(defender.damage(damage), damage, List.of(
                                    new DelayedEffect(1, List.of(
                                            new DamageRoll(damage, Die.D4, Stat.INT)
                                    ), attacker, defender, attack)
                            ));
                        } else {
                            return super.computeAttack(attack, attacker, defender);
                        }
                    }
                });

        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                STANDARD_ACTIONS,
                STANDARD_ACTIONS,
                XP_GAIN, List.of()), COMBAT, foo);

        dnd.playCombatAction(new SpellAttack(MELF_ARROW), false);
        dnd.playCombatAction(new EndTurn(), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                4, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(DndCombat.LONGSWORD),
                List.of(),
                STANDARD_ACTIONS,
                SPELL_SLOTS);

        assertEquals(
                new Fight(true, newGoblin,
                        List.of(
                                "Foo: cast Melf's Magic Arrow (3 hp damage)",
                                "goblin: melee attack with longsword (3 hp damage)",
                                "Foo: cast Melf's Magic Arrow (3 hp damage)"),
                        5,
                        IN_PROGRESS,
                        new AvailableActions(1, 1, 30),
                        new AvailableActions(0, 1, 30),
                        XP_GAIN, List.of()),
                game().combatStatus());
    }

    @Test
    void enemy_damage_lasts_two_turns() {
        DnD dnd = new DnD(gameRepository, dmChannel, playersChannel,
                new MockCounterCombat());

        startWith(combatGoblin,
                new Fight(false, goblin, List.of(),
                        5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new EndTurn(), false);
        dnd.playCombatAction(new EndTurn(), false);

        assertEquals(
                new Fight(true, goblin,
                        List.of(
                                "goblin: cast Melf's Magic Arrow (3 hp damage)",
                                "goblin: cast Melf's Magic Arrow (3 hp damage)"),
                        5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN,
                        List.of()),
                game().combatStatus());
    }

    @Test
    void enemy_damage_lasts_two_turns_and_kills_player() {
        DnD dnd = new DnD(gameRepository, dmChannel, playersChannel,
                new MockCounterCombat());

        GameChar foo = new GameChar("Foo", 3,
                CharClass.FIGHTER,
                4, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(LONGSWORD),
                List.of(), STANDARD_ACTIONS, SPELL_SLOTS);

        startWith(combatGoblin,
                new Fight(false, goblin, List.of(),
                        5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of()),
                COMBAT, foo);

        dnd.playCombatAction(new EndTurn(), false);
        dnd.playCombatAction(new EndTurn(), false);

        assertTrue(game().playerChar().xp() == 1000);
        assertTrue(game().playerChar().isDead());
        assertEquals(
                new Fight(false, goblin,
                        List.of(
                                "goblin: cast Melf's Magic Arrow (3 hp damage)",
                                "goblin: killed Foo, cast Melf's Magic Arrow (3 hp damage)"),
                        5,
                        ENEMY_WON,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN,
                        List.of()),
                game().combatStatus());
    }

    @Test
    void delayed_effect_kills_and_gives_xp() {
        DnD dnd = new DnD(gameRepository, dmChannel, playersChannel,
                new MockCombat(true, 3) {
                    @Override
                    public AttackResult computeAttack(
                            Attacks attack,
                            GameChar attacker,
                            GameChar defender
                    ) {
                        if (attack instanceof SpellAttack) {
                            return new Hit(defender.damage(damage), damage, List.of(
                                    new DelayedEffect(1, List.of(
                                            new DamageRoll(damage, Die.D4, Stat.INT)
                                    ), attacker, defender, attack)
                            ));
                        } else {
                            return super.computeAttack(attack, attacker, defender);
                        }
                    }
                });

        GameChar goblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                4, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(LONGSWORD),
                List.of(),
                STANDARD_ACTIONS, SPELL_SLOTS);

        GameChar foo = new GameChar("Foo", 3,
                CharClass.FIGHTER,
                10, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(LONGSWORD),
                List.of(), STANDARD_ACTIONS, SPELL_SLOTS);

        startWith(combatGoblin, new Fight(true, goblin, List.of(), 5, IN_PROGRESS,
                STANDARD_ACTIONS,
                STANDARD_ACTIONS,
                XP_GAIN, List.of()), COMBAT, foo);

        dnd.playCombatAction(new SpellAttack(MELF_ARROW), false);
        dnd.playCombatAction(new EndTurn(), false);

        GameChar newGoblin = new GameChar(
                "goblin",
                3,
                CharClass.FIGHTER,
                0, 10, 15, 1000, 1500,
                STATS_FIGHTER,
                List.of(DndCombat.LONGSWORD),
                List.of(),
                STANDARD_ACTIONS,
                SPELL_SLOTS);

        assertTrue(game().playerChar().xp() > 1000);
        assertEquals(
                new Fight(true, newGoblin,
                        List.of(
                                "Foo: cast Melf's Magic Arrow (3 hp damage)",
                                "goblin: melee attack with longsword (3 hp damage)",
                                "Foo: killed goblin, cast Melf's Magic Arrow (3 hp damage)",
                                "Gained " + XP_GAIN + " xp"),
                        5,
                        PLAYER_WON,
                        new AvailableActions(1, 1, 30),
                        new AvailableActions(0, 1, 30),
                        XP_GAIN, List.of()),
                game().combatStatus());
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
                new NoChat(),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of()
        );
        gameRepository.save(game);
    }

    private static class MockCombat implements Combat
    {
        final boolean playerActsFirst;
        final int damage;

        private MockCombat(
                boolean playerActsFirst,
                int damage
        ) {
            this.playerActsFirst = playerActsFirst;
            this.damage = damage;
        }

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
                        List.of(LONGSWORD, DAGGER),
                        List.of(),
                        STANDARD_ACTIONS, SPELL_SLOTS
                );
                return new Fight(playerActsFirst, opponent, List.of(), 5, IN_PROGRESS,
                        STANDARD_ACTIONS,
                        STANDARD_ACTIONS,
                        XP_GAIN, List.of());
            }

            @Override
            public GeneratedCombatAction generateAttack(Fight fight) {
                if (fight.opponentActions().actions() > 0)
                    return new GeneratedCombatAction(
                            new WeaponAttack(fight.opponent().weapons().getFirst()),
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
                return new Hit(defender.damage(damage), damage, List.of());
            }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (MockCombat) obj;
            return this.playerActsFirst == that.playerActsFirst &&
                    this.damage == that.damage;
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerActsFirst, damage);
        }

        @Override
        public String toString() {
            return "MockCombat[" +
                    "playerActsFirst=" + playerActsFirst + ", " +
                    "damage=" + damage + ']';
        }

        }

    private class MockCounterCombat
            extends MockCombat
    {
        private int genNum = 0;
        private int computeNum = 0;

        public MockCounterCombat() {super(true, 3);}

        @Override
        public GeneratedCombatAction generateAttack(Fight fight) {
            if (genNum++ == 0)
                return new GeneratedCombatAction(
                        new SpellAttack(MELF_ARROW),
                        false);
            else
                return new GeneratedCombatAction(
                        new EndTurn(),
                        false);
        }

        @Override
        public AttackResult computeAttack(
                Attacks attack,
                GameChar attacker,
                GameChar defender
        ) {
            if (computeNum++ == 0)
                return new Hit(defender.damage(damage), damage, List.of(
                        new DelayedEffect(1, List.of(
                                new DamageRoll(damage, Die.D4, Stat.INT)
                        ), attacker, defender, attack)
                ));
            else
                return new Hit(defender.damage(damage), 3, List.of());
        }
    }
}