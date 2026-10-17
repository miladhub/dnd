package org.meh.dnd;

public record CombatActionView(
        String name,
        String info,
        String label,
        boolean bonusAction,
        String target,
        java.util.List<CombatTargetView> targets,
        String category,
        String icon
)
{
    public CombatActionView(String name, String info, String label, boolean bonusAction) {
        this(name, info, label, bonusAction, "", java.util.List.of(), categoryFor(name), iconFor(name));
    }

    public CombatActionView(String name, String info, String label,
                            boolean bonusAction, String target) {
        this(name, info, label, bonusAction, target, java.util.List.of(), categoryFor(name), iconFor(name));
    }

    public CombatActionView(String name, String info, String label, boolean bonusAction,
                            String target, java.util.List<CombatTargetView> targets) {
        this(name, info, label, bonusAction, target, targets, categoryFor(name), iconFor(name));
    }

    public boolean hasTargets() {
        return !targets.isEmpty();
    }

    public boolean movement() {
        return name.startsWith("Move");
    }

    public boolean isEndTurn() {
        return name.equals("EndTurn");
    }

    private static String categoryFor(String name) {
        if (name.startsWith("Move")) return "movement";
        if (name.equals("EndTurn")) return "end-turn";
        if (name.equals("Spell")) return "spell";
        return "weapon";
    }

    private static String iconFor(String name) {
        return categoryFor(name);
    }

    public String iconSvg() {
        return switch (icon) {
            case "spell" -> "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M12 2l1.8 6.2L20 10l-6.2 1.8L12 18l-1.8-6.2L4 10l6.2-1.8L12 2zM19 15l.7 2.3L22 18l-2.3.7L19 21l-.7-2.3L16 18l2.3-.7L19 15z\" fill=\"currentColor\"/></svg>";
            case "movement" -> "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M12 2l4 4h-3v5h5V8l4 4-4 4v-3h-5v5h3l-4 4-4-4h3v-5H6v3l-4-4 4-4v3h5V6H8l4-4z\" fill=\"currentColor\"/></svg>";
            case "end-turn" -> "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M4 5h11l-3-3 2-2 7 7-7 7-2-2 3-3H4v8h5v3H4a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z\" fill=\"currentColor\"/></svg>";
            default -> "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M14.5 3a5.5 5.5 0 0 0-5.1 7.6L3 17a2.1 2.1 0 1 0 3 3l6.4-6.4A5.5 5.5 0 0 0 20 6.5l-3.8 3.8-2.5-.7-.7-2.5L16.8 3a5.5 5.5 0 0 0-2.3 0z\" fill=\"currentColor\"/></svg>";
        };
    }
}
