package pushy;

import junit.framework.TestCase;

import pushy.internal.Message;
import pushy.modules.*;

import java.io.IOException;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
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

    /**
     * Make sure we can pass around non-primitive objects.
     */
    public void testDictionaryMap() {
        Map map = new HashMap();
        map.put("a", "b");
        map.put("c", "d");

        // Store a local object somewhere so that we might access it later.
        Module builtin = client.getModule("__builtin__");
        builtin.__setattr__("mymap", map);

        // Check that the same object will be returned.
        assertSame(map, builtin.__getattr__("mymap"));
        assertSame(map, client.evaluate("__import__('__builtin__').mymap"));

        // Check that a Map becomes a dictionary.
        Boolean isdict =
            (Boolean)client.evaluate(
                "isinstance(__import__('__builtin__').mymap, dict)");
        assertTrue(isdict.booleanValue());

        // Check that dictionary methods can be called.
        String[] keys =
            (String[])client.evaluate(
                "tuple(__import__('__builtin__').mymap.keys())");
        String[] values =
            (String[])client.evaluate(
                "tuple(__import__('__builtin__').mymap.values())");
        assertEquals(2, keys.length);
        assertEquals(2, values.length);
        Arrays.sort(keys);
        Arrays.sort(values);
        assertEquals("a", keys[0]);
        assertEquals("c", keys[1]);
        assertEquals("b", values[0]);
        assertEquals("d", values[1]);

        // Check that updates to the remote object are effective in the local
        // one.
        client.evaluate("__import__('__builtin__').mymap.update({'a':1})");
        assertEquals(new Integer(1), map.get("a"));
    }

    /**
     * Ensure floats are marshalled to/from Python correctly.
     */
    public void testFloatMarshalling()
    {
        // Python -> Java
        assertEquals(new Double(Double.NaN),
            client.evaluate("float('nan')"));
        assertEquals(new Double(Double.POSITIVE_INFINITY),
            client.evaluate("float('inf')"));
        assertEquals(new Double(Double.NEGATIVE_INFINITY),
            client.evaluate("float('-inf')"));
        assertEquals(new Double(1.2e34d),
            client.evaluate("1.2e34"));

        // Java -> Python
        PushyObject id = (PushyObject)client.evaluate("lambda a: a");
        assertEquals(
            new Double(Double.NaN),
            id.__call__(new Object[]{new Double(Double.NaN)}));
        assertEquals(
            new Double(Double.POSITIVE_INFINITY),
            id.__call__(new Object[]{new Double(Double.POSITIVE_INFINITY)}));
        assertEquals(
            new Double(Double.NEGATIVE_INFINITY),
            id.__call__(new Object[]{new Double(Double.NEGATIVE_INFINITY)}));
        assertEquals(
            new Double(1.2e34),
            id.__call__(new Object[]{new Double(1.2e34)}));
    }
}

