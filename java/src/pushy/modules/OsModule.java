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
    private PushyObject chmodMethod;
    private PushyObject getcwdMethod;
    private PushyObject killMethod;
    private PushyObject removeMethod;
    private PushyObject rmdirMethod;
    private PushyObject mkdirMethod;
    private PushyObject makedirsMethod;
    private PushyObject statMethod;
    private PushyObject listdirMethod;
    private PushyObject renameMethod;

    public final String sep;
    public final String pathsep;
    public final String linesep;
    public final Map environ;

    public OsModule(Client client) {
        super(client, "os");
        if (__hasattr__("kill"))
            killMethod = __getmethod__("kill");
        chdirMethod = __getmethod__("chdir");
        chmodMethod = __getmethod__("chmod");
        getcwdMethod = __getmethod__("getcwd");
        removeMethod = __getmethod__("remove");
        rmdirMethod = __getmethod__("rmdir");
        mkdirMethod = __getmethod__("mkdir");
        makedirsMethod = __getmethod__("makedirs");
        statMethod = __getmethod__("stat");
        listdirMethod = __getmethod__("listdir");
        renameMethod = __getmethod__("rename");

        // Get module-level attributes.
        sep = (String)__getattr__("sep");
        pathsep = (String)__getattr__("pathsep");
        linesep = (String)__getattr__("linesep");
        environ = (Map)__getattr__("environ");
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

    public void chmod(String path, int mode) {
        chmodMethod.__call__(new Object[]{path, new Integer(mode)});
    }

    public void rename(String src, String dest) {
        renameMethod.__call__(new Object[]{src, dest});
    }
}

