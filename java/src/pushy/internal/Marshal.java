/*
 * Copyright (c) 2009 Andrew Wilkins <axwalk@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package pushy.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.lang.reflect.Array;

import java.math.BigInteger;

import java.util.HashMap;
import java.util.Map;

/**
 * A class for marshaling to/from the Python marshal format.
 */
public class Marshal
{
    /**
     * Currently only version 0 is supported.
     */
    private static int version = 0;

    // Constants used by getLong.
    private static int LONG_SHIFT = 15;
    private static int LONG_BASE  = (1 << LONG_SHIFT);
    private static int LONG_MASK  = (LONG_BASE-1);
    private static BigInteger BIG_INT_MIN =
        new BigInteger("" + Integer.MIN_VALUE);
    private static BigInteger BIG_INT_MAX =
        new BigInteger("" + Integer.MAX_VALUE);
    private static BigInteger BIG_LONG_MIN =
        new BigInteger("" + Long.MIN_VALUE);
    private static BigInteger BIG_LONG_MAX =
        new BigInteger("" + Long.MAX_VALUE);

    // Handlers (class -> handler)
    private static Map handlers = new HashMap();
    // Types (type code -> class)
    private static Map types = new HashMap();

    /**
     * Object types.
     */
    private static class Type
    {
        private static final byte NULL           = '0';
        private static final byte NONE           = 'N';
        private static final byte FALSE          = 'F';
        private static final byte TRUE           = 'T';
        private static final byte STOPITER       = 'S';
        private static final byte ELLIPSIS       = '.';
        private static final byte INT            = 'i';
        private static final byte INT64          = 'I';
        private static final byte FLOAT          = 'f';
        private static final byte BINARY_FLOAT   = 'g';
        private static final byte COMPLEX        = 'x';
        private static final byte BINARY_COMPLEX = 'y';
        private static final byte LONG           = 'l';
        private static final byte STRING         = 's';
        private static final byte INTERNED       = 't';
        private static final byte STRINGREF      = 'R';
        private static final byte TUPLE          = '(';
        private static final byte LIST           = '[';
        private static final byte DICT           = '{';
        private static final byte CODE           = 'c';
        private static final byte UNICODE        = 'u';
        private static final byte UNKNOWN        = '?';
        private static final byte SET            = '<';
        private static final byte FROZENSET      = '>';
    };

    /**
     * Check if an object can be marshaled.
     */
    public static boolean isMarshallable(Object object)
    {
        if (object == null || isMarshallableType(object.getClass()))
        {
            return true;
        }
        else if (object.getClass().isArray())
        {
            boolean all = true;
            for (int i = 0; all && i < Array.getLength(object); ++i)
                if (!isMarshallable(Array.get(object, i)))
                    all = false;
            return all;
        }
        return false;
    }

    /**
     * Check if instances of the specified class can be marshalled.
     */
    public static boolean isMarshallableType(Class class_)
    {
        return handlers.containsKey(class_);
    }

    /**
     * Marshal an object, and return the byte array.
     */
    public static byte[]
    dump(Object object) throws IOException, MarshalException
    {
        try
        {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            dump(object, stream);
            return stream.toByteArray();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Unmarshal a byte array into an object.
     */
    public static Object
    load(byte[] bytes) throws IOException, MarshalException
    {
        try
        {
            ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
            return load(stream);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Marshal an object to an output stream.
     */
    public static void
    dump(Object object, OutputStream stream)
        throws IOException, MarshalException
    {
        // Handle null values.
        if (object == null)
        {
            stream.write(Type.NONE);
            return;
        }

        // Handle arrays.
        if (object.getClass().isArray())
        {
            // XXX
            // Herein lies a conundrum. If we say it's a tuple, then Python
            // thinks it can't modify any elements. If we say it's a list, then
            // the other side thinks it can modify the list structure. For now,
            // prefer the former.
            int size = Array.getLength(object);
            stream.write(Type.TUPLE);
            putInt32(stream, size);
            for (int i = 0; i < size; ++i)
                dump(Array.get(object, i), stream);
            return;
        }

        // Handle all other types.
        Handler handler = (Handler)handlers.get(object.getClass());
        if (handler != null)
        {
            handler.dump(stream, object);
        }
        else
        {
            throw new MarshalException(
                          "unsupported type: " + object.getClass());
        }
    }

    /**
     * Unmarshal an object from an input stream.
     */
    public static Object
    load(InputStream stream) throws IOException, MarshalException
    {
        int type = stream.read();
        if (type == -1)
            throw new EOFException();

        if (type == Type.NULL || type == Type.NONE)
            return null;

        // Load a tuple: size followed by size*objects.
        if (type == Type.TUPLE)
        {
            int size = getInt32(stream);
            java.util.List items = new java.util.ArrayList();
            for (int i = 0; i < size; ++i)
                items.add(load(stream));
            return createArray(items);
        }

        // Handle all other type codes.
        Class class_ = (Class)types.get(new Integer(type));
        if (class_ == null)
            throw new MarshalException("unsupported type: " + (char)type);
        return ((Handler)handlers.get(class_)).load(stream, type);
    }

    /**
     * Write a 8-bit integer (byte).
     */
    private static void
    putInt8(OutputStream stream, byte value) throws IOException
    {
        stream.write(value);
    }

    /**
     * Get a little-endian 16-bit integer.
     */
    private static short getInt16(InputStream stream) throws IOException
    {
        return (short)(stream.read() | (stream.read() << 8));
    }

    /**
     * Write a little-endian 16-bit integer.
     */
    private static void
    putInt16(OutputStream stream, short value) throws IOException
    {
        putInt8(stream, (byte)value);
        putInt8(stream, (byte)(value >> 8));
    }

    /**
     * Get a little-endian 32-bit integer.
     */
    private static int getInt32(InputStream stream) throws IOException
    {
        int b0 = stream.read();
        int b1 = stream.read();
        int b2 = stream.read();
        int b3 = stream.read();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    /**
     * Write a little-endian 32-bit integer.
     */
    private static void
    putInt32(OutputStream stream, int value) throws IOException
    {
        putInt8(stream, (byte)value);
        putInt8(stream, (byte)(value >> 8));
        putInt8(stream, (byte)(value >> 16));
        putInt8(stream, (byte)(value >> 24));
    }

    /**
     * Get a little-endian 64-bit integer.
     */
    private static long getInt64(InputStream stream) throws IOException
    {
        long lo4 = (long)getInt32(stream);
        long hi4 = (long)getInt32(stream);
        return ((hi4 << 32) & 0xFFFFFFFF00000000L) | (lo4 & 0xFFFFFFFFL);
    }

    /**
     * Write a little-endian 64-bit integer.
     */
    private static void
    putInt64(OutputStream stream, long value) throws IOException
    {
        putInt32(stream, (int)(value & 0xFFFFFFFFL));
        putInt32(stream, (int)((value >> 32) & 0xFFFFFFFFL));
    }

    /**
     * Get an arbitrary-precision integer.
     * @return A Integer, Long, or BigInteger.
     */
    private static Number getLong(InputStream stream) throws IOException
    {
        int ndigits = getInt32(stream);
        if (ndigits == 0)
            return new Integer(0);

        boolean negative = ndigits < 0;
        ndigits = Math.abs(ndigits);

        // Calculate the absolute value of the integer.
        BigInteger bigint = new BigInteger("0");
        short[] digits = new short[ndigits];
        for (int i = 0; i < ndigits; ++i)
            digits[i] = getInt16(stream);
        for (int i = 0; i < ndigits; ++i)
        {
            bigint = bigint.shiftLeft(LONG_SHIFT);
            bigint = bigint.add(new BigInteger("" + digits[ndigits-i-1]));
        }

        // Negate, if necessary. The sign is conveyed in the size (ndigits).
        if (negative)
            bigint = bigint.negate();

        // Get the smallest of Integer, Long or BigInteger.
        int cmp = bigint.compareTo(BIG_INT_MAX);
        if (cmp > 0)
        {
            cmp = bigint.compareTo(BIG_LONG_MAX);
            if (cmp > 0)
                return bigint;
            return new Long(bigint.longValue());
        }
        else if (cmp == 0)
        {
            return new Integer(bigint.intValue());
        }
        else
        {
            cmp = bigint.compareTo(BIG_INT_MIN);
            if (cmp < 0)
            {
                cmp = bigint.compareTo(BIG_LONG_MIN);
                if (cmp < 0)
                    return bigint;
                return new Long(bigint.longValue());
            }
            else
            {
                return new Integer(bigint.intValue());
            }
        }
    }

    /**
     * Write an arbitrary-precision integer.
     */
    private static void
    putLong(OutputStream stream, BigInteger bigint) throws IOException
    {
        int ndigits = 0;
        boolean negative = bigint.compareTo(BigInteger.ZERO) < 0;
        if (negative)
            bigint = bigint.abs();

        // Count the number of digits.
        BigInteger t = new BigInteger(bigint.toString());
        for (; !t.equals(BigInteger.ZERO); ++ndigits)
            t = t.shiftRight(LONG_SHIFT);

        // Write the size.
        putInt32(stream, negative ? -ndigits : ndigits);

        // Write the digits.
        t = new BigInteger(bigint.toString());
        for (int i = 0; i < ndigits; ++i)
        {
            putInt16(stream, (short)(t.shortValue() & LONG_MASK));
            t = t.shiftRight(LONG_SHIFT);
        }
    }

    /**
     * Read a string.
     */
    private static String getString(InputStream stream) throws IOException
    {
        return getString(stream, "ISO-8859-1"); // LATIN-1
    }

    /**
     * Read a string with the specified character set.
     */
    private static String
    getString(InputStream stream, String charset) throws IOException
    {
        int size = getInt32(stream);
        if (size == 0)
            return "";

        byte[] buf = new byte[size];
        int nread = 0;
        do
        {
            int partial = stream.read(buf, nread, buf.length-nread);
            if (partial == -1)
                throw new java.io.EOFException();
            nread += partial;
        } while (nread < buf.length);
        return new String(buf, charset);
    }

    /**
     * Write a string.
     */
    private static void
    putString(OutputStream stream, String string) throws IOException
    {
        putInt32(stream, string.length());
        byte[] bytes = string.getBytes("ISO-8859-1");
        stream.write(bytes);
    }

    private static Map primitiveTypes = new HashMap();
    static
    {
        primitiveTypes.put(Boolean.class,   Boolean.TYPE);
        primitiveTypes.put(Byte.class,      Byte.TYPE);
        primitiveTypes.put(Character.class, Character.TYPE);
        primitiveTypes.put(Double.class,    Double.TYPE);
        primitiveTypes.put(Float.class,     Float.TYPE);
        primitiveTypes.put(Integer.class,   Integer.TYPE);
        primitiveTypes.put(Long.class,      Long.TYPE);
        primitiveTypes.put(Short.class,     Short.TYPE);
    }

    /**
     * Convert an object list to an array of the most specific, primitive
     * array.
     */
    public static Object createArray(java.util.List list)
    {
        // Convert to a type-specific array if all elements are of the
        // same type, otherwise just return an Object array.
        if (list.isEmpty())
            return new Object[]{};

        java.util.Iterator iter = list.iterator();
        Class compType = iter.next().getClass();
        while (iter.hasNext() && !compType.equals(Object.class))
            if (!iter.next().getClass().equals(compType))
                compType = Object.class;

        if (compType.equals(Object.class))
            return list.toArray(new Object[]{});

        // If the type is an Object type corresponding to primitive
        // type (e.g. Integer), get the primitive type.
        Class primitiveType = (Class)primitiveTypes.get(compType);
        if (primitiveType != null)
            compType = primitiveType;

        // Create the array.
        Object array = Array.newInstance(compType, list.size());
        iter = list.iterator();
        for (int i = 0; iter.hasNext(); ++i)
            Array.set(array, i, iter.next());
        return array;
    }

    // Define type handlers and type mappings.
    static
    {
        handlers.put(Short.class,      new ShortHandler());
        handlers.put(Integer.class,    new IntegerHandler());
        handlers.put(Long.class,       new LongHandler());
        handlers.put(BigInteger.class, new BigIntegerHandler());
        handlers.put(String.class,     new StringHandler());
        handlers.put(Boolean.class,    new BooleanHandler());

        types.put(new Integer(Type.INT),      Integer.class);
        types.put(new Integer(Type.INT64),    Long.class);
        types.put(new Integer(Type.LONG),     BigInteger.class);
        types.put(new Integer(Type.STRING),   String.class);
        types.put(new Integer(Type.INTERNED), String.class);
        types.put(new Integer(Type.UNICODE),  String.class);
        types.put(new Integer(Type.TRUE),     Boolean.class);
        types.put(new Integer(Type.FALSE),    Boolean.class);
    }

    /**
     * Defines an interface for a type handler.
     */
    private static interface Handler
    {
        public void dump(OutputStream stream, Object value) throws IOException;
        public Object load(InputStream stream, int type) throws IOException;
    }

    private static class IntegerHandler implements Handler
    {
        public void dump(OutputStream stream, Object value) throws IOException
        {
            stream.write(Type.INT);
            putInt32(stream, ((Integer)value).intValue());
        }

        public Object load(InputStream stream, int type) throws IOException
        {
            return new Integer(getInt32(stream));
        }
    }

    private static class ShortHandler extends IntegerHandler
    {
        public void dump(OutputStream stream, Object value) throws IOException
        {
            super.dump(stream, new Integer(((Short)value).shortValue()));
        }
    }

    private static class LongHandler implements Handler
    {
        public void dump(OutputStream stream, Object value) throws IOException
        {
            stream.write(Type.INT64);
            putInt64(stream, ((Long)value).longValue());
        }

        public Object load(InputStream stream, int type) throws IOException
        {
            return new Long(getInt64(stream));
        }
    }

    private static class BigIntegerHandler implements Handler
    {
        public void dump(OutputStream stream, Object value) throws IOException
        {
            stream.write(Type.LONG);
            putLong(stream, (BigInteger)value);
        }

        public Object load(InputStream stream, int type) throws IOException
        {
            return getLong(stream);
        }
    }

    private static class BooleanHandler implements Handler
    {
        public void dump(OutputStream stream, Object value) throws IOException
        {
            stream.write(value.equals(Boolean.TRUE) ? Type.TRUE : Type.FALSE);
        }

        public Object load(InputStream stream, int type) throws IOException
        {
            return type == Type.TRUE ? Boolean.TRUE : Boolean.FALSE;
        }
    }

    private static class StringHandler implements Handler
    {
        public void dump(OutputStream stream, Object value) throws IOException
        {
            stream.write(Type.STRING);
            putString(stream, (String)value);
        }

        public Object load(InputStream stream, int type) throws IOException
        {
            if (type == Type.UNICODE)
                return getString(stream, "UTF-8");
            return getString(stream);
        }
    }
}

