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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import pushy.Client;
import pushy.PushyObject;
import pushy.Module;

public class FileReader extends java.io.InputStreamReader {
    private static Charset charset; 
    static {charset = Charset.forName("UTF-8");}

    public FileReader(File file) {
        this(file.getClient(), file);
    }

    public FileReader(Client client, java.io.File file) {
        this(client, file.getAbsolutePath());
    }

    public FileReader(Client client, String path) {
        super(getInputStream(client, path), charset.newDecoder());
    }

    private static InputStream getInputStream(Client client, String path) {
        Module builtin = client.getModule("__builtin__");
        PushyObject open = (PushyObject)builtin.__getattr__("open");
        PushyObject file =
            (PushyObject)open.__call__(new String[]{path, "r"});
        return new FileInputStream(file);
    }
}

