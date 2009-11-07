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

import pushy.PushyObject;

public class DelegatingPushyObject implements PushyObject
{
    private PushyObject delegate;

    public DelegatingPushyObject(PushyObject delegate)
    {
        this.delegate = delegate;
    }

    public boolean __hasattr__(String key)
    {
        return delegate.__hasattr__(key);
    }

    public Object __getattr__(String name)
    {
        return delegate.__getattr__(name);
    }

    public void __setattr__(String name, Object value)
    {
        delegate.__setattr__(name, value);
    }

    public Object __getitem__(Object key)
    {
        return delegate.__getitem__(key);
    }

    public void __setitem__(Object key, Object value)
    {
        delegate.__setitem__(key, value);
    }

    public int __len__()
    {
        return delegate.__len__();
    }

    public Object __call__()
    {
        return delegate.__call__();
    }

    public Object __call__(Object[] args)
    {
        return delegate.__call__(args);
    }

    public Object __call__(Object[] args, java.util.Map kwargs)
    {
        return delegate.__call__(args, kwargs);
    }
}

