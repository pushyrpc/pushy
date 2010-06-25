/*
 * Copyright (c) 2009, 2010 Andrew Wilkins <axwalk@gmail.com>
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

import pushy.modules.GetpassModule;
import pushy.modules.OsModule;
import pushy.modules.OsPathModule;
import pushy.modules.PlatformModule;
import pushy.modules.TimeModule;
import pushy.modules.WinregModule;

public class RemoteSystem
{
    private Client client;
    private RemoteSystemProperties properties;

    public RemoteSystem(Client client)
    {
        this.client = client;
        this.properties = new RemoteSystemProperties(client);
    }

    public long currentTimeMillis()
    {
        TimeModule timeModule = (TimeModule)client.getModule("time");
        float timeFloat = timeModule.time();
        return (long)(timeFloat * 1000);
    }

    public String getProperty(String key)
    {
        return properties.getProperty(key);
    }

    public java.util.Properties getProperties()
    {
        return properties;
    }
}

class RemoteSystemProperties extends java.util.Properties
{
    public static final long serialVersionUID = 0L;

    private static final String[] DYNAMIC_VALUE_KEYS = new String[] {
        "os.name", "os.arch", "os.version",
        "file.separator", "path.separator", "line.separator",
        "user.name", "user.home", "user.dir"
    };

    private Client client;
    private GetpassModule getpassModule;
    private OsModule osModule;
    private OsPathModule osPathModule;
    private PlatformModule platformModule;

    public RemoteSystemProperties(Client client)
    {
        super();
        this.client = client;
        getpassModule = (GetpassModule)client.getModule("getpass");
        osModule = (OsModule)client.getModule("os");
        osPathModule = (OsPathModule)client.getModule("os.path");
        platformModule = (PlatformModule)client.getModule("platform");
        for (int i = 0; i < DYNAMIC_VALUE_KEYS.length; ++i)
        {
            String value = getDynamicValue(DYNAMIC_VALUE_KEYS[i]);
            if (value != null)
                setProperty(DYNAMIC_VALUE_KEYS[i], value);
        }
    }

    public Object get(Object key)
    {
        Object value = null;
        if (key instanceof String)
            value = getDynamicValue((String)key);
        if (value == null)
            value = super.get(key);
        return value;
    }

    private String getDynamicValue(String key)
    {
        if (key.equals("os.name"))
            return getOsName();
        if (key.equals("os.arch"))
            return platformModule.machine();
        if (key.equals("os.version"))
            return getOsVersion();
        if (key.equals("file.separator"))
            return osModule.sep;
        if (key.equals("path.separator"))
            return osModule.pathsep;
        if (key.equals("line.separator"))
            return osModule.linesep;
        if (key.equals("user.name"))
            return getpassModule.getuser();
        if (key.equals("user.home"))
            return osPathModule.expanduser("~" + getpassModule.getuser());
        if (key.equals("user.dir"))
            return osModule.getcwd();
        return null;
    }

    /**
     * Determine the operating system name, mapping from the values reported by
     * Python to those reported by common JVMs.
     *
     * @see http://lopica.sourceforge.net/os.html
     */
    private String getOsName()
    {
        String system = platformModule.system();
        if (system.equals("Windows") || system.equals("Microsoft"))
        {
            String release = platformModule.release();

            // For Python 2.6 and below, Windows 7 is reported as
            // "post2008Server". This has been fixed in Python 2.7
            // (http://bugs.python.org/issue7863)
            //
            // The Python trunk's platform.py at 24 June 2010 consults the
            // registry if a certain new function isn't available (not in 2.6),
            // and checks for the presence of "Server" in the product name.
            //
            if (release.equals("post2008Server") &&
                platformModule.version().startsWith("6.1."))
            {
                boolean isServer = false;
                WinregModule winreg =
                    (WinregModule)client.getModule("_winreg");
                Object hkey = winreg.openKey(WinregModule.HKEY_LOCAL_MACHINE,
                    "Software\\Microsoft\\Windows NT\\CurrentVersion");
                try {
                    String prodname =
                        (String)winreg.queryValue(hkey, "ProductName");
                    if (prodname.indexOf("Server") != -1)
                        isServer = true;
                } finally {
                    winreg.closeKey(hkey);
                }

                // Replace the post2008Server with the correct value.
                release = isServer ? "2008ServerR2" : "7";
            }

            return "Windows" + " " + release;
        }
        return system;
    }

    /**
     * Get the os version, as it would be reported by Java.
     */
    public String getOsVersion()
    {
        String system = platformModule.system();
        if (system.equals("Windows") || system.equals("Microsoft"))
        {
            String[] win32ver = platformModule.win32_ver();
            String version = win32ver[1]; // Release/build version
            if (version.length() == 0)
            {
                // platform.win32_ver() has been known to return empty strings
                // (observed in a Cygwin environment, Windows 2003 Server).
                version = platformModule.version();
            }

            int dot = version.lastIndexOf('.');
            String release = version.substring(0, dot);
            String build = version.substring(dot+1);

            // The Sun and IBM JREs report os.version differently on Windows.
            // Sun reports it as the version number, while the IBM JRE appends
            // the service pack (if any) and build number. Don't know about
            // other vendors.
            // XXX Do we need to test for "*Oracle*" now?
            String vmVendor = System.getProperty("java.vm.vendor");
            if (vmVendor.equals("Sun Microsystems Inc."))
                return release;

            String csd = win32ver[2]; // Corrective Service Deliverable (SP)
            if (csd.startsWith("SP"))
                csd = "Service Pack " + csd.substring(2);
            String osVersion = release + " build " + build;
            if (csd.length() > 0)
                osVersion += " " + csd;
            return osVersion;
        }
        else
        {
            return platformModule.release();
        }
    }
}

