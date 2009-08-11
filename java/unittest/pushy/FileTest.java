package pushy;

import junit.framework.TestCase;

import pushy.modules.*;
import java.io.IOException;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileTest extends TestCase
{
    private Client client;

    public void setUp() throws IOException {
        client = new Client("local:");
    }

    public void tearDown() {
        client.close();
    }

    /**
     * Test delete.
     */
    public void testDelete() throws Exception {
        TempfileModule tempfile = (TempfileModule)client.getModule("tempfile");
        File file = tempfile.mkdtemp();
        assertTrue(file.exists());
        file.delete();
        assertFalse(file.exists());
    }
}

