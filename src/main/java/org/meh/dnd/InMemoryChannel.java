package org.meh.dnd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class InMemoryChannel<T>
        implements Channel<T>
{
    private final Map<String, List<Consumer<T>>> consumers = new ConcurrentHashMap<>();

    @Override
    public void subscribe(
            String topic,
            Consumer<T> consumer
    ) {
        consumers.putIfAbsent(topic, new ArrayList<>());
        consumers.get(topic).add(consumer);
    }

    @Override
    public void post(
            String topic,
            T message
    ) {
        consumers.getOrDefault(topic, List.of()).forEach(c -> c.accept(message));
    }
}
