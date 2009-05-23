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

package pushy;

import java.util.Map;

public class PushyObject
{
    private Client client;
    private long id;

    public PushyObject(Client client, long id)
    {
        this.client = client;
        this.id = id;
    }

    public PushyObject(PushyObject object)
    {
        this.client = object.client;
        this.id = object.id;
    }

    public String toString()
    {
        PushyObject str = (PushyObject)__getattr__("__str__");
        return (String)str.__call__(new Object[]{});
    }

    public Client getClient()
    {
        return client;
    }

    public Object __call__()
    {
        return client.callobj(id);
    }

    public Object __call__(Object[] args)
    {
        return client.callobj(id, args);
    }

    public Object __call__(Object[] args, Map kwargs)
    {
        return client.callobj(id, args, kwargs);
    }

    public boolean __hasattr__(String key)
    {
        return client.hasattr(id, key);
    }

    public Object __getattr__(String key)
    {
        return client.getattr(id, key);
    }

    public PushyObject __getmethod__(String key)
    {
        return (PushyObject)__getattr__(key);
    }

    public Object __getitem__(Object key)
    {
        return client.getitem(id, key);
    }

    public void __setitem__(Object key, Object value)
    {
        client.setitem(id, key, value);
    }

    public int __len__()
    {
        return client.len(id);
    }
}

