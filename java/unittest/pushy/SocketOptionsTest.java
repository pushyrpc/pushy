package pushy;

import pushy.modules.SocketModule;

import junit.framework.TestCase;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

/**
 * Tests for RemoteSocket socket options.
 *
 * Admittedly, these tests are a bit weak. They don't really verify that the
 * option is being set on the Python object.
 */
public class SocketOptionsTest extends TestCase
{
    private Client       client;
    private SocketModule module;
    private PushyObject  object;
    private Socket       socket;

    public void setUp() throws IOException {
        client = new Client("local:");
        module = (SocketModule)client.getModule("socket");
        PushyObject socketMethod = (PushyObject)module.__getattr__("socket");
        object = (PushyObject)socketMethod.__call__();
        socket = new pushy.net.RemoteSocket(client, object);
    }

    public void tearDown() {
        try {
            socket.close();
        } catch (IOException e) {}
        client.close();
    }

    public void testSoLinger() throws IOException
    {
        socket.setSoLinger(false, 0);
        assertEquals(-1, socket.getSoLinger()); // off
        socket.setSoLinger(true, 0);
        assertEquals(0, socket.getSoLinger()); // on, linger=0
        socket.setSoLinger(true, 1);
        assertEquals(1, socket.getSoLinger()); // on, linger=1
    }

    public void testTcpNoDelay() throws IOException
    {
        socket.setTcpNoDelay(false);
        assertEquals(false, socket.getTcpNoDelay());
        socket.setTcpNoDelay(true);
        assertEquals(true, socket.getTcpNoDelay());
    }

    public void testTrafficClass() throws IOException
    {
        SocketModule module = (SocketModule)client.getModule("socket");
        int iptos_throughput = 0xe0; // IPTOS_CLASS_CS7
        socket.setTrafficClass(iptos_throughput);
        assertEquals(iptos_throughput, socket.getTrafficClass());
    }

    public void testSoTimeout() throws IOException
    {
        socket.setSoTimeout(0);
        assertEquals(0, socket.getSoTimeout());
        socket.setSoTimeout(10);
        assertEquals(10, socket.getSoTimeout());

        boolean threwSocketException = false;
        try {
            socket.setSoTimeout(-1);
        } catch (SocketException e) {
            threwSocketException = true;
        }
        assertTrue(threwSocketException);
    }

    // On Linux, the kernel will automagically double the buffer size for
    // you. Rather than checking Linux specifically, let's be a little more
    // rigorous and compare to the Python object.
    public void testSendBufferSize() throws IOException
    {
        socket.setSendBufferSize(4096);
        Object SOL_SOCKET = module.__getattr__("SOL_SOCKET");
        Object SO_SNDBUF = module.__getattr__("SO_SNDBUF");
        PushyObject getsockopt = (PushyObject)object.__getattr__("getsockopt");
        Object res = getsockopt.__call__(new Object[]{SOL_SOCKET, SO_SNDBUF});
        assertEquals(((Number)res).intValue(), socket.getSendBufferSize());
    }

    public void testReceiveBufferSize() throws IOException
    {
        socket.setReceiveBufferSize(4096);
        Object SOL_SOCKET = module.__getattr__("SOL_SOCKET");
        Object SO_RCVBUF = module.__getattr__("SO_RCVBUF");
        PushyObject getsockopt = (PushyObject)object.__getattr__("getsockopt");
        Object res = getsockopt.__call__(new Object[]{SOL_SOCKET, SO_RCVBUF});
        assertEquals(((Number)res).intValue(), socket.getReceiveBufferSize());
    }

    public void testOOBInline() throws IOException
    {
        socket.setOOBInline(false);
        assertFalse(socket.getOOBInline());
        socket.setOOBInline(true);
        assertTrue(socket.getOOBInline());
    }

    public void testKeepAlive() throws IOException
    {
        socket.setKeepAlive(false);
        assertFalse(socket.getKeepAlive());
        socket.setKeepAlive(true);
        assertTrue(socket.getKeepAlive());
    }

    public void testReuseAddress() throws IOException
    {
        socket.setReuseAddress(false);
        assertFalse(socket.getReuseAddress());
        socket.setReuseAddress(true);
        assertTrue(socket.getReuseAddress());
    }
}

