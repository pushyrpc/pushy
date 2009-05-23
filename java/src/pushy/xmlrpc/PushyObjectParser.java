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
import pushy.PushyListObject;
import pushy.PushyMapObject;
import pushy.PushyObject;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.parser.LongParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class PushyObjectParser extends LongParser
{
    private Client client;
    private String type;

    public PushyObjectParser(Client client)
    {
        this.client = client;
        this.type = null;
    }

    public Object getResult() throws XmlRpcException
    {        
        Object result = super.getResult();
        if (result != null && result instanceof Long)
        {
            Long longResult = (Long)result;
            result = createPushyObject(longResult.longValue());
        }
        return result;
    }

    private Object createPushyObject(long id)
    {
        PushyObject object = new PushyObject(client, id);
        if (type != null)
        {
            if (type.equals("list"))
                return new PushyListObject(object);
            else if (type.equals("dict"))
                return new PushyMapObject(object);
        }
        return object; 
    }

    public void startElement(String puri, String localName, String name,
                             Attributes attrs) throws SAXException
    {
        super.startElement(puri, localName, name, attrs);
        this.type = attrs.getValue("type");
    }
}

