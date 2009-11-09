/*
 * Copyright (c) 2009 Andrew Wilkins <axwalk@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package pushy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.net.JarURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import pushy.modules.ModuleFactory;
import pushy.internal.Connection;

public class Client
{
    private static final String jarPath =
        getJarPath(Client.class.getResource("Client.class"));

    // Determine the path to the jar file that defines Pushy, which will be
    // used to import the 'pushy' module.
    private static String getJarPath(URL url)
    {
        try
        {
            if (url.getProtocol().equals("jar"))
            {
                JarURLConnection conn = (JarURLConnection)url.openConnection();
                return conn.getJarFile().getName();
            }
            else if (url.getProtocol().equals("bundleresource"))
            {
                // This is a bit of a hack. If the URL is a bundleresource
                // (i.e. loaded from a OSGi bundle), then we can get a
                // "local URL" which is a file or jar URL.
                URLConnection conn = url.openConnection();
                java.lang.reflect.Method getLocalURL =
                    conn.getClass().getMethod("getLocalURL", null);
                URL localURL = (URL)getLocalURL.invoke(conn, null);
                return getJarPath(localURL);
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private Process pushyServer;
    private Connection connection;
    private PushyObject remoteConnection;
    private PushyObject remoteEvaluate;
    private Map modules = new HashMap();
    private RemoteSystem system;

    /**
     * Create a Pushy connection, with the specified address.
     *
     * @param address The Pushy address to connect with.
     */
    public Client(String address) throws IOException
    {
        this(address, null);
    }

    /**
     * Create a Pushy connection, with the specified address and a map of
     * arbitrary keyword arguments.
     *
     * @param address The Pushy address to connect with.
     * @param properties Keyword arguments to pass to 'pushy.connect'.
     */
    public Client(String address, Map properties) throws IOException
    {
        String pushyLoaderProgram =
            "import sys;" +
            "sys.path.insert(0, sys.argv[1]);" +
            "import pushy.server;" +
            "pushy.server.serve_forever(sys.stdin, sys.stdout)";

        // Start XML-RPC server process.
        String[] args =
            new String[]{"python", "-u", "-c", pushyLoaderProgram, jarPath};
        pushyServer = Runtime.getRuntime().exec(args);

        try
        {
            // Create the connection.
            connection = new Connection(pushyServer.getInputStream(),
                                        pushyServer.getOutputStream());

            // If the address is non-local, create a connection in the local
            // connection.
            PushyObject pushyModule =
                (PushyObject)connection.evaluate(
                    "__import__('pushy')", null, null);
            PushyObject connectMethod =
                (PushyObject)pushyModule.__getattr__("connect");
            remoteConnection =
                (PushyObject)connectMethod.__call__(
                    new Object[]{address}, properties);
            remoteEvaluate = (PushyObject)remoteConnection.__getattr__("eval");
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            try
            {
                BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    pushyServer.getErrorStream()));
                String line = reader.readLine();
                while (line != null)
                {
                    System.err.println(line);
                    line = reader.readLine();
                }
            }
            catch (Throwable e2)
            {
                e2.printStackTrace();
            }
            pushyServer.destroy();
            throw new RuntimeException(e);
        }
        pushyServer.getErrorStream().close();
    }

    protected void finalize()
    {
        close();
        try {
            super.finalize();
        } catch (Throwable e) {}
    }

    /**
     * Close the server.
     */
    public void close()
    {
        if (pushyServer != null)
        {
            synchronized (this)
            {
                if (pushyServer != null)
                {
                    try
                    {
                        pushyServer.getOutputStream().close();
                        pushyServer.getInputStream().close();
                    }
                    catch (java.io.IOException e) {}
                    pushyServer.destroy();
                    pushyServer = null;
                }
            }
        }
    }

    /**
     * Evaluate an expression.
     */
    public Object evaluate(String expression)
    {
        return evaluate(expression, null);
    }

    /**
     * Evaluate an expression.
     */
    public Object evaluate(String expression, Map locals)
    {
        return evaluate(expression, locals, null);
    }

    /**
     * Evaluate an expression.
     */
    public Object evaluate(String expression, Map locals, Map globals)
    {
        return remoteEvaluate.__call__(
                   new Object[]{expression, locals, globals});
    }

    /**
     * Acquire a reference to a module.
     *
     * @param name The name of the module.
     * @return A subclass of pushy.Module.
     */
    public synchronized Module getModule(String name)
    {
        Module module = (Module)modules.get(name);
        if (module == null)
        {
            module = ModuleFactory.createModule(this, name);
            modules.put(name, module);
        }
        return module;
    }

    /**
     * Copy a local file to the remote system, using a transport-specific
     * mechanism for greater efficiency where available.
     *
     * @param localFile The path of the local file to copy from.
     * @param remoteFile The path of the remote file to copy to.
     */
    public void putfile(String localFile, String remoteFile)
    {
        //execute("putfile", new Object[]{localFile, remoteFile});
    }

    /**
     * Copy a remote file to the local system, using a transport-specific
     * mechanism for greater efficiency where available.
     *
     * @param remoteFile The path of the remote file to copy from.
     * @param localFile The path of the local file to copy to.
     */
    public void getfile(String remoteFile, String localFile)
    {
        //execute("getfile", new Object[]{remoteFile, localFile});
    }

    /**
     * Get an instance of RemoteSystem, which mimics java.lang.System.
     */
    public RemoteSystem getSystem()
    {
        if (system == null) {
            synchronized (this) {
                if (system == null)
                    system = new RemoteSystem(this);
            }
        }
        return system;
    }
}

