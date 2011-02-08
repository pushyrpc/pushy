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
    public static final int IPPROTO_TCP = 6;

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
}

