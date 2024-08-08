package org.meh.dnd;

public interface Combat
{
    Fight generateFight(String opponentName);
    CombatActions generateAttack(Fight fight);
    AttackResult computeAttack(
            Attacks attack,
            GameChar attacker,
            GameChar defender
    );
}
