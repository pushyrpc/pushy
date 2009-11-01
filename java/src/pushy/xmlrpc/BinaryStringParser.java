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

package pushy.xmlrpc;

import pushy.Client;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.parser.ByteArrayParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class BinaryStringParser extends ByteArrayParser
{
    private Client client;

    public BinaryStringParser(Client client)
    {
        this.client = client;
    }

    public Object getResult() throws XmlRpcException
    {        
        Object result = super.getResult();
        if (result != null && result instanceof byte[])
        {
            byte[] byteArrayResult = (byte[])result;
            try
            {
                // Convert the byte array to a string using the Latin 1
                // character set, which is single-byte and contains the full
                // range [0-255].
                result = new String(byteArrayResult, "ISO-8859-1");
            }
            catch (java.io.UnsupportedEncodingException e)
            {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    public void startElement(String puri, String localName, String name,
                             Attributes attrs) throws SAXException
    {
        super.startElement(puri, localName, name, attrs);
    }
}

