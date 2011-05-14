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

import pushy.Module;
import pushy.PushyObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Connection extends BaseConnection
{
    private static final Logger logger =
        Logger.getLogger(BaseConnection.class.getName());

    public Connection(InputStream istream, OutputStream ostream)
    {
        super(istream, ostream);
    }

    protected ProxyObject
    createProxy(Number id, Number opmask, Integer type, Object args)
    {
        return Proxy.getProxy(id, opmask, type, args, this);
    }

    protected ExportedObject
    createExportObject(Number id, Proxy.Type type, Object object)
    {
        if (type.equals(Proxy.Type.dictionary))
            return new ExportedMap(id, (Map)object, this);
        return new ExportedObject(id, type, object, this);
    }

    /**
     * Call this method for "op__xxx___" message type requests.
     */
    private Object invokeOperator(Message.Type type, Object object)
    {
        try
        {
            return sendRequest(type, new Object[]{object});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Call this method for "op__xxx___" message type requests.
     */
    private Object
    invokeOperator(Message.Type type, Object object, Object[] args)
    {
        try
        {
            return sendRequest(type, new Object[]{object, args});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Call this method for "op__xxx___" message type requests.
     */
    private Object
    invokeOperator(Message.Type type, Object object, Object[] args, Map kwargs)
    {
        try
        {
            // Convert the keyword arguments to a tuple of items.
            Object[] kwargs_items = null;
            if (kwargs != null)
            {
                kwargs_items = new Object[kwargs.size()];
                Iterator iter = kwargs.entrySet().iterator();
                for (int i = 0; iter.hasNext(); ++i)
                {
                    Map.Entry entry = (Map.Entry)iter.next();
                    kwargs_items[i] = new Object[]{entry.getKey(),
                                                   entry.getValue()};
                }
            }

            return sendRequest(type, new Object[]{object, args, kwargs_items});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Object evaluate(String expression, Map locals, Map globals)
    {
        try
        {
            return sendRequest(Message.Type.evaluate,
                               new Object[]{expression, locals, globals});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean hasattr(Object object, String name)
    {
        try
        {
            sendRequest(Message.Type.getattr, new Object[]{object, name});
            return true;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (RemoteException e)
        {
            return false;
        }
    }

    public Object getattr(Object object, String name)
    {
        try
        {
            return sendRequest(Message.Type.getattr,
                               new Object[]{object, name});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void setattr(Object object, String name, Object value)
    {
        try
        {
            sendRequest(Message.Type.setattr,
                        new Object[]{object, name, value});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Object getitem(Object object, Object index)
    {
        return invokeOperator(
                   Message.Type.op__getitem__, object, new Object[]{index});
    }

    public void setitem(Object object, Object index, Object value)
    {
        invokeOperator(
            Message.Type.op__setitem__, object, new Object[]{index, value});
    }

    public int len(Object object)
    {
        Integer length =
            (Integer)invokeOperator(Message.Type.op__len__, object);
        return length.intValue();
    }

    public Object call(Object object, Object[] args, java.util.Map kwargs)
    {
        return invokeOperator(Message.Type.op__call__, object, args, kwargs);
    }

    public String str(Object object)
    {
        try
        {
            return (String)sendRequest(Message.Type.getstr, object);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Handle a message.
     */
    protected Object handle(Message.Type type, Object arg)
    {
        if (type.equals(Message.Type.evaluate))
            throw new UnsupportedOperationException("Evaluate is unsupported");

        if (type.equals(Message.Type.as_tuple))
        {
            Object[] args = (Object[])arg;
            type = Message.Type.getType(((Number)args[0]).intValue());
            Object result = handle(type, args[1]);
            return createArray(result);
        }

        // __getattr__
        if (type.equals(Message.Type.getattr))
        {
            Object[] args = (Object[])arg;
            return ((PushyObject)args[0]).__getattr__((String)args[1]);
        }

        // __setattr__
        if (type.equals(Message.Type.setattr))
        {
            Object[] args = (Object[])arg;
            ((PushyObject)args[0]).__setattr__((String)args[1], args[2]);
            return null;
        }

        // __str__ & __repr__
        if (type.equals(Message.Type.getstr) ||
            type.equals(Message.Type.getrepr))
        {
            return arg.toString();
        }

        // __hash__
        if (type.equals(Message.Type.op__hash__))
            return new Integer(arg.hashCode());

        // __call__
        if (type.equals(Message.Type.op__call__))
        {
            Object[] args = (Object[])arg;
            Object[] kwargs = (Object[])args[2];
            Map kwargsMap = null;
            if (kwargs != null && kwargs.length > 0)
            {
                kwargsMap = new HashMap();
                for (int i = 0; i < kwargs.length; ++i)
                {
                    Object[] pair = (Object[])kwargs[i];
                    kwargsMap.put(pair[0], pair[1]);
                }
            }
            return ((PushyObject)args[0]).__call__(
                       (Object[])args[1], kwargsMap);
        }

        throw new UnsupportedOperationException("Unsupported type: " + type);
    }

    // Convert an object to an array, to support the "as_tuple" message.
    private static Object createArray(Object object)
    {
        if (object == null || object.getClass().isArray())
            return null;
        if (object instanceof Collection)
            return ((Collection)object).toArray();
        throw new IllegalArgumentException(
            "Non collection type cannot be cast to an array");
    }
}

