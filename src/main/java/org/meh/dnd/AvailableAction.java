package org.meh.dnd;

public record AvailableAction(
        AvailableActionType type,
        String info,
        boolean bonusAction
)
{
}
