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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class Map extends AbstractMap implements Iterable {
    private PushyObject object;
    private PushyObject update;
    private PushyObject get;
    private PushyObject contains;
    private PushyObject values;
 
    public Map(PushyObject object) {
        this.object = object;
        update = (PushyObject)object.__getattr__("update");
        get = (PushyObject)object.__getattr__("get");
        contains = (PushyObject)object.__getattr__("__contains__");
        values = (PushyObject)object.__getattr__("values");
    }

    public Iterator iterator() {
        return keySet().iterator();
    }

    public boolean containsKey(Object key) {
        return ((Boolean)contains.__call__(new Object[]{key})).booleanValue();
    }

    public boolean containsValue(Object value) {
        
        Boolean res = (Boolean)contains.__call__(new Object[]{value});
        return res.booleanValue();
    }

    public Object get(Object key) {
        return get.__call__(new Object[]{key});
    }
    
    public Object put(Object key, Object value) {
        Object old = get(key);
        object.__setitem__(key, value);
        return old;
    }

    public void putAll(java.util.Map map) {
        update.__call__(new Object[]{map});
    }

    public int size() {
        return object.__len__();
    }

    public Set entrySet() {
        return new PushyMapEntrySet(object);
    }

    public Collection values() {
        return (Collection)values.__call__();
    }
}


class PushyMapEntrySet extends AbstractSet {
    private PushyObject object;
    private PushyObject items;

    public PushyMapEntrySet(PushyObject object) {
        this.object = object;
        items = (PushyObject)object.__getattr__("items");
    }

    public Iterator iterator() {
        java.util.List itemsList = (java.util.List)items.__call__();
        return new PushyMapEntrySetIterator(itemsList.iterator());
    }

    public int size() {
        return object.__len__();
    }
}


class PushyMapEntrySetIterator implements Iterator {
    private java.util.Map map;
    private java.util.Map.Entry current;
    private Iterator iterator;

    public PushyMapEntrySetIterator(Iterator iterator) {
        this.iterator = iterator;
    }

    public boolean hasNext() {
        return iterator.hasNext();
    }

    public Object next() {
        current = new PushyMapEntry(map, (Object[])iterator.next());
        return current;
    }

    public void remove() {
        if (current == null)
            throw new IllegalStateException();
        map.remove(current.getKey());
        current = null;
    }
}

class PushyMapEntry implements java.util.Map.Entry {
    private java.util.Map map;
    private Object key;
    private Object value;

    public PushyMapEntry(java.util.Map map, Object[] pair) {
        this.map = map;
        key = pair[0];
        value = pair[1];
    }

    public Object getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    public Object setValue(Object value) {
        Object old = this.value;
        if (value != this.value) {
            map.put(key, value);
            this.value = value;
        }
        return old;
    }

    public int hashCode() {
        return (key   == null ? 0 : key.hashCode()) ^
               (value == null ? 0 : value.hashCode());
    }

    public boolean equals(Object o) {
        if (o instanceof java.util.Map.Entry) {
            java.util.Map.Entry rhs = (java.util.Map.Entry)o;
            if (key == null) {
                if (rhs.getKey() != null)
                    return false;
            } else {
                if (rhs.getKey() == null || !key.equals(rhs.getKey()))
                    return false;
            }
            if (value == null) {
                if (rhs.getValue() != null)
                    return false;
            } else {
                if (rhs.getValue() == null || !value.equals(rhs.getValue()))
                    return false;
            }
        }
        return false;
    }
}

