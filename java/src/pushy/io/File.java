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

package pushy.io;

import java.io.IOException;

import pushy.Client;
import pushy.modules.OsModule;
import pushy.modules.OsPathModule;

public class File extends java.io.File {
    private Client client;
    private OsModule osModule;
    private OsPathModule osPathModule;

    public File(Client client, String pathname) {
        super(pathname);
        this.client = client;
        osModule = (OsModule)client.getModule("os");
        osPathModule = (OsPathModule)client.getModule("os.path");
    }

    public File(Client client, String parent, String child) {
        super(parent, child);
        this.client = client;
        osModule = (OsModule)client.getModule("os");
        osPathModule = (OsPathModule)client.getModule("os.path");
    }

    public File(Client client, java.io.File parent, String child) {
        super(parent, child);
        this.client = client;
        osModule = (OsModule)client.getModule("os");
        osPathModule = (OsPathModule)client.getModule("os.path");
    }

    public boolean delete() {
        if (exists()) {
            if (isDirectory())
                osModule.rmdir(getPath());
            else
                osModule.remove(getPath());
            return !exists();
        }
        return false;
    }

    public boolean exists() {
        return osPathModule.exists(getPath());
    }

    public String getAbsolutePath() {
        return super.getAbsolutePath().replace("\\", "/");
    }

    public String getCanonicalPath() throws IOException {
        return super.getCanonicalPath().replace("\\", "/");
    }

    public String getName() {
        return super.getName();
    }

    public String getParent() {
        return super.getParent();
    }

    public String getPath() {
        return super.getPath().replace("\\", "/");
    }

    public boolean isDirectory() {
        return osPathModule.isdir(getPath());
    }

    public boolean isFile() {
        return osPathModule.isfile(getPath());
    }

    public boolean mkdir() {
        if (!exists()) {
            osModule.mkdir(getPath());
            return exists();
        }
        return false;
    }

    public boolean mkdirs() {
        if (!exists()) {
            osModule.makedirs(getPath());
            return exists();
        }
        return false;
    }

    public String[] list() {
        return osModule.listdir(getPath());
    }

    public long length() {
        return ((Integer)osModule.stat(getPath()).__getattr__(
                   "st_size")).intValue();
    }

    public boolean isAbsolute() {
        return osPathModule.isabs(getPath());
    }

    /**
     * Copy the remote file to a local file.
     */
    public void get(String localPath) {
        client.getfile(getPath(), localPath);
    }

    /**
     * Copy the local file to the remote file.
     */
    public void put(String localPath) {
        client.putfile(localPath, getPath());
    }
}

