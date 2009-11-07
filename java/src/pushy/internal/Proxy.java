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
import java.util.List;

public class Proxy
{
    /**
     * Create a new Proxy object.
     */
    public static Object
    getProxy(Number id, Number operators, Integer type, Object argument,
             Connection connection)
    {
        PushyObjectImpl proxy = new PushyObjectImpl(id, connection);

        // TODO for "object" types, use the operator mask to determine which
        //      interfaces to implement.

        if (Type.list.equals(type))
            return new PushyListObject(proxy);

        if (Type.dictionary.equals(type))
            return new PushyMapObject(proxy);

        return proxy;
    }

    /**
     * A class for describing a proxied object type.
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
            return "ProxyType(" + code + ", '" + name + "')";
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

        // Define proxy types: must be in the same order as in the Python
        // code.
        public static final Type object     = createType("object");
        public static final Type exception  = createType("exception");
        public static final Type dictionary = createType("dictionary");
        public static final Type list       = createType("list");
        public static final Type set        = createType("set");
        public static final Type module     = createType("module");
    }
}

