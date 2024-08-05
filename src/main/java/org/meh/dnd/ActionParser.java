package org.meh.dnd;

public class ActionParser
{
    public static Actions actionFrom(String action,
                                     String info
    ) {
        return switch (action) {
            case "Attack" -> new Attack(info);
            case "Dialogue" -> new Dialogue(info);
            case "Rest" -> new Rest();
            case "Explore" -> new Explore(info);
            case "Say" -> new Say(info);
            case "EndDialogue" -> new EndDialogue();
            default ->
                    throw new IllegalStateException("Unexpected value: " + action);
        };
    }
}
