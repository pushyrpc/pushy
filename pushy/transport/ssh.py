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

import os, sys
import pushy.transport
import pushy.util.askpass

__all__ = ["ParamikoPopen", "NativePopen", "Popen"]

is_windows = (sys.platform == "win32")

try:
    import paramiko
except ImportError:
    paramiko = None

if paramiko:
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
            for name in ("port", "username", "password", "pkey",
                         "key_filename", "timeout", "allow_agent",
                         "look_for_keys"):
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
# Native SSH. Truly secure only when a password is not specified.

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

native_scp = os.environ.get("PUSHY_NATIVE_SCP", None)
if native_scp is not None:
    if not os.path.exists(native_scp):
        import warnings
        warnings.warn(
            "Ignoring non-existant PUSHY_NATIVE_SCP (%r)" % native_scp)
        native_scp = None

if native_scp is None:
    paths = []
    if "PATH" in os.environ:
        paths = os.environ["PATH"].split(os.pathsep)

    scp_program = "scp"
    if is_windows:
        scp_program = "pscp.exe"

    for path in paths:
        path = os.path.join(path, scp_program)
        if os.path.exists(path):
            native_scp = path
            break

if native_ssh is not None:
    import subprocess

    class NativePopen(pushy.transport.BaseTransport):
        """
        An SSH transport for Pushy, which uses the native ssh or plink program.
        """
        def __init__(self, command, address, username=None, password=None,
                     port=None, **kwargs):
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
            if password is not None and ssh_program == "plink.exe":
                args.extend(["-pw", password])

            # If a port other than 22 is specified, add it to the arguments.
            # Plink expects "-P", while ssh expects "-p".
            self.__port = 22
            if port is not None:
                port = int(port)
                if port != 22:
                    self.__port = port
                    if ssh_program == "plink.exe":
                        args.extend(["-P", str(port)])
                    else:
                        args.extend(["-p", str(port)])

            # Add the address and command to the arguments.
            args.extend([address, " ".join(command)])

            if is_windows or password is None:
                self.__proc = subprocess.Popen(args, stdin=subprocess.PIPE, 
                                               stdout=subprocess.PIPE,
                                               stderr=subprocess.PIPE)
            else:
                self.__proc = pushy.util.askpass.Popen(args, password,
                                                       stdin=subprocess.PIPE, 
                                                       stdout=subprocess.PIPE,
                                                       stderr=subprocess.PIPE)
            self.stdout = self.__proc.stdout
            self.stdin  = self.__proc.stdin
            self.stderr = self.__proc.stderr

            # If we have native SCP, store the address and password for later
            # use.
            if native_scp:
                self.__address = address
                self.__password = password
                self.putfile = self._putfile
                self.getfile = self._getfile

        def _putfile(self, src, dest):
            "Copy local file 'src' to remote file 'dest'"
            return self.scp(src, self.__address + ":" + dest)

        def _getfile(self, src, dest):
            "Copy remote file 'src' to local file 'dest'"
            return self.scp(self.__address + ":" + src, dest)

        def scp(self, src, dest):
            args = [native_scp]
            if self.__password is not None and scp_program == "pscp.exe":
                args.extends(["-pw", self.__password])
            if self.__port != 22:
                # Both pscp and scp expect "-P" (hooray for consistency!)
                args.extend(["-P", str(self.__port)])
            args.extend((src, dest))
            if is_windows or self.__password is None:
                proc = subprocess.Popen(args, stdin=subprocess.PIPE)
            else:
                proc = pushy.util.askpass.Popen(args, self.__password,
                                                stdin=subprocess.PIPE)
            proc.stdin.close()
            return proc.wait()

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
        @type  port: int
        @param port: The port of the SSH daemon to connect to. If not
                     specified, the default port of 22 will be used.
        """

        # Determine default value for "use_native", based on other parameters.
        if use_native is None:
            # On Windows, PuTTy is actually slower than Paramiko!
            use_native = \
                (not paramiko) or ((not is_windows) and (password is None))
        if use_native:
            kwargs["password"] = password
            return NativePopen(command, *args, **kwargs)
        assert paramiko is not None
        return ParamikoPopen(command, password=password, *args, **kwargs)
elif paramiko:
    Popen = ParamikoPopen
else:
    import warnings
    warnings.warn("No paramiko or native ssh transport")

