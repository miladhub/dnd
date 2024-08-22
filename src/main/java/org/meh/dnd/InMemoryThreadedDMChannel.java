package org.meh.dnd;

import java.util.concurrent.ExecutorService;

public class InMemoryThreadedDMChannel
        extends InMemoryThreadedChannel<Actions>
        implements DMChannel
{
    public InMemoryThreadedDMChannel(ExecutorService executor) {
        super(executor);
    }
}
