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

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ExportedMap extends ExportedObject
{
    private Map methods = new HashMap();

    public ExportedMap(Number id, Map map, Connection connection)
    {
        super(id, Proxy.Type.dictionary, map, connection);

        // keys()
        methods.put("keys", new Callable() {
            public Object call(Object[] args, Map kwargs) {
                return new ArrayList(getMap().keySet());
            }
        });

        // values()
        methods.put("values", new Callable() {
            public Object call(Object[] args, Map kwargs) {
                // XXX
                // Must return a list, not a tuple, or certain Python modules
                // will cark it (namely CreateProcess in subprocess).
                //
                // Investigate whether this is a strict requirement in Python,
                // or if the module is badly written. If the former, Python
                // should probably fail gracefully, not cause an access
                // violation.
                return new ArrayList(getMap().values());
            }
        });

        // update()
        methods.put("update", new Callable() {
            public Object call(Object[] args, Map kwargs) {
                Map map = (Map)getObject();
                if (args != null && args.length == 1)
                {
                    if (args[0] instanceof Map)
                    {
                        map.putAll((Map)args[0]);
                    }
                    else if (args[0] instanceof Iterable)
                    {
                        Iterator iter = ((Iterable)args[0]).iterator();
                        while (iter.hasNext())
                        {
                            Object pair = iter.next();
                            map.put(Array.get(pair, 0), Array.get(pair, 1));
                        }
                    }
                    else
                    {
                        int length = Array.getLength(args[0]);
                        for (int i = 0; i < length; ++i)
                        {
                            Object pair = Array.get(args[0], i);
                            map.put(Array.get(pair, 0), Array.get(pair, 1));
                        }
                    }
                }
                if (kwargs != null)
                    map.putAll(kwargs);
                return null;
            }
        });
    }

    private Map getMap()
    {
        return (Map)getObject();
    }

    public Object __getattr__(String name)
    {
        Object method = methods.get(name);
        if (method != null)
            return method;
        return super.__getattr__(name);
    }
}

