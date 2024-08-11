package org.meh.dnd;

import static java.lang.Integer.parseInt;
import static org.meh.dnd.Dir.AWAY_FROM_ENEMY;
import static org.meh.dnd.Dir.TOWARDS_ENEMY;

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
            case "Start" -> new Start();
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
            case "Melee" -> new WeaponAttack(info);
            case "Spell" -> new SpellAttack(info);
            case "MoveForward" -> new Move(TOWARDS_ENEMY, parseInt(info));
            case "MoveBackward" -> new Move(AWAY_FROM_ENEMY, parseInt(info));
            case "EndTurn" -> new EndTurn();
            default ->
                    throw new IllegalStateException("Unexpected value: " + action);
        };
    }

    private static String cleanAction(String action) {
        return action.replaceAll(":", "");
    }
}
