package org.meh.dnd;

import java.util.List;

public record DelayedEffect(
        int inTurns,
        List<DamageRoll> damageRolls,
        GameChar attacker,
        GameChar defender,
        Attacks attack
)
{
}
