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
logger = logging.getLogger("pushy")
logger.disabled = True
if not logger.disabled:
    class ShutdownSafeFileHandler(logging.FileHandler):
        """
        A FileHandler class which protects calls to "close" from concurrent
        logging operations.
        """
        def __init__(self, filename):
            logging.FileHandler.__init__(self, filename)
        def close(self):
            self.acquire()
            try:
                logging.FileHandler.close(self)
            finally:
                self.release()

    handler = ShutdownSafeFileHandler("pushy.%d.log" % os.getpid())
    handler.setFormatter(
        logging.Formatter(
            "[%(process)d:(%(threadName)s:%(thread)d)] %(message)s"))
    logger.addHandler(handler)
    logger.setLevel(logging.DEBUG)
    logger.debug("sys.argv: %r", sys.argv)

