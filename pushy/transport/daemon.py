# Copyright (c) 2009, 2011 Andrew Wilkins <axwalk@gmail.com>
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

import os, socket, StringIO

import pushy.transport
import pushy.server

DEFAULT_PORT = pushy.server.DEFAULT_PORT

class WrappedSocketFile(object):
    def __init__(self, file_, socket_, how):
        self.__file = file_
        self.__socket = socket_
        self.__how = how
    def close(self):
        try:
            self.__socket.shutdown(self.__how)
        except: pass
        return self.__file.close()
    def __getattr__(self, name):
        return getattr(self.__file, name)

class Popen(pushy.transport.BaseTransport):
    def __init__(self, command, address, port=DEFAULT_PORT, **kwargs):
        pushy.transport.BaseTransport.__init__(self, address, daemon=True)
        self.__socket = socket.socket()
        self.__socket.connect((address, port))
        self.stdin  = WrappedSocketFile(self.__socket.makefile("wb"),
                                        self.__socket, socket.SHUT_WR)
        self.stdout = WrappedSocketFile(self.__socket.makefile("rb"),
                                        self.__socket, socket.SHUT_RD)
        self.stderr = StringIO.StringIO()
        self.stdin._close = True

    def close(self):
        self.__socket.close()

