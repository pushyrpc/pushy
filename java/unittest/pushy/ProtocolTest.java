package pushy;

import junit.framework.TestCase;

import pushy.modules.*;
import java.io.IOException;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProtocolTest extends TestCase
{
    private Client2 client;

    public void setUp() throws IOException {
        client = new Client2("local:");
    }

    public void tearDown() {
        client.close();
    }

    public void testEvaluate() {
        assertEquals(new Integer(3), client.evaluate("1+2"));
    }

    public void testArrays() {
        Object value = client.evaluate("(1,2,3)");
        assertTrue(value.getClass().isArray());
        assertEquals(Integer.TYPE, value.getClass().getComponentType());
        int[] array = (int[])value;
        assertEquals(3, array.length);
        assertEquals(1, array[0]);
        assertEquals(2, array[1]);
        assertEquals(3, array[2]);
    }
}

