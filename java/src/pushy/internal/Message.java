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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class for decoding, encoding and describing a message.
 */
public class Message
{
    private final Type   type;
    private final byte[] payload;
    private long         target;
    private long         source;

    /**
     * Create a new message, with the current thread designated as source
     * of the message.
     */
    public Message(Type type, byte[] payload, long target)
    {
        this(type, payload, target, ThreadId.getThreadId());
    }

    private Message(Type type, byte[] payload, long target, long source)
    {
        this.type    = type;
        this.payload = payload;
        this.target  = target;
        this.source  = source;
    }

    public Type getType()
    {
        return type;
    }

    public long getSource()
    {
        return source;
    }

    public long getTarget()
    {
        return target;
    }

    public final byte[] getPayload()
    {
        return payload;
    }

    public boolean equals(Object rhs)
    {
        if (rhs instanceof Message)
        {
            Message other = (Message)rhs;
            return type.equals(other.type) &&
                   target == other.target &&
                   source == other.source &&
                   Arrays.equals(payload, other.payload);
        }
        return super.equals(rhs);
    }

    /**
     * Pack a message into its network representation.
     */
    public byte[] pack()
    {
        // 21 bytes for the header + the payload.
        byte[] buffer = new byte[21 + payload.length];
        buffer[0] = (byte)type.getCode();
        packLong(buffer, 1, source);
        packLong(buffer, 9, target);
        packInteger(buffer, 17, payload.length);
        System.arraycopy(payload, 0, buffer, 21, payload.length);
        return buffer;
    }

    /**
     * Read a message from the given input stream.
     */
    public static Message
    unpack(java.io.InputStream stream) throws java.io.IOException
    {
        // Header is 21 bytes, made up of:
        //     1 byte  (type)
        //     8 bytes (source)
        //     8 bytes (target)
        //     4 bytes (payload size)
        byte[] header = read(stream, 21);

        // Unpack the header.
        Type type = Type.getType((int)header[0]);
        long source = unpackLong(header, 1);
        long target = unpackLong(header, 9);
        int length = unpackInteger(header, 17);

        // Read the payload and create the message.
        byte[] payload = read(stream, length);
        return new Message(type, payload, target, source);
    }

    // Utility method for creating and filling up a byte array from an
    // input stream.
    private static byte[]
    read(java.io.InputStream stream, int length) throws java.io.IOException
    {
        byte[] buf = new byte[length];
        read(stream, buf);
        return buf;
    }

    // Utility method for filling up a byte array from an input stream.
    private static void
    read(java.io.InputStream stream, byte[] buf) throws java.io.IOException
    {
        int nread = 0;
        do
        {
            int partial = stream.read(buf, nread, buf.length-nread);
            if (partial == -1)
                throw new java.io.EOFException();
            nread += partial;
        } while (nread < buf.length);
    }

    // Utility method for packing a network-order "long" into a byte array.
    private static void packLong(byte[] buf, int offset, long value)
    {
        buf[offset]   = (byte)((value >> 56) & 0xFF);
        buf[offset+1] = (byte)((value >> 48) & 0xFF);
        buf[offset+2] = (byte)((value >> 40) & 0xFF);
        buf[offset+3] = (byte)((value >> 32) & 0xFF);
        buf[offset+4] = (byte)((value >> 24) & 0xFF);
        buf[offset+5] = (byte)((value >> 16) & 0xFF);
        buf[offset+6] = (byte)((value >> 8)  & 0xFF);
        buf[offset+7] = (byte)((value)       & 0xFF);
    }

    // Utility method for unpacking a network-order "long" from a byte array.
    private static long unpackLong(byte[] buf, int offset)
    {
        return (((long)buf[offset+0]) << 56) | (((long)buf[offset+1]) << 48) |
               (((long)buf[offset+2]) << 40) | (((long)buf[offset+3]) << 32) |
               (((long)buf[offset+4]) << 24) | (((long)buf[offset+5]) << 16) |
               (((long)buf[offset+6]) << 8)  | (((long)buf[offset+7]));
    }

    // Utility method for packing a network-order "int" into a byte array.
    private static void packInteger(byte[] buf, int offset, int value)
    {
        buf[offset]   = (byte)((value >> 24) & 0xFF);
        buf[offset+1] = (byte)((value >> 16) & 0xFF);
        buf[offset+2] = (byte)((value >> 8)  & 0xFF);
        buf[offset+3] = (byte)((value)       & 0xFF);
    }

    // Utility method for unpacking a network-order "int" from a byte array.
    private static int unpackInteger(byte[] buf, int offset)
    {
        return (((int)buf[offset+0]) << 24) | (((int)buf[offset+1]) << 16) |
               (((int)buf[offset+2]) << 8)  | (((int)buf[offset+3]) << 0);
    }

    /**
     * A class for describing a message type.
     */
    public static class Type
    {
        private int code;
        private String name;

        public Type(int code, String name)
        {
            this.code = code;
            this.name = name;
        }

        public int getCode()
        {
            return code;
        }

        public String getName()
        {
            return name;
        }

        public String toString()
        {
            return "MessageType(" + code + ", '" + name + "')";
        }

        public int hashCode()
        {
            return code;
        }

        public boolean equals(Object rhs)
        {
            if (rhs instanceof Type)
                return code == ((Type)rhs).code;
            else if (rhs instanceof Integer)
                return code == ((Integer)rhs).intValue();
            else if (rhs instanceof String)
                return name.equals(rhs);
            return super.equals(rhs);
        }

        // Provide a means of getting a message type by its code.
        private static List types = new ArrayList();
        public static Type getType(int code)
        {
            return (Type)types.get(code);
        }

        // Method for defining a type, given a name. The type's code
        // will be the next index into the 'types' list.
        private static Type createType(String name)
        {
            Type type = new Type(types.size(), name);
            types.add(type);
            return type;
        }

        // Define message types.
        public static final Type evaluate = createType("evaluate");
    }
}

