/*
 * Copyright (c) 2010, 2011 Andrew Wilkins <axwalk@gmail.com>
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
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.InetAddress;

import pushy.Client;
import pushy.PushyObject;

public class RemoteServerSocket extends java.net.ServerSocket
{
    private static final int DEFAULT_BACKLOG = 3;
    private static final InetAddress ANY_ADDR = null;
    static
    {
        try
        {
            InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
        }
        catch (java.net.UnknownHostException e) {}
    }

    private RemoteSocket socket;

    public RemoteServerSocket(Client client) throws IOException
    {
        pushy.modules.SocketModule socketModule =
            (pushy.modules.SocketModule)client.getModule("socket");
        socket = socketModule.socket();
    }

    public RemoteServerSocket(Client client, int port) throws IOException
    {
        this(client, port, 0);
    }

    public
    RemoteServerSocket(Client client, int port, int backlog) throws IOException
    {
        this(client, port, backlog, null);
    }

    public RemoteServerSocket(Client client, int port, int backlog,
                              InetAddress bindAddr) throws IOException
    {
        pushy.modules.SocketModule socketModule =
            (pushy.modules.SocketModule)client.getModule("socket");
        socket = socketModule.socket();

        // Bind the socket immediately.
        if (bindAddr == null)
            bindAddr = ANY_ADDR;
        bind(new InetSocketAddress(bindAddr, port), backlog);
    }

    public void bind(SocketAddress endpoint) throws IOException
    {
        bind(endpoint, 0);
    }

    public void bind(SocketAddress endpoint, int backlog) throws IOException
    {
        // If endpoint is null, then listen on any interface with an
        // unspecified port.
        if (endpoint == null)
            endpoint = new InetSocketAddress(ANY_ADDR, 0);

        // If backlog <= 0, use the default.
        if (backlog <= 0)
            backlog = DEFAULT_BACKLOG;

        socket.bind(endpoint);
        socket.listen(backlog);
    }

    public Socket accept()
    {
        return socket.accept();
    }

    public void close() throws IOException
    {
        if (!isClosed())
        {
            socket.close();
            socket = null;
        }
    }

    public int getLocalPort()
    {
        return socket.getLocalPort();
    }

    public boolean isBound()
    {
        return socket != null && socket.isBound();
    }

    public boolean isClosed()
    {
        return socket == null;
    }
}

