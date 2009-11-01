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
import java.io.OutputStream;

import pushy.Client;
import pushy.Module;
import pushy.PushyObject;

public class FileOutputStream extends OutputStream {
    private PushyObject file;
    private PushyObject writeMethod;
    private PushyObject closeMethod;
    private PushyObject flushMethod;

    /**
     * Create a FileOutputStream for a Pushy file-like object.
     */
    public FileOutputStream(PushyObject file) {
        this.file = file;
        writeMethod = (PushyObject)file.__getattr__("write");
        closeMethod = (PushyObject)file.__getattr__("close");
        flushMethod = (PushyObject)file.__getattr__("flush");
    }

    public FileOutputStream(File file) {
        this(file, false);
    }

    public FileOutputStream(File file, boolean append) {
        this(file.getClient(), file, append);
    }

    public FileOutputStream(Client client, java.io.File file) {
        this(client, file, false);
    }

    public FileOutputStream(Client client, java.io.File file, boolean append) {
        this(client, file.getAbsolutePath(), append);
    }

    public FileOutputStream(Client client, String path) {
        this(client, path, false);
    }

    public FileOutputStream(Client client, String path, boolean append) {
        this(client, path, append ? "ab" : "wb");
    }

    public FileOutputStream(File file, String mode) {
        this(file.getClient(), file, mode);
    }

    public FileOutputStream(Client client, java.io.File file, String mode) {
        this(client, file.getAbsolutePath(), mode);
    }

    public FileOutputStream(Client client, String path, String mode) {
        this(open(client, path, mode));
    }

    public void close() throws IOException {
        closeMethod.__call__(null);
    }

    public void flush() throws IOException {
        flushMethod.__call__(null);
    }

    public void write(byte[] b, int offset, int length) throws IOException {
        byte[] newbytes = new byte[length];
        System.arraycopy(b, offset, newbytes, 0, length);
        writeMethod.__call__(new Object[]{newbytes});
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(int b) throws IOException {
        write(new byte[]{(byte)b});
    }

    // Open a file in the remote interpreter.
    private static PushyObject open(Client client, String path, String mode) {
        Module builtin = client.getModule("__builtin__");
        PushyObject open = (PushyObject)builtin.__getattr__("open");
        return (PushyObject)open.__call__(new String[]{path, mode});
    }
}

