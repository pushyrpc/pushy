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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class Connection extends BaseConnection
{
    public Connection(InputStream istream, OutputStream ostream)
    {
        super(istream, ostream);
    }

    protected Object
    createProxy(Number id, Number opmask, Integer type, Object args)
    {
        return Proxy.getProxy(id, opmask, type, args, this);
    }

    public Object evaluate(String expression)
    {
        try
        {
            return sendRequest(Message.Type.evaluate, expression);
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

    public Object getitem(Object object, Object index)
    {
        try
        {
            return sendRequest(Message.Type.op__getitem__,
                               new Object[]{object, index});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void setitem(Object object, Object index, Object value)
    {
        try
        {
            sendRequest(
                Message.Type.op__setitem__,
                new Object[]{object, index, value});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public int len(Object object)
    {
        try
        {
            Integer length =
                (Integer)sendRequest(Message.Type.op__len__, object);
            return length.intValue();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public Object call(Object object, Object[] args, java.util.Map kwargs)
    {
        try
        {
            return sendRequest(Message.Type.op__call__,
                               new Object[]{object, args, kwargs});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
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
}

