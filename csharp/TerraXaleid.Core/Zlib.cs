// SPDX-License-Identifier: GPL-3.0-or-later

using System;
using System.Buffers.Binary;
using System.IO;
using System.IO.Compression;

namespace TerraXaleid.Core;

/// <summary>zlib (RFC 1950) helpers — the chunk payloads are zlib streams, not raw deflate.</summary>
public static class Zlib
{
    /// <summary>Inflates a chunk payload. The result must be exactly one 64x64 block.</summary>
    public static byte[] Decompress(ReadOnlySpan<byte> payload)
    {
        using var input = new MemoryStream(payload.ToArray(), writable: false);
        using var zs = new ZLibStream(input, CompressionMode.Decompress);
        using var output = new MemoryStream(ChunkCells * 4);
        zs.CopyTo(output);
        return output.ToArray();
    }

    public static byte[] Compress(ReadOnlySpan<byte> data, int level)
    {
        var options = new ZLibCompressionOptions { CompressionLevel = level };
        using var output = new MemoryStream(data.Length / 4);
        using (var zs = new ZLibStream(output, options, leaveOpen: true))
        {
            zs.Write(data);
        }

        return output.ToArray();
    }

    internal const int ChunkCells = 64 * 64;
}
