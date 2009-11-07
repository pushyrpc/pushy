package pushy;

import junit.framework.TestCase;

import pushy.internal.Message;
import pushy.modules.*;

import java.io.IOException;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProtocolTest extends TestCase
{
    private Client client;

    public void setUp() throws IOException {
        client = new Client("local:");
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

    public void testStrings() {
        Object value = client.evaluate("u'\\u1234'");
        assertEquals("\u1234", value);

        // Make sure all of the characters 0-255 are returned unscathed.
        value = client.evaluate("''.join([chr(i) for i in range(256)])");
        assertTrue(value instanceof String);
        String stringValue = (String)value;
        assertEquals(256, stringValue.length());
        for (int i = 0; i < stringValue.length(); ++i)
            assertEquals((char)i, stringValue.charAt(i));
    }

    public void testProxyObject() {
        Object dirFunction = client.evaluate("dir");
        assertTrue(dirFunction instanceof PushyObject);
        Object value = ((PushyObject)dirFunction).__call__();
        assertTrue(value instanceof List);
    }

    /**
     * Make sure the message types in Python match those in Java.
     */
    public void testMessageTypes() {
        Module message = client.getModule("pushy.protocol.message");
        Object[] types = (Object[])message.__getattr__("message_types");
        for (int i = 0; i < types.length; ++i)
        {
            PushyObject remoteMessageType = (PushyObject)types[i];
            Message.Type localMessageType = Message.Type.getType(i);
            if (localMessageType != null)
            {
                assertEquals(
                    remoteMessageType.__getattr__("name"),
                    localMessageType.getName());
            }
        }
    }
}

