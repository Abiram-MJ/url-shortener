package com.abi.urlshortener.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @ParameterizedTest
    @CsvSource({
        "1,  1",
        "10, A",
        "62, 10",
        "3844, 100"
    })
    void encode_knownValues(long input, String expected) {
        assertThat(encoder.encode(input)).isEqualTo(expected);
    }

    @Test
    void encode_thenDecode_roundTrip() {
        long original = 123456789L;
        assertThat(encoder.decode(encoder.encode(original))).isEqualTo(original);
    }

    @Test
    void encode_throwsForZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.encode(0));
    }

    @Test
    void decode_throwsForInvalidChar() {
        assertThatIllegalArgumentException().isThrownBy(() -> encoder.decode("!bad"));
    }
}
