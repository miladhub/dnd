package org.meh.dnd;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class InMemoryThreadedChannel<T>
        implements Channel<T>
{
    private final ExecutorService executor;
    private final List<Consumer<T>> consumers = new CopyOnWriteArrayList<>();

    public InMemoryThreadedChannel(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public void subscribe(Consumer<T> consumer) {
        consumers.add(consumer);
    }

    @Override
    public void post(T message) {
        consumers.forEach(c -> executor.submit(() -> c.accept(message)));
    }
}
