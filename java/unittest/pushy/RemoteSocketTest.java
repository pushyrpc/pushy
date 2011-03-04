package pushy;

import pushy.net.RemoteServerSocket;

import junit.framework.TestCase;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketAddress;

/**
 * Tests for RemoteSocket and RemoteServerSocket.
 */
public class RemoteSocketTest extends TestCase
{
    private Client client;

    public void setUp() throws IOException {
        client = new Client("local:");
    }

    public void tearDown() {
        client.close();
    }

    public void testUnboundServerSocket() throws Exception {
        // Default constructor
        RemoteServerSocket server = new RemoteServerSocket(client);
        try {
            assertFalse(server.isBound());
            assertNull(server.getLocalSocketAddress());
        } finally {
            server.close();
        }

        // Non-default constructors are bound at construction time.
        server = new RemoteServerSocket(client, 0);
        try {
            assertTrue(server.isBound());
            assertNotNull(server.getLocalSocketAddress());
        } finally {
            server.close();
        }
    }

    public void testLocalSocketAddress() throws Exception {
        RemoteServerSocket server = new RemoteServerSocket(client, 0);
        try {
            SocketAddress socketAddress = server.getLocalSocketAddress();
            assertNotNull(socketAddress);
            assertTrue(socketAddress instanceof InetSocketAddress);
            InetSocketAddress inetSocketAddress =
                (InetSocketAddress)socketAddress;
            assertEquals(server.getLocalPort(), inetSocketAddress.getPort());
            assertEquals("0.0.0.0", inetSocketAddress.getHostName());
        } finally {
            server.close();
        }
    }

    /**
     * Test the accept() method of RemoteServerSocket.
     */
    public void testAccept() throws Exception {
        final ServerSocket server = new RemoteServerSocket(client, 0);
        final int serverPort = server.getLocalPort();
        try {
            // Create another thread to connect to the socket.
            final int[] peerPort = new int[]{-1};
            Thread thread = new Thread() {
                public void run() {
                    try {
                        Socket socket = new Socket(
                            InetAddress.getLocalHost(), serverPort);
                        peerPort[0] = socket.getLocalPort();
                        socket.close();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.start();

            // Accept the connection.
            Socket peer = server.accept();
            try {
                assertTrue(peer.isConnected());
                thread.join();
                SocketAddress peerAddress = peer.getRemoteSocketAddress();
                assertTrue(peerAddress instanceof InetSocketAddress);
                InetSocketAddress peerInetAddress =
                    (InetSocketAddress)peerAddress;
                assertFalse(-1 == peerPort[0]);
                assertEquals(peerPort[0], peerInetAddress.getPort());
            } finally {
                peer.close();
            }
        } finally {
            server.close();
        }
    }

    /**
     * Ensure shutting down the input stream of the socket causes an EOF
     * on the other side.
     */
    public void testShutdownInput() throws Exception {
        final RemoteServerSocket server = new RemoteServerSocket(client, 0);
        final int serverPort = server.getLocalPort();
        final Socket[] peer = new Socket[1];
        try {
            // Create another thread to connect to the socket.
            final Socket[] peerClient = new Socket[1];
            Thread thread = new Thread() {
                public void run() {
                    try {
                        peerClient[0] = new Socket(
                            InetAddress.getLocalHost(), serverPort);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.start();

            // Wait for the connection, then shut down the input stream.
            Socket peerServer = server.accept();
            try {
                thread.join();
                assertNotNull(peerClient[0]);
                try {
                    assertFalse(peerServer.isInputShutdown());
                    peerServer.shutdownInput();
                    assertEquals(-1, peerClient[0].getInputStream().read());
                    assertTrue(peerServer.isInputShutdown());
                } finally {
                    peerClient[0].close();
                }
            } finally {
                peerServer.close();
            }
        } finally {
            server.close();
        }
    }

    /**
     * Ensure shutting down the output stream of the socket causes an EOF
     * on the other side.
     */
    public void testShutdownOutput() throws Exception {
        final RemoteServerSocket server = new RemoteServerSocket(client, 0);
        final int serverPort = server.getLocalPort();
        final Socket[] peer = new Socket[1];
        try {
            // Create another thread to connect to the socket.
            final Socket[] peerClient = new Socket[1];
            Thread thread = new Thread() {
                public void run() {
                    try {
                        peerClient[0] = new Socket(
                            InetAddress.getLocalHost(), serverPort);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.start();

            // Wait for the connection, then shut down the output stream.
            Socket peerServer = server.accept();
            try {
                thread.join();
                assertNotNull(peerClient[0]);
                try {
                    assertFalse(peerServer.isOutputShutdown());
                    peerServer.shutdownOutput();
                    assertTrue(peerServer.isOutputShutdown());
                    try {
                        peerServer.getOutputStream().write(1);
                        fail("Expected IOException");
                    } catch (IOException e) {/* Expected */}
                } finally {
                    peerClient[0].close();
                }
            } finally {
                peerServer.close();
            }
        } finally {
            server.close();
        }
    }

    /**
     * Ensure that the receive buffer size of RemoteServerSocket is inherited
     * by connected sockets.
     */
    public void testDefaultReceiveBufferSize() throws Exception {
        final ServerSocket server = new RemoteServerSocket(client, 0);
        final int serverPort = server.getLocalPort();
        server.setReceiveBufferSize(4096);
        try {
            // Create another thread to connect to the socket.
            final int[] peerPort = new int[]{-1};
            Thread thread = new Thread() {
                public void run() {
                    try {
                        Socket socket = new Socket(
                            InetAddress.getLocalHost(), serverPort);
                        peerPort[0] = socket.getLocalPort();
                        socket.close();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            };
            thread.start();

            // Accept the connection.
            Socket peer = server.accept();
            try {
                assertTrue(peer.isConnected());
                assertEquals(
                    server.getReceiveBufferSize(),
                    peer.getReceiveBufferSize());
                thread.join();
            } finally {
                peer.close();
            }
        } finally {
            server.close();
        }
    }
}

