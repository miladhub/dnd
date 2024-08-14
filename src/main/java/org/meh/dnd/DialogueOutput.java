package org.meh.dnd;

import java.util.List;

public record DialogueOutput(
        String target,
        String phrase,
        List<Actions> answers
)
        implements PlayerOutput
{
    public DialogueOutput withChoices(List<Actions> answers) {
        return null;
    }
}
