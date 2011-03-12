# Copyright (c) 2011 Andrew Wilkins <axwalk@gmail.com>
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
sys.path.append(os.path.join(thisdir, ".."))

import pushy, unittest

class TestCompile(unittest.TestCase):
    def setUp(self):
        self.conn = pushy.connect("local:")
    def tearDown(self):
        self.conn.close()

    def test_lambda(self):
        osgetpid = lambda: os.getpid()
        remote_lambda = self.conn.compile(osgetpid)
        self.assertEquals(os.getpid(), osgetpid())
        self.assertEquals(self.conn.modules.os.getpid(), remote_lambda())

    def test_function(self):
        def function_with_defaults(a, b=2):
            return a + b
        remote_function = self.conn.compile(function_with_defaults)
        self.assertEquals(3, remote_function(1, 2))
        self.assertEquals(3, remote_function(1))
        self.assertEquals(3, remote_function(a=1, b=2))

    def test_compile_eval(self):
        code = self.conn.compile("lambda a,b: a+b", "eval")
        lambda_ = self.conn.eval(code)
        self.assertEquals(3, lambda_(1, 2))

    def test_compile_exec(self):
        code = self.conn.compile("def abc(a, b):\n\treturn a+b", "exec")
        locals_ = {}
        self.conn.eval(code, locals=locals_)
        self.assertTrue("abc" in locals_)
        self.assertEquals(3, locals_["abc"](1, 2))

if __name__ == "__main__":
    unittest.main()

