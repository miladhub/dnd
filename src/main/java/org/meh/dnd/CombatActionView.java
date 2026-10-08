package org.meh.dnd;

public record CombatActionView(
        String name,
        String info,
        String label,
        boolean bonusAction,
        String target,
        java.util.List<CombatTargetView> targets
)
{
    public CombatActionView(String name, String info, String label, boolean bonusAction) {
        this(name, info, label, bonusAction, "", java.util.List.of());
    }

    public CombatActionView(String name, String info, String label,
                            boolean bonusAction, String target) {
        this(name, info, label, bonusAction, target, java.util.List.of());
    }

    public boolean hasTargets() {
        return !targets.isEmpty();
    }
}
