# Copyright (c) 2009 Andrew Wilkins <axwalk@gmail.com>
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

__all__ = ["ImpacketPopen"]

import impacket.smb

class PushySMB(impacket.smb.SMB):
    def __init__(self, *args, **kwargs):
        impacket.smb.SMB.__init__(self, *args, **kwargs)

class SMBFile:
    def __init__(self, server, tid, fid):
        self.__lock = threading.Lock()
        self.__server = server
        self.__tid = tid
        self.__fid = fid

    def __del__(self):
        self.close()

    def flush(self):
        pass

    def close(self):
        if self.__server is not None:
            self.__lock.acquire()
            try:
                if self.__server is not None:
                    self.__server.close(self.__tid, self.__fid)
                    self.__server = None
            finally:
                self.__lock.release()

    def write(self, data):
        self.__lock.acquire()
        try:
            self.__server.write_raw(self.__tid, self.__fid, data)
        finally:
            self.__lock.release()

    def read(self, size):
        self.__lock.acquire()
        try:
            data = self.__server.read(self.__tid, self.__fid, 0, size)
            if data is None:
                data = ""
            return data
        finally:
            self.__lock.release()

    def readlines(self):
        data = ""
        p = self.read(8192)
        while len(p):
            data += p
            p = self.read(8192)
        return data.splitlines()

class ImpacketPopen:
    """
    SMB transport implementation which uses Impacket for connecting to a
    remote named pipe.
    """

    def __init__(self, hostname, username, password, domain):
        """
        @param hostname: The hostname of the target machine.
        @param username: The username to authenticate with.
        @param password: The password to authenticate with.
        @param domain: The domain to authenticate with.
        """

        # Create SMB connection, authenticate user.
        # TODO make port configurable
        server = PushySMB("*SMBSERVER", hostname)
        server.login(username, password, domain)

        # Connect to IPC$ share.
        tid_ipc = server.tree_connect_andx(r"\\*SMBSERVER\IPC$")

        # Open Pushy service's named pipes.
        fid = server.open(tid_ipc, r"\pipe\pushy",
                          impacket.smb.SMB_O_OPEN,
                          impacket.smb.SMB_ACCESS_READWRITE)[0]

        # Create file-like objects for stdin/stdout/stderr.
        self.stdin  = SMBFile(server, tid_ipc, fid)
        self.stdout = self.stdin
        self.server = server

    def close(self):
        self.stdin.close()
        self.stdout.close()

