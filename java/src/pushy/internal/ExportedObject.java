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

import pushy.PushyObject;

public class ExportedObject implements PushyObject
{
    private Number id;
    private Object object;
    private Connection connection;
    private Proxy.Type type;
    private int version;
    private Object marshallable;

    public ExportedObject(Number id, Proxy.Type type,
                          Object object, Connection connection)
    {
        assert object != null;
        this.id = id;
        this.type = type;
        this.object = object;
        this.connection = connection;
        this.version = 0;
        this.marshallable = null;
    }

    public Number getId()
    {
        return id;
    }

    public Object getObject()
    {
        return object;
    }

    public Proxy.Type getType()
    {
        return type;
    }

    public Connection getConnection()
    {
        return connection;
    }

    public int getVersion()
    {
        return version;
    }

    public synchronized int incrementVersion()
    {
        return ++version;
    }

    public void setMarshallableRepresentation(Object marshallable)
    {
        this.marshallable = marshallable;
    }

    public Object getMarshallableRepresentation()
    {
        return marshallable;
    }

    public String toString()
    {
        return object.toString();
    }

    public int hashCode()
    {
        return object.hashCode();
    }

    public boolean __hasattr__(String name)
    {
        try
        {
            __getattr__(name);
            return true;
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    public Object __getattr__(String name)
    {
        try
        {
            java.lang.reflect.Field field = object.getClass().getField(name);
            return field.get(object);
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void __setattr__(String name, Object value)
    {
        try
        {
            java.lang.reflect.Field field = object.getClass().getField(name);
            field.set(object, value);
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Object __call__()
    {
        return __call__(null);
    }

    public Object __call__(Object[] args)
    {
        return __call__(args, null);
    }

    public Object __call__(Object[] args, java.util.Map kwargs)
    {
        if (object instanceof Callable)
            return ((Callable)object).call(args, kwargs);
        throw new UnsupportedOperationException("__call__");
    }

    public int __len__()
    {
        throw new UnsupportedOperationException("__len__");
    }

    public Object __getitem__(Object index)
    {
        throw new UnsupportedOperationException("__getitem__");
    }

    public void __setitem__(Object index, Object value)
    {
        throw new UnsupportedOperationException("__setitem__");
    }
}

