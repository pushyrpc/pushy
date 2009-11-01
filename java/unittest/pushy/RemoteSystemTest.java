package pushy;

import junit.framework.TestCase;
import java.io.IOException;

public class RemoteSystemTest extends TestCase
{
    private Client client;
    private RemoteSystem remoteSystem;

    public void setUp() throws IOException {
        client = new Client("local:");
        remoteSystem = client.getSystem();
    }

    public void tearDown() {
        client.close();
    }

    public void testUserProperties() {
        // Same user, same name.
        assertEquals(
            System.getProperty("user.name"),
            remoteSystem.getProperty("user.name"));
        // Same user, same home.
        assertEquals(
            System.getProperty("user.home"),
            remoteSystem.getProperty("user.home"));
        // The "local:" transport creates a new process in the same working
        // directory as the initiating process, hence this should pass.
        assertEquals(
            System.getProperty("user.dir"),
            remoteSystem.getProperty("user.dir"));
    }

    /**
     * Test line, path and file separators. They should be the same as in the
     * calling Java process for a "local:" transport.
     */
    public void testSeparators() {
        assertEquals(
            System.getProperty("line.separator"),
            remoteSystem.getProperty("line.separator"));
        assertEquals(
            System.getProperty("path.separator"),
            remoteSystem.getProperty("path.separator"));
        assertEquals(
            System.getProperty("file.separator"),
            remoteSystem.getProperty("file.separator"));
    }

    public void testOsProperties() {
        assertEquals(
            System.getProperty("os.name"),
            remoteSystem.getProperty("os.name"));
        // May not be equal (e.g. may be i686 as opposed to i386).
        // TODO check equivalence.
        //assertEquals(
        //    System.getProperty("os.arch"),
        //    remoteSystem.getProperty("os.arch"));
        assertEquals(
            System.getProperty("os.version"),
            remoteSystem.getProperty("os.version"));
    }
}

