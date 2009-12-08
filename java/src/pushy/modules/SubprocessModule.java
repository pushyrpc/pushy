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

package pushy.modules;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

import pushy.Client;
import pushy.PushyObject;
import pushy.io.FileInputStream;
import pushy.io.FileOutputStream;
import pushy.Module;

public class SubprocessModule extends Module {
    private Client client;
    private OsModule osModule;
    private SignalModule signalModule;
    private PushyObject Popen;
    private Object PIPE;
    private Object STDOUT;

    public SubprocessModule(Client client) {
        super(client, "subprocess");
        this.client = client;
        osModule = (OsModule)client.getModule("os");
        signalModule = (SignalModule)client.getModule("signal");
        Popen = __getmethod__("Popen");
        PIPE = __getattr__("PIPE");
        STDOUT = __getattr__("STDOUT");
    }

    public Process exec(String command) {
        return exec(new String[]{command});
    }

    public Process exec(String[] command) {
        return exec(command, false);
    }

    public Process exec(String[] command, boolean combineStderrStdout) {
        return exec(command, combineStderrStdout, null, null);
    }

    public Process
    exec(String[] command, boolean combineStderrStdout, Map env, String cwd) {
        Object[] args = new Object[]{command};
        Map kwargs = new HashMap();
        kwargs.put("stdout", PIPE);
        kwargs.put("stdin", PIPE);
        kwargs.put("stderr", combineStderrStdout ? STDOUT : PIPE);

        // Set the current working directory.
        if (cwd != null)
            kwargs.put("cwd", (String)cwd);

        // Set the environment variables for the remote process. We will create
        // a remote dictionary and copy our entries into it, so the subprocess
        // doesn't try to access objects from our map.
        if (env != null)
        {
            Map remoteEnv = (Map)client.evaluate("{}");
            for (Iterator iter = env.entrySet().iterator(); iter.hasNext();)
            {
                Map.Entry entry = (Map.Entry)iter.next();
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (!(key instanceof String))
                {
                    throw new IllegalArgumentException(
                                  "Non-string found in environment map: " +
                                   key);
                }
                if (!(value instanceof String))
                {
                    throw new IllegalArgumentException(
                                  "Non-string found in environment map: " +
                                   value);
                }
            }
            remoteEnv.putAll(env);
            kwargs.put("env", remoteEnv);
        }

        PushyObject popen = (PushyObject)Popen.__call__(args, kwargs);
        return new JPushyProcess(client, popen, osModule, signalModule,
                                 combineStderrStdout);
    }
}

/**
 * Wrapper around a subprocess.Popen, which presents the standard Java
 * java.lang.Process interface.
 */
class JPushyProcess extends Process {
    private Client client;
    private PushyObject popen;
    private OsModule osModule;
    private SignalModule signalModule;
    private FileInputStream stdoutStream;
    private FileInputStream stderrStream;
    private FileOutputStream stdinStream;

    JPushyProcess(Client client,
                  PushyObject popen,
                  OsModule osModule,
                  SignalModule signalModule,
                  boolean combineStderrStdout)
    {
        this.client = client;
        this.popen = popen;
        this.osModule = osModule;
        this.signalModule = signalModule;
        stdoutStream =
            new FileInputStream((PushyObject)popen.__getattr__("stdout"));
        stdinStream =
            new FileOutputStream((PushyObject)popen.__getattr__("stdin"));
        if (!combineStderrStdout)
            stderrStream =
                new FileInputStream((PushyObject)popen.__getattr__("stderr"));
    }

    private int getPid() {
        return ((Integer)(popen.__getattr__("pid"))).intValue();
    }

    public void destroy() {
        String osName = client.getSystem().getProperty("os.name");
        boolean isWindows = osName.startsWith("Windows");
        if (isWindows)
        {
            // On Windows use the "taskkill" command.
            SubprocessModule subprocess =
                (SubprocessModule)client.getModule("subprocess");

            Process taskkillProcess = subprocess.exec(
                new String[]{"taskkill", "/f", "/pid", ""+getPid()},
                true, null, null);

            // Wait for the taskkill command to complete.
            try {
                taskkillProcess.waitFor();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }   
        else
        {
            osModule.kill(getPid(), signalModule.SIGKILL);
        }
    }

    public int exitValue() {
        Object returncode = popen.__getattr__("returncode");
        if (returncode == null)
            throw new IllegalThreadStateException(
                          "Process has not yet terminated");
        return ((Integer)returncode).intValue();
    }

    public InputStream getErrorStream() {
        return stderrStream;
    }

    public InputStream getInputStream() {
        return stdoutStream;
    }

    public OutputStream getOutputStream() {
        return stdinStream;
    }

    public int waitFor() throws InterruptedException {
        return ((Integer)((PushyObject)popen.__getattr__(
                   "wait")).__call__()).intValue();
    }
}

