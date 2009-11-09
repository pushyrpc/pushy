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

import os, sys, threading
import pushy.util

class OutputRedirector(threading.Thread):
    """
    A class for reading data from a file and passing it to the "write" method
    of an object.
    """

    def __init__(self, fileno, bufsize = 8192):
        threading.Thread.__init__(self)
        self.setDaemon(True)
        self.__fileno = fileno
        self.__bufsize = bufsize

    def getfile(self):
        raise "Not implemented"

    def run(self):
        try:
            pushy.util.logger.debug("Entered output redirector")
            try:
                data = os.read(self.__fileno, self.__bufsize)
                while len(data) > 0:
                    self.getfile().write(data)
                    data = os.read(self.__fileno, self.__bufsize)
            finally:
                self.getfile().flush()
        except:
            pushy.util.logger.debug("Leaving output redirector")
            pass

class StdoutRedirector(OutputRedirector):
    def getfile(self):
        return sys.stdout

class StderrRedirector(OutputRedirector):
    def getfile(self):
        return sys.stderr

