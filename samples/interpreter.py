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

import code, common, os, sys

class RemoteConsole(code.InteractiveConsole):
    def __init__(self, connection):
        code.InteractiveConsole.__init__(self)
        self.connection = connection
        self.compile = connection.modules.codeop.CommandCompiler()

    def runcode(self, co):
        try:
            self.connection.eval(co)
        except SystemExit:
            raise
        except:
            self.showtraceback()
        else:
            if code.softspace(sys.stdout, 0):
                print

if __name__ == "__main__":
    """
    Sample session:

    python interpreter.py local:
      Connected to: 
      Client PID is: 32486
      Peer PID is: 32487
      Python 2.6.2 (release26-maint, Apr 19 2009, 01:56:41) 
      [GCC 4.3.3] on linux2
      Type "help", "copyright", "credits" or "license" for more information.
      (RemoteConsole)
      >>> open("test.dat", "w").write(str(os.getpid()))
      >>> 

      andrew@sun$ cat test.dat 
      32487
    """

    connection = common.get_connection()
    print "Connected to:", connection.server.address
    print "Client PID is:", os.getpid()
    print "Peer PID is:", connection.modules.os.getpid()

    # XXX Note that executing commands in the remote interpreter that result
    #     in it writing to stdout/stderr may hang the process. This is due to
    #     an outstanding issue with Pushy, where the stdout/err redirectors
    #     actively attempt to write back to the client.
    RemoteConsole(connection).interact()

