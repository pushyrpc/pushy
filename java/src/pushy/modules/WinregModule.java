/*
 * Copyright (c) 2010 Andrew Wilkins <axwalk@gmail.com>
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

package pushy.modules;

import pushy.Client;
import pushy.PushyObject;
import pushy.Module;

public class WinregModule extends Module {
    // Predefined keys.
    public static final Long HKEY_CURRENT_USER = new Long(0xFFFFFFFF80000001L);
    public static final Long HKEY_LOCAL_MACHINE= new Long(0xFFFFFFFF80000002L);

    // Value types.
    public static final int REG_NONE = 0;
    public static final int REG_SZ = 1;
    public static final int REG_EXPAND_SZ = 2; 
    public static final int REG_BINARY = 3; 
    public static final int REG_DWORD = 4;
    public static final int REG_DWORD_LITTLE_ENDIAN = 4;
    public static final int REG_DWORD_BIG_ENDIAN = 5; 
    public static final int REG_LINK = 6;
    public static final int REG_MULTI_SZ = 7;
    public static final int REG_RESOURCE_LIST = 8;
    public static final int REG_FULL_RESOURCE_DESCRIPTOR = 9;
    public static final int REG_RESOURCE_REQUIREMENTS_LIST = 10;

    // Methods.
    private PushyObject openKeyMethod;
    private PushyObject closeKeyMethod;
    private PushyObject queryValueExMethod;

    public WinregModule(Client client) {
        super(client, "_winreg");
        openKeyMethod = __getmethod__("OpenKey");
        closeKeyMethod = __getmethod__("CloseKey");
        queryValueExMethod = __getmethod__("QueryValueEx");
    }

    /**
     * Open a subkey of an already open key.
     */
    public Object openKey(Object key, String subkey)
    {
        return openKeyMethod.__call__(new Object[]{key, subkey});
    }

    /**
     * Close a key opened with "openKey".
     */
    public void closeKey(Object key)
    {
        closeKeyMethod.__call__(new Object[]{key});
    }

    /**
     * Query the value of a registry key.
     */
    public Object queryValue(Object key, String valueName)
    {
        Object res = queryValueExMethod.__call__(new Object[]{key, valueName});
        if (res != null)
        {
            if (res instanceof int[])
                return new Integer(((int[])res)[0]);
            else
                return ((Object[])res)[0];
        }
        return null;
    }
}

