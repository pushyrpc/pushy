package pushy;

import junit.framework.TestCase;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

import pushy.internal.Marshal;

public class MarshalTest extends TestCase
{
    // Assert that the value, when marshalled, is unmarshalled to a value equal
    // to the input.
    private void assertMarshalIdentity(Object value) throws Exception
    {
        Object unmarshalled = Marshal.load(Marshal.dump(value));
        if (value == null)
        {
            assertNull(unmarshalled);
        }
        else
        {
            if (value.getClass().isArray())
            {
                int length = Array.getLength(value);
                assertEquals(length, Array.getLength(unmarshalled));
                for (int i = 0; i < length; ++i)
                {
                    assertEquals("Element " + i + "doesn't match",
                                 Array.get(value, i),
                                 Array.get(unmarshalled, i));
                }
            }
            else
                assertEquals(value, unmarshalled);
        }
    }

    // Assert that two byte arrays are equal.
    private void assertBytesEqual(byte[] expected, byte[] actual)
    {
        assertTrue(Arrays.equals(expected, actual));
    }

    public void testMarshalInteger() throws Exception
    {
        int[] integers =
            new int[]{-1, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE};

        for (int i = 0; i < integers.length; ++i)
        {
            Integer integer = new Integer(integers[i]);
            byte[] bytes = Marshal.dump(integer);
            assertEquals(integer, Marshal.load(bytes));
        }

        // Test for known output.
        assertBytesEqual(
            new byte[]{'i', 0, 0, 0, 0},
            Marshal.dump(new Integer(0)));
    }

    public void testMarshalLong() throws Exception
    {
        long[] longs =
            new long[]{-1, 0, 1, Long.MIN_VALUE, Long.MAX_VALUE};

        for (int i = 0; i < longs.length; ++i)
        {
            Long long_ = new Long(longs[i]);
            byte[] bytes = Marshal.dump(long_);
            assertEquals(long_, Marshal.load(bytes));
        }
    }

    public void testMarshalBigInteger() throws Exception
    {
        int[] integers =
            new int[]{-1, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE};

        long[] longs =
            new long[]{
                (long)Integer.MIN_VALUE - 1, (long)Integer.MAX_VALUE + 1,
                Long.MIN_VALUE, Long.MAX_VALUE};

        BigInteger[] bigints =
            new BigInteger[]{
                new BigInteger(""+Long.MIN_VALUE).subtract(BigInteger.ONE),
                new BigInteger(""+Long.MAX_VALUE).add(BigInteger.ONE)};

        for (int i = 0; i < integers.length; ++i)
        {
            byte[] bytes = Marshal.dump(new BigInteger(""+integers[i]));
            assertEquals(new Integer(integers[i]), Marshal.load(bytes));
        }

        for (int i = 0; i < longs.length; ++i)
        {
            byte[] bytes = Marshal.dump(new BigInteger(""+longs[i]));
            assertEquals(new Long(longs[i]), Marshal.load(bytes));
        }

        for (int i = 0; i < bigints.length; ++i)
            assertMarshalIdentity(bigints[i]);

        // Test for known output.
        assertBytesEqual(
            new byte[]{'l', 0, 0, 0, 0},
            Marshal.dump(BigInteger.ZERO));
        assertBytesEqual(
            new byte[]{'l', -1, -1, -1, -1, 1, 0},
            Marshal.dump(new BigInteger("-1")));
    }

    public void testMarshalString() throws Exception
    {
        assertMarshalIdentity("abc");
        assertMarshalIdentity("");

        assertBytesEqual(
            new byte[]{'s', 3, 0, 0, 0, 'a', 'b', 'c'},
            Marshal.dump("abc"));
        assertBytesEqual(
            new byte[]{'s', 0, 0, 0, 0},
            Marshal.dump(""));

        // Test UTF-8/Unicode strings.
        byte[] bytes = new byte[]{'u', 3, 0, 0, 0, -30, -66, -110};
        assertEquals(new String("\u2f92"), Marshal.load(bytes));
    }

    public void testMarshalBoolean() throws Exception
    {
        assertBytesEqual(new byte[]{'T'}, Marshal.dump(Boolean.TRUE));
        assertBytesEqual(new byte[]{'F'}, Marshal.dump(Boolean.FALSE));
        assertMarshalIdentity(Boolean.TRUE);
        assertMarshalIdentity(Boolean.FALSE);
    }

    public void testMarshalArray() throws Exception
    {
        assertMarshalIdentity(new Object[]{});
        assertMarshalIdentity(new int[]{1,2,3});
    }
}

