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

package pushy.net;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.InetAddress;

import pushy.PushyObject;
import pushy.Client;

public class RemoteSocket extends java.net.Socket
{
    private PushyObject object;
    private PushyObject accept;

    public RemoteSocket(PushyObject object)
    {
        this.object = object;
        accept = (PushyObject)object.__getattr__("accept");
    }

    public void bind(SocketAddress bindpoint) throws IOException
    {
        // Check/set bindpoint (address).
        if (bindpoint == null)
        {
            bindpoint =
                new InetSocketAddress(
                    InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 0);
        }
        else
        {
            if (!(bindpoint instanceof InetSocketAddress))
                throw new IllegalArgumentException("Unsupported address type");
        }

        InetSocketAddress socketAddress = (InetSocketAddress)bindpoint;
        InetAddress address = socketAddress.getAddress();

        // Call the bind() method.
        PushyObject bindMethod = (PushyObject)object.__getattr__("bind");
        try {
            bindMethod.__call__(new Object[]{
                new Object[]{
                    address.getHostAddress(),
                    new Integer(socketAddress.getPort())
            }});
        } catch (RuntimeException e) {
            // Socket.bind() throws an IOException if it fails.
            SocketException se = new SocketException();
            se.initCause(e);
            throw se;
        }
    }

    public synchronized boolean isClosed()
    {
        return object == null;
    }

    public synchronized void close()
    {
        if (!isClosed())
        {
            ((PushyObject)object.__getattr__("close")).__call__();
            object = null;
        }
    }

    public synchronized int getLocalPort()
    {
        if (!isClosed())
        {
            PushyObject getsockname =
                (PushyObject)object.__getattr__("getsockname");
            Object[] address = (Object[])getsockname.__call__();
            return ((Number)address[1]).intValue();
        }
        return -1; // TODO check if this is what "real" Java does.
    }

    public void listen(int backlog)
    {
        PushyObject listen = (PushyObject)object.__getattr__("listen");
        listen.__call__(new Object[]{new Integer(backlog)});
    }

    public RemoteSocket accept()
    {
        PushyObject[] result = (PushyObject[])accept.__call__();
        return new RemoteSocket(result[0]);
    }
}

