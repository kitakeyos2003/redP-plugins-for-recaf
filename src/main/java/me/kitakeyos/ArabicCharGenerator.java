/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kitakeyos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 *
 * @author kitakeyos - Hoàng Hữu Dũng
 */
public class ArabicCharGenerator {

    private static final int ARABIC_ALPHABET_START = 0x0600;
    private static final int ARABIC_ALPHABET_END = 0x06FF;
    private static char[] ARABIC_ALPHABET = {'س', 'ش', 'ض', 'ظ', 'خ', 'ح', 'پ', 'ٽ', 'ټ', 'ٻ', 'ٺ', 'ٹ', 'ڣ', 'ڢ', 'ڡ'};

    private static final Random random = new Random();

    public static char generateRandomChar() {
        int index = random.nextInt(ARABIC_ALPHABET.length);
        return ARABIC_ALPHABET[index];
    }

    public static char generateRandomLatinChar() {
        int randomUppercaseLetter = (int) (Math.random() * 26 + 65); // A-Z
        int randomLowercaseLetter = (int) (Math.random() * 26 + 97); // a-z
        int randomCase = Math.random() < 0.5 ? randomUppercaseLetter : randomLowercaseLetter;
        return (char) randomCase;
    }

    public static String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(generateRandomChar());
        }
        return sb.toString();
    }

    public static String getUniqueSpecialChar(Set<String> usedValues) {
        String specialChar;
        do {
            specialChar = generateRandomString(random.nextInt(100) + 10);
        } while (usedValues.contains(specialChar));
        usedValues.add(specialChar);
        return specialChar;
    }
}
