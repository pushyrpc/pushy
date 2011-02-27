/*
 * Copyright (c) 2009 Andrew Wilkins <axwalk@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package pushy;

/**
 * An interface for Pushy proxy objects.
 */
public interface PushyObject
{
    /**
     * Check if the object has an attribute with the given name.
     *
     * @param name The name of the attribute to determine the existence of.
     * @return True if the attribute exists, else false.
     */
    public boolean __hasattr__(String name);

    /**
     * Get an attribute with the given name.
     *
     * @param name The name of the attribute whose value is to be returned.
     * @return The value of the attribute.
     */
    public Object __getattr__(String name);

    /**
     * Set an attribute with the given name and value.
     *
     * @param name The name of the attribute whose value is to be set.
     * @param value The value to set the attribute to.
     */
    public void __setattr__(String name, Object value);

    /**
     * Get the value of an entry in the object, identified by the given key.
     *
     * @param key The index into the object of the entry to retrieve.
     * @return The value of the entry in the object at the specified index.
     */
    public Object __getitem__(Object key);

    /**
     * Set the value of an entry in the object, identified by the given key.
     *
     * @param key The index into the object of the entry to modify.
     * @param value The new value to set.
     */
    public void __setitem__(Object key, Object value);

    /**
     * Get the length of the object (i.e. call len(object))
     *
     * @return The length of the object.
     */
    public int __len__();

    /**
     * Call the object with no arguments.
     *
     * @return The return value of the call.
     */
    public Object __call__();

    /**
     * Call the object with positional arguments.
     *
     * @param args Positional arguments.
     * @return The return value of the call.
     */
    public Object __call__(Object[] args);

    /**
     * Call the object with positional and keyword arguments.
     *
     * @param args Positional arguments.
     * @param kwargs Keyword arguments.
     * @return The return value of the call.
     */
    public Object __call__(Object[] args, java.util.Map kwargs);
}

