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
import static org.meh.dnd.AvailableActionType.WEAPON;

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

    @Test
    void exhausted_ai_main_action_does_not_become_a_negative_action() {
        GameChar player = wizard(new AvailableActions(0, 0, 0),
                new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0));
        GameChar enemyCharacter = npc("enemy", new AvailableActions(0, 0, 5));
        Combatant playerCombatant = new Combatant("player", player, true,
                20, 0, player.availableActions());
        Combatant enemy = new Combatant("enemy", enemyCharacter, false,
                10, 5, enemyCharacter.availableActions());
        saveFight(player, List.of(playerCombatant, enemy));

        dnd.playCombatAction(new EndTurn(), false);

        Fight fight = (Fight) gameRepository.game().orElseThrow().combatStatus();
        assertEquals("player", fight.activeCombatant());
        assertTrue(fight.combatants().stream().allMatch(c ->
                c.actions().actions() >= 0
                        && c.actions().bonusActions() >= 0
                && c.actions().remainingSpeed() >= 0));
    }

    @Test
    void stale_ai_attack_is_replaced_with_end_turn_when_main_action_is_exhausted() {
        CombatActions staleAttack = new WeaponAttack(DndCombat.LONGSWORD, "enemy");

        CombatActions safeAction = DnD.normalizeAiAction(
                staleAttack, new AvailableActions(0, 1, 30));

        assertTrue(safeAction instanceof EndTurn);
    }

    @Test
    void combat_ui_renders_one_attack_with_a_target_dropdown() {
        GameChar player = wizard(STANDARD_ACTIONS,
                new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0));
        Combatant playerCombatant = new Combatant("player", player, true,
                20, 0, STANDARD_ACTIONS);
        Combatant goblin = new Combatant("goblin-id", npc("goblin", STANDARD_ACTIONS),
                false, 10, 5, STANDARD_ACTIONS);
        Combatant orc = new Combatant("orc-id", npc("orc", STANDARD_ACTIONS),
                false, 5, 5, STANDARD_ACTIONS);
        List<Combatant> combatants = List.of(playerCombatant, goblin, orc);
        CombatOutput output = new CombatOutput(true, STANDARD_ACTIONS, STANDARD_ACTIONS,
                goblin.character(), List.of(), false, false, 5,
                List.of(new AvailableAction(WEAPON, DndCombat.LONGSWORD.name(), false)),
                combatants, "player");
        Game game = new Game(COMBAT, List.of(output), player,
                new Peace(), new NoChat(), "background", "place", new Nobody(),
                List.of(), List.of());

        String html = ViewEncoderDecoder.encodeOutput(output, game);

        assertEquals(1, html.split("Attack with longsword", -1).length - 1);
        assertTrue(html.contains("<select"));
        assertTrue(html.contains(">goblin</option>"));
        assertTrue(html.contains(">orc</option>"));
    }

    @Test
    void multi_combat_movement_log_names_the_target() {
        GameChar player = wizard(new AvailableActions(0, 0, 5),
                new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0));
        Combatant playerCombatant = new Combatant("player", player, true,
                20, 0, player.availableActions());
        Combatant enemy = new Combatant("enemy", npc("goblin", new AvailableActions(0, 0, 0)),
                false, 10, 5, new AvailableActions(0, 0, 0));
        saveFight(player, List.of(playerCombatant, enemy));

        dnd.playCombatAction(new Move(Dir.AWAY_FROM_ENEMY, 5), false);

        Fight fight = (Fight) gameRepository.game().orElseThrow().combatStatus();
        assertTrue(fight.log().contains("wizard: move 5 feet away from goblin"));
    }

    @Test
    void dead_player_ends_multi_combat_even_when_an_ally_is_still_alive() {
        GameChar deadPlayer = wizard(new AvailableActions(0, 0, 0),
                new SpellSlots(0, 0, 0, 0, 0, 0, 0, 0, 0)).withHp(0);
        Combatant player = new Combatant("player", deadPlayer, true,
                20, 0, deadPlayer.availableActions());
        Combatant ally = new Combatant("ally", npc("Finnean", STANDARD_ACTIONS),
                true, 15, 0, STANDARD_ACTIONS);
        Combatant enemy = new Combatant("enemy", npc("Wolf", STANDARD_ACTIONS),
                false, 10, 5, STANDARD_ACTIONS);
        Fight fight = new Fight(false, enemy.character(), List.of(), 5,
                IN_PROGRESS, deadPlayer.availableActions(), enemy.actions(), 100,
                List.of(), List.of(player, ally, enemy), "ally");

        Fight advanced = dnd.advanceMultiFight(fight, fight.combatants(), fight.log());

        assertEquals(FightOutcome.ENEMY_WON, advanced.outcome());
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
