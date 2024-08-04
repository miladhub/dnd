package org.meh.dnd.openai;

public record OpenAiResponseMessage(
        Role role,
        String content,
        OpenAiFunctionCall function_call
)
{
}
