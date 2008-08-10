# Copyright (c) 2008 Andrew Wilkins <axwalk@gmail.com>
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

import paramiko

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

class Popen:
    def __init__(self, command, **kwargs):
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

"""
else:
    import popen2

    class SSHPopen:
        def __init__(self, command, hostname, *args, **kwargs):
            if kwargs.has_key("username"):
                hostname = kwargs["username"] + "@" + hostname
            command = 'ssh "%s" "%s"' % (hostname, command)
            self.stdout, self.stdin = popen2.popen2(command)
"""

