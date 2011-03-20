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
sys.path.insert(0, os.path.join(thisdir, ".."))

import pushy, unittest

OLD_STYLE = """\
class X:
    def __init__(self):
        self.x = 123
"""

NEW_STYLE = """\
class X(object):
    def __init__(self):
        self.x = 123
"""

class TestClasses(unittest.TestCase):
    def setUp(self):
        self.conn = pushy.connect("local:")
    def tearDown(self):
        self.conn.close()

    def test_newstyle(self):
        locals_ = {}
        self.conn.execute(NEW_STYLE, locals=locals_)
        x = locals_["X"]()
        self.assertEquals(123, x.x)

    def test_oldstyle(self):
        locals_ = {}
        self.conn.execute(OLD_STYLE, locals=locals_)
        x = locals_["X"]()
        self.assertEquals(123, x.x)
        

if __name__ == "__main__":
    #import logging, pushy.util
    #pushy.util.logger.addHandler(logging.StreamHandler())
    #pushy.util.logger.setLevel(logging.DEBUG)
    unittest.main()

