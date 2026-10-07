package org.meh.dnd;

public record CombatActionView(
        String name,
        String info,
        String label,
        boolean bonusAction,
        String target
)
{
    public CombatActionView(String name, String info, String label, boolean bonusAction) {
        this(name, info, label, bonusAction, "");
    }
}
