/* MIT License

 Copyright (c) 2018 SLP-team

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.
 */

package my.counting.trie;

import java.util.List;

/*
 * Array storage
 */
public class ArrayStorage {
	public static boolean checkExactSequence(List<Integer> indices, int index, int[] successor) {
		boolean valid = successor.length == indices.size() - index;
		if (valid) {
			for (int i = 1; i < successor.length; i++) {
				if (indices.get(index + i) != successor[i]) {
					valid = false;
					break;
				}
			}
		}
		return valid;
	}

	public static boolean checkPartialSequence(List<Integer> indices, int index, int[] successor) {
		boolean valid = successor.length >= indices.size() - index;
		if (valid) {
			for (int i = 1; i < indices.size() - index; i++) {
				if (indices.get(index + i) != successor[i]) {
					valid = false;
					break;
				}
			}
		}
		return valid;
	}
}