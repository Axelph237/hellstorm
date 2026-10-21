package dev.candycanecazoo.hellstorm.game;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** One game's event bus. Listeners run in subscription order on the server thread. */
public final class GameEvents {
    private final List<Consumer<GameEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<GameEvent> listener) {
        listeners.add(listener);
    }

    public void emit(GameEvent event) {
        for (Consumer<GameEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
