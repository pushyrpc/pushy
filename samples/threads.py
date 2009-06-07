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

import common, sys, threading, time

words = "Hello from Pushy".split()

def thread_function(connection, word):
    env = connection.modules.os.environ.copy()
    env["WORD"] = word
    connection.modules.subprocess.call(
        ["python", "-c", "import os; print os.environ['WORD']"], env=env)

if __name__ == "__main__":
    connection = common.get_connection()
    try:
        print "Connected to:", connection.server.address

        # Demonstrate the thread-safety of pushy, by executing multiple
        # programs on the peer.
        threads = []
        for word in words:
            threads.append(
                threading.Thread(
                    target=thread_function, args=(connection, word)))
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()

        # Give the output a chance to come through. It would be nice to be able
        # to call "flush" on sys.stdout and have it block until the data is
        # written to the client interpreter's file.
        time.sleep(1)
        sys.stdout.flush()
    finally:
        del connection

