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
import java.io.InputStream;

import pushy.Client;
import pushy.Module;
import pushy.PushyObject;

public class FileInputStream extends InputStream {
    private PushyObject file;
    private PushyObject readMethod;
    private PushyObject closeMethod;
    
    public FileInputStream(PushyObject file) {
        this.file = file;
        readMethod = (PushyObject)file.__getattr__("read");
        closeMethod = (PushyObject)file.__getattr__("close");
    }

    public FileInputStream(File file) {
        this(file.getClient(), file);
    }

    public FileInputStream(Client client, java.io.File file) {
        this(client, file.getAbsolutePath());
    }

    public FileInputStream(Client client, String path) {
        this(client, path, "rb");
    }

    public FileInputStream(File file, String mode) {
        this(file.getClient(), file, mode);
    }

    public FileInputStream(Client client, java.io.File file, String mode) {
        this(client, file.getAbsolutePath(), mode);
    }

    public FileInputStream(Client client, String path, String mode) {
        this(open(client, path, mode));
    }

    public int read() throws IOException {
        String char_ =
            (String)readMethod.__call__(new Object[]{new Integer(1)});
        if (char_.length() == 0)
            return -1;
        return char_.charAt(0);
    }

    public void close() throws IOException {
        closeMethod.__call__(null);
    }

    public int read(byte[] b, int offset, int length) throws IOException {
        String bytes =
            (String)readMethod.__call__(new Object[]{new Integer(length)});
        if (bytes.length() == 0)
            return -1;
        System.arraycopy(
            bytes.getBytes("ISO-8859-1"), 0, b, offset, bytes.length());
        return bytes.length();
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    // Open a file in the remote interpreter.
    private static PushyObject open(Client client, String path, String mode) {
        Module builtin = client.getModule("__builtin__");
        PushyObject open = (PushyObject)builtin.__getattr__("open");
        return (PushyObject)open.__call__(new String[]{path, mode});
    }
}

