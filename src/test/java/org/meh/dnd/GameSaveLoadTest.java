package org.meh.dnd;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            new AvailableAction(WEAPON, "longsword", false),
            new AvailableAction(WEAPON, "longsword", true),
            new AvailableAction(MOVE, "5", false),
            new AvailableAction(END_TURN, "", false));
    private static final AvailableActions STANDARD_ACTIONS = new AvailableActions(1, 1, 30);
    private static final SpellSlots SPELL_SLOTS = new SpellSlots(4, 3, 0, 0, 0, 0, 0, 0, 0);
    private final GameChar goblin = new GameChar(
            "goblin",
            3,
            CharClass.FIGHTER,
            10, 10, 15, 1000, 1500,
            STATS_FIGHTER,
            List.of(LONGSWORD),
            List.of(),
            STANDARD_ACTIONS,
            SPELL_SLOTS);
    private final CombatOutput combatGoblin =
            new CombatOutput(true, STANDARD_ACTIONS, STANDARD_ACTIONS, goblin, List.of(), false, false, 5, AVAILABLE_ACTIONS);
    private final GameChar foo = new GameChar("Foo", 3,
            CharClass.FIGHTER,
            10, 10, 15, 1000, 1500, STATS_FIGHTER,
            List.of(LONGSWORD, LONGBOW),
            List.of(MAGIC_MISSILE, SHOCKING_GRASP),
            STANDARD_ACTIONS,
            SPELL_SLOTS);

    @Test
    void save_load_no_quests()
    throws Exception {
        Game game = new Game(
                EXPLORING,
                List.of(combatGoblin),
                foo,
                new Peace(),
                new NoChat(),
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
                new NoChat(),
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
                new NoChat(),
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
                new NoChat(),
                "Once upon a time in the west...",
                "Dark Forest",
                new Nobody(),
                List.of(),
                List.of(new KillGoal(NpcType.WARRIOR, "goblin", false))
        );
        assertEquals(saved, g);
    }

    @Test
    void save_and_load_character()
    throws Exception {
        String json =
                //language=JSON
                """
                {
                    "ac" : 15,
                    "availableActions" : {
                      "actions" : 1,
                      "bonusActions" : 1,
                      "remainingSpeed" : 30
                    },
                    "charClass" : "FIGHTER",
                    "hp" : 10,
                    "level" : 3,
                    "maxHp" : 10,
                    "name" : "Foo",
                    "nextXp" : 1500,
                    "spellSlots" : {
                      "level1" : 4,
                      "level2" : 3,
                      "level3" : 0,
                      "level4" : 0,
                      "level5" : 0,
                      "level6" : 0,
                      "level7" : 0,
                      "level8" : 0,
                      "level9" : 0
                    },
                    "spells" : [ {
                      "damage" : "D4",
                      "level" : 1,
                      "name" : "Magic Missile",
                      "ranged" : true,
                      "rollsToHit" : false
                    }, {
                      "damage" : "D8",
                      "level" : 0,
                      "name" : "Shocking Grasp",
                      "ranged" : false,
                      "rollsToHit" : true
                    } ],
                    "stats" : {
                      "charisma" : 10,
                      "constitution" : 14,
                      "dexterity" : 13,
                      "intelligence" : 8,
                      "strength" : 15,
                      "wisdom" : 12
                    },
                    "weapons" : [ {
                      "damage" : "D8",
                      "light" : true,
                      "name" : "longsword",
                      "ranged" : false,
                      "twoHanded" : false
                    }, {
                      "damage" : "D6",
                      "light" : false,
                      "name" : "longbow",
                      "ranged" : true,
                      "twoHanded" : true
                    } ],
                    "xp" : 1000
                  }
                """;
        ObjectMapper mapper = new ObjectMapper();
        GameChar ch = mapper.readValue(json, GameChar.class);
        assertEquals(foo, ch);
    }

    public record RecWithWeapons(List<Weapon> weapons) {}

    @Test
    void save_and_load_record_with_weapons()
    throws Exception {
        String json =
                //language=JSON
                """
                {
                  "weapons" :
                  [
                    {
                      "damage" : "D8",
                      "light" : true,
                      "name" : "longsword",
                      "ranged" : false,
                      "twoHanded" : false
                    },
                    {
                      "damage" : "D6",
                      "light" : false,
                      "name" : "longbow",
                      "ranged" : true,
                      "twoHanded" : true
                    }
                  ]
                }
                """;
        ObjectMapper mapper = new ObjectMapper();
        RecWithWeapons ch = mapper.readValue(json, RecWithWeapons.class);
        assertEquals(new RecWithWeapons(foo.weapons()), ch);
    }

    public record Foo(String foo) {}
    public record RecWithFoes(List<Foo> foes) {}

    @Test
    void save_and_load_record_with_foes()
    throws Exception {
        String json =
                //language=JSON
                """
                {
                  "foes" :
                  [
                    {
                      "foo" : "foo1"
                    },
                    {
                      "foo" : "foo2"
                    }
                  ]
                }
                """;
        ObjectMapper mapper = new ObjectMapper();
        RecWithFoes ch = mapper.readValue(json, RecWithFoes.class);
        assertEquals(new RecWithFoes(
                List.of(new Foo("foo1"), new Foo("foo2"))),
                ch);
    }
}