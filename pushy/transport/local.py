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

import os, shutil, subprocess, sys

import pushy.transport

class Popen(pushy.transport.BaseTransport):
    def __init__(self, command, address, **kwargs):
        pushy.transport.BaseTransport.__init__(self, address)

        if not sys.platform.startswith("java"):
            command[0] = sys.executable

        self.__proc = subprocess.Popen(command, stdin=subprocess.PIPE,
                                       stdout=subprocess.PIPE,
                                       stderr=subprocess.PIPE,
                                       bufsize=65535)

        self.stdout = self.__proc.stdout
        self.stderr = self.__proc.stderr
        self.stdin  = self.__proc.stdin

    def getfile(self, src, dest):
        shutil.copyfile(src, dest)

    def putfile(self, src, dest):
        shutil.copyfile(src, dest)

    def __del__(self):
        self.close()

    def close(self):
        self.stdin.close()
        self.__proc.wait()

