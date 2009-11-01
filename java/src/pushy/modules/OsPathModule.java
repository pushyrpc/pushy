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

public class OsPathModule extends Module {
    private PushyObject existsMethod;
    private PushyObject isDirMethod;
    private PushyObject isFileMethod;
    private PushyObject isAbsMethod;
    private PushyObject abspathMethod;
    private PushyObject expanduserMethod;

    public OsPathModule(Client client) {
        super(client, "os.path");
        existsMethod = (PushyObject)__getattr__("exists");
        isDirMethod = (PushyObject)__getattr__("isdir");
        isFileMethod = (PushyObject)__getattr__("isfile");
        isAbsMethod = (PushyObject)__getattr__("isabs");
        abspathMethod = (PushyObject)__getattr__("abspath");
        expanduserMethod = (PushyObject)__getattr__("expanduser");
    }

    public boolean exists(String path) {
        return ((Boolean)existsMethod.__call__(
                   new Object[]{path})).booleanValue();
    }
    
    public boolean isdir(String path) {
        return ((Boolean)isDirMethod.__call__(
                   new Object[]{path})).booleanValue();
    }
    
    public boolean isfile(String path) {
        return ((Boolean)isFileMethod.__call__(
                   new Object[]{path})).booleanValue();
    }

    public boolean isabs(String path) {
        return ((Boolean)isAbsMethod.__call__(
                   new Object[]{path})).booleanValue();
    }

    public String abspath(String path) {
        return (String)abspathMethod.__call__(new Object[]{path});
    }

    public String expanduser(String path) {
        return (String)expanduserMethod.__call__(new Object[]{path});
    }
}

