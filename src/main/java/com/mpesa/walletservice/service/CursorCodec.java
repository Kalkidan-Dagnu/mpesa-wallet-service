package com.mpesa.walletservice.service;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Base64;


@Component
public class CursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /**
     * Encode {@code entryId} as an opaque cursor string.
     *
     * @param entryId positive Long
     * @return URL-safe Base64 string, no padding
     */
    public String encode(long entryId) {
        byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(entryId).array();
        return ENCODER.encodeToString(bytes);
    }

    /**
     * Decode a cursor string back to an entry_id.
     *
     * @param cursor value previously returned by {@link #encode(long)}
     * @return the original entry_id
     * @throws InvalidCursorException if the cursor is null, empty, or not a
     *                                valid encoding produced by this codec
     */
    public long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new InvalidCursorException("cursor must not be blank");
        }
        try {
            byte[] bytes = DECODER.decode(cursor);
            if (bytes.length != Long.BYTES) {
                throw new InvalidCursorException("cursor has wrong length: " + cursor);
            }
            long entryId = ByteBuffer.wrap(bytes).getLong();
            if (entryId <= 0) {
                throw new InvalidCursorException("cursor encodes non-positive entry_id: " + cursor);
            }
            return entryId;
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException("cursor is not valid Base64: " + cursor, e);
        }
    }

    // ── nested exception — keeps all cursor-related code in one place ────────

    public static final class InvalidCursorException extends RuntimeException {
        public InvalidCursorException(String message) {
            super(message);
        }
        public InvalidCursorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
