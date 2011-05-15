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

public class PushyObjectImpl implements PushyObject, ProxyObject
{
    private Number id;
    private Connection connection;
    private int version;

    PushyObjectImpl(Number id, Connection connection)
    {
        this.id = id;
        this.connection = connection;
        this.version = 0;
    }

    protected void finalize() throws Throwable
    {
        connection.deleted(this);
        super.finalize();
    }

    public Object getId()
    {
        return this.id;
    }

    public BaseConnection getConnection()
    {
        return connection;
    }

    public int getVersion()
    {
        return version;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

    public boolean __hasattr__(String key)
    {
        return connection.hasattr(this, key);
    }

    public Object __getattr__(String key)
    {
        return connection.getattr(this, key);
    }

    public void __setattr__(String key, Object value)
    {
        connection.setattr(this, key, value);
    }

    public Object __getitem__(Object index)
    {
        return connection.getitem(this, index);
    }

    public void __setitem__(Object index, Object value)
    {
        connection.setitem(this, index, value);
    }

    public int __len__()
    {
        return connection.len(this);
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
        return connection.call(this, args, kwargs);
    }

    public String toString()
    {
        return connection.str(this);
    }
}

