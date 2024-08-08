package org.meh.dnd;

public class ActionParser
{
    public static Actions actionFrom(String action,
                                     String info
    ) {
        return switch (cleanAction(action)) {
            case "Attack" -> new Attack(info);
            case "Dialogue" -> new Dialogue(info);
            case "Rest" -> new Rest();
            case "Explore" -> new Explore(info);
            case "Say" -> new Say(cleanSay(info));
            case "EndDialogue" -> new EndDialogue();
            default ->
                    throw new IllegalStateException("Unexpected value: " + action);
        };
    }

    private static String cleanSay(String what) {
        String clean = what.trim();
        if (clean.startsWith("\"") && clean.endsWith("\""))
            return clean.substring(1, clean.length() - 1).trim();
        else
            return clean;
    }

    public static CombatActions combatActionFrom(
            String action,
            String info
    ) {
        return switch (action) {
            case "Melee" -> new MeleeAttack(info);
            case "Spell" -> new SpellAttack(info);
            default ->
                    throw new IllegalStateException("Unexpected value: " + action);
        };
    }

    private static String cleanAction(String action) {
        return action.replaceAll(":", "");
    }
}
