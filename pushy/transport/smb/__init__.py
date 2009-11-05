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

__all__ = ["Popen"]

import struct, sys, pushy.transport, StringIO

# On Windows, prefer the native interface.
if sys.platform == "win32":
    try:
        import native
        BasePopen = native.NativePopen
    except ImportError:
        BasePopen = None

# If we're not on Windows, or the native interface is unavailable, use the
# Impacket library instead.
if BasePopen is None:
    import impacket_transport
    BasePopen = impacket_transport.ImpacketPopen

# Common class which inherits from either the native win32 or Impacket class.
class Popen(pushy.transport.BaseTransport, BasePopen):
    def __init__(self, command, address, username=None, password=None,
                 domain="", **kwargs):
        """
        @param username: The username to authenticate with.
        @param password: The password to authenticate with.
        @param domain: The domain to authenticate with.
        """

        # If no domain is specified, and the domain is specified in the
        # username, split it out.
        if not domain and username and "\\" in username:
            domain, username = username.split("\\")

        pushy.transport.BaseTransport.__init__(self, address)
        BasePopen.__init__(self, address, username, password, domain)
        self.stderr = StringIO.StringIO()

        # Write program arguments.
        packed_args = struct.pack(">B", len(command))
        for arg in command:
            packed_args += struct.pack(">I", len(arg)) + arg

        self.stdin.write(packed_args)
        self.stdin.flush()

