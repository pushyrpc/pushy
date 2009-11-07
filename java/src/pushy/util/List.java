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

package pushy.util;

import pushy.PushyObject;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;

public class List extends AbstractList {
    private PushyObject object;
    private PushyObject insert;
    private PushyObject append;

    public List(PushyObject object) {
        this.object = object;
        insert = (PushyObject)object.__getattr__("insert");
        append = (PushyObject)object.__getattr__("append");
    }

    public Object get(int index) {
        return object.__getitem__(new Integer(index));
    }

    public Object set(int index, Object value) {
        Integer key = new Integer(index);
        Object old = object.__getitem__(key);
        object.__setitem__(key, value);
        return old;
    }

    public int size() {
        return object.__len__();
    }

    public void add(int index, Object o) {
        insert.__call__(new Object[]{new Integer(index), o});
    }

    public boolean add(Object o) {
        append.__call__(new Object[]{o});
        return true;
    }
}

