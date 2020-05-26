package com.owl.downloader.core;

import com.owl.downloader.event.Dispatcher;
import com.owl.downloader.event.Event;

import java.net.ProxySelector;
import java.util.HashMap;
import java.util.Map;

/**
 * The skeleton implementation of task
 *
 * @author Ricardo Evans
 * @version 1.0
 */
public abstract class BaseTask implements Task {
    private static final long serialVersionUID = -1021838795789258648L;
    private volatile Status status = Status.WAITING;
    private static final Map<Status, Event> EVENT_MAP = new HashMap<>();
    private FileData.BlockSelector blockSelector;
    private final String name;
    private int maximumConnections = Session.getInstance().getMaximumConnections();
    private String directory = Session.getInstance().getDirectory();
    private int blockSize;
    private ProxySelector proxySelector = Session.getInstance().getProxySelector();

    static {
        EVENT_MAP.put(Status.ACTIVE, Event.START);
        EVENT_MAP.put(Status.WAITING, Event.WAIT);
        EVENT_MAP.put(Status.PAUSED, Event.PAUSE);
        EVENT_MAP.put(Status.COMPLETED, Event.COMPLETE);
        EVENT_MAP.put(Status.ERROR, Event.ERROR);
    }

    protected BaseTask(String name) {
        this.name = name;
    }

    @Override
    public final Status status() {
        return status;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public void start() {
        if (status != Status.PAUSED) throw new IllegalStateException("only paused task can be started");
        changeStatus(Status.WAITING);
    }

    @Override
    public void pause() {
        if (status != Status.ACTIVE && status != Status.WAITING)
            throw new IllegalStateException("only active|waiting tasks can be paused");
        changeStatus(Status.PAUSED);
    }

    @Override
    public void prepare() {
        changeStatus(Status.ACTIVE);
    }

    /**
     * Equal to changeStatus(status, null)
     *
     * @param status the destination status
     * @see BaseTask#changeStatus(Status, Exception)
     */
    protected final void changeStatus(Status status) {
        changeStatus(status, null);
    }

    /**
     * Change the status of the task to the given status, this will automatically send an event
     *
     * @param status    the target status
     * @param exception why status change
     */
    protected final void changeStatus(Status status, Exception exception) {
        this.status = status;
        Dispatcher.getInstance().dispatch(EVENT_MAP.get(status), this, exception);
    }

    @Override
    public int getMaximumConnections() {
        return maximumConnections;
    }

    @Override
    public void setMaximumConnections(int maximumConnections) {
        if (maximumConnections <= 0) throw new IllegalArgumentException();
        this.maximumConnections = maximumConnections;
    }

    @Override
    public String getDirectory() {
        return directory;
    }

    @Override
    public void setDirectory(String directory) {
        this.directory = directory;
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public void setBlockSize(int blockSize) {
        if (blockSize <= 0) throw new IllegalArgumentException();
        this.blockSize = blockSize;
    }

    @Override
    public ProxySelector getProxySelector() {
        return proxySelector;
    }

    @Override
    public void setProxySelector(ProxySelector proxySelector) {
        this.proxySelector = proxySelector;
    }

    @Override
    public FileData.BlockSelector getBlockSelector() {
        if (blockSelector == null) return FileData.BlockSelector.getDefault();
        return blockSelector;
    }

    @Override
    public void setBlockSelector(FileData.BlockSelector blockSelector) {
        this.blockSelector = blockSelector;
    }
}
