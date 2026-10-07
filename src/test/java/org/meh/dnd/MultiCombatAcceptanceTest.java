package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.meh.dnd.CharClass.WIZARD;
import static org.meh.dnd.DndCombat.MELF_ARROW;
import static org.meh.dnd.DndCombat.STANDARD_ACTIONS;
import static org.meh.dnd.FightOutcome.IN_PROGRESS;
import static org.meh.dnd.GameMode.COMBAT;

class MultiCombatAcceptanceTest {
    private final InMemoryGameRepository gameRepository = new InMemoryGameRepository();
    private final PlayerChannel playersChannel = new InMemoryPlayerChannel();
    private final DnD dnd = new DnD(gameRepository, new InMemoryDMChannel(),
            playersChannel, new DndCombat());

    @Test
    void resets_all_living_combatants_at_the_start_of_a_new_round() {
        GameChar player = wizard(new AvailableActions(1, 0, 30),
                new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0));
        GameChar ally = npc("guard", STANDARD_ACTIONS);
        Combatant playerCombatant = new Combatant("player", player, true,
                20, 0, player.availableActions());
        Combatant enemy = new Combatant("enemy", ally, false,
                10, 5, new AvailableActions(0, 0, 0));
        saveFight(player, List.of(playerCombatant, enemy));

        dnd.playCombatAction(new EndTurn(), false);

        Fight fight = (Fight) gameRepository.game().orElseThrow().combatStatus();
        Combatant resetEnemy = fight.combatants().stream()
                .filter(c -> c.id().equals("enemy"))
                .findFirst().orElseThrow();
        assertEquals("player", fight.activeCombatant());
        assertEquals(STANDARD_ACTIONS, resetEnemy.actions());
    }

    @Test
    void melfs_arrow_applies_delayed_damage_and_consumes_spell_slot_in_multi_combat() {
        GameChar player = wizard(new AvailableActions(1, 0, 30),
                new SpellSlots(0, 1, 0, 0, 0, 0, 0, 0, 0));
        GameChar enemyCharacter = npc("goblin", STANDARD_ACTIONS)
                .withHp(100);
        Combatant playerCombatant = new Combatant("player", player, true,
                20, 0, player.availableActions());
        Combatant enemy = new Combatant("enemy", enemyCharacter, false,
                10, 5, new AvailableActions(0, 0, 0));
        saveFight(player, List.of(playerCombatant, enemy));

        dnd.playCombatAction(new SpellAttack(MELF_ARROW, "enemy"), false);
        Fight afterCast = (Fight) gameRepository.game().orElseThrow().combatStatus();
        assertEquals(0, afterCast.combatants().stream()
                .filter(c -> c.id().equals("player"))
                .findFirst().orElseThrow().character().spellSlots().level2());
        assertEquals(1, afterCast.delayedEffects().size());

        dnd.playCombatAction(new EndTurn(), false);

        Fight afterDelayedDamage = (Fight) gameRepository.game().orElseThrow().combatStatus();
        Combatant updatedPlayer = afterDelayedDamage.combatants().stream()
                .filter(c -> c.id().equals("player"))
                .findFirst().orElseThrow();
        Combatant updatedEnemy = afterDelayedDamage.combatants().stream()
                .filter(c -> c.id().equals("enemy"))
                .findFirst().orElseThrow();
        assertTrue(updatedEnemy.character().hp() < enemyCharacter.hp());
        assertTrue(afterDelayedDamage.delayedEffects().isEmpty());
        assertTrue(afterDelayedDamage.log().stream()
                .anyMatch(line -> line.contains("Melf's Magic Arrow")));
        assertFalse(updatedPlayer.character().isDead());
    }

    private void saveFight(GameChar player, List<Combatant> combatants) {
        Fight fight = new Fight(true, combatants.get(1).character(), List.of(), 5,
                IN_PROGRESS, combatants.get(0).actions(), combatants.get(1).actions(),
                100, List.of(), combatants, "player");
        gameRepository.save(new Game(COMBAT, List.of(), player, fight,
                new NoChat(), "background", "place", new Nobody(), List.of(), List.of()));
    }

    private static GameChar wizard(AvailableActions actions, SpellSlots slots) {
        return new GameChar("wizard", 3, WIZARD, 20, 20, 13, 0, 300,
                DndCombat.STATS_WIZARD, List.of(), List.of(MELF_ARROW), actions, slots);
    }

    private static GameChar npc(String name, AvailableActions actions) {
        return new GameChar(name, 3, CharClass.FIGHTER, 10, 10, 13, 0, 300,
                DndCombat.STATS_FIGHTER, List.of(), List.of(), actions,
                new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0));
    }
}
