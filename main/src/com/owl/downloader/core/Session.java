package com.owl.downloader.core;

import com.owl.downloader.event.Dispatcher;
import com.owl.downloader.event.Event;

import java.io.Serializable;
import java.net.ProxySelector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Session is a singleton class used to manage all the tasks, session also holds some global configurations.
 * <p>Note that there is no guarantee that the configuration changes are applied immediately</p>
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public final class Session implements Serializable {
    private static final long serialVersionUID = -5892477784930807782L;
    private static Session instance = null;
    private final List<Task> tasks = new LinkedList<>();
    private final ReadWriteLock tasksLock = new ReentrantReadWriteLock();
    private ThreadPoolExecutor executor;
    private int maxTasks = 5;
    private int keepaliveTime = 60;
    private ProxySelector proxySelector = ProxySelector.getDefault();

    private Session() {
        Dispatcher.getInstance().attach(this::onTaskStatusChange);
    }

    /**
     * Start the session, some initializations done here, all the tasks cannot be executed until the session is started
     */
    public void start() {
        executor = new ThreadPoolExecutor(0, maxTasks, keepaliveTime, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        tasks.stream().filter(task -> task.status() == Task.Status.ACTIVE).forEach(executor::execute);
        adjustActiveTaskCount();
    }

    /**
     * Stop the session, release the resources
     */
    public void stop() {
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    // Execute waiting tasks if active tasks count does not reach max tasks
    private void adjustActiveTaskCount() {
        tasksLock.readLock().lock();
        try {
            Iterator<Task> iterator = tasks.stream().filter(task -> task.status() == Task.Status.WAITING).iterator();
            while (executor.getActiveCount() < executor.getMaximumPoolSize() && iterator.hasNext())
                executor.execute(iterator.next());
        } finally {
            tasksLock.readLock().unlock();
        }
    }

    private boolean onTaskStatusChange(Event event, Task task, Exception exception) {
        adjustActiveTaskCount();
        return false;
    }

    /**
     * Get the unique instance
     *
     * @return the unique instance
     */
    public static Session getInstance() {
        if (instance == null)
            synchronized (Session.class) {
                if (instance == null) instance = new Session();   // Double check
            }
        return instance;
    }

    /**
     * Get the existing tasks
     *
     * @return the readonly list of existing tasks
     */
    public List<Task> getTasks() {
        tasksLock.readLock().lock();
        try {
            return new LinkedList<>(tasks);
        } finally {
            tasksLock.readLock().unlock();
        }
    }

    /**
     * Add the given task to session, which means the task get opportunity to be executed
     *
     * @param task the task to be added
     */
    public void insertTask(Task task) {
        tasksLock.writeLock().lock();
        try {
            tasks.add(task);
            Dispatcher.getInstance().dispatch(Event.INSERT, task, null);
        } finally {
            tasksLock.writeLock().unlock();
        }
    }

    /**
     * Remove the given task from session
     *
     * @param task the task to be removed
     * @throws IllegalStateException if the task to be removed is in ACTIVE status
     */
    public void removeTask(Task task) {
        if (task.status() == Task.Status.ACTIVE)
            throw new IllegalStateException("cannot remove a task which is active");
        tasksLock.writeLock().lock();
        try {
            tasks.remove(task);
            Dispatcher.getInstance().dispatch(Event.REMOVE, task, null);
        } finally {
            tasksLock.writeLock().unlock();
        }
    }

    /**
     * Get the maximum active task count
     *
     * @return the maximum active task count
     */
    public int getMaxTasks() {
        return maxTasks;
    }

    /**
     * Set the maximum active task count
     *
     * @param maxTasks the maximum active task count
     * @throws IllegalArgumentException if the maxTasks is not positive
     */
    public void setMaxTasks(int maxTasks) {
        if (maxTasks <= 0)
            throw new IllegalArgumentException("cannot set maximum active task count to negative or zero");
        this.maxTasks = maxTasks;
        adjustActiveTaskCount();
    }

    /**
     * Get the overview download speed, which is the sum of the task's download speed
     *
     * @return the overview download speed
     */
    public long downloadSpeed() {
        return tasks.stream().mapToLong(Task::downloadSpeed).sum();
    }

    /**
     * Get the overview upload speed, which is the sum of the task's upload speed
     *
     * @return the overview upload speed
     */
    public long uploadSpeed() {
        return tasks.stream().mapToLong(Task::uploadSpeed).sum();
    }

    /**
     * Get the keepalive time of idle thread used to execute tasks, in seconds
     * <p>The default value is 60 seconds</p>
     *
     * @return the keepalive time of idle thread
     */
    public int getKeepaliveTime() {
        return keepaliveTime;
    }

    /**
     * Set the keepalive time of idle thread, in seconds
     *
     * @param keepaliveTime the keepalive time of idle thread
     * @throws IllegalArgumentException if the keepalive time to be set is negative
     */
    public void setKeepaliveTime(int keepaliveTime) {
        if (keepaliveTime < 0)
            throw new IllegalArgumentException("cannot set keepalive time of idle thread to negative");
        this.keepaliveTime = keepaliveTime;
    }

    /**
     * Get the proxy selector
     *
     * @return the proxy selector
     */
    public ProxySelector getProxySelector() {
        return proxySelector;
    }

    /**
     * Set the proxy selector
     *
     * @param proxySelector the proxy selector
     * @throws NullPointerException if the given proxy selector is null
     */
    public void setProxySelector(ProxySelector proxySelector) {
        Objects.requireNonNull(proxySelector);
        this.proxySelector = proxySelector;
    }

    private Object readResolve() {
        if (instance == null)
            synchronized (Session.class) {
                if (instance == null) instance = this;
            }
        return instance;
    }
}
