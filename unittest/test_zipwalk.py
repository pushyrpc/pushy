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

import pushy.util
import unittest
import zipfile

def sort_list(o):
    if type(o) is list:
        return sorted(o)
    return o

class TestZipwalk(unittest.TestCase):
    def test_walk(self):
        zf = zipfile.ZipFile(os.path.join(thisdir, "data", "test_zipwalk.zip"))
        zip_items = \
            sort_list([map(sort_list,item) for item in pushy.util.zipwalk(zf)])

        dir_ = os.path.join(thisdir, "data", "test_zipwalk")
        dir_items = sort_list([map(sort_list, item) for item in os.walk(dir_)])
        self.assertEqual(len(zip_items), len(dir_items))

        for i in range(len(zip_items)):
            zip_item, dir_item = zip_items[i], dir_items[i]
            if zip_item[0] == None:
                self.assertEqual(dir_item[0], dir_)
            else:
                self.assertEqual(zip_item[0], dir_item[0][len(dir_)+1:])
            self.assertEqual(zip_item[1:], dir_item[1:])

if __name__ == "__main__":
    unittest.main()

