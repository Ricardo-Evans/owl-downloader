package com.owl.downloader.event;

import com.owl.downloader.core.Task;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Singleton class used to manage all the event
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public class Dispatcher {
    private final List<EventHandler> handlers = new LinkedList<>();
    private static Dispatcher instance = null;

    /**
     * Get the unique instance
     *
     * @return the unique instance
     */
    public static Dispatcher getInstance() {
        if (instance == null) {
            synchronized (Dispatcher.class) {
                if (instance == null) instance = new Dispatcher();  // Double check
            }
        }
        return instance;
    }

    private Dispatcher() {
    }

    /**
     * Deliver an event
     *
     * @param event     the event type
     * @param task      where the event occur
     * @param exception any exception related if exist
     */
    public synchronized void dispatch(Event event, Task task, Exception exception) {
        boolean handled = false;
        Iterator<EventHandler> iterator = handlers.iterator();
        while (!handled && iterator.hasNext()) handled = iterator.next().handle(event, task, exception);
    }

    /**
     * Attach a handler
     *
     * @param handler the event handler
     * @throws NullPointerException if handler is null
     */
    public synchronized void attach(EventHandler handler) {
        Objects.requireNonNull(handler);
        handlers.add(handler);
    }

    /**
     * Detach a handler
     *
     * @param handler the event handler
     * @throws NullPointerException if handler is null
     */
    public synchronized void detach(EventHandler handler) {
        Objects.requireNonNull(handler);
        handlers.remove(handler);
    }
}
