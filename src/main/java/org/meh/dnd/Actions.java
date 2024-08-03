package org.meh.dnd;

public sealed interface Actions
        permits Attack, Dialogue, Rest, Explore
{
}
