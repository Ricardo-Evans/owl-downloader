package com.owl.downloader.core;

import com.owl.downloader.event.Dispatcher;
import com.owl.downloader.event.Event;
import com.owl.downloader.event.EventHandler;
import com.owl.downloader.exception.UnsupportedProtocolException;
import org.junit.jupiter.api.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ProxySelector;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class SessionTest {
    private static Session session1 = Session.getInstance();
    private static Dispatcher dispatcher = Dispatcher.getInstance();
    private static Method adjustActiveTaskCountMethod;
    private static Field tasksField;
    private static Field tasksLockField;
    private static LinkedList<Task> tasks;
    private static Field maxTasksField;
    private static Field keepaliveTimeField;
    private static Field proxySelectorField;
    private static Field directoryField;
    private static Field maximumConnectionsField;
    private static Field blockSizeField;
    private static Field executorField;
    private static EventHandler handle1;

    private static BaseTask task1 = new BaseTask("name of task1") {
        @Override
        public long downloadSpeed() {
            return 1111;
        }

        @Override
        public long uploadSpeed() {
            return 111;
        }

        @Override
        public long downloadedLength() {
            return 1000;
        }

        @Override
        public long uploadedLength() {
            return 1000;
        }

        @Override
        public long totalLength() {
            return 1000;
        }

        @Override
        public List<FileData> files() {
            return null;
        }

        @Override
        public void run() {
            System.out.println("task1 is running.");
        }
    };

    private static BaseTask task2 = new BaseTask("name of task2") {
        @Override
        public long downloadSpeed() {
            return -2222;
        }

        @Override
        public long uploadSpeed() {
            return 222;
        }

        @Override
        public long downloadedLength() {
            return 1000;
        }

        @Override
        public long uploadedLength() {
            return 1000;
        }

        @Override
        public long totalLength() {
            return 1000;
        }

        @Override
        public List<FileData> files() {
            return null;
        }

        @Override
        public void run() {
            System.out.println("task2 is running.");
        }
    };

    static {
        try {
            adjustActiveTaskCountMethod = Session.class.getDeclaredMethod("adjustActiveTaskCount");
            tasksField = Session.class.getDeclaredField("tasks");
            tasksLockField = Session.class.getDeclaredField("tasksLock");
            maxTasksField = Session.class.getDeclaredField("maxTasks");
            keepaliveTimeField = Session.class.getDeclaredField("keepaliveTime");
            proxySelectorField = Session.class.getDeclaredField("proxySelector");
            directoryField = Session.class.getDeclaredField("directory");
            maximumConnectionsField = Session.class.getDeclaredField("maximumConnections");
            blockSizeField = Session.class.getDeclaredField("blockSize");
            executorField = Session.class.getDeclaredField("executor");

            adjustActiveTaskCountMethod.setAccessible(true);
            tasksField.setAccessible(true);
            tasksLockField.setAccessible(true);
            maxTasksField.setAccessible(true);
            keepaliveTimeField.setAccessible(true);
            proxySelectorField.setAccessible(true);
            directoryField.setAccessible(true);
            maximumConnectionsField.setAccessible(true);
            blockSizeField.setAccessible(true);
            executorField.setAccessible(true);

            tasks = (LinkedList<Task>) tasksField.get(session1);
            handle1 = (Event event, Task task, Exception exception)->{
                System.out.println(event);
                System.out.println(task);
                System.out.println(exception);
                return false;
            };
            dispatcher.attach(handle1);

        } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    @BeforeAll
    @Order(1)
    static void startTest() throws IOException, IllegalAccessException {
        session1.start();
        assertTrue(executorField.get(session1) instanceof ForkJoinPool);
        assertEquals(0,tasks.stream().filter(task -> task.status() == Task.Status.ACTIVE).count());
    }

    @AfterAll
    @Order(MAX_VALUE)
    static void stopTest() throws IOException {
        session1.stop();
    }

    @BeforeAll
    @Order(2)
    static void insertTaskTest() throws IOException {
        session1.insertTask(task1);
        session1.insertTask(task2);
        assertEquals(2, tasks.size());
    }

    @AfterAll
    @Order(MAX_VALUE-1)
    static void removeTaskTest() throws IOException {
        tasks.getFirst().prepare();
        assertThrows(IllegalStateException.class,()->session1.removeTask(task1));
        tasks.getFirst().pause();
        session1.removeTask(task1);
        assertEquals(1,tasks.size());
        assertSame(task2,tasks.getFirst());
    }

    @Test
    void adjustActiveTaskCountTest() throws InvocationTargetException, IllegalAccessException {
        adjustActiveTaskCountMethod.invoke(session1);
        assertEquals(0, tasks.stream().filter(task -> task.status()== Task.Status.WAITING).count());
    }

    @Test
    void getInstanceTest() {
        Session session2 = Session.getInstance();
        assertSame(session1,session2);
    }

    @Test
    void getTasksTest() {
        List<Task> tasks2 = session1.getTasks();
        for (int i=0;i<tasks2.size();i++){
            assertEquals(tasks.get(i),tasks2.get(i));
        }
    }


    @Test
    void getMaxTasksTest() throws IllegalAccessException {
        assertEquals(maxTasksField.get(session1),session1.getMaxTasks());
    }

    @Test
    void setMaxTasksTest() {
        assertThrows(IllegalArgumentException.class,()->session1.setMaxTasks(-1));
        assertThrows(IllegalArgumentException.class,()->session1.setMaxTasks(0));
    }

    @Test
    void downloadSpeedTest() {
        assertEquals(-1111,session1.downloadSpeed());
    }

    @Test
    void uploadSpeedTest() {
        assertEquals(333,session1.uploadSpeed());
    }

    @Test
    void getKeepaliveTimeTest() throws IllegalAccessException {
        assertEquals(keepaliveTimeField.get(session1),session1.getKeepaliveTime());
    }

    @Test
    void setKeepaliveTimeTest() {
        assertThrows(IllegalArgumentException.class,()->session1.setKeepaliveTime(-1));
        session1.setKeepaliveTime(100);
        assertEquals(100,session1.getKeepaliveTime());
    }

    @Test
    void getProxySelectorTest() throws IllegalAccessException {
        assertSame(proxySelectorField.get(session1),session1.getProxySelector());
    }

    @Test
    void setProxySelectorTest() throws IllegalAccessException {
        assertThrows(NullPointerException.class,()->session1.setProxySelector(null));
        ProxySelector defaultSelector = (ProxySelector) proxySelectorField.get(session1);
        ProxySelector mockProxySelector = mock(ProxySelector.class);
        session1.setProxySelector(mockProxySelector);
        assertSame(mockProxySelector,session1.getProxySelector());
        session1.setProxySelector(ProxySelector.getDefault());
        assertSame(defaultSelector,proxySelectorField.get(session1));
    }

    @Test
    void getDirectory() throws IllegalAccessException {
        assertSame(directoryField.get(session1),session1.getDirectory());
    }

    @Test
    void setDirectory() {
        String defaultDirectory = session1.getDirectory();
        assertThrows(NullPointerException.class,()->session1.setDirectory(null));
        session1.setDirectory("test/src/com/owl/downloader/core");
        assertSame("test/src/com/owl/downloader/core",session1.getDirectory());
        session1.setDirectory(defaultDirectory);
        assertSame(defaultDirectory,session1.getDirectory());
    }

    @Test
    void getMaximumConnections() throws IllegalAccessException {
        assertEquals(maximumConnectionsField.get(session1),session1.getMaximumConnections());
    }

    @Test
    void setMaximumConnections() {
        assertThrows(IllegalArgumentException.class,()->session1.setMaximumConnections(0));
        assertThrows(IllegalArgumentException.class,()->session1.setMaximumConnections(-10086));
        assertThrows(IllegalArgumentException.class,()->session1.setMaximumConnections(-1));
        session1.setMaximumConnections(100);
        assertSame(100,session1.getMaximumConnections());
        session1.setMaximumConnections(5);
        assertSame(5,session1.getMaximumConnections());
    }

    @Test
    void getBlockSize() throws IllegalAccessException {
        assertEquals(blockSizeField.get(session1),session1.getBlockSize());
    }

    @Test
    void setBlockSize() {
        assertThrows(IllegalArgumentException.class,()->session1.setBlockSize(0));
        assertThrows(IllegalArgumentException.class,()->session1.setBlockSize(-10086));
        assertThrows(IllegalArgumentException.class,()->session1.setBlockSize(-1));
        session1.setBlockSize(1<<20);
        assertEquals(1<<20,session1.getBlockSize());
        session1.setBlockSize(1<<14);
        assertEquals(1<<14,session1.getBlockSize());
    }

    @Test
    void fromUri() {
        assertThrows(NullPointerException.class,()->Session.fromUri(URI.create(null)));
        URI uri1 = URI.create("ftp://www.runoob.com/java/java-exceptions.html");
        assertThrows(UnsupportedProtocolException.class,
                ()->Session.fromUri(uri1));
        URI uri2 = URI.create("https://www.runoob.com/java/java-exceptions.html");
        assertTrue(()->Session.fromUri(uri2) instanceof HttpTask);
    }

    // TODO: Test of supported protocol should wait for bt protocol to be written.
    @Disabled
    void fromFile() {
        assertThrows(NullPointerException.class,()->Session.fromFile(null));
        assertThrows(IllegalArgumentException.class,
                ()->Session.fromFile(new File("E:\\EI 333\\owl-downloader")),
                "the file used to construct task cannot be directories");
        assertThrows(UnsupportedProtocolException.class,
                ()->Session.fromFile(new File("DefaultIOScheduler.java")),
                "protocol java is not supported");
    }
}