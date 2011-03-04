/*
 * Copyright (c) 2011 Andrew Wilkins <axwalk@gmail.com>
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

package pushy.modules;

import java.lang.reflect.Array;

import pushy.Client;
import pushy.PushyObject;
import pushy.Module;

public class StructModule extends Module
{
    private final PushyObject packMethod;
    private final PushyObject unpackMethod;
    private final PushyObject calcsizeMethod;

    public StructModule(Client client)
    {
        super(client, "struct");
        calcsizeMethod = __getmethod__("calcsize");
        unpackMethod = __getmethod__("unpack");
        packMethod = __getmethod__("pack");
    }

    public String pack(String format, Object[] values)
    {
        Object[] args = new Object[values.length + 1];
        args[0] = format;
        if (values.length > 0)
            System.arraycopy(values, 0, args, 1, values.length);
        return (String)packMethod.__call__(args);
    }

    public Object[] unpack(String format, String bytes)
    {
        Object result = unpackMethod.__call__(new Object[]{format, bytes});
        if (result instanceof Object[])
            return (Object[])result;
        Object[] array = new Object[Array.getLength(result)];
        for (int i = 0; i < array.length; ++i)
            array[i] = Array.get(result, i);
        return array;
    }

    public int calcsize(String format)
    {
        Number size = (Number)calcsizeMethod.__call__(new Object[]{format});
        return size.intValue();
    }
}

