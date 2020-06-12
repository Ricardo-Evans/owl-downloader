package com.owl.downloader.io;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.ForkJoinPool;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultIOSchedulerTest {
    private static DefaultIOScheduler scheduler1 = DefaultIOScheduler.getInstance();
    private static Field selectorField;
    private static Field daemonField;
    private static Field executorFiled;
    private static Field runningField;
    private static Method doReadMethod;
    private static Method doWriteMethod;
    private static Method readMethod;
    private static Method writeMethod;
    private static Method runMethod;

    static {
        try {
            selectorField = DefaultIOScheduler.class.getDeclaredField("selector");
            daemonField = DefaultIOScheduler.class.getDeclaredField("daemon");
            executorFiled = DefaultIOScheduler.class.getDeclaredField("executor");
            runningField = DefaultIOScheduler.class.getDeclaredField("running");
            doReadMethod = DefaultIOScheduler.class.getDeclaredMethod("doRead", ReadableByteChannel.class, ByteBuffer.class, IOCallback.class);
            doWriteMethod = DefaultIOScheduler.class.getDeclaredMethod("doWrite", WritableByteChannel.class, ByteBuffer.class, IOCallback.class);
            readMethod = DefaultIOScheduler.class.getDeclaredMethod("read", ReadableByteChannel.class, ByteBuffer.class, IOCallback.class);
            writeMethod = DefaultIOScheduler.class.getDeclaredMethod("write", WritableByteChannel.class, ByteBuffer.class, IOCallback.class);
            runMethod = DefaultIOScheduler.class.getDeclaredMethod("run");
            selectorField.setAccessible(true);
            daemonField.setAccessible(true);
            executorFiled.setAccessible(true);
            runningField.setAccessible(true);
            doReadMethod.setAccessible(true);
            doWriteMethod.setAccessible(true);
            readMethod.setAccessible(true);
            writeMethod.setAccessible(true);
            runMethod.setAccessible(true);
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getInstanceTest() {
        DefaultIOScheduler scheduler2 = DefaultIOScheduler.getInstance();
        assertSame(scheduler1,scheduler2);
    }

    @BeforeAll
    static void startTest()  throws IOException, IllegalAccessException {
        System.out.println("start "+ runningField.get(scheduler1));
        scheduler1.start();
        Selector selector = (Selector) selectorField.get(scheduler1);
        assertTrue(selector.isOpen());
        Thread daemon = (Thread) daemonField.get(scheduler1);
        assertTrue(daemon.isDaemon());
        assertTrue(daemon.isAlive());
        ForkJoinPool executor = (ForkJoinPool) executorFiled.get(scheduler1);
        assertTrue(executor.isQuiescent());
        assertEquals(0,executor.getPoolSize());
    }

    @AfterAll
    static void stopTest() throws IOException, IllegalAccessException {
        System.out.println("stop "+ runningField.get(scheduler1));
        scheduler1.stop();
        assertNull(executorFiled.get(scheduler1));
        assertNull(daemonField.get(scheduler1));
        assertNull(selectorField.get(scheduler1));
        System.out.println("stop2 "+ runningField.get(scheduler1));
    }

    @Test
    void doReadTest() throws IOException, InvocationTargetException, IllegalAccessException {
        System.out.println("doRead "+ runningField.get(scheduler1));
        //create a temporal file and write data into the it
        String filePath1 = "test/src/com/owl/downloader/io/doReadTest.txt";
        BufferedWriter bfWriter = new BufferedWriter(new FileWriter(filePath1));
        bfWriter.write("1".repeat(400)); // size 400 Bytes in total
        bfWriter.close();

        RandomAccessFile file1 = new RandomAccessFile(filePath1,"rw");
        FileChannel channel1 = file1.getChannel();

        ByteBuffer buffer1 = ByteBuffer.allocate(16 * 1024);
        IOCallback callback = (Channel fileChannel, ByteBuffer responseBuffer, int size, Exception exception)->{
            assertEquals(400,size);
            assertNull(exception);
        };
        doReadMethod.invoke(scheduler1,channel1,buffer1,callback);
        buffer1.clear();
        file1.close();
        //delete the temporal file
        assertTrue(new File(filePath1).delete());
    }

    @Test
    void doWriteTest() throws IOException, InvocationTargetException, IllegalAccessException {
        System.out.println("doWrite "+ runningField.get(scheduler1));
        String filePath1 = "test/src/com/owl/downloader/io/doWriteTest.txt";
        RandomAccessFile file1 = new RandomAccessFile(filePath1,"rw");
        FileChannel channel1 = file1.getChannel();

        String data = "t".repeat(400);
        ByteBuffer buffer1 = ByteBuffer.allocate(1024);
        buffer1.clear();
        buffer1.put(data.getBytes());
        buffer1.flip(); //switch from read mode to write mode

        IOCallback callback = (Channel fileChannel, ByteBuffer responseBuffer, int size, Exception exception)->{
            assertEquals(400,size);
            assertNull(exception);
        };
        doWriteMethod.invoke(scheduler1,channel1,buffer1,callback);
        buffer1.clear();
        file1.close();
        //delete the temporal file
        assertTrue(new File(filePath1).delete());
    }

    @Disabled
    void ReadRunTest() throws IllegalAccessException, IOException {
        assertTrue((Boolean) runningField.get(scheduler1));
        SocketChannel mockChannel = mock(SocketChannel.class);
        mockChannel.configureBlocking(false);
        ByteBuffer mockBuffer = mock(ByteBuffer.class);
        IOCallback callback = (Channel fileChannel, ByteBuffer responseBuffer, int size, Exception exception)->{
            System.out.println(fileChannel);
            System.out.println(responseBuffer);
            System.out.println(size);
            System.out.println(exception);
        };
//        read.invoke(scheduler1,mockChannel,mockBuffer,callback);

    }
}