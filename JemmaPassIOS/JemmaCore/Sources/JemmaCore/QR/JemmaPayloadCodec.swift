import Foundation
import Compression

// MARK: - JemmaPayloadCodec (`_j2:` Deflate-raw RFC 1951 + Base64)

public enum JemmaPayloadCodec: Sendable {
    public static let prefix = "_j2:"

    public enum CodecError: Error {
        case compressionFailed
        case decompressionFailed
        case invalidBase64
        case invalidPrefix
        case invalidUTF8
    }

    /// Encodes a `JemmaProfileJ` to `_j2:<base64(deflate-raw(json))>`
    public static func encode(profile: JemmaProfileJ) throws -> String {
        let encoder = JSONEncoder()
        let jsonData = try encoder.encode(profile)
        let compressed = try deflateRaw(data: jsonData)
        return prefix + compressed.base64EncodedString()
    }

    /// Decodes a `_j2:` or raw JSON string into `JemmaProfileJ`
    public static func decode(payload: String) throws -> JemmaProfileJ {
        let trimmed = payload.trimmingCharacters(in: .whitespacesAndNewlines)
        let jsonData: Data
        if trimmed.hasPrefix(prefix) {
            let base64Part = String(trimmed.dropFirst(prefix.count))
            guard let compressedData = Data(base64Encoded: base64Part) else {
                throw CodecError.invalidBase64
            }
            jsonData = try inflateRaw(data: compressedData)
        } else {
            // Raw JSON fallback
            guard let data = trimmed.data(using: .utf8) else {
                throw CodecError.invalidUTF8
            }
            jsonData = data
        }

        let decoder = JSONDecoder()
        return try decoder.decode(JemmaProfileJ.self, from: jsonData)
    }

    // MARK: - RFC 1951 Raw Deflate / Inflate using Apple Compression Framework
    public static func deflateRaw(data: Data) throws -> Data {
        guard !data.isEmpty else { return Data() }

        let destinationBufferSize = data.count + 512
        var destinationBuffer = [UInt8](repeating: 0, count: destinationBufferSize)

        let compressedSize = data.withUnsafeBytes { (sourcePointer: UnsafeRawBufferPointer) -> Int in
            guard let sourceAddress = sourcePointer.baseAddress?.assumingMemoryBound(to: UInt8.self) else {
                return 0
            }
            return destinationBuffer.withUnsafeMutableBufferPointer { destPointer -> Int in
                guard let destAddress = destPointer.baseAddress else { return 0 }
                return compression_encode_buffer(
                    destAddress,
                    destinationBufferSize,
                    sourceAddress,
                    data.count,
                    nil,
                    COMPRESSION_ZLIB
                )
            }
        }

        guard compressedSize > 0 else {
            throw CodecError.compressionFailed
        }

        return Data(destinationBuffer.prefix(compressedSize))
    }

    public static func inflateRaw(data: Data, maxOutputSize: Int = 10 * 1024 * 1024) throws -> Data {
        guard !data.isEmpty else { return Data() }

        var bufferSize = max(data.count * 4, 4096)
        var destinationBuffer = [UInt8](repeating: 0, count: bufferSize)

        while bufferSize <= maxOutputSize {
            let decompressedSize = data.withUnsafeBytes { (sourcePointer: UnsafeRawBufferPointer) -> Int in
                guard let sourceAddress = sourcePointer.baseAddress?.assumingMemoryBound(to: UInt8.self) else {
                    return 0
                }
                return destinationBuffer.withUnsafeMutableBufferPointer { destPointer -> Int in
                    guard let destAddress = destPointer.baseAddress else { return 0 }
                    return compression_decode_buffer(
                        destAddress,
                        bufferSize,
                        sourceAddress,
                        data.count,
                        nil,
                        COMPRESSION_ZLIB
                    )
                }
            }

            if decompressedSize > 0 && decompressedSize < bufferSize {
                return Data(destinationBuffer.prefix(decompressedSize))
            }

            bufferSize *= 2
            destinationBuffer = [UInt8](repeating: 0, count: bufferSize)
        }

        throw CodecError.decompressionFailed
    }
}
