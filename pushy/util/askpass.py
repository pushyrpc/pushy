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

import sys, os, subprocess, tempfile
from subprocess import PIPE, STDOUT


def forknotty():
    """
    Fork the process, detach the child from the terminal, and then wait for it
    to complete in the parent process.
    """

    # Fork.
    pid = os.fork()
    if pid > 0:
        (pid, return_code) = os.waitpid(pid, 0)
        os._exit(return_code)

    # Detach from parent process.
    os.setsid()
    os.chdir("/")
    os.umask(0)

    # Fork again - some UNIXes need this.
    pid = os.fork()
    if pid > 0:
        (pid, return_code) = os.waitpid(pid, 0)
        os._exit(return_code)


class _Popen(subprocess.Popen):
    def __init__(self, askpass, *args, **kwargs):
        subprocess.Popen.__init__(self, *args, **kwargs)
        self.__askpass = askpass
    def __del__(self):
        try:
            subprocess.Popen.__del__(self)
        finally:
            if os.path.exists(self.__askpass):
                os.remove(self.__askpass)
    def close(self):
        try:
            subprocess.Popen.close(self)
        finally:
            if os.path.exists(self.__askpass):
                os.remove(self.__askpass)


def Popen(command, password, stdout=None, stderr=None, stdin=None):
    """
    Execute the specified command in a subprocess, with SSH_ASKPASS set in such
    a way as to allow passing a password non-interactively to programs that
    need it (e.g. ssh, scp).
    """

    # Create a temporary, executable-by-owner file.
    (fd, path) = tempfile.mkstemp(text=True)
    os.chmod(path, 0700)

    try:
        tf = os.fdopen(fd, "w+")
        print >> tf, "#!" + sys.executable
        print >> tf, "import os; print os.environ['SSH_PASSWORD']"
        print >> tf, "import os; os.remove(__file__)"
        tf.close()

        environ = os.environ.copy()
        environ["SSH_PASSWORD"] = password
        environ["DISPLAY"] = "none:0.0"
        environ["SSH_ASKPASS"] = path
        return _Popen(path, command, preexec_fn=forknotty, stdout=stdout,
                      stderr=stderr, stdin=stdin, env=environ)
    except:
        if os.path.exists(path):
            os.remove(path)

if __name__ == "__main__":
    proc = Popen(["python", "-c", "print 123"], "blah").wait()

