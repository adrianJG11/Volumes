package com.adrian.volumes.book;

/** ISBN-10 and ISBN-13 represent editions; store one canonical ISBN-13 form. */
public final class Isbn {
    private Isbn() {}

    public static String normalize(String input) {
        if (input == null) throw new IllegalArgumentException("ISBN is required.");
        String value = input.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        if (value.matches("[0-9]{9}[0-9X]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                int digit = value.charAt(i) == 'X' ? 10 : value.charAt(i) - '0';
                sum += digit * (10 - i);
            }
            if (sum % 11 != 0) throw invalid();
            String prefix = "978" + value.substring(0, 9);
            return prefix + checkDigit(prefix);
        }
        if (!value.matches("97[89][0-9]{10}")
                || value.charAt(12) - '0' != checkDigit(value.substring(0, 12))) throw invalid();
        return value;
    }

    private static int checkDigit(String prefix) {
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (prefix.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return (10 - sum % 10) % 10;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Enter a valid ISBN-10 or ISBN-13, including its check digit.");
    }
}
