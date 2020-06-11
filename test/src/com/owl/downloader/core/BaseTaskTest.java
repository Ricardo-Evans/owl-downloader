package com.owl.downloader.core;

import com.owl.downloader.event.Dispatcher;
import com.owl.downloader.event.Event;
import com.owl.downloader.event.EventHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ProxySelector;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BaseTaskTest {
    BaseTask baseTask1 = new BaseTask("name of baseTask1") {
        @Override
        public long downloadSpeed() {
            return 0;
        }

        @Override
        public long uploadSpeed() {
            return 0;
        }

        @Override
        public long downloadedLength() {
            return 0;
        }

        @Override
        public long uploadedLength() {
            return 0;
        }

        @Override
        public long totalLength() {
            return 0;
        }

        @Override
        public List<FileData> files() {
            return null;
        }

        @Override
        public void run() {
            System.out.println("baseTask1 is running");
        }
    };

    private static Dispatcher dispatcher = Dispatcher.getInstance();
    private static Field statusField;
    private static Field blockSelectorField;
    private static Field nameField;
    private static Field maximumConnectionsField;
    private static Field directoryField;
    private static Field blockSizeField;
    private static Field proxySelectorField;
    private static Method changeStatusMethod;
    private static EventHandler handler1;

    static {
        try {
            statusField = BaseTask.class.getDeclaredField("status");
            blockSelectorField = BaseTask.class.getDeclaredField("blockSelector");
            nameField = BaseTask.class.getDeclaredField("name");
            maximumConnectionsField = BaseTask.class.getDeclaredField("maximumConnections");
            directoryField = BaseTask.class.getDeclaredField("directory");
            blockSelectorField = BaseTask.class.getDeclaredField("blockSelector");
            proxySelectorField = BaseTask.class.getDeclaredField("proxySelector");
            blockSizeField = BaseTask.class.getDeclaredField("blockSize");
            changeStatusMethod = BaseTask.class.getDeclaredMethod("changeStatus", Task.Status.class, Exception.class);

            statusField.setAccessible(true);
            blockSelectorField.setAccessible(true);
            nameField.setAccessible(true);
            maximumConnectionsField.setAccessible(true);
            directoryField.setAccessible(true);
            blockSelectorField.setAccessible(true);
            proxySelectorField.setAccessible(true);
            blockSizeField.setAccessible(true);
            changeStatusMethod.setAccessible(true);

            handler1 = (Event event, Task task, Exception exception)->{
                System.out.println("Event: "+event);
                return false;
            };
            dispatcher.attach(handler1);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Test
    void statusTest() throws IllegalAccessException {
        assertSame(statusField.get(baseTask1),baseTask1.status());
    }

    @Test
    void name() throws IllegalAccessException {
        assertSame(nameField.get(baseTask1),baseTask1.name());
    }

    @Test
    void start() throws InvocationTargetException, IllegalAccessException {
        System.out.println();
        System.out.println("In start function: ");
        changeStatusMethod.invoke(baseTask1,Task.Status.COMPLETED,null);
        assertThrows(IllegalStateException.class,()->baseTask1.start());
        changeStatusMethod.invoke(baseTask1,Task.Status.ACTIVE,null);
        assertThrows(IllegalStateException.class,()->baseTask1.start());
        changeStatusMethod.invoke(baseTask1,Task.Status.WAITING,null);
        assertThrows(IllegalStateException.class,()->baseTask1.start());

        changeStatusMethod.invoke(baseTask1,Task.Status.PAUSED,null);
        baseTask1.start();
        assertEquals(Task.Status.WAITING,statusField.get(baseTask1));

        changeStatusMethod.invoke(baseTask1,Task.Status.ERROR,null);
        baseTask1.start();
        assertEquals(Task.Status.WAITING,statusField.get(baseTask1));
    }

    @Test
    void pause() throws InvocationTargetException, IllegalAccessException {
        System.out.println();
        System.out.println("In pause function: ");
        changeStatusMethod.invoke(baseTask1,Task.Status.COMPLETED,null);
        assertThrows(IllegalStateException.class,()->baseTask1.pause());
        changeStatusMethod.invoke(baseTask1,Task.Status.ERROR,null);
        assertThrows(IllegalStateException.class,()->baseTask1.pause());
        changeStatusMethod.invoke(baseTask1,Task.Status.PAUSED,null);
        assertThrows(IllegalStateException.class,()->baseTask1.pause());

        changeStatusMethod.invoke(baseTask1,Task.Status.ACTIVE,null);
        baseTask1.pause();
        assertEquals(Task.Status.PAUSED,statusField.get(baseTask1));

        changeStatusMethod.invoke(baseTask1,Task.Status.WAITING,null);
        baseTask1.pause();
        assertEquals(Task.Status.PAUSED,statusField.get(baseTask1));
    }

    @Test
    void prepare() throws IllegalAccessException {
        baseTask1.prepare();
        System.out.println();
        System.out.println("In prepare function: ");
        assertEquals(Task.Status.ACTIVE,statusField.get(baseTask1));
    }

    @Test
    void changeStatus() throws InvocationTargetException, IllegalAccessException {
        System.out.println();
        System.out.println("In changeStatus function: ");
        changeStatusMethod.invoke(baseTask1,Task.Status.ACTIVE,null);
        assertEquals(Task.Status.ACTIVE,statusField.get(baseTask1));
        changeStatusMethod.invoke(baseTask1,Task.Status.WAITING,null);
        assertEquals(Task.Status.WAITING,statusField.get(baseTask1));
        changeStatusMethod.invoke(baseTask1,Task.Status.COMPLETED,null);
        assertEquals(Task.Status.COMPLETED,statusField.get(baseTask1));
        changeStatusMethod.invoke(baseTask1,Task.Status.PAUSED,null);
        assertEquals(Task.Status.PAUSED,statusField.get(baseTask1));
        changeStatusMethod.invoke(baseTask1,Task.Status.ERROR,null);
        assertEquals(Task.Status.ERROR,statusField.get(baseTask1));
    }

    @Test
    void getMaximumConnections() throws IllegalAccessException {
        assertEquals(maximumConnectionsField.get(baseTask1),baseTask1.getMaximumConnections());
    }

    @Test
    void setMaximumConnections() {
        assertThrows(IllegalArgumentException.class,()->baseTask1.setMaximumConnections(0));
        assertThrows(IllegalArgumentException.class,()->baseTask1.setMaximumConnections(-10086));
        assertThrows(IllegalArgumentException.class,()->baseTask1.setMaximumConnections(-1));
        baseTask1.setMaximumConnections(100);
        assertSame(100,baseTask1.getMaximumConnections());
        baseTask1.setMaximumConnections(5);
        assertSame(5,baseTask1.getMaximumConnections());
    }

    @Test
    void getDirectory() throws IllegalAccessException {
        assertSame(directoryField.get(baseTask1),baseTask1.getDirectory());
    }

    @Test
    void setDirectory() {
        String defaultDirectory = baseTask1.getDirectory();
        baseTask1.setDirectory("test/src/com/owl/downloader/core");
        assertSame("test/src/com/owl/downloader/core",baseTask1.getDirectory());
        baseTask1.setDirectory(defaultDirectory);
        assertSame(defaultDirectory,baseTask1.getDirectory());
    }

    @Test
    void getBlockSize() throws IllegalAccessException {
        assertEquals(blockSizeField.get(baseTask1),baseTask1.getBlockSize());
    }

    @Test
    void setBlockSize() {
        assertThrows(IllegalArgumentException.class,()->baseTask1.setBlockSize(0));
        assertThrows(IllegalArgumentException.class,()->baseTask1.setBlockSize(-10086));
        assertThrows(IllegalArgumentException.class,()->baseTask1.setBlockSize(-1));
        baseTask1.setBlockSize(1<<20);
        assertEquals(1<<20,baseTask1.getBlockSize());
        baseTask1.setBlockSize(1<<14);
        assertEquals(1<<14,baseTask1.getBlockSize());
    }

    @Test
    void getProxySelector() throws IllegalAccessException {
        assertSame(proxySelectorField.get(baseTask1),baseTask1.getProxySelector());
    }

    @Test
    void setProxySelector() throws IllegalAccessException {
        ProxySelector defaultSelector = (ProxySelector) proxySelectorField.get(baseTask1);
        ProxySelector mockProxySelector = mock(ProxySelector.class);
        baseTask1.setProxySelector(mockProxySelector);
        assertSame(mockProxySelector,baseTask1.getProxySelector());
        baseTask1.setProxySelector(ProxySelector.getDefault());
        assertSame(defaultSelector,proxySelectorField.get(baseTask1));
    }

    //TODO: implement BlockSelector test
    @Disabled
    void getBlockSelector() throws IllegalAccessException {
        if (blockSizeField.get(baseTask1)!=null){
            assertSame(blockSizeField.get(baseTask1),baseTask1.getBlockSelector());
        }
    }

    @Disabled
    void setBlockSelector() {
    }
}