package org.meh.dnd;

public interface Combat
{
    Fight generateFight(
            GameChar gameChar,
            String opponentName);
    GeneratedCombatAction generateAttack(Fight fight);
    AttackResult computeAttack(
            Attacks attack,
            GameChar attacker,
            GameChar defender
    );
}
