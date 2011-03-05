# Copyright (c) 2008, 2011 Andrew Wilkins <axwalk@gmail.com>
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

from distutils.core import setup

long_description = """
Pushy is a Python library for allowing one to connect to a remote Python
interpreter, and transparently access objects in that interpreter as if they
were objects within the local interpreter.

Pushy has the novel ability to execute a remote Python interpreter and start a
request servicing loop therein, without requiring any custom Python libraries
(including Pushy) to be initially present. To accomplish this, Pushy requires
an SSH daemon to be present on the remote machine.

Pushy was initially developed to simplify automated testing to the point that
testing software on remote machines becomes little different to testing on the
local machine.
""".strip()

description = """
A library for transparently accessing objects in a remote Python interpreter
""".strip()

classifiers = [
    "Intended Audience :: Developers",
    "Operating System :: Microsoft :: Windows",
    "Operating System :: POSIX",
    "Programming Language :: Python",
    "Programming Language :: Java",
    "License :: OSI Approved :: MIT License",
    "Development Status :: 4 - Beta",
    "Topic :: Software Development :: Libraries",
    "Topic :: Software Development :: Object Brokering",
    "Topic :: Software Development :: Testing",
    "Topic :: System :: Distributed Computing",
]

dependencies = [
    "paramiko",
]

setup(
    name="pushy",
    version="0.4",
    description = description,
    long_description = long_description,
    classifiers = classifiers,
    requires = dependencies,
    author="Andrew Wilkins",
    author_email="axwalk@gmail.com",
    url="http://launchpad.net/pushy/",
    packages=["pushy", "pushy.protocol", "pushy.transport",
              "pushy.transport.smb", "pushy.util"]
)

