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

    public void testKnownMessage() throws Exception
    {
/*
Sending Message(MessageType(3, 'getattr'), 140247671006960->1,
't\x00\x00\x00\x06oi\x00\x00\x00\x00\x00\x00\x00\x0bss\x05\x00\x00\x00items'
[26 bytes]) (1): ...
*/
        byte[] bytes = new byte[]{3, 0, 0, 127, -115, -12, -100, 102, -16, 0,
                                  0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 26, 116, 0, 0,
                                  0, 6, 111, 105, 0, 0, 0, 0, 0, 0, 0, 11, 115,
                                  115, 5, 0, 0, 0, 105, 116, 101, 109, 115};

        Message m = Message.unpack(new ByteArrayInputStream(bytes));
        assertEquals(Message.Type.getattr, m.getType());
        assertEquals(140247671006960L, m.getSource());
    }
}

