package org.meh.dnd;

public record Explore(String place)
        implements Actions
{
    public Explore {
        if (place == null || place.isBlank())
            throw new IllegalArgumentException("place");
    }
}
