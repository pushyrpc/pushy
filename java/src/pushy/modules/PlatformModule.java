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

package pushy.modules;

import pushy.PushyObject;
import pushy.Client;
import pushy.Module;

public class PlatformModule extends Module
{
    private PushyObject platformMethod;
    private PushyObject systemMethod;
    private PushyObject machineMethod;
    private PushyObject releaseMethod;
    private PushyObject win32verMethod;

    public PlatformModule(Client client)
    {
        super(client, "platform");
        platformMethod = __getmethod__("platform");
        systemMethod = __getmethod__("system");
        machineMethod = __getmethod__("machine");
        releaseMethod = __getmethod__("release");
        if (__hasattr__("win32_ver"))
            win32verMethod = __getmethod__("win32_ver");
    }

    public String platform()
    {
        return (String)platformMethod.__call__();
    }

    public String system()
    {
        return (String)systemMethod.__call__();
    }

    public String machine()
    {
        return (String)machineMethod.__call__();
    }

    public String release()
    {
        return (String)releaseMethod.__call__();
    }

    public String[] win32_ver()
    {
        if (win32verMethod != null)
        {
            Object[] objects = (Object[])win32verMethod.__call__();
            String[] values = new String[objects.length];
            for (int i = 0; i < objects.length; ++i)
                values[i] = (String)objects[i];
            return values;
        }
        throw new UnsupportedOperationException("win32_ver is not supported");
    }
}

