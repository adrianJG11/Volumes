package com.adrian.volumes.book;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IsbnTest {
    @Test
    void normalizesEquivalentEditions() {
        assertEquals("9780140449242", Isbn.normalize(" 978-0-14-044924-2 "));
        assertEquals("9780140449242", Isbn.normalize("0-14-044924-8"));
        assertEquals("9780804429573", Isbn.normalize("0-8044-2957-x"));
        assertEquals("9791090636071", Isbn.normalize("9791090636071"));
    }

    @Test
    void rejectsInvalidValues() {
        for (String value : new String[]{"", "abc", "9780140449243", "0140449249", "978014044924", "0000000000000", "978/0140449242"})
            assertThrows(IllegalArgumentException.class, () -> Isbn.normalize(value), value);
        assertThrows(IllegalArgumentException.class, () -> Isbn.normalize(null));
    }
}
