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
    private PushyObject chdirMethod;
    private PushyObject getcwdMethod;
    private PushyObject killMethod;
    private PushyObject removeMethod;
    private PushyObject rmdirMethod;
    private PushyObject mkdirMethod;
    private PushyObject makedirsMethod;
    private PushyObject statMethod;
    private PushyObject listdirMethod;

    public final String sep;
    public final String pathsep;
    public final String linesep;
    public final Map environ;

    public OsModule(Client client) {
        super(client, "os");
        if (__hasattr__("kill"))
            killMethod = (PushyObject)__getattr__("kill");
        chdirMethod = (PushyObject)__getattr__("chdir");
        getcwdMethod = (PushyObject)__getattr__("getcwd");
        removeMethod = (PushyObject)__getattr__("remove");
        rmdirMethod = (PushyObject)__getattr__("rmdir");
        mkdirMethod = (PushyObject)__getattr__("mkdir");
        makedirsMethod = (PushyObject)__getattr__("makedirs");
        statMethod = (PushyObject)__getattr__("stat");
        listdirMethod = (PushyObject)__getattr__("listdir");

        // Get module-level attributes.
        sep = (String)__getattr__("sep");
        pathsep = (String)__getattr__("pathsep");
        linesep = (String)__getattr__("linesep");
        environ = new PushyMapObject((PushyObject)__getattr__("environ"));
    }

    public PushyObject stat(String path) {
        return (PushyObject)statMethod.__call__(new Object[]{path});
    }

    public String getcwd() {
        return (String)getcwdMethod.__call__();
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

    public void rmdir(String path) {
        rmdirMethod.__call__(new Object[]{path});
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

    public void chdir(String path) {
        chdirMethod.__call__(new Object[]{path});
    }
}

