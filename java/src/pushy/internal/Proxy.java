/*
 * Copyright (c) 2009, 2011 Andrew Wilkins <axwalk@gmail.com>
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Proxy
{
    /**
     * Create an object which provides local access to a remote object.
     */
    public static ProxyObject
    getProxy(Number id, Number operators, Integer type, Object argument,
             Connection connection)
    {
        PushyObjectImpl proxy = new PushyObjectImpl(id, connection);

        // TODO for "object" types, use the operator mask to determine which
        //      interfaces to implement.

        if (Type.list.equals(type))
            return new ProxyList(proxy);

        if (Type.dictionary.equals(type))
            return new ProxyMap(proxy);

        return proxy;
    }

    /**
     * Create an object which 
     */
    public static Type getType(Object object)
    {
        if (object instanceof List)
            return Type.list;

        if (object instanceof Map)
            return Type.dictionary;

        if (object instanceof Set)
            return Type.set;

        if (object instanceof Throwable)
            return Type.exception;

        return Type.object;
    }

    /**
     * Get a bitmask for the operators supported by an object.
     */
    public static Number getOperators(Object object)
    {
        java.math.BigInteger mask = new java.math.BigInteger("0");
        for (Iterator iter = Message.Type.getTypes().iterator();
             iter.hasNext();)
        {
            Message.Type type = (Message.Type)iter.next();
            if (type.getName().startsWith("op__"))
            {
                if (hasOperator(object, type))
                    mask = mask.setBit(type.getCode());
            }
        }
        return mask;
    }

    /**
     * Check if the object supports the specified operator.
     */
    public static boolean hasOperator(Object object, Message.Type type)
    {
        // __cmp__ maps to Comparable.
        if (type.equals(Message.Type.op__cmp__) ||
            type.equals(Message.Type.op__rcmp__))
        {
            return object instanceof Comparable;
        }

        // Everything supports __hash__.
        if (type.equals(Message.Type.op__hash__))
            return true;

        // Collections support __len__.
        if (type.equals(Message.Type.op__len__))
            return object instanceof Collection;

        // Lists and maps support __getitem__, __setitem__ and __contains__.
        if (type.equals(Message.Type.op__getitem__) ||
            type.equals(Message.Type.op__setitem__) ||
            type.equals(Message.Type.op__contains__))
        {
            return object instanceof List || object instanceof Map;
        }

        // All collections support __delitem__.
        if (type.equals(Message.Type.op__delitem__))
            return object instanceof Collection;

        // Iterables support __iter__.
        if (type.equals(Message.Type.op__iter__))
            return object instanceof Iterable;

        // Classes (constructors) and BoundMethod support __call__.
        if (type.equals(Message.Type.op__call__))
            return object instanceof Class || object instanceof Callable;

        return false;
    }

    /**
     * Get argument for the proxy object, to pass to the remote side.
     */
    public static Object getArgument(Object object, Type type)
    {
/*
        if (type.equals(Type.dictionary))
        {
            Map map = (Map)object;
            Object[] items = new Object[map.size()];
            int i = 0;
            for (Iterator iter = map.entrySet().iterator(); iter.hasNext();)
            {
                Map.Entry entry = (Map.Entry)iter.next();
                items[i++] = new Object[]{entry.getKey(), entry.getValue()};
            }
            return items;
        }
        else if (type.equals(Type.list) || type.equals(Type.set))
        {
            return ((Collection)object).toArray(new Object[]{});
        }
*/
        return null;
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
        public static final Type oldstyleclass = createType("oldstyleclass");
        public static final Type object        = createType("object");
        public static final Type exception     = createType("exception");
        public static final Type dictionary    = createType("dictionary");
        public static final Type list          = createType("list");
        public static final Type set           = createType("set");
        public static final Type module        = createType("module");
    }
}

