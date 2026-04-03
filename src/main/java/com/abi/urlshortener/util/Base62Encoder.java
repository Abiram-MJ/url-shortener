package com.abi.urlshortener.util;

import org.springframework.stereotype.Component;

/**
 * Encodes a positive long into a Base62 string using the character set
 * [0-9A-Za-z]. Used to convert auto-increment IDs into compact short codes.
 *
 * <p>Example: ID 1 → "1", ID 3844 → "100", ID 56800235583 → "NQCwb"
 */
@Component
public class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length(); // 62

    /**
     * Encodes a positive long value to a Base62 string.
     *
     * @param value the ID to encode; must be >= 1
     * @return a non-empty Base62 string
     * @throws IllegalArgumentException if value is less than 1
     */
    public String encode(long value) {
        if (value < 1) {
            throw new IllegalArgumentException("Value must be >= 1, got: " + value);
        }
        StringBuilder sb = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            sb.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to its original long value.
     *
     * @param encoded the Base62 string to decode
     * @return the decoded long value
     * @throws IllegalArgumentException if the string contains characters outside the alphabet
     */
    public long decode(String encoded) {
        long result = 0;
        for (char c : encoded.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            result = result * BASE + index;
        }
        return result;
    }
}
