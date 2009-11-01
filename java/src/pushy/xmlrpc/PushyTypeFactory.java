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
import org.apache.ws.commons.util.NamespaceContextImpl;
import org.apache.xmlrpc.common.TypeFactoryImpl;
import org.apache.xmlrpc.common.XmlRpcController;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.parser.NullParser;
import org.apache.xmlrpc.parser.TypeParser;
import org.apache.xmlrpc.serializer.NullSerializer;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.xml.sax.SAXException;

public class PushyTypeFactory extends TypeFactoryImpl
{
    private Client client;

    public PushyTypeFactory(XmlRpcController controller, Client client)
    {
        super(controller);
        this.client = client;
    }

    public TypeParser getParser(XmlRpcStreamConfig config,
            NamespaceContextImpl context, String puri, String localName)
    {
        if ("".equals(puri) && NullSerializer.NIL_TAG.equals(localName))
        {
            return new NullParser();
        }
        else if (localName.equals("pobject"))
        {
            return new PushyObjectParser(client);
        }
        else if (localName.equals("bstring"))
        {
            return new BinaryStringParser(client);
        }
        else
        {
            return super.getParser(config, context, puri, localName);
        }
    }

    public TypeSerializer
    getSerializer(XmlRpcStreamConfig config, Object object) throws SAXException
    {
        if (object == null)
        {
            return new PythonNullSerializer();
        }
        else
        {
            return super.getSerializer(config, object);
        }
    }
}

