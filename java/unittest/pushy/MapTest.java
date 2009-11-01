package pushy;

import junit.framework.TestCase;

import pushy.modules.*;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapTest extends TestCase
{
    private Client client;

    public void setUp() throws IOException {
        client = new Client("local:");
    }

    public void tearDown() {
        client.close();
    }

    /**
     * Check that a UserDict (e.g. os.environ) is returned as a Map.
     */
    public void testUserDict() throws Exception {
        OsModule os = (OsModule)client.getModule("os");
        Map environ = os.environ;
        assertTrue(environ.containsKey("PATH"));
    }
}

