package org.meh.dnd.openai;

import java.util.List;

public interface OpenAiClient
{
    ChatResponse chatCompletion(
            List<OpenAiRequestMessage> messages,
            List<ModelFunction> functions
    ) throws Exception;
}
