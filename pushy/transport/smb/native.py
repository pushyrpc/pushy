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

__all__ = ["NativePopen"]

import os, struct, sys, subprocess, StringIO, threading
import pushy.transport
from ctypes import *
from ctypes.wintypes import *
import msvcrt

# Win32 API Constants
LOGON32_LOGON_INTERACTIVE    = 2
LOGON32_PROVIDER_DEFAULT     = 0
NameSamCompatible            = 2
GENERIC_READ                 = 0x80000000
GENERIC_WRITE                = 0x40000000
OPEN_EXISTING                = 3
INVALID_HANDLE_VALUE         = 2**32-1
FILE_FLAG_NO_BUFFERING       = 0x20000000
FILE_FLAG_WRITE_THROUGH      = 0x80000000
FILE_FLAG_OVERLAPPED         = 0x40000000
ERROR_IO_PENDING             = 997
SC_MANAGER_CONNECT           = 0x0001
SC_MANAGER_CREATE_SERVICE    = 0x0002
SC_MANAGER_ENUMERATE_SERVICE = 0x0004
SERVICE_WIN32                = 0x00000030
SERVICE_STATE_ALL            = 0x00000003
ERROR_MORE_DATA              = 234


# Structures
class overlapped_internal_struct(Structure):
    _fields_ = [("Offset", DWORD), ("OffsetHigh", DWORD)]
class overlapped_internal_union(Union):
    _fields_ = [("struct", overlapped_internal_struct),
                ("Pointer", c_void_p)]
class OVERLAPPED(Structure):
    _fields_ = [("Internal", c_void_p), ("InternalHigh", c_void_p),
                ("union", overlapped_internal_union), ("hEvent", HANDLE)]

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
WriteFile.argtypes = [
    HANDLE, c_void_p, DWORD, POINTER(DWORD), POINTER(OVERLAPPED)]

ReadFile = windll.kernel32.ReadFile
ReadFile.restype = BOOL
ReadFile.argtypes = [
    HANDLE, c_void_p, DWORD, POINTER(DWORD), POINTER(OVERLAPPED)]

CreateEvent = windll.kernel32.CreateEventA
CreateEvent.restype = HANDLE
CreateEvent.argtypes = [c_void_p, BOOL, BOOL, c_char_p]

GetOverlappedResult = windll.kernel32.GetOverlappedResult
GetOverlappedResult.restype = BOOL
GetOverlappedResult.argtypes = [
    HANDLE, POINTER(OVERLAPPED), POINTER(DWORD), BOOL]

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

FlushFileBuffers = windll.kernel32.FlushFileBuffers
FlushFileBuffers.restype = BOOL
FlushFileBuffers.argtypes = [HANDLE]

OpenSCManager = windll.advapi32.OpenSCManagerA
OpenSCManager.restype = HANDLE
OpenSCManager.argtypes = [c_char_p, c_char_p, DWORD]

CloseServiceHandle = windll.advapi32.CloseServiceHandle
CloseServiceHandle.restype = BOOL
CloseServiceHandle.argtypes = [HANDLE]

EnumServicesStatusEx = windll.advapi32.EnumServicesStatusExA
EnumServicesStatusEx.restype = BOOL
EnumServicesStatusEx.argtypes = [
    HANDLE, DWORD, DWORD, DWORD, c_char_p, DWORD, POINTER(DWORD),
    POINTER(DWORD), POINTER(DWORD), c_char_p]


class Handle:
    "Class for closing underlying Win32 handle on deletion."
    def __init__(self, handle=HANDLE()):
        self.handle = handle
    def __del__(self):
        self.close()
    def close(self):
        if self.handle:
            value = CloseHandle(self.handle)
            if not value:
                raise Exception, "Failed to close handle"
        self.handle = None


class Win32File(Handle):
    """
    Not sure why, but a simple open(pipe_path, "w+b") can't be shared for
    both stdin and stdout. Calling ReadFile/WriteFile directly, we can do
    this.

    We must use overlapped I/O, since a read and write may occur on the
    pipe at the same time. Without using overlapped I/O, one caller would
    be blocked by the other.
    """

    def __init__(self, handle):
        Handle.__init__(self, HANDLE(handle))
        self.__fd = msvcrt.open_osfhandle(self.handle.value, 0)

    def close(self):
        Handle.close(self)

    def flush(self):
        FlushFileBuffers(self.handle)

    def fileno(self):
        return self.__fd

    def write(self, data):
        buffer = create_string_buffer(data)
        total_written = 0
        while total_written < len(data):
            written = DWORD(0)
            overlapped = OVERLAPPED()
            overlapped.hEvent = CreateEvent(None, True, False, None)
            try:
                # Cross-network pipes are limited to 65535 bytes at a time.
                n = len(data) - total_written
                if n > 65535:
                    n = 65535

                if not WriteFile(self.handle,
                                 addressof(buffer)+total_written, n,
                                 byref(written), byref(overlapped)):
                    error = GetLastError()
                    if error != ERROR_IO_PENDING:
                        raise IOError, error
                    if not GetOverlappedResult(self.handle,
                                               byref(overlapped),
                                               byref(written), True):
                        raise IOError, GetLastError()
            finally:
                CloseHandle(overlapped.hEvent)
            total_written += written.value

    def read(self, size):
        buffer = create_string_buffer(size)
        total_nread = 0
        while total_nread < size:
            overlapped = OVERLAPPED()
            overlapped.hEvent = CreateEvent(None, True, False, None)
            try:
                nread = DWORD(0)
                if not ReadFile(self.handle,
                                addressof(buffer)+total_nread,
                                size-total_nread, byref(nread),
                                byref(overlapped)):
                    error = GetLastError()
                    if error != ERROR_IO_PENDING:
                        raise IOError, error
                    if not GetOverlappedResult(self.handle,
                                               byref(overlapped),
                                               byref(nread), True):
                        raise IOError, GetLastError()
                total_nread += nread.value
            finally:
                CloseHandle(overlapped.hEvent)
        return buffer.raw[:total_nread]

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

def install_service(hostname):
    access = SC_MANAGER_CONNECT | SC_MANAGER_ENUMERATE_SERVICE
    handle = OpenSCManager(hostname, None, access)
    if handle == 0:
        raise IOError, GetLastError()
    try:
        bufsize = 2**16-1
        buf = create_string_buffer(bufsize)
        bytesneeded = DWORD(0)
        servicecount = DWORD(0)
        resumehandle = DWORD(0)
        while not EnumServicesStatusEx(handle, 0, SERVICE_WIN32,
                                       SERVICE_STATE_ALL, buf, bufsize,
                                       byref(bytesneeded), byref(servicecount),
                                       byref(resumehandle), None):
            if GetLastError() != ERROR_MORE_DATA:
                raise IOError, GetLastError()
            print "Enum succeeded", servicecount, resumehandle, error
        print servicecount
    finally:
        CloseServiceHandle(handle)

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
    """
    SMB transport implementation which uses the Windows native API for
    connecting to a remote named pipe.
    """

    def __init__(self, hostname, username, password, domain):
        """
        @param hostname: The hostname of the target machine.
        @param username: The username to authenticate with.
        @param password: The password to authenticate with.
        @param domain: The domain to authenticate with.
        """

        # Determine current username/domain. If they're the same as the
        # one specified, don't bother authenticating.
        current_username = get_current_username_domain()
        if domain == "":
            import platform
            already_logged = \
                (current_username == (platform.node(), username))
        else:
            already_logged = (current_username == (domain, username))

        # Log on, if we're not already logged on.
        if already_logged or \
           logon(authenticate(username, password, domain)):
            try:
                # Install the pushy service.
                install_service(hostname)

                # Connect to the pushy daemon's named pipe.
                flags = FILE_FLAG_NO_BUFFERING | FILE_FLAG_WRITE_THROUGH |\
                        FILE_FLAG_OVERLAPPED
                handle = \
                    CreateFile(r"\\%s\pipe\pushy" % hostname,
                               GENERIC_READ | GENERIC_WRITE,
                               0, 0, OPEN_EXISTING, flags, 0)
                if handle == INVALID_HANDLE_VALUE:
                    raise Exception, "Failed to open named pipe"
                self.stdin = self.stdout = Win32File(handle)
            finally:
                if not already_logged:
                    RevertToSelf()
        else:
            raise Exception, "Authentication failed"

