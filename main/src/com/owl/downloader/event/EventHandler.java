package com.owl.downloader.event;

import com.owl.downloader.core.Task;

/**
 * Event handler, called by Dispatcher when an event occur
 *
 * @author Ricardo Evans
 * @version 1.0
 * @see Dispatcher
 */
@FunctionalInterface
public interface EventHandler {
    /**
     * Called when an event occur
     *
     * @param event     the event type
     * @param task      the task where the event occur
     * @param exception any exception related if exist
     * @return whether this handler handle the event, which means the following handler will not receive the event if true is returned
     */
    boolean handle(Event event, Task task, Exception exception);
}
