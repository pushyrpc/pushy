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

import java.nio.charset.Charset;

import pushy.Client;
import pushy.PushyObject;

public class FileWriter extends java.io.OutputStreamWriter {
    private static Charset charset; 
    static {charset = Charset.forName("UTF-8");}

    public FileWriter(File file) {
        this(file, false);
    }

    public FileWriter(File file, boolean append) {
        this(file.getClient(), file, append);
    }

    public FileWriter(Client client, java.io.File file) {
        this(client, file, false);
    }

    public FileWriter(Client client, java.io.File file, boolean append) {
        this(client, file.getAbsolutePath(), append);
    }

    public FileWriter(Client client, String path) {
        this(client, path, false);
    }

    public FileWriter(Client client, String path, boolean append) {
        super(new FileOutputStream(client, path, append ? "a" : "w"),
              charset.newEncoder());
    }
}

