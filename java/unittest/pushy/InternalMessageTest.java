package pushy;

import junit.framework.TestCase;

import pushy.internal.*;
import java.io.IOException;
import java.io.ByteArrayInputStream;

public class InternalMessageTest extends TestCase
{
    public void testPackUnpack() throws Exception
    {
        byte[] payload = new byte[256];
        for (int i = 0; i < payload.length; ++i)
            payload[i] = (byte)i;

        Message m1 = new Message(Message.Type.evaluate, payload, 0);
        byte[] packed = m1.pack();
        Message m2 = Message.unpack(new ByteArrayInputStream(packed));
        assertEquals(m1, m2);
    }
}

