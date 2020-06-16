package com.owl.downloader.event;

import com.owl.downloader.core.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.LinkedList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit test of Dispatcher
 *
 * @author Zsi-r
 * @version 1.0
 */
public class DispatcherTest {
    private static Dispatcher dispatcher1;
    private static Field handlers;
    private static EventHandler handle1;
    private static LinkedList handlersList;

    static {
        try {
             dispatcher1 = Dispatcher.getInstance();
             handlers = Dispatcher.class.getDeclaredField("handlers");
             handlers.setAccessible(true);
             handlersList = (LinkedList)handlers.get(dispatcher1);

             handle1 = (Event event, Task task, Exception exception)->{
                System.out.println(event);
                System.out.println(task);
                System.out.println(exception);
                return false;
            };
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Test
    void getInstanceTest() {
        Dispatcher dispatcher2 = Dispatcher.getInstance();
        assertSame(dispatcher1,dispatcher2);
    }

    @BeforeAll
    static void attachTest() {
        handlersList.clear();
        for (int i = 0 ; i < 2 ; i++){
            dispatcher1.attach(handle1);
        }
        assertSame(2,handlersList.size());
    }

    @Test
    void dispatchTest() {
        dispatcher1.dispatch(Event.INSERT,null,new IllegalArgumentException());
        dispatcher1.dispatch(Event.INSERT,null,null);
        Task mockTask = mock(Task.class);
        dispatcher1.dispatch(Event.INSERT,mockTask,new NullPointerException());
    }

    @AfterAll
    static void detachTest(){
        assertThrows(NullPointerException.class,()->dispatcher1.detach(null));//parameter should not be null
        EventHandler handle2 = (Event event, Task task, Exception exception)-> true;
        assertDoesNotThrow(()->dispatcher1.detach(handle2));//If this handlerList does not contain the handle, it is unchanged and doesn't throw exception.
        assertSame(2,handlersList.size());
        for (int i = 0 ; i < 3 ; i++){
            dispatcher1.attach(handle2);
        }
        dispatcher1.detach(handle2);
        assertSame(4,handlersList.size());//only detach one handle with the lowest index
        //when handlerList is empty
        handlersList.clear();
        assertSame(0,handlersList.size());
        assertDoesNotThrow(()->dispatcher1.detach(handle1));//If this handlerList is empty, it is unchanged and doesn't throw exception.
    }

}