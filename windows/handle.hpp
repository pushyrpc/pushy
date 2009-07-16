#ifndef _PUSHY_HANDLE_HPP
#define _PUSHY_HANDLE_HPP

#include <windows.h>

class Handle
{
public:
    Handle(HANDLE handle);
    ~Handle();

    /**
     * Conversion operator.
     */
    operator HANDLE() const;

    /**
     * Close the handle.
     */
    void close();

private:
    HANDLE handle;

private:
    Handle(Handle const&);
    Handle& operator=(Handle const&);
};

#endif

