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
"""

import os, paramiko, sys
import pushy.transport

__all__ = ["ParamikoPopen", "NativePopen", "Popen"]

is_windows = (sys.platform == "win32")

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
    """
    An SSH transport for Pushy, which uses Paramiko.
    """

    def __init__(self, command, address, **kwargs):
        """
        @param kwargs: Any parameter that can be passed to
                       U{paramiko.SSHClient.connect<http://www.lag.net/paramiko/docs/paramiko.SSHClient-class.html#connect>}.
                         - port
                         - username
                         - password
                         - pkey
                         - key_filename
                         - timeout
                         - allow_agent
                         - look_for_keys
        """

        pushy.transport.BaseTransport.__init__(self, address)
        self.__client = SafeSSHClient()

        connect_args = {"hostname": address}
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

    def __del__(self):
        self.close()

    def close(self):
        self.stdin.close()
        self.stdout.close()
        self.stderr.close()
        self.__client.close()


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
    if is_windows:
        ssh_program = "plink.exe"

    for path in paths:
        path = os.path.join(path, ssh_program)
        if os.path.exists(path):
            native_ssh = path
            break

if native_ssh is not None:
    import subprocess

    class NativePopen(pushy.transport.BaseTransport):
        """
        An SSH transport for Pushy, which uses the native ssh or plink program.
        """
        def __init__(self, command, address, username=None, password=None):
            """
            @param address: The hostname/address to connect to.
            @param username: The username to connect with.
            """

            pushy.transport.BaseTransport.__init__(self, address)

            if username is not None:
                address = username + "@" + address

            command = ['"%s"' % word for word in command]
            args = [native_ssh]

            # Plink allows specification of password on the command-line. Not
            # secure, as other processes may see others' command-line
            # arguments. "use_native=False" should be specified if no password
            # specified, and security is of great importance.
            if password is not None:
                assert ssh_program == "plink.exe"
                args.extend(["-pw", password])

            args.extend([address, " ".join(command)])
            self.__proc = subprocess.Popen(args, stdin=subprocess.PIPE, 
                                           stdout=subprocess.PIPE,
                                           stderr=subprocess.PIPE)
            self.stdout = self.__proc.stdout
            self.stdin  = self.__proc.stdin
            self.stderr = self.__proc.stderr

        def __del__(self):
            self.close()

        def close(self):
            self.stdin.close()
            self.stdout.close()
            self.stderr.close()
            try:
                import os, signal
                os.kill(self.__proc.pid, signal.SIGTERM)
            except: pass
            self.__proc.wait()

###############################################################################
# Define Popen.

if native_ssh is not None:
    def Popen(command, use_native=None, password=None, *args, **kwargs):
        """
        Selects between the native and Paramiko SSH transports, opting to use
        the native program where possible.

        @type  use_native: bool
        @param use_native: If set to False, then the native implementation will
                           never be used, regardless of whether a password is
                           specified.
        @type  password: string
        @param password: The password to use for authentication. If a password
                         is not specified, then native ssh/plink program is
                         used, except if L{use_native} is False.
        """

        # Determine default value for "use_native", based on other parameters.
        if use_native is None:
            # On Windows, PuTTy is actually slower than Paramiko!
            use_native = ((not is_windows) and (password is None))

        if use_native:
            return NativePopen(command, *args, **kwargs)
        return ParamikoPopen(command, password=password, *args, **kwargs)
else:
    Popen = ParamikoPopen

