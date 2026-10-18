package org.meh.dnd;

import java.util.List;

/** Stable, renderer-neutral JSON models for non-HTML clients. */
public final class ApiModels {
    private ApiModels() {}

    public record State(
            String mode,
            String place,
            String background,
            List<String> diary,
            List<Quest> quests,
            Character player,
            Narrative narrative,
            Combat combat
    ) {}

    public record Character(
            String name, int level, String charClass, int ac, int hp, int maxHp,
            int xp, int nextXp, Stats stats, List<Weapon> weapons,
            List<Spell> spells, AvailableActions availableActions, SpellSlots spellSlots
    ) {}

    public record Stats(
            int strength, int dexterity, int constitution, int intelligence,
            int wisdom, int charisma
    ) {}

    public record Weapon(String name, boolean ranged, String damage, boolean twoHanded, boolean light) {}
    public record Spell(String name, boolean ranged, String damage, boolean rollsToHit, int level) {}
    public record SpellSlots(int first, int second, int third, int fourth, int fifth, int sixth, int seventh, int eighth, int ninth) {}
    public record AvailableActions(int actions, int bonusActions, int remainingSpeed) {}
    public record Quest(String type, String target, String description, boolean reached) {}

    public record Narrative(
            String type, String text, List<Action> choices, String storyLine,
            String interactionTarget
    ) {}

    public record Action(
            String type, String info, String target, String direction,
            int amount, boolean bonusAction
    ) {}

    public record Combat(
            boolean playerTurn, boolean playerWon, boolean enemyWon,
            String activeCombatant, int distance, AvailableActions availableActions,
            List<String> log, List<Combatant> combatants, List<Target> targets,
            List<Action> actions
    ) {}

    public record Combatant(
            String id, Character character, boolean ally, int initiative,
            int position, GridPosition gridPosition, AvailableActions actions,
            boolean active, boolean dead
    ) {}

    public record Target(
            String id, String name, int hp, int maxHp, int distance, List<Action> actions
    ) {}

    public record GridPosition(int column, int row) {}

    public record Event(
            String protocol, int version, long sequence, String type, State state
    ) {}

    public record CommandResult(boolean accepted, String error, State state) {}
}
