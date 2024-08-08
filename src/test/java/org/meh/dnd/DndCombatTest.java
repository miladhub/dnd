package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.meh.dnd.DndCombat.*;

class DndCombatTest
{
    private final DndCombat c = new DndCombat();
    private final GameChar all_weapons = new GameChar("Foo", 10, 10, List.of(SWORD, BOW), List.of());
    private final GameChar all_spells = new GameChar("Foo", 10, 10, List.of(), List.of(MAGIC_MISSILE, SHOCKING_GRASP));
    private final GameChar everything = new GameChar("Foo", 10, 10, List.of(SWORD, BOW), List.of(MAGIC_MISSILE, SHOCKING_GRASP));
    private final GameChar only_melee = new GameChar("Foo", 10, 10, List.of(SWORD), List.of(SHOCKING_GRASP));
    private final GameChar only_ranged = new GameChar("Foo", 10, 10, List.of(BOW), List.of(MAGIC_MISSILE));

    @Test
    void pick_melee_weapon_with_less_than_5_feet() {
        assertEquals(new WeaponAttack(SWORD.name()), c.generateAttack(new Fight(true, all_weapons, "", 5, FightStatus.IN_PROGRESS)));
        assertEquals(new WeaponAttack(SWORD.name()), c.generateAttack(new Fight(true, everything, "", 5, FightStatus.IN_PROGRESS)));
    }

    @Test
    void pick_melee_spell_with_less_than_5_feet() {
        assertEquals(new SpellAttack(SHOCKING_GRASP.name()), c.generateAttack(new Fight(true, all_spells, "", 5, FightStatus.IN_PROGRESS)));
    }

    @Test
    void pick_ranged_weapon_with_more_than_5_feet() {
        assertEquals(new WeaponAttack(BOW.name()), c.generateAttack(new Fight(true, all_weapons, "", 6, FightStatus.IN_PROGRESS)));
        assertEquals(new WeaponAttack(BOW.name()), c.generateAttack(new Fight(true, everything, "", 6, FightStatus.IN_PROGRESS)));
    }

    @Test
    void pick_ranged_spell_with_more_than_5_feet() {
        assertEquals(new SpellAttack(MAGIC_MISSILE.name()), c.generateAttack(new Fight(true, all_spells, "", 6, FightStatus.IN_PROGRESS)));
    }

    @Test
    void pick_movement_towards_enemy_if_no_ranged_weapon() {
        assertEquals(new Move(Dir.TOWARDS_ENEMY, 5), c.generateAttack(new Fight(true, only_melee, "", 6, FightStatus.IN_PROGRESS)));
    }

    @Test
    void pick_movement_away_from_enemy_if_only_ranged_weapon() {
        assertEquals(new Move(Dir.AWAY_FROM_ENEMY, 5), c.generateAttack(new Fight(true, only_ranged, "", 5, FightStatus.IN_PROGRESS)));
    }
}