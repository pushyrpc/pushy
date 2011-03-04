package pushy;

import pushy.modules.StructModule;

import junit.framework.TestCase;
import java.io.IOException;

/**
 * Tests for StructModule.
 */
public class StructModuleTest extends TestCase
{
    private Client client;
    private StructModule struct_;

    public void setUp() throws IOException {
        client = new Client("local:");
        struct_ = (StructModule)client.getModule("struct");
    }

    public void tearDown() {
        client.close();
    }

    public void testPackUnpack() {
        Object[] result = struct_.unpack("ii",
            struct_.pack("ii", new Object[]{
                new Integer(-123), new Integer(456)}));
        assertEquals(2, result.length);
        assertEquals(new Integer(-123), result[0]);
        assertEquals(new Integer(456), result[1]);
    }

    public void testCalcsize() {
        assertEquals(4, struct_.calcsize("i"));
    }
}

