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

import os, struct, sys, subprocess, StringIO, threading
import pushy.transport

if sys.platform != "win32":
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
        def __init__(self, hostname, username, password, domain):
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

    BasePopen = ImpacketPopen
else:
    from ctypes import *
    from ctypes.wintypes import *
    import msvcrt

    # Win32 API Constants
    LOGON32_LOGON_INTERACTIVE = 2
    LOGON32_PROVIDER_DEFAULT  = 0
    NameSamCompatible         = 2
    GENERIC_READ              = 0x80000000
    GENERIC_WRITE             = 0x40000000
    OPEN_EXISTING             = 3
    INVALID_HANDLE_VALUE      = 2**32-1

    # Win32 API Functions
    GetLastError = windll.kernel32.GetLastError
    GetLastError.restype = DWORD
    GetLastError.argtypes = []

    CreateFile = windll.kernel32.CreateFileA
    CreateFile.restype = HANDLE
    CreateFile.argtypes = [c_char_p, DWORD, DWORD, c_void_p, DWORD, DWORD,
                           HANDLE]

    WriteFile = windll.kernel32.WriteFile
    WriteFile.restype = BOOL
    WriteFile.argtypes = [HANDLE, c_void_p, DWORD, POINTER(DWORD), c_void_p]

    ReadFile = windll.kernel32.ReadFile
    ReadFile.restype = BOOL
    ReadFile.argtypes = [HANDLE, c_void_p, DWORD, POINTER(DWORD), c_void_p]

    CloseHandle = windll.kernel32.CloseHandle
    CloseHandle.restype = BOOL
    CloseHandle.argtypes = [HANDLE]

    LogonUser = windll.advapi32.LogonUserA
    LogonUser.restype = BOOL
    LogonUser.argtypes = [c_char_p, c_char_p, c_char_p, DWORD, DWORD,
                          POINTER(HANDLE)]

    ImpersonateLoggedOnUser = windll.advapi32.ImpersonateLoggedOnUser
    ImpersonateLoggedOnUser.restype = BOOL
    ImpersonateLoggedOnUser.argtypes = [HANDLE]

    RevertToSelf = windll.advapi32.RevertToSelf
    RevertToSelf.restype = BOOL
    RevertToSelf.argtypes = []

    GetUserNameExA = windll.secur32.GetUserNameExA
    GetUserNameExA.restype = BOOL
    GetUserNameExA.argtypes = [DWORD, c_char_p, POINTER(DWORD)]

    class Handle:
        "Class for closing underlying Win32 handle on deletion."
        def __init__(self, handle=HANDLE()):
            self.handle = handle
        def __del__(self):
            if self.handle and not CloseHandle(self.handle):
                raise Exception, "Failed to close handle"
            def __repr__(self):
                return "Handle(%d)" % int(self.handle.value)


    class Win32File(Handle):
        """
        Not sure why, but a simple open(pipe_path, "w+b") can't be shared for
        both stdin and stdout. Calling ReadFile/WriteFile directly, we can do
        this.
        """

        def __init__(self, handle):
            Handle.__init__(self, handle)
            self.__fd = msvcrt.open_osfhandle(self.handle, 0)

        def flush(self):
            pass

        def close(self):
            del self.handle

        def fileno(self):
            return self.__fd

        def write(self, data):
            while len(data) > 0:
                written = DWORD(0)
                if not WriteFile(self.handle, data, len(data),
                                 byref(written), 0):
                    raise IOError, GetLastError()
                data = data[written.value:]

        def read(self, size):
            buffer = create_string_buffer(size)
            total_nread = 0
            while total_nread < size:
                nread = DWORD(0)
                if not ReadFile(self.handle,
                                addressof(buffer)+total_nread,
                                size-total_nread, byref(nread), 0):
                    raise IOError, GetLastError()
                total_nread += nread.value
            return buffer.raw

        def readlines(self):
            data = ""
            p = self.read(8192)
            while len(p):
                data += p
                p = self.read(8192)
            return data.splitlines()


    def authenticate(username, password, domain):
        if domain is None:
            slash = username.find("\\")
            if slash != -1:
                domain = username[:slash]
                username = username[slash+1:]
        handle = Handle()
        if LogonUser(username, domain, password, LOGON32_LOGON_INTERACTIVE,
                     LOGON32_PROVIDER_DEFAULT, byref(handle.handle)):
            return handle
        return None

    def logon(token):
        """
        Impersonates a user, given a token returned from 'authenticate'. If the
        function succeeds, it will reutrn TRUE, else FALSE.
        """
        if token:
            return ImpersonateLoggedOnUser(token.handle)
        return False

    def get_current_username_domain():
        username = create_string_buffer(1024)
        namelen  = DWORD(1024)
        if GetUserNameExA(NameSamCompatible, username, byref(namelen)):
            username = tuple(username.value.split("\\"))
            if len(username) == 1:
                return (None, username[0])
            return username
        return None

    class NativePopen:
        def __init__(self, hostname, username, password, domain):
            # Determine current username/domain. If they're the same as the
            # one specified, don't bother authenticating.
            current_username = get_current_username_domain()
            already_logged = (current_username == (domain, username))

            if already_logged or \
               logon(authenticate(username, password, domain)):
                try:
                    handle = CreateFile(r"\\%s\pipe\pushy" % hostname,
                                        GENERIC_READ | GENERIC_WRITE,
                                        0, 0, OPEN_EXISTING, 0, 0)

                    if handle == INVALID_HANDLE_VALUE:
                        raise Exception, "Failed to open named pipe"
                    self.stdin = self.stdout = Win32File(handle)
                finally:
                    if not already_logged:
                        RevertToSelf()
            else:
                raise Exception, "Authentication failed"
    BasePopen = NativePopen

class Popen(pushy.transport.BaseTransport, BasePopen):
    def __init__(self, command, **kwargs):
        pushy.transport.BaseTransport.__init__(self)
        hostname = kwargs.get("address", None)
        username = kwargs.get("username", None)
        password = kwargs.get("password", None)
        domain   = kwargs.get("domain", "")
        BasePopen.__init__(self, hostname, username, password, domain)
        self.stderr = StringIO.StringIO()

        # Write program arguments.
        packed_args = struct.pack(">B", len(command))
        for arg in command:
            packed_args += struct.pack(">I", len(arg)) + arg

        self.stdin.write(packed_args)
        self.stdin.flush()

