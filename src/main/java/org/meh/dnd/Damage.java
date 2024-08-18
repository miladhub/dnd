package org.meh.dnd;

import java.util.List;

public record Damage(
        List<DamageRoll> rolls,
        List<DelayedEffect> delayedEffects
)
{
}
