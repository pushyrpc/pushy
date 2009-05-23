package pushy;

import junit.framework.TestCase;

import pushy.RemoteProcessBuilder;
import pushy.modules.OsPathModule;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProcessBuilderTest extends TestCase
{
    private Client client;

    public void setUp() throws IOException {
        client = new Client("local:");
    }

    public void tearDown() {
        client.close();
    }

    public void testEnvironment() throws Exception {
        String[] command =
            new String[]{
                "python", "-c", "import os; print os.environ['PUSHYTEST']"};

        RemoteProcessBuilder pb =
            new RemoteProcessBuilder(client, command);
        pb.environment().put("PUSHYTEST", "aloha");
        Process proc = pb.start();

        try
        {
            BufferedReader reader =
                new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            assertEquals("aloha", reader.readLine());
        }
        finally
        {
            assertEquals(0, proc.waitFor());
        }
    }

    public void testCwd() throws Exception {
        OsPathModule ospath = (OsPathModule)client.getModule("os.path");
        String root = ospath.abspath("/");

        String[] command =
            new String[]{"python", "-c", "import os; print os.getcwd()"};

        RemoteProcessBuilder pb =
            new RemoteProcessBuilder(client, command);
        pb.directory(new pushy.io.File(client, root));
        Process proc = pb.start();

        try
        {
            BufferedReader reader =
                new BufferedReader(
                    new InputStreamReader(proc.getInputStream()));
            assertEquals(root, reader.readLine());
        }
        finally
        {
            assertEquals(0, proc.waitFor());
        }
    }
}

