package pushy;

import junit.framework.TestCase;

import java.io.IOException;

public class BinaryStringTest extends TestCase
{
    private Client client;

    public void setUp() throws IOException {
        client = new Client("local:");
    }

    public void tearDown() {
        client.close();
    }

    /**
     * Test that binary strings aren't translated on their way to/from the
     * Python process, or in creation of the Java String object.
     */
    public void testBinaryStringTranslation() throws Exception {
        String value = (String)client.evaluate("'\\xBB\\xEE\\xEE\\xFF'");
        assertEquals("\u00BB\u00EE\u00EE\u00FF", value);
    }
}

