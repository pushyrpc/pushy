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

    public String toString()
    {
        return "Message(" + type + ", " + getSource() + ", " +
                getTarget() + ", " + payload.length + " bytes)";
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
        java.io.ByteArrayOutputStream stream =
            new java.io.ByteArrayOutputStream(21 + payload.length);
        try {
            pack(stream);
        } catch (java.io.IOException e) {}
        return stream.toByteArray();
    }

    /**
     * Pack a message into its network representation.
     */
    public void pack(java.io.OutputStream stream) throws java.io.IOException
    {
        stream.write((byte)type.getCode());
        pack(stream, source);
        pack(stream, target);
        pack(stream, payload.length);
        stream.write(payload, 0, payload.length);
    }

    /**
     * Read a message from the given input stream.
     */
    public static Message
    unpack(java.io.InputStream stream) throws java.io.IOException
    {
        // Unpack the header.
        Type type = Type.getType(stream.read());
        long source = unpackLong(stream);
        long target = unpackLong(stream);
        int length = unpackInteger(stream);

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
    private static void
    pack(java.io.OutputStream stream, long value) throws java.io.IOException
    {
        stream.write((byte)((value >> 56) & 0xFF));
        stream.write((byte)((value >> 48) & 0xFF));
        stream.write((byte)((value >> 40) & 0xFF));
        stream.write((byte)((value >> 32) & 0xFF));
        stream.write((byte)((value >> 24) & 0xFF));
        stream.write((byte)((value >> 16) & 0xFF));
        stream.write((byte)((value >> 8) & 0xFF));
        stream.write((byte)((value) & 0xFF));
    }

    // Utility method for unpacking a network-order "long" from a byte array.
    private static long
    unpackLong(java.io.InputStream stream) throws java.io.IOException
    {
        long hi4 = (long)unpackInteger(stream);
        long lo4 = (long)unpackInteger(stream);
        return (hi4 << 4) | lo4;
    }

    // Utility method for packing a network-order "int" into a byte array.
    private static void
    pack(java.io.OutputStream stream, int value) throws java.io.IOException
    {
        stream.write((byte)((value >> 24) & 0xFF));
        stream.write((byte)((value >> 16) & 0xFF));
        stream.write((byte)((value >> 8) & 0xFF));
        stream.write((byte)((value) & 0xFF));
    }

    // Utility method for unpacking a network-order "int" from a byte array.
    private static int
    unpackInteger(java.io.InputStream stream) throws java.io.IOException
    {
        int b0 = stream.read();
        int b1 = stream.read();
        int b2 = stream.read();
        int b3 = stream.read();
        if (b0 == -1 || b1 == -1 || b2 == -1 || b3 == -1)
            throw new java.io.EOFException();
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
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
            if (code < 0 || code >= types.size())
                return null;
            return (Type)types.get(code);
        }

        public static List getTypes()
        {
            return java.util.Collections.unmodifiableList(types);
        }

        // Method for defining a type, given a name. The type's code
        // will be the next index into the 'types' list.
        private static Type createType(String name)
        {
            Type type = new Type(types.size(), name);
            types.add(type);
            return type;
        }

        // Define message types: must be in the same order as in the Python
        // code.
        public static final Type evaluate       = createType("evaluate");
        public static final Type response       = createType("response");
        public static final Type exception      = createType("exception");
        public static final Type getattr        = createType("getattr");
        public static final Type setattr        = createType("setattr");
        public static final Type getstr         = createType("getstr");
        public static final Type getrepr        = createType("getrepr");
        public static final Type op__call__     = createType("op__call__");
        public static final Type op__lt__       = createType("op__lt__");
        public static final Type op__le__       = createType("op__le__");
        public static final Type op__eq__       = createType("op__eq__");
        public static final Type op__ne__       = createType("op__ne__");
        public static final Type op__gt__       = createType("op__gt__");
        public static final Type op__ge__       = createType("op__ge__");
        public static final Type op__cmp__      = createType("op__cmp__");
        public static final Type op__rcmp__     = createType("op__rcmp__");
        public static final Type op__hash__     = createType("op__hash__");
        public static final Type op__nonzero__  = createType("op__nonzero__");
        public static final Type op__unicode__  = createType("op__unicode__");
        public static final Type op__len__      = createType("op__len__");
        public static final Type op__getitem__  = createType("op__getitem__");
        public static final Type op__setitem__  = createType("op__setitem__");
        public static final Type op__delitem__  = createType("op__delitem__");
        public static final Type op__iter__     = createType("op__iter__");
        public static final Type op__contains__ = createType("op__contains__");
        public static final Type op__get__      = createType("op__get__");
        public static final Type op__set__      = createType("op__set__");
        public static final Type op__delete__   = createType("op__delete__");
        public static final Type op__getslice__ = createType("op__getslice__");
        public static final Type op__setslice__ = createType("op__setslice__");
        public static final Type op__delslice__ = createType("op__delslice__");
        public static final Type op__add__      = createType("op__add__");
        public static final Type op__sub__      = createType("op__sub__");
        public static final Type op__mul__      = createType("op__mul__");
        public static final Type op__floordiv__ = createType("op__floordiv__");
        public static final Type op__mod__      = createType("op__mod__");
        public static final Type op__divmod__   = createType("op__divmod__");
        public static final Type op__pow__      = createType("op__pow__");
        public static final Type op__lshift__   = createType("op__lshift__");
        public static final Type op__rshift__   = createType("op__rshift__");
        public static final Type op__and__      = createType("op__and__");
        public static final Type op__xor__      = createType("op__xor__");
        public static final Type op__or__       = createType("op__or__");
        public static final Type op__div__      = createType("op__div__");
        public static final Type op__truediv__  = createType("op__truediv__");
        public static final Type op__radd__     = createType("op__radd__");
        public static final Type op__rsub__     = createType("op__rsub__");
        public static final Type op__rdiv__     = createType("op__rdiv__");
        public static final Type op__rtruediv__ = createType("op__rtruediv__");
        public static final Type op__rfloordiv__
            = createType("op__rfloordiv__");
        public static final Type op__rmod__     = createType("op__rmod__");
        public static final Type op__rdivmod__  = createType("op__rdivmod__");
        public static final Type op__rpow__     = createType("op__rpow__");
        public static final Type op__rlshift__  = createType("op__rlshift__");
        public static final Type op__rrshift__  = createType("op__rrshift__");
        public static final Type op__rand__     = createType("op__rand__");
        public static final Type op__rxor__     = createType("op__rxor__");
        public static final Type op__ror__      = createType("op__ror__");
        public static final Type op__iadd__     = createType("op__iadd__");
        public static final Type op__isub__     = createType("op__isub__");
        public static final Type op__imul__     = createType("op__imul__");
        public static final Type op__idiv__     = createType("op__idiv__");
        public static final Type op__itruediv__ = createType("op__itruediv__");
        public static final Type op__ifloordiv__
            = createType("op__ifloordiv__");
        public static final Type op__imod__     = createType("op__imod__");
        public static final Type op__ipow__     = createType("op__ipow__");
        public static final Type op__ilshift__  = createType("op__ilshift__");
        public static final Type op__irshift__  = createType("op__irshift__");
        public static final Type op__iand__     = createType("op__iand__");
        public static final Type op__ixor__     = createType("op__ixor__");
        public static final Type op__ior__      = createType("op__ior__");
        public static final Type op__neg__      = createType("op__neg__");
        public static final Type op__pos__      = createType("op__pos__");
        public static final Type op__abs__      = createType("op__abs__");
        public static final Type op__invert__   = createType("op__invert__");
        public static final Type op__complex__  = createType("op__complex__");
        public static final Type op__int__      = createType("op__int__");
        public static final Type op__long__     = createType("op__long__");
        public static final Type op__float__    = createType("op__float__");
        public static final Type op__oct__      = createType("op__oct__");
        public static final Type op__hex__      = createType("op__hex__");
        public static final Type op__index__    = createType("op__index__");
        public static final Type op__coerce__   = createType("op__coerce__");
        public static final Type op__enter__    = createType("op__enter__");
        public static final Type op__exit__     = createType("op__exit__");

        /**
         * Check if a message type is a a response type.
         */
        public boolean isResponse()
        {
            return this.equals(response) || this.equals(exception);
        }
    }
}

