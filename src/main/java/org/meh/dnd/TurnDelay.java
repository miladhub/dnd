package org.meh.dnd;

@FunctionalInterface
interface TurnDelay {
    void pause();

    static TurnDelay none() {
        return () -> { };
    }

    static TurnDelay ofMillis(long millis) {
        return () -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
