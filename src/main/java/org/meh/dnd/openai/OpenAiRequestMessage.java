package org.meh.dnd.openai;

public record OpenAiRequestMessage(
        Role role,
        String content
)
{
}
