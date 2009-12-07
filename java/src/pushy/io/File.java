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
import pushy.modules.StatModule;

public class File extends java.io.File {
    public static final long serialVersionUID = 0L;

    private Client client;
    private OsModule osModule;
    private OsPathModule osPathModule;
    private StatModule statModule;

    public File(File parent, String child) {
        this(parent.getClient(), parent, child);
    }

    public File(Client client, String pathname) {
        super(pathname);
        this.client = client;
        osModule = (OsModule)client.getModule("os");
        osPathModule = (OsPathModule)client.getModule("os.path");
        statModule = (StatModule)client.getModule("stat");
    }

    public File(Client client, String parent, String child) {
        super(parent, child);
        this.client = client;
        osModule = (OsModule)client.getModule("os");
        osPathModule = (OsPathModule)client.getModule("os.path");
        statModule = (StatModule)client.getModule("stat");
    }

    public File(Client client, java.io.File parent, String child) {
        super(parent, child);
        this.client = client;
        osModule = (OsModule)client.getModule("os");
        osPathModule = (OsPathModule)client.getModule("os.path");
        statModule = (StatModule)client.getModule("stat");
    }

    /**
     * Return the pushy.Client associated with this File object.
     */
    public Client getClient() {
        return client;
    }

    public boolean delete() {
        if (exists()) {
            if (isDirectory())
                osModule.rmdir(getAbsolutePath());
            else
                osModule.remove(getAbsolutePath());
            return !exists();
        }
        return false;
    }

    public boolean exists() {
        return osPathModule.exists(getAbsolutePath());
    }

    public String getAbsolutePath() {
        return osPathModule.abspath(getPath());
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
        return osPathModule.isdir(getAbsolutePath());
    }

    public boolean isFile() {
        return osPathModule.isfile(getAbsolutePath());
    }

    public boolean mkdir() {
        if (!exists()) {
            osModule.mkdir(getAbsolutePath());
            return exists();
        }
        return false;
    }

    public boolean mkdirs() {
        if (!exists()) {
            osModule.makedirs(getAbsolutePath());
            return exists();
        }
        return false;
    }

    public String[] list() {
        return osModule.listdir(getAbsolutePath());
    }

    public String[] list(java.io.FilenameFilter filter) {
        String[] filenames = list();
        if (filter == null || filenames == null)
            return filenames;

        int count = 0;
        for (int i = 0; i < filenames.length; ++i)
        {
            if (filter.accept(this, filenames[i]))
                filenames[count++] = filenames[i];
        }

        if (count == filenames.length)
            return filenames;
        String[] newfilenames = new String[count];
        if (count > 0)
            System.arraycopy(filenames, 0, newfilenames, 0, count);
        return newfilenames;
    }

    public java.io.File[] listFiles() {
        String[] filenames = list();
        if (filenames == null)
            return null;
        java.io.File[] files = new java.io.File[filenames.length];
        for (int i = 0; i < filenames.length; ++i)
            files[i] = new pushy.io.File(client, filenames[i]);
        return files;
    }

    public java.io.File[] listFiles(java.io.FileFilter filter) {
        java.io.File[] files = listFiles();
        if (filter == null || files == null)
            return files;

        int count = 0;
        for (int i = 0; i < files.length; ++i)
        {
            if (filter.accept(files[i]))
                files[count++] = files[i];
        }

        if (count == files.length)
            return files;
        java.io.File[] newfiles = new java.io.File[count];
        if (count > 0)
            System.arraycopy(files, 0, newfiles, 0, count);
        return newfiles;
    }

    public java.io.File[] listFiles(java.io.FilenameFilter filter) {
        java.io.File[] files = listFiles();
        if (filter == null || files == null)
            return files;

        int count = 0;
        for (int i = 0; i < files.length; ++i)
        {
            if (filter.accept(this, files[i].getName()))
                files[count++] = files[i];
        }

        if (count == files.length)
            return files;
        java.io.File[] newfiles = new java.io.File[count];
        if (count > 0)
            System.arraycopy(files, 0, newfiles, 0, count);
        return newfiles;
    }

    public long length() {
        return ((Integer)osModule.stat(getAbsolutePath()).__getattr__(
                   "st_size")).intValue();
    }

    public boolean isAbsolute() {
        return osPathModule.isabs(getAbsolutePath());
    }

    public boolean setExecutable(boolean executable) {
        return setExecutable(executable, true);
    }

    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        int mask = statModule.S_IXUSR;
        if (!ownerOnly)
            mask = mask | statModule.S_IXGRP | statModule.S_IXOTH;

        // Get the new mode.
        int current_mode = getMode();
        int new_mode = current_mode;
        if (executable)
            new_mode |= mask;
        else
            new_mode &= ~mask;

        // If the mode has changed, set it on the remote file system.
        if (new_mode != current_mode)
        {
            try
            {
                osModule.chmod(getAbsolutePath(), new_mode);
            }
            catch (Throwable e)
            {
                return false;
            }
        }
        return true;
    }

    public boolean renameTo(java.io.File to) {
        try {
            osModule.rename(getAbsolutePath(), to.getAbsolutePath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Copy the remote file to a local file.
     */
    public void get(String localPath) {
        client.getfile(getAbsolutePath(), localPath);
    }

    /**
     * Copy the local file to the remote file.
     */
    public void put(String localPath) {
        client.putfile(localPath, getAbsolutePath());
    }

    /**
     * Get the file's mode (permissions).
     */
    public int getMode() {
        return ((Integer)osModule.stat(
                   getAbsolutePath()).__getattr__("st_mode")).intValue();
    }
}

