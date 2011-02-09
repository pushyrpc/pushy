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
import pushy.modules.SocketModule;

public class RemoteSocket extends java.net.Socket
{
    private Client client;
    private PushyObject object;
    private PushyObject accept;
    private boolean bound = false;
    private boolean connected = false;
    private boolean isInputShutdown = false;
    private boolean isOutputShutdown = false;

    public RemoteSocket(Client client, PushyObject object)
    {
        this.client = client;
        this.object = object;
        accept = (PushyObject)object.__getattr__("accept");
    }

    /**
     * Bind the socket to an (Inet)SocketAddress.
     *
     * Only AF_INET/AF_INET6 families are supported.
     */
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
            bound = true;
        } catch (RuntimeException e) {
            // Socket.bind() throws an IOException if it fails.
            SocketException se = new SocketException();
            se.initCause(e);
            throw se;
        }
    }

    public boolean isConnected()
    {
        return connected;
    }

    public synchronized boolean isBound()
    {
        return bound;
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
            bound = false;
            connected = false;
            isInputShutdown = true;
            isOutputShutdown = true;
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

    /**
     * Get the peer's address.
     */
    public synchronized SocketAddress getRemoteSocketAddress()
    {
        if (!isClosed())
        {
            PushyObject getpeername =
                (PushyObject)object.__getattr__("getpeername");
            Object[] address = (Object[])getpeername.__call__();
            if (address.length == 2)
            {
                String host = (String)address[0];
                int port = ((Integer)address[1]).intValue();
                return InetSocketAddress.createUnresolved(host, port);
            }
            else if (address.length == 4)
            {
                String host = (String)address[0];
                int port = ((Integer)address[1]).intValue();
                int scope = ((Integer)address[3]).intValue();
                return InetSocketAddress.createUnresolved(
                            host + "%" + scope, port);
            }
            else
            {
                throw new RuntimeException("Unexpected result: " + address);
            }
        }
        return null;
    }

    public void listen(int backlog)
    {
        PushyObject listen = (PushyObject)object.__getattr__("listen");
        listen.__call__(new Object[]{new Integer(backlog)});
    }

    public RemoteSocket accept()
    {
        Object[] result = (Object[])accept.__call__();
        RemoteSocket socket = new RemoteSocket(client, (PushyObject)result[0]);
        socket.connected = true;
        return socket;
    }

    public synchronized void shutdownInput() throws IOException
    {
        if (!isConnected())
            throw new SocketException("Socket is not connected");
        if (!isInputShutdown)
        {
            SocketModule module = (SocketModule)client.getModule("socket");
            PushyObject shutdown = (PushyObject)object.__getattr__("shutdown");
            Object SHUT_WR = module.__getattr__("SHUT_WR");
            shutdown.__call__(new Object[]{SHUT_WR});
            isInputShutdown = true;
        }
    }

    public synchronized void shutdownOutput() throws IOException
    {
        if (!isConnected())
            throw new SocketException("Socket is not connected");
        if (!isOutputShutdown)
        {
            SocketModule module = (SocketModule)client.getModule("socket");
            PushyObject shutdown = (PushyObject)object.__getattr__("shutdown");
            Object SHUT_WR = module.__getattr__("SHUT_RD");
            shutdown.__call__(new Object[]{SHUT_WR});
            isOutputShutdown = true;
        }
    }

    public boolean isInputShutdown()
    {
        return isInputShutdown;
    }

    public boolean isOutputShutdown()
    {
        return isOutputShutdown;
    }
}

