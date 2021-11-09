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

import com.intellij.completion.ngram.slp.counting.Counter;
import com.intellij.completion.ngram.slp.modeling.runners.ModelRunner;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AbstractTrie implements Counter {

	
	public static int COUNT_OF_COUNTS_CUTOFF = 3;
	public volatile static int[][] nCounts = new int[ModelRunner.DEFAULT_NGRAM_ORDER][4];
	
	public int[] counts;

	public AbstractTrie() {
		this.counts = new int[2 + COUNT_OF_COUNTS_CUTOFF];
	}
	
	/**
	 * Return a new AbstractTrie instance of your choosing.
	 * For instance, {@link MapTrieCounter} at present returns a map for the root and second level, than a regular Trie,
	 * whereas TrieCounter always uses a Trie.
	 */
	abstract AbstractTrie makeNext(int depth);
	
	public abstract List<Integer> getSuccessors();
	public abstract Object getSuccessor(int key);

	abstract List<Integer> getTopSuccessorsInternal(int limit);
	
	abstract void putSuccessor(int key, Object o);
	abstract void removeSuccessor(int key);

	public abstract void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;
	public abstract void writeExternal(ObjectOutput out) throws IOException;

	/*
	 * Getters and Setters
	 */
	@Override
	public final int getCount() {
		return this.counts[0];
	}
	
	final int getCount(Object successor) {
		if (successor == null) return 0;
		else if (successor instanceof AbstractTrie) return ((AbstractTrie) successor).getCount();
		else return ((int[]) successor)[0];
	}
	
	public final int getContextCount() {
		return this.counts[1];
	}

	@Override
	public final int getCountOfCount(int n, int count) {
		int minN = Math.min(n, nCounts.length) - 1;
		int minC = Math.min(count, nCounts[minN].length) - 1;
		return nCounts[minN][minC];
	}
	
	@Override
	public final long[] getCounts(List<Integer> indices) {
		if (indices.isEmpty()) return new long[] { getCount(), getCount() };
		return getCounts(indices, 0);
	}

	private long[] getCounts(List<Integer> indices, int index) {
		Integer next = indices.get(index);
		Object succ = this.getSuccessor(next);
		boolean nearLast = index == indices.size() - 1;
		// Recurse if applicable
		if ((succ instanceof AbstractTrie)) {
			AbstractTrie successor = (AbstractTrie) succ;
			if (!nearLast) return successor.getCounts(indices, index + 1);
			else return new long[] { successor.getCount(), this.counts[1] };
		}
		// Else, return counts from array if present
		long[] counts = new long[2];
		if (nearLast) counts[1] = this.counts[1];
		if (succ != null) {
			int[] successor = (int[]) succ;
			if (ArrayStorage.checkPartialSequence(indices, index, successor)) {
				counts[0] = successor[0];
				if (!nearLast) counts[1] = counts[0];
			}
			else if (!nearLast && successor.length >= indices.size() - index
					&& ArrayStorage.checkPartialSequence(indices.subList(0, indices.size() - 1), index, successor)) {
				counts[1] = successor[0];
			}
		}
		return counts;
	}
	
	@Override
	public final int[] getDistinctCounts(int range, List<Integer> indices) {
		return getDistinctCounts(range, indices, 0);
	}

	private int[] getDistinctCounts(int range, List<Integer> indices, int index) {
		if (index < indices.size()) {
			int next = indices.get(index);
			Object succ = getSuccessor(next);
			if (succ == null) return new int[range];
			if (succ instanceof AbstractTrie) {
				AbstractTrie successor = (AbstractTrie) succ;
				return successor.getDistinctCounts(range, indices, index + 1);
			}
			else {
				int[] successor = (int[]) succ;
				int[] distinctCounts = new int[range];
				if (ArrayStorage.checkPartialSequence(indices, index, successor)
						&& !ArrayStorage.checkExactSequence(indices, index, successor)) {
					distinctCounts[Math.min(range - 1, successor[0] - 1)] = 1;
				}
				return distinctCounts;
			}
		} else {
			int[] distinctCounts = new int[range];
			int totalDistinct = this.getSuccessorCount();
			for (int i = 2; i < this.counts.length - 1 && i - 1 < range; i++) {
				int countOfCountsI = this.counts[i];
				distinctCounts[i - 2] = countOfCountsI;
				totalDistinct -= countOfCountsI;
			}
			distinctCounts[range - 1] = totalDistinct;
			return distinctCounts;
		}
	}

	@Override
	public final int getSuccessorCount() {
		return Arrays.stream(this.counts).skip(2).sum();
	}

	@Override
	public final int getSuccessorCount(List<Integer> indices) {
		Object successor = getSuccessorNode(indices, 0);
		if (successor == null) return 0;
		else if (successor instanceof AbstractTrie) return ((AbstractTrie) successor).getSuccessorCount();
		else return 1;
	}
	
	private Object getSuccessorNode(List<Integer> indices, int index) {
		if (index == indices.size()) return this;
		int next = indices.get(index);
		Object succ = getSuccessor(next);
		if (succ == null) return null;
		else if (succ instanceof AbstractTrie) {
			AbstractTrie successor = (AbstractTrie) succ;
			return successor.getSuccessorNode(indices, index + 1);
		}
		else {
			int[] successor = (int[]) succ;
			if (!ArrayStorage.checkPartialSequence(indices, index, successor)) return null;
			int[] trueSucc = new int[1 + successor.length - (indices.size() - index)];
			trueSucc[0] = successor[0];
			for (int i = 1; i < trueSucc.length; i++) {
				trueSucc[i] = successor[i + indices.size() - index - 1];
			}
			return trueSucc;
		}
	}

	@Override
	public List<Integer> getTopSuccessors(List<Integer> indices, int limit) {
		Object successor = getSuccessorNode(indices, 0);
		if (successor == null) return new ArrayList<>();
		else if (successor instanceof AbstractTrie) return ((AbstractTrie) successor).getTopSuccessorsInternal(limit);
		else {
			int[] succ = (int[]) successor;
			List<Integer> successors = new ArrayList<>();
			if (succ.length > 1) successors.add(succ[1]);
			return successors;
		}
	}

	/*
	 * Updater Methods
	 */
	@Override
	public final void count(List<Integer> indices) {
		update(indices, 1);
	}

	@Override
	public final void unCount(List<Integer> indices) {
		update(indices, -1);
	}

	public final void updateCount(int adj) {
		update(Collections.emptyList(), adj);
	}

	public final void update(List<Integer> indices, int adj) {
		update(indices, 0, adj);
	}
	
	private synchronized void update(List<Integer> indices, int index, int adj) {
		if (index < indices.size()) {
			int key = indices.get(index);
			Object successor = getSuccessor(key);
			if (successor != null) this.updateSuccessor(indices, index, adj, successor);
			else this.addArray(indices, index, adj);
		}
		this.counts[0] += adj;
		if (index != indices.size()) this.counts[1] += adj;
		updateNCounts(index, this.getCount(), adj);
	}

	private void updateSuccessor(List<Integer> indices, int index, int adj, Object succ) {
		if (succ instanceof AbstractTrie) updateTrie(indices, index, adj, succ);
		else updateArray(indices, index, adj, succ);
	}

	private void updateTrie(List<Integer> indices, int index, int adj, Object succ) {
		AbstractTrie next = (AbstractTrie) succ;
		if (next instanceof ArrayTrieCounter) {
			ArrayTrieCounter arrayCounter = (ArrayTrieCounter) next;
			if (arrayCounter.indices.length > 10) {
				next = promoteArrayToMap(indices, index, arrayCounter);
			}
		}
		next.update(indices, index + 1, adj);
		updateCoCs(next.getCount(), adj);
		if (next.getCount() == 0) {
			this.removeSuccessor(indices.get(index));
		}
	}

	private void updateArray(List<Integer> indices, int index, int adj, Object succ) {
		int[] successor = (int[]) succ;
		boolean valid = ArrayStorage.checkExactSequence(indices, index, successor);
		if (valid) updateArrayCount(indices, index, adj, successor);
		else {
			AbstractTrie newNext = promoteArrayToTrie(indices, index, successor);
			updateTrie(indices, index, adj, newNext);
		}
	}

	private void updateArrayCount(List<Integer> indices, int index, int adj, int[] successor) {
		successor[0] += adj;
		if (successor[0] == 0) {
			this.removeSuccessor(indices.get(index));
		}
		updateCoCs(successor[0], adj);
		for (int i = index + 1; i <= indices.size(); i++) {
			updateNCounts(i, successor[0], adj);
		}
	}

	private AbstractTrie promoteArrayToMap(List<Integer> indices, int index, ArrayTrieCounter counter) {
		AbstractTrie newNext = new MapTrieCounter();
		newNext.counts = counter.counts;
		for (int i = 0; i < counter.indices.length; i++) {
			int ix = counter.indices[i];
			if (ix == Integer.MAX_VALUE) continue;
			Object successor = counter.successors[i];
			newNext.putSuccessor(ix, successor);
		}
		this.putSuccessor(indices.get(index), newNext);
		return newNext;
	}
		
	private AbstractTrie promoteArrayToTrie(List<Integer> indices, int index, int[] successor) {
		AbstractTrie newNext = makeNext(index);
		newNext.updateCount(successor[0]);
		if (successor.length > 1) {
			newNext.counts[1] = newNext.counts[0];
			int[] temp = Arrays.copyOfRange(successor, 1, successor.length);
			temp[0] = successor[0];
			newNext.putSuccessor(successor[1], temp);
			if (COUNT_OF_COUNTS_CUTOFF > 0) {
				newNext.counts[1 + Math.min(temp[0], COUNT_OF_COUNTS_CUTOFF)]++;
			}
		}
		this.putSuccessor(indices.get(index), newNext);
		return newNext;
	}

	private void addArray(List<Integer> indices, int index, int adj) {
		if (adj < 0) {
			//System.out.println("Attempting to forget unknown event: " + indices.subList(index, indices.size()));
			return;
		}
		int[] singleton = new int[indices.size() - index];
		singleton[0] = adj;
		for (int i = 1; i < singleton.length; i++) {
			singleton[i] = indices.get(index + i);
		}
		putSuccessor(indices.get(index), singleton);
		updateCoCs(adj, adj);
		for (int i = index + 1; i <= indices.size(); i++) {
			updateNCounts(i, adj, adj);
		}
	}

	private static void updateNCounts(int n, int count, int adj) {
		if (n == 0) return;
		if (n > ModelRunner.DEFAULT_NGRAM_ORDER) return;
		int[] toUpdate = nCounts[n - 1];
		int currIndex = Math.min(count, toUpdate.length);
		int prevIndex = Math.min(count - adj, toUpdate.length);
		if (currIndex != prevIndex) {
			boolean updateCurr = currIndex > 0;
			boolean updatePrev = prevIndex > 0;
			if (updateCurr) toUpdate[currIndex - 1]++;
			if (updatePrev) toUpdate[prevIndex - 1]--;	
		}
	}
	
	private void updateCoCs(int count, int adj) {
		if (COUNT_OF_COUNTS_CUTOFF == 0) return;
		int currIndex = Math.min(count, COUNT_OF_COUNTS_CUTOFF);
		int prevIndex = Math.min(count - adj, COUNT_OF_COUNTS_CUTOFF);
		if (currIndex != prevIndex) {
			if (currIndex >= 1) this.counts[currIndex + 1]++;
			if (prevIndex >= 1) this.counts[prevIndex + 1]--;
		}
	}
}
