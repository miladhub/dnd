package org.meh.dnd.openai;

public record OpenAiChoice(
        OpenAiResponseMessage message,
        String finish_reason
)
{
}
