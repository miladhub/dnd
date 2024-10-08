package org.meh.dnd;

public interface Combat
{
    Fight generateFight(
            GameChar gameChar,
            Attack attack
    );
    GeneratedCombatAction generateAttack(Fight fight);
    AttackResult computeAttack(
            Attacks attack,
            GameChar attacker,
            GameChar defender
    );
}
