# Copyright (c) 2008, 2009 Andrew Wilkins <axwalk@gmail.com>
# 
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation
# files (the "Software"), to deal in the Software without
# restriction, including without limitation the rights to use,
# copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following
# conditions:
# 
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
# OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
# HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
# WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
# OTHER DEALINGS IN THE SOFTWARE.

"""
Popen-like interface for executing commands on a remote host via SSH.

Author: Andrew Wilkins <axwalk@gmail.com>
"""

import os, paramiko, sys
import pushy.transport

class SafeSSHClient(paramiko.SSHClient):
    """
    Subclass of paramiko.SSHClient, which makes its Transport a daemon
    thread.

    This is required to stop paramiko from hanging Python when the program
    is finished.
    """

    def __init__(self):
        paramiko.SSHClient.__init__(self)
        self.load_system_host_keys()
        self.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    def __setattr__(self, name, value):
        if name == "_transport" and value:
            value.setDaemon(True)
        self.__dict__[name] = value

class ParamikoPopen(pushy.transport.BaseTransport):
    def __init__(self, command, **kwargs):
        pushy.transport.BaseTransport.__init__(self)
        self.__client = SafeSSHClient()

        connect_args = {"hostname": kwargs["address"]}
        for name in ("port", "username", "password", "pkey", "key_filename",
                     "timeout", "allow_agent", "look_for_keys"):
            if name in kwargs:
                connect_args[name] = kwargs[name]
        self.__client.connect(**connect_args)

        # Join arguments into a string
        args = command
        for i in range(len(args)):
            if " " in args[i]:
                args[i] = "'%s'" % args[i]
        command = " ".join(args)

        stdin, stdout, stderr = self.__client.exec_command(command)
        self.stdin  = stdin
        self.stdout = stdout
        self.stderr = stderr
        self.fs = self.__client.open_sftp()

###############################################################################
# Native SSH. This is used when no password is specified.

native_ssh = os.environ.get("PUSHY_NATIVE_SSH", None)
if native_ssh is not None:
    if not os.path.exists(native_ssh):
        import warnings
        warnings.warn(
            "Ignoring non-existant PUSHY_NATIVE_SSH (%r)" % native_ssh)
        native_ssh = None

if native_ssh is None:
    paths = []
    if "PATH" in os.environ:
        paths = os.environ["PATH"].split(os.pathsep)

    ssh_program = "ssh"
    if sys.platform == "win32":
        ssh_program = "plink.exe"

    for path in paths:
        path = os.path.join(path, ssh_program)
        if os.path.exists(path):
            native_ssh = path
            break

if native_ssh is not None:
    import subprocess

    class NativePopen(pushy.transport.BaseTransport):
        def __init__(self, command, *args, **kwargs):
            pushy.transport.BaseTransport.__init__(self)

            hostname = kwargs["address"]
            if kwargs.has_key("username"):
                hostname = kwargs["username"] + "@" + hostname

            command = ['"%s"' % word for word in command]
            args = [native_ssh, hostname, " ".join(command)]
            self.__proc = subprocess.Popen(args, stdin=subprocess.PIPE, 
                                           stdout=subprocess.PIPE,
                                           stderr=subprocess.PIPE)
            self.stdout = self.__proc.stdout
            self.stdin  = self.__proc.stdin
            self.stderr = self.__proc.stderr

        def __del__(self):
            self.stdin.close()
            self.stdout.close()
            self.stderr.close()
            try:
                import os, signal
                os.kill(self.__proc.pid, signal.SIGTERM)
            except: pass
            self.__proc.wait()

        def close(self):
            self.stdin.close()

###############################################################################
# Define Popen.

if native_ssh is not None:
    def Popen(command, *args, **kwargs):
        if kwargs.get("use_native", True) and "password" not in kwargs:
            return NativePopen(command, *args, **kwargs)
        return ParamikoPopen(command, *args, **kwargs)
else:
    Popen = ParamikoPopen

