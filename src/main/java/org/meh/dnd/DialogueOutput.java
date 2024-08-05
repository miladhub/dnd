package org.meh.dnd;

import java.util.List;

public record DialogueOutput(
        String phrase,
        List<Actions> answers
)
        implements PlayerOutput
{
}
