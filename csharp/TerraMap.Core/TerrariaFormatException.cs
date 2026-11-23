// SPDX-License-Identifier: GPL-3.0-or-later

using System;

namespace TerraMap.Core;

/// <summary>
/// Thrown when a .wld / .map payload does not match the layout this library understands.
/// Never swallowed: a malformed file must surface as a hard failure, not a silent default.
/// </summary>
public sealed class TerrariaFormatException : Exception
{
    public TerrariaFormatException(string message) : base(message)
    {
    }

    public TerrariaFormatException(string message, Exception inner) : base(message, inner)
    {
    }
}
