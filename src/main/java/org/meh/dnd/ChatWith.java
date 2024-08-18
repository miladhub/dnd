package org.meh.dnd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record ChatWith(
        String target,
        List<ChatMessage> messages)
        implements Chat
{
    public ChatWith add(
            ChatMessage... newOnes
    ) {
        List<ChatMessage> newMessages = new ArrayList<>(messages);
        newMessages.addAll(Arrays.asList(newOnes));
        return new ChatWith(target, newMessages);
    }
}
