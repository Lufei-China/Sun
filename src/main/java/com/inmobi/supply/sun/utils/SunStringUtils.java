package com.inmobi.supply.sun.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * String util
 */
public class SunStringUtils {

	public static final int LOWER_ALPHA_MIN = 97;
	public static final int LOWER_ALPHA_MAX = 122;
	public static final int LOW_UP_LENGTH = 32;

	/**
	 * @param text the string param you want to upper one char of it
	 * @param lowerCharacter the one char you want to upper
	 * @return upper result
	 */
	public static String upperCharacter(String text, char lowerCharacter) {
		if (!validLowerCharacter(text,lowerCharacter)) {
			return text;
		}
		char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] == lowerCharacter) {
				chars[i] -= LOW_UP_LENGTH;
			}
		}
		return String.valueOf(chars);
	}

	private static boolean validLowerCharacter(String text,char lowerCharacter) {
		return lowerCharacter >= LOWER_ALPHA_MIN && lowerCharacter <= LOWER_ALPHA_MAX
				&& StringUtils.isNotBlank(text);
	}
	public static String fracReduction(int numerator, int denominator) {
		//找到最大公约数，然后分别处以最大公约数
		int m = numerator;
		int n = denominator;
		int r;
		while (numerator > 0){
			r = denominator % numerator;
			denominator = numerator;
			numerator = r;
		}

		return m/denominator + "/" + n/denominator;
	}
}
