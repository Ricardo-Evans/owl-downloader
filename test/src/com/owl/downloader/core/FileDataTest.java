package com.owl.downloader.core;

import com.owl.downloader.event.Dispatcher;
import com.owl.downloader.event.Event;
import com.owl.downloader.event.EventHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Testing the FileData
 *
 * @author Zsi-r
 * @version 1.0
 */
@SuppressWarnings("unchecked")//Line 61 will generate unchecked cast warning. We know this cast is safe so we ignore this warning.
class FileDataTest {

    private static String filePath = "test/src/com/owl/downloader/core/FileDataTest.txt";
    private static Method splitMethod;
    private static Field fileField ;
    private static Field blocksField ;
    private static LinkedList<FileData.Block> blockList;
    private static FileData fileDatabyFile;
    private static FileData fileDatabyPath ;
    private static File file ;
    private static int blockSize;


    static {
        try {
            splitMethod = FileData.class.getDeclaredMethod("split",int.class);
            fileField = FileData.class.getDeclaredField("file");
            blocksField = FileData.class.getDeclaredField("blocks");
            splitMethod.setAccessible(true);
            fileField.setAccessible(true);
            blocksField.setAccessible(true);
            // Write data into file. The file size is 1986 bytes
            String data = "1".repeat(1986);
            BufferedWriter bfWriter = new BufferedWriter(new FileWriter(filePath));
            bfWriter.write(data);
            bfWriter.close();
            file = new File(filePath);
            //initialize fileData
            blockSize = 100;
            fileDatabyFile = new FileData(file,blockSize);
            fileDatabyPath = new FileData(filePath,blockSize);
            blockList = (LinkedList<FileData.Block>) blocksField.get(fileDatabyFile);

        } catch (NoSuchFieldException | NoSuchMethodException | IOException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @BeforeAll
    static void constructorTest(){
        //the path of file cannot be null
        assertThrows(NullPointerException.class,()->new FileData((String)null,100));
        //the file cannot be null
        assertThrows(NullPointerException.class,()->new FileData((File)null,100));
        //the block size of file must be positive integer
        assertThrows(IllegalArgumentException.class,()->new FileData(filePath,0));
        assertThrows(IllegalArgumentException.class,()->new FileData(filePath,-1));
        assertThrows(IllegalArgumentException.class,()->new FileData(filePath, -100));


    }

    @Test
    void splitTest()  {
        assertEquals(20,blockList.size());
    }

    @Test
    void getFileTest() {
        assertSame(file, fileDatabyFile.getFile());
    }

    @Test
    void getBlocksTest() throws IllegalAccessException {
        assertEquals(blocksField.get(fileDatabyFile).toString(),fileDatabyFile.getBlocks().toString());
    }

    /**
     *  Testing the Block class
     */
    @Test
    void BlockClassTest(){
        int offset = 0;
        int length ;
        for (FileData.Block block : blockList) {
            length = (int) Math.min(blockSize, file.length() - block.offset);
            assertEquals(length, block.length);
            assertEquals(offset, block.offset);
            offset += blockSize;
        }
        assertSame(fileDatabyFile,blockList.getFirst().file());
    }

    @AfterAll
    static void deleteFile(){
        assertTrue(()->file.delete());
    }
}