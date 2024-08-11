package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.meh.dnd.CharClass.*;
import static org.meh.dnd.DndCombat.*;
import static org.meh.dnd.FightOutcome.*;

class DndCombatTest
{
    private final DndCombat c = new DndCombat();
    private final GameChar all_weapons = new GameChar("Foo", 3, FIGHTER, 10,
            10, 13, 1000, 1500, STATS_FIGHTER, List.of(SWORD, BOW), List.of(), STANDARD_ACTIONS);
    private final GameChar all_spells = new GameChar("Foo", 3, FIGHTER, 10, 10, 13, 1000, 1500, STATS_FIGHTER, List.of(), List.of(MAGIC_MISSILE, SHOCKING_GRASP), STANDARD_ACTIONS);
    private final GameChar everything_light = new GameChar("Foo", 3, FIGHTER, 10, 10, 13, 1000, 1500, STATS_FIGHTER, List.of(SWORD, BOW), List.of(MAGIC_MISSILE, SHOCKING_GRASP), STANDARD_ACTIONS);
    private final GameChar everything_heavy = new GameChar("Foo", 3, FIGHTER,
            10, 10, 13, 1000, 1500, STATS_FIGHTER, List.of(BATTLEAXE, BOW),
            List.of(MAGIC_MISSILE, SHOCKING_GRASP), STANDARD_ACTIONS);
    private final GameChar only_melee = new GameChar("Foo", 3, FIGHTER, 10, 10, 13, 1000, 1500, STATS_FIGHTER, List.of(SWORD), List.of(SHOCKING_GRASP), STANDARD_ACTIONS);
    private final GameChar only_ranged = new GameChar("Foo", 3, FIGHTER, 10,
            10, 13, 1000, 1500, STATS_FIGHTER, List.of(BOW), List.of(MAGIC_MISSILE), STANDARD_ACTIONS);
    private final AvailableActions available =
            new AvailableActions(1, 1, 30);

    @Test
    void pick_melee_weapon_with_less_than_5_feet_heavy() {
        assertEquals(new GeneratedCombatAction(new WeaponAttack(BATTLEAXE.name()), false),
                c.generateAttack(new Fight(true, everything_heavy, List.of(), 5,
                        IN_PROGRESS, available, available)));
    }

    @Test
    void pick_melee_spell_with_less_than_5_feet() {
        assertEquals(new GeneratedCombatAction(new SpellAttack(SHOCKING_GRASP.name()), false),
                c.generateAttack(new Fight(true, all_spells, List.of(), 5,
                        IN_PROGRESS, available, available)));
    }

    @Test
    void pick_ranged_weapon_with_more_than_5_feet() {
        assertEquals(new GeneratedCombatAction(new WeaponAttack(BOW.name()), false),
                c.generateAttack(new Fight(true, all_weapons, List.of(), 6,
                        IN_PROGRESS, available, available)));
        assertEquals(new GeneratedCombatAction(new WeaponAttack(BOW.name()), false),
                c.generateAttack(new Fight(true, everything_light, List.of(), 6,
                        IN_PROGRESS, available, available)));
    }

    @Test
    void pick_ranged_spell_with_more_than_5_feet() {
        assertEquals(new GeneratedCombatAction(new SpellAttack(MAGIC_MISSILE.name()), false),
                c.generateAttack(new Fight(true, all_spells, List.of(), 6,
                        IN_PROGRESS, available, available)));
    }

    @Test
    void pick_movement_towards_enemy_if_no_ranged_weapon() {
        assertEquals(new GeneratedCombatAction(new Move(Dir.TOWARDS_ENEMY, 5), false),
                c.generateAttack(new Fight(true, only_melee, List.of(), 6,
                        IN_PROGRESS, available, available)));
    }

    @Test
    void pick_movement_away_from_enemy_if_only_ranged_weapon() {
        assertEquals(new GeneratedCombatAction(new Move(Dir.AWAY_FROM_ENEMY, 5), false),
                c.generateAttack(new Fight(true, only_ranged, List.of(), 5,
                        IN_PROGRESS, available, available)));
    }

    @Test
    void choose_bonus_if_main_action_is_not_available() {
        assertEquals(new GeneratedCombatAction(new WeaponAttack(SWORD.name()), true),
                c.generateAttack(new Fight(true, everything_light, List.of(), 5,
                        IN_PROGRESS, available, new AvailableActions(0, 1, 30))));
    }

    @Test
    void stop_if_no_action_useful() {
        assertEquals(new GeneratedCombatAction(new EndTurn(), false),
                c.generateAttack(new Fight(true, everything_heavy, List.of(), 5,
                        IN_PROGRESS, available, new AvailableActions(0, 1, 30))));
    }
}