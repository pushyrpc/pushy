# Copyright (c) 2008, 2011 Andrew Wilkins <axwalk@gmail.com>
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
I{Pushy} is a Python package for simple, Pythonic remote procedure calls.

Pushy allows one to connect two Python interpreters together, and provide
access to their modules, objects and functions to each other. What sets Pushy
apart from other such frameworks is its ability to operate without any custom
services running to achieve this. 

Pushy comes packaged with several different transports:
 - L{SSH<pushy.transport.ssh>} (Secure Shell), via
   U{Paramiko<http://www.lag.net/paramiko/>}, or the standard ssh/plink
   programs.
 - L{SMB<pushy.transport.smb>} (Windows Named Pipes), via Impacket, or
   (on Windows) native Windows named-pipe operations.
 - L{Local<pushy.transport.local>}, for connecting to a new Python interpreter
   on the local machine.
 - L{Daemon<pushy.transport.daemon>}, for connecting to a "nailed-up"
   socket-based service.

By using the SSH transport (originally the only transport available), one can
create and connect to a new Python interpreter on a remote machine, with only
Python installed and an SSH daemon running. Pushy does not need to be installed
on the remote machine; the necessary code is I{pushed} to it - hence the name.
Moreover, Pushy gains the the encryption and authentication provided by SSH for
free. There's no need to worry about your RPC service being compromised, if you
run it with reduced privileges.

A Pushy connection can be initiated by invoking
L{pushy.connect<pushy.connect>}. This function will return a connection object,
which provides access to the remote interpreter's modules, whose classes,
objects and functions can be transparently accessed from the local interpreter.

@version: 0.4
@author: Andrew Wilkins
@contact: axwalk@gmail.com
@license: MIT
"""

transports = {"ssh": None, "local": None, "daemon": None, "smb": None}

from client import connect
from server import serve_forever

