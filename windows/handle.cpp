#include "handle.hpp"
#include <stdexcept>

Handle::Handle(HANDLE handle_) : handle(handle_)
{
    if (handle == INVALID_HANDLE_VALUE)
        throw std::invalid_argument("handle == INVALID_HANDLE_VALUE");
}

Handle::~Handle()
{
    close();
}

Handle::operator HANDLE() const
{
    return handle;
}

void Handle::close()
{
    if (handle != INVALID_HANDLE_VALUE)
    {
        CloseHandle(handle);
        handle = INVALID_HANDLE_VALUE;
    }
}

