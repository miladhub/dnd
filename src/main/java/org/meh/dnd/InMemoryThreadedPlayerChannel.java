package org.meh.dnd;

import java.util.concurrent.ExecutorService;

public class InMemoryThreadedPlayerChannel
        extends InMemoryThreadedChannel<PlayerOutput>
        implements PlayerChannel {
    public InMemoryThreadedPlayerChannel(ExecutorService executor) {
        super(executor);
    }
}
