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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import pushy.Client;
import pushy.Module;

public class ModuleFactory {
    private static Map classes = new HashMap(); 

    static {
        classes.put("getpass", GetpassModule.class);
        classes.put("os", OsModule.class);
        classes.put("os.path", OsPathModule.class);
        classes.put("platform", PlatformModule.class);
        classes.put("subprocess", SubprocessModule.class);
        classes.put("signal", SignalModule.class);
        classes.put("shutil", ShutilModule.class);
        classes.put("socket", SocketModule.class);
        classes.put("stat", StatModule.class);
        classes.put("tempfile", TempfileModule.class);
        classes.put("time", TimeModule.class);
    }

    public static Module createModule(Client client, String name)
    {
        // Wrap the module object in a Module.
        Class class_ = (Class)classes.get(name);
        if (class_ != null)
        {
            try
            {
                Constructor ctor =
                    class_.getConstructor(new Class[]{Client.class});
                return (Module)ctor.newInstance(new Object[]{client});
            }
            catch (Throwable e)
            {
                e.printStackTrace();
            }
        }
        return new Module(client, name);
    }
}

