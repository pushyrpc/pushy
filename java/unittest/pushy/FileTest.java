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

    /**
     * Test pushy.io.File.getAbsolutePath().
     */
    public void testFileGetAbsolutePath() {
        OsModule os = (OsModule)client.getModule("os");
        TempfileModule tempfile = (TempfileModule)client.getModule("tempfile");

        File dir = tempfile.mkdtemp();
        assertTrue(dir.exists());
        String cwd = client.getSystem().getProperty("user.dir");
        try {
            os.chdir(dir.getAbsolutePath());
            assertEquals(
                dir.getAbsolutePath(),
                new pushy.io.File(client, ".").getAbsolutePath());
        } finally {
            os.chdir(cwd); // Go back to where you came from.
            dir.delete();
        }
    }

    /**
     * Test pushy.io.FileReader/Writer.
     */
    public void testFileReaderWriter() throws Exception {
        TempfileModule tempfile = (TempfileModule)client.getModule("tempfile");
        File dir = tempfile.mkdtemp();
        assertTrue(dir.exists());
        try {
            File file = new pushy.io.File(client, dir, "test.txt");
            pushy.io.FileWriter writer = new pushy.io.FileWriter(client, file);
            try {
                // Write to the file.
                writer.write("abc");

                // Close the writer, reopen and write something else. The file
                // should've been truncated, as we didn't request the file to
                // be opened in "append" mode.
                writer.close();
                writer = new pushy.io.FileWriter(client, file);
                writer.write("def");

                // Close the writer, reopen in append mode and write something
                // else. Expect no truncation this time.
                writer.close();
                writer = new pushy.io.FileWriter(client, file, true);
                writer.write("123");
                writer.close();

                // Check the contents of the file.
                pushy.io.FileReader reader =
                    new pushy.io.FileReader(client, file);
                try {
                    String line =
                        new java.io.BufferedReader(reader).readLine();
                    assertEquals("def123", line);
                } finally {
                    reader.close();
                }
            } finally {
                writer.close();
                file.delete();
            }
        } finally {
            dir.delete();
        }
    }
}

