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

import os, sys

thisdir = os.path.dirname(__file__)
sys.path.insert(0, os.path.join(thisdir, ".."))

import pushy, unittest

class TestTunnel(unittest.TestCase):
    def setUp(self):
        self.conn = pushy.connect("local:")
    def tearDown(self):
        self.conn.close()

    def test_tunnel_connect(self):
        """
        Make sure we can create connections within connections, transparently
        tunnelling to the secondary conncection's interpreter.
        """

        remote_pushy = self.conn.modules.pushy
        remote_conn = remote_pushy.connect("local:")
        try:
            assert os.getpid() != self.conn.modules.os.getpid()
            assert self.conn.modules.os.getpid() != \
                   remote_conn.modules.os.getpid()
        finally:
            remote_conn.close()

if __name__ == "__main__":
    unittest.main()

