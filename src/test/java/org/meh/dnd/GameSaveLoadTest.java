package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.meh.dnd.AvailableActionType.*;
import static org.meh.dnd.DndCombat.*;
import static org.meh.dnd.GameMode.EXPLORING;

class GameSaveLoadTest
{
    private static final List<AvailableAction> AVAILABLE_ACTIONS = List.of(
            new AvailableAction(WEAPON, "sword", false),
            new AvailableAction(WEAPON, "sword", true),
            new AvailableAction(MOVE, "5", false),
            new AvailableAction(END_TURN, "", false));
    public static final AvailableActions STANDARD_ACTIONS = new AvailableActions(1, 1, 30);
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
    private final GameChar foo = new GameChar("Foo", 3,
            CharClass.FIGHTER,
            10, 10, 15, 1000, 1500, STATS_FIGHTER, List.of(SWORD, BOW),
            List.of(MAGIC_MISSILE, SHOCKING_GRASP), STANDARD_ACTIONS);

    @Test
    void save_load_no_quests()
    throws Exception {
        Game game = new Game(
                EXPLORING,
                List.of(combatGoblin),
                foo,
                new Peace(),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of()
        );
        String saveStr = GameSaveLoad.save(game);
        Game g = GameSaveLoad.load(new StringReader(saveStr));
        Game saved = new Game(
                EXPLORING,
                List.of(new ExploreOutput(
                        "Dark Forest",
                        "Ready.",
                        List.of(new Start("Dark Forest")),
                        "")),
                foo,
                new Peace(),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of()
        );
        assertEquals(saved, g);
    }

    @Test
    void save_load_with_quests()
    throws Exception {
        Game game = new Game(
                EXPLORING,
                List.of(combatGoblin),
                foo,
                new Peace(),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new KillGoal(NpcType.WARRIOR, "goblin", false))
        );
        String saveStr = GameSaveLoad.save(game);
        Game g = GameSaveLoad.load(new StringReader(saveStr));
        Game saved = new Game(
                EXPLORING,
                List.of(new ExploreOutput(
                        "Dark Forest",
                        "Ready to continue.",
                        List.of(new Explore("Dark Forest")),
                        "")),
                foo,
                new Peace(),
                new Chat(List.of()),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new KillGoal(NpcType.WARRIOR, "goblin", false))
        );
        assertEquals(saved, g);
    }
}