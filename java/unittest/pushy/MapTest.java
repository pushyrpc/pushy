package pushy;

import junit.framework.TestCase;

import pushy.modules.*;
import java.io.IOException;
import java.util.HashMap;
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

    /**
     * Make sure we can update remote dictionaries with local maps.
     */
    public void testDictUpdate() throws Exception {
        Map dict = (Map)client.evaluate("{}");
        Map map = new HashMap();
        map.put("abc", "xyz");
        dict.putAll(map);
        assertEquals(1, dict.size());
    }
}

