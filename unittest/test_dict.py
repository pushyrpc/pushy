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

import logging, os, sys

thisdir = os.path.dirname(__file__)
sys.path.insert(0, os.path.join(thisdir, ".."))

import pushy, unittest

class TestDict(unittest.TestCase):
    def setUp(self):
        self.conn = pushy.connect("local:")

    def test_setitem(self):
        "Ensure we can set items in a remote dictionary."
        dict_type = self.conn.modules.types.DictType
        d = dict_type()
        d["x"] = "y"
        self.assertEqual("y", d["x"])

    def test_init_kwargs(self):
        "Ensure we can instantiate a dictionary object with keyword arguments."
        dict_type = self.conn.modules.types.DictType
        d = dict_type(x="y")
        self.assertEqual("y", d["x"])
        self.assertEqual(dict(x="y"), d)

    def test_update(self):
        """
        Ensure we can update a remote dictionary object with either another
        remote dictionary, or a local dictionary.
        """
        dict_type = self.conn.modules.types.DictType
        d = dict_type(x="y")
        d.update({"y":"z"})
        self.assertEqual({"x": "y", "y": "z"}, d)

        d2 = dict_type(a="b")
        d.update(d2)
        self.assertEqual({"x": "y", "y": "z", "a": "b"}, d)
        
    def test_delitem(self):
        "Ensure we can remove items from a remote dictionary object."
        dict_type = self.conn.modules.types.DictType
        d = dict_type(x="y")
        del d["x"]
        self.assertEqual({}, d)

        remote_list = self.conn.modules.types.ListType()
        self.assertNotEquals(list, type(remote_list))
        self.assertTrue(isinstance(remote_list, list))
        local_dict = {"x": "y"}
        remote_list.append(local_dict)
        self.assertEqual([local_dict], remote_list)
        #del local_dict["x"]
        #self.assertEqual([{}], remote_list)

    def test_objects(self):
        """
        Ensure we can store non-primitive objects in a remote dictionary, and
        methods are proxied as expected.
        """

        class custom_object(object):
            pass

        dict_type = self.conn.modules.types.DictType
        key, value = custom_object(), custom_object()
        d = dict_type({key: value})
        d.keys()[0].x = 1
        d[key].y = 2
        self.assertEqual(1, key.x)
        self.assertEqual(2, value.y)

if __name__ == "__main__":
    #import logging, pushy.util
    #pushy.util.logger.addHandler(logging.StreamHandler())
    #pushy.util.logger.setLevel(logging.DEBUG)
    unittest.main()

