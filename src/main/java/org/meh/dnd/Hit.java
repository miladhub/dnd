package org.meh.dnd;

import java.util.List;

public record Hit(
        GameChar gameChar,
        int damage,
        List<DelayedEffect> delayedEffects
)
        implements AttackResult
{
}
