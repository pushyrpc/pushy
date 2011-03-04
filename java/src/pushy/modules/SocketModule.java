/*
 * Copyright (c) 2009, 2011 Andrew Wilkins <axwalk@gmail.com>
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

package pushy.modules;

import pushy.Client;
import pushy.PushyObject;
import pushy.Module;
import pushy.net.RemoteSocket;

public class SocketModule extends Module {
    public static final int AF_INET     = 2;
    public static final int AF_INET6    = 10;
    public static final int SOCK_STREAM = 1;
    public static final int SOCK_DGRAM  = 2;

    // SOL_SOCKET is a special "protocol" value for getsocketop/setsockopt.
    public static final Protocol SOL_SOCKET = new Protocol("SOL_SOCKET");
    public static final Protocol IPPROTO_TCP = new Protocol("IPPROTO_TCP");
    public static final Protocol IPPROTO_IP = new Protocol("IPPROTO_IP");

    // Socket options.
    public static final SocketOption SO_KEEPALIVE =
        new SocketOption("SO_KEEPALIVE");
    public static final SocketOption SO_REUSEADDR =
        new SocketOption("SO_REUSEADDR");
    public static final SocketOption SO_RCVBUF =
        new SocketOption("SO_RCVBUF");
    public static final SocketOption SO_SNDBUF =
        new SocketOption("SO_SNDBUF");
    public static final SocketOption SO_OOBINLINE =
        new SocketOption("SO_OOBINLINE");
    public static final SocketOption SO_TCPNODELAY =
        new SocketOption("SO_TCPNODELAY");
    public static final SocketOption SO_LINGER =
        new SocketOption("SO_LINGER");
    public static final SocketOption TCP_NODELAY =
        new SocketOption("TCP_NODELAY");
    public static final SocketOption IP_TOS =
        new SocketOption("IP_TOS");

    private Client client;
    private PushyObject gethostnameMethod;
    private PushyObject gethostbynameMethod;
    private PushyObject socketMethod;

    public SocketModule(Client client) {
        super(client, "socket");
        this.client = client;
        gethostnameMethod = __getmethod__("gethostname");
        gethostbynameMethod = __getmethod__("gethostbyname");
        socketMethod = __getmethod__("socket");
    }

    public String getHostName() {
        return (String)gethostnameMethod.__call__();
    }

    public String getHostByName(String name) {
        return (String)gethostbynameMethod.__call__(new String[]{name});
    }

    public RemoteSocket socket() {
        return socket(AF_INET);
    }

    public RemoteSocket socket(int family) {
        return socket(family, SOCK_STREAM);
    }

    public RemoteSocket socket(int family, int type) {
        return socket(family, type, 0);
    }

    public RemoteSocket socket(int family, int type, int protocol) {
        PushyObject socketObject =
            (PushyObject)socketMethod.__call__(new Object[]{
                new Integer(family),
                new Integer(type),
                new Integer(protocol)});
        return new RemoteSocket(client, socketObject);
    }

    /**
     * Get (and cache) a constant value.
     */
    public Integer getConstant(Constant constant)
    {
        if (!constant.isSet())
        {
            synchronized (constant)
            {
                if (!constant.isSet())
                {
                    Number value = (Number)__getattr__(constant.getName());
                    constant.setValue(value.intValue());
                }
            }
        }
        return constant.getValue();
    }

    public static class SocketOption extends Constant
    {
        SocketOption(String name)
        {
            super(name);
        }
    }

    public static class Protocol extends Constant
    {
        Protocol(String name)
        {
            super(name);
        }
    }
}

class Constant
{
    private Integer constant;
    private String name;

    Constant(String name)
    {
        this.constant = null;
        this.name = name;
    }

    String getName()
    {
        return name;
    }

    public boolean isSet()
    {
        return constant != null;
    }

    public void setValue(int constant)
    {
        if (this.constant != null)
            throw new RuntimeException("constant is already set");
        this.constant = new Integer(constant);
    }

    public Integer getValue()
    {
        return constant;
    }
}

