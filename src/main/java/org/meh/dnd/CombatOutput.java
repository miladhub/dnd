package org.meh.dnd;

public record CombatOutput(
        String opponent
)
        implements PlayerOutput
{
}
