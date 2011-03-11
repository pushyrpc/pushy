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

import os, sys

thisdir = os.path.dirname(__file__)
sys.path.append(os.path.join(thisdir, ".."))

import pushy, unittest

class TestAutoImport(unittest.TestCase):
    def setUp(self):
        self.conn = pushy.connect("local:")
    def tearDown(self):
        self.conn.close()

    def test_platform(self):
        import platform
        self.assertEquals(platform.system(),
                          self.conn.modules.platform.system())

    def test_submodule(self):
        # By design, if you manually import a package, its modules will not be
        # automatically imported.
        manual = self.conn.remote_import("pushy")
        self.assertFalse(hasattr(manual, "transport"))

        # Ensure we can import modules within packages easily.
        manual = self.conn.remote_import("pushy.transport")
        # This would previously have failed.
        auto = self.conn.modules.pushy.transport
        self.assert_(manual is auto)

if __name__ == "__main__":
    unittest.main()

