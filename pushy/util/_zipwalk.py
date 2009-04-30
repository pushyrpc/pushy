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

import os

def zipwalk(zf, subdir=None):
    """
    An os.walk-like function for iterating through the hierarchy of a zip
    archive.
    """

    root = [{}, []]

    for name in zf.namelist():
        is_dir = name.endswith("/")
        if is_dir:
            name = name[:-1]
        parts = name.split("/")

        parent = root
        for part in parts[:-1]:
            if part not in parent[0]:
                parent[0][part] = [{}, []]
            parent = parent[0][part]
        if is_dir:
            parent[0][parts[-1]] = [{}, []]
        else:
            parent[1].append(parts[-1])

    def _walk(hierarchy, dir):
        yield (dir, hierarchy[0].keys(), hierarchy[1])
        for (subdir, sub_hierarchy) in hierarchy[0].items():
            if dir is not None:
                subdir = os.path.join(dir, subdir)
            for result in _walk(sub_hierarchy, subdir):
                yield result

    subdir_hierarchy = root
    if subdir is not None:
        subdir = os.path.normpath(subdir)
        for part in subdir.split(os.sep):
            subdir_hierarchy = subdir_hierarchy[0][part]
    return _walk(subdir_hierarchy, subdir)

