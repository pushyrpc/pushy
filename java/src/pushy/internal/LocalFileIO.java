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

package pushy.internal;

import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Utility class for local file operations.
 */
public class LocalFileIO
{
    /**
     * Copy the contents of one file to another.
     */
    public static void copyfile(String src, String dest)
    {
        try
        {
            FileInputStream in = new FileInputStream(src);
            try
            {
                FileOutputStream out = new FileOutputStream(dest);
                try
                {
                    byte[] buffer = new byte[1024];
                    int nread;
                    while ((nread = in.read(buffer)) > 0)
                        out.write(buffer, 0, nread);
                }
                finally
                {
                    out.close();
                }
            }
            finally
            {
                in.close();
            }
        }
        catch (java.io.IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}

