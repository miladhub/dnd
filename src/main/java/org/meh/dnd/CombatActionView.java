package org.meh.dnd;

public record CombatActionView(
        String name,
        String info,
        String label,
        boolean bonusAction
)
{
}
