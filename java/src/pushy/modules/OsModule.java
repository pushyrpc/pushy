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

import pushy.Client;
import pushy.PushyObject;
import pushy.Module;
import pushy.PushyMapObject;

import java.util.List;
import java.util.Map;

public class OsModule extends Module {
    private PushyObject killMethod;
    private PushyObject removeMethod;
    private PushyObject mkdirMethod;
    private PushyObject makedirsMethod;
    private PushyObject statMethod;
    private PushyObject listdirMethod;
    private PushyMapObject environ;

    public OsModule(Client client) {
        super(client, "os");
        if (__hasattr__("kill"))
            killMethod = (PushyObject)__getattr__("kill");
        removeMethod = (PushyObject)__getattr__("remove");
        mkdirMethod = (PushyObject)__getattr__("mkdir");
        makedirsMethod = (PushyObject)__getattr__("makedirs");
        statMethod = (PushyObject)__getattr__("stat");
        listdirMethod = (PushyObject)__getattr__("listdir");
        environ = new PushyMapObject((PushyObject)__getattr__("environ"));
    }

    public PushyObject stat(String path) {
        return (PushyObject)statMethod.__call__(new Object[]{path});
    }

    public void kill(int pid, int signal) {
        if (killMethod != null) {
            killMethod.__call__(
                new Object[]{new Integer(pid), new Integer(signal)});
        } else {
            throw new UnsupportedOperationException("kill is not supported");
        }
    }

    public void remove(String path) {
        removeMethod.__call__(new Object[]{path});
    }

    public void mkdir(String path) {
        mkdirMethod.__call__(new Object[]{path});
    }

    public void makedirs(String path) {
        makedirsMethod.__call__(new Object[]{path});
    }

    public String[] listdir(String path) {
        List files = (List)listdirMethod.__call__(new Object[]{path});
        return (String[])files.toArray(new String[]{});
    }

    public Map getenv() {
        return environ;
    }
}

