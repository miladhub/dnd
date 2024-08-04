package org.meh.dnd.openai;

public record OpenAiUsage(
        int prompt_tokens,
        int completion_tokens,
        int total_tokens
)
{
}
