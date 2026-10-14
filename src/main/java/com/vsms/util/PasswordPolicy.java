package com.vsms.util;

/** Simple password rules shared by every place a password is set. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt only looks at the first 72 bytes. */
    public static final int MAX_LENGTH = 72;
    public static final String RULES = "Use 8 to 72 characters with at least one letter and one digit.";

    private PasswordPolicy() {
    }

    /** @return a message describing the problem, or null if the password is acceptable */
    public static String check(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return "Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long.";
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return "Password must contain at least one letter and one digit.";
        }
        return null;
    }
}
