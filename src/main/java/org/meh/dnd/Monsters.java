package org.meh.dnd;

import java.util.List;

public record Monsters()
{
    public static GameChar generate(String name) {
        return new GameChar(
                name,
                List.of(new Weapon("sword")),
                List.of());
    }
}
