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

public class Module extends pushy.internal.DelegatingPushyObject
{
    public Module(Client client, String name)
    {
        this(__import__(client, name));
    }

    protected Module(PushyObject object)
    {
        super(object);
    }

    protected PushyObject __getmethod__(String name)
    {
        return (PushyObject)__getattr__(name);
    }

    private static PushyObject __import__(Client client, String name)
    {
        // Get the module object.
        PushyObject module =
            (PushyObject)client.evaluate("__import__('"+name+"')");
        if (name.contains("."))
        {
            String[] parts = name.split("\\.");
            for (int i = 1; i < parts.length; ++i)
                module = (PushyObject)module.__getattr__(parts[i]);
        }
        return module;
    }
}

