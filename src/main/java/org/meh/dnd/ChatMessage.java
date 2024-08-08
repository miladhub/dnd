package org.meh.dnd;

public record ChatMessage(
        ChatRole role,
        String message
)
{
}
