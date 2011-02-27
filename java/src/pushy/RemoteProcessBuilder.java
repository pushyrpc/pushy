/*
 * Copyright (c) 2009, 2011 Andrew Wilkins <axwalk@gmail.com>
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

import pushy.modules.OsModule;
import pushy.modules.SubprocessModule;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that mimics
 * {@link java.lang.ProcessBuilder java.lang.ProcessBuilder}, but creates
 * processes on the remote host.
 */
public class RemoteProcessBuilder
{
    private Client  client;
    private List    command;
    private File    directory;
    private boolean redirectErrorStream;
    private Map     environment;

    public RemoteProcessBuilder(Client client, String[] command)
    {
        this(client, Arrays.asList(command));
    }

    public RemoteProcessBuilder(Client client, List command)
    {
        this.client = client;
        this.command = command;
    }

    public List command()
    {
        return command;
    }

    public RemoteProcessBuilder command(String[] command)
    {
        return command(Arrays.asList(command));
    }

    public RemoteProcessBuilder command(List command)
    {
        this.command = command;
        return this;
    }

    public File directory()
    {
        return directory;
    }

    public RemoteProcessBuilder directory(File directory)
    {
        this.directory = directory;
        return this;
    }

    public Map environment()
    {
        if (environment == null)
        {
            environment = new HashMap();
            environment.putAll(((OsModule)client.getModule("os")).environ);
        }
        return environment;
    }

    public boolean redirectErrorStream()
    {
        return redirectErrorStream;
    }

    public RemoteProcessBuilder redirectErrorStream(boolean redirect)
    {
        this.redirectErrorStream = redirect;
        return this;
    }

    public Process start()
    {
        SubprocessModule subprocess =
            (SubprocessModule)client.getModule("subprocess");
        String[] args = (String[])command.toArray(new String[]{});
        String cwd = (directory==null) ? null : directory.getAbsolutePath();
        return subprocess.exec(args, redirectErrorStream, environment, cwd);
    }
}

