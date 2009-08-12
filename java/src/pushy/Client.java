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

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import pushy.modules.ModuleFactory;
import pushy.xmlrpc.PushyTypeFactory;

public class Client
{
    private static final String jarPath =
        getJarPath(Client.class.getResource("Client.class"));

    // Determine the path to the jar file that defines Pushy, which will be
    // used to import the 'jpushy' module.
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

    private Process jpushyServer;
    private XmlRpcClientConfigImpl config;
    private XmlRpcClient client;
    private Map modules = new HashMap();

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
        String jpushyLoaderProgram =
            "import subprocess, sys;" +
            "sys.path.insert(0, sys.argv[1]);" +
            "import jpushy; jpushy.start()";

        // Start XML-RPC server process.
        String[] args =
            new String[]{"python", "-c", jpushyLoaderProgram, jarPath};
        jpushyServer = Runtime.getRuntime().exec(args);

        try
        {
            // Determine the port that the XML-RPC server is listening on.
            String line =
                new BufferedReader(
                    new InputStreamReader(
                        jpushyServer.getInputStream())).readLine();
            int jpushyServerPort = Integer.parseInt(line);

            URL url = new URL("http://127.0.0.1:"+jpushyServerPort+"/RPC2");
            config = new XmlRpcClientConfigImpl();
            config.setServerURL(url);
            config.setEnabledForExceptions(true);
            config.setEnabledForExtensions(true);

            client = new XmlRpcClient();
            client.setConfig(config);
            client.setTypeFactory(new PushyTypeFactory(client, this));

            // Finally, call the 'connect' function.
            execute("connect", new Object[]{address, properties});
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            try
            {
                BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    jpushyServer.getErrorStream()));
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

            jpushyServer.destroy();
            throw new RuntimeException(e);
        }

        jpushyServer.getOutputStream().close();
        jpushyServer.getErrorStream().close();
        jpushyServer.getInputStream().close();
    }

    public void finalize()
    {
        close();
    }

    /**
     * Close the server.
     */
    public void close()
    {
        if (jpushyServer != null)
        {
            synchronized (this)
            {
                if (jpushyServer != null)
                {
                    jpushyServer.destroy();
                    jpushyServer = null;
                }
            }
        }
    }

    protected Object execute(String method, Object[] args)
    {
        try
        {
            return client.execute(method, args);
        }
        catch (XmlRpcException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Evaluate an expression.
     */
    public Object evaluate(String expression)
    {
        return execute("evaluate", new Object[]{expression});
    }

    /**
     * Call a function with no arguments.
     *
     * @param function The name of the function to call.
     * @return The result of calling the function.
     */
    public Object call(String function)
    {
        return call(function, null, null);
    }

    /**
     * Call a function with the specified arguments.
     *
     * @param function The name of the function to call.
     * @param args Positional arguments to invoke the function with.
     * @return The result of calling the function.
     */
    public Object call(String function, Object[] args)
    {
        return call(function, args, null);
    }

    /**
     * Call a function, given its "absolute" name, i.e. qualified with its
     * module, with the specified positional and named arguments.
     *
     * @param function The name of the function to call.
     * @param args Positional arguments to invoke the function with.
     * @param namedArgs Named (keyword) arguments to call the function with.
     * @return The result of calling the function.
     */
    public Object call(String function, Object[] args, Map namedArgs)
    {
        Object[] callArgs = new Object[] {function, args, namedArgs};
        return execute("call", callArgs);
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
     * Determine whether the object with the specified ID has the attribute
     * with the given name.
     *
     * @param id The object ID.
     * @param name The attribute name.
     * @return True if and only if the object has the attribute, else false.
     */
    public boolean hasattr(long id, String name)
    {
        Object res = execute("hasattr_", new Object[]{""+id, name});
        return ((Boolean)res).booleanValue();
    }

    /**
     * Get the value of an attribute from the object with the specified ID.
     *
     * @param id The object ID.
     * @param name The attribute name.
     * @return The value of the attribute of the object.
     */
    public Object getattr(long id, String name)
    {
        return execute("getattr_", new Object[]{""+id, name});
    }

    /**
     * Get an item of an object, given a key (i.e. object[key])
     *
     * @param id The object ID.
     * @param name The item key.
     * @return The value of the item of the object.
     */
    public Object getitem(long id, Object key)
    {
        return execute("getitem_", new Object[]{""+id, key});
    }

    /**
     * Set the value of an item of an object, given a key and value.
     *
     * @param id The object ID.
     * @param name The item key.
     * @param value The item value.
     */
    public void setitem(long id, Object key, Object value)
    {
        execute("setitem_", new Object[]{""+id, key, value});
    }

    /**
     * Get the length of the object.
     *
     * @param id The object ID.
     * @return The length of the object.
     */
    public int len(long id)
    {
        return ((Integer)execute("getlen_", new Object[]{""+id})).intValue();
    }

    /**
     * Call the object with no arguments.
     *
     * @param id The object ID.
     * @return The result of calling the object.
     */
    public Object callobj(long id)
    {
        return callobj(id, null, null);
    }

    /**
     * Call the object with positional arguments.
     *
     * @param id The object ID.
     * @param args The positional arguments to call the object with.
     * @return The result of calling the object.
     */
    public Object callobj(long id, Object[] args)
    {
        return callobj(id, args, null);
    }

    /**
     * Call the object with positional and keyword arguments.
     *
     * @param id The object ID.
     * @param args The positional arguments to call the object with.
     * @param kwargs The keyword arguments to call the object with.
     * @return The result of calling the object.
     */
    public Object callobj(long id, Object[] args, Map kwargs)
    {
        Object[] callArgs = new Object[]{""+id, args, kwargs};
        return execute("callobj", callArgs);
    }

    public void putfile(String localFile, String remoteFile)
    {
        execute("putfile", new Object[]{localFile, remoteFile});
    }

    public void getfile(String remoteFile, String localFile)
    {
        execute("getfile", new Object[]{remoteFile, localFile});
    }
}

