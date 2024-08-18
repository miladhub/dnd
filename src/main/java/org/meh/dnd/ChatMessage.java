package org.meh.dnd;

public record ChatMessage(
        ChatRole role,
        String speaker,
        String message
)
{
}
