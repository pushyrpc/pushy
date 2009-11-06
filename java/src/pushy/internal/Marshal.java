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

import java.math.BigInteger;

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
        if (object instanceof Short)
        {
            stream.write(Type.INT);
            putInt32(stream, ((Short)object).shortValue());
        }
        if (object instanceof Integer)
        {
            stream.write(Type.INT);
            putInt32(stream, ((Integer)object).intValue());
        }
        else if (object instanceof Long)
        {
            stream.write(Type.INT64);
            putInt64(stream, ((Long)object).longValue());
        }
        else if (object instanceof BigInteger)
        {
            stream.write(Type.LONG);
            putLong(stream, (BigInteger)object);
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

        switch (type)
        {
            // Null/None types.
            case Type.NULL:
            case Type.NONE:
                return null;

            // Boolean types.
            case Type.FALSE:
                return Boolean.FALSE;
            case Type.TRUE:
                return Boolean.TRUE;

            // Integer types.
            case Type.INT:
                return new Integer(getInt32(stream));
            case Type.INT64:
                return new Long(getInt64(stream));
            case Type.LONG:
                return getLong(stream);

            // Unsupported types.
            case Type.ELLIPSIS:
            case Type.STOPITER:
                throw new MarshalException("unsupported type: " + type);

            default:
                throw new MarshalException("bad marshal data");
        }
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
}

