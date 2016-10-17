/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.util;

import java.util.Iterator;

public class XYZMap<T extends XYZAddressable> implements Iterable<T> {

	private final static int HASH_SEED = 1183822147;


	private XYZAddressable[] buckets;

	private int size;

	private float loadFactor;

	private int loadCap;

	private int mask;


	public XYZMap(float loadFactor, int power) {

		if (loadFactor > 1.0) {
			throw new IllegalArgumentException("Load factors > 1.0 are no supported by XYZMap!");
		}

		this.loadFactor = loadFactor;
		this.buckets = new XYZAddressable[1 << power];

		this.refreshFields();
	}


	public int getSize() {
		return size;
	}


	private static int hash(int x, int y, int z){
		int hash = HASH_SEED;
		hash += x;
		hash *= HASH_SEED;
		hash += y;
		hash *= HASH_SEED;
		hash += z;
		hash *= HASH_SEED;
		return hash;
	}

	private int getIndex(int x, int y, int z) {return hash(x, y, z) & this.mask;}

	private int getNextIndex(int index) {return (index + 1) & this.mask;}


	@SuppressWarnings("unchecked")
	public T put(T value) {

		int x = value.getX();
		int y = value.getY();
		int z = value.getZ();
		int index = getIndex(x, y, z);

		XYZAddressable bucket = this.buckets[index];

		// find the first empty bucket or the bucket for the element's exact position
		while (bucket != null) {

			// if the bucket matches the element's position, override its current content
			if (bucket.getX() == x && bucket.getY() == y && bucket.getZ() == z) {
				this.buckets[index] = value;
				return (T) bucket;
			}

			index = getNextIndex(index);
			bucket = this.buckets[index];
		}

		this.buckets[index] = value;

		// if adding the new element has caused this map to exceed its load loadCap, grow
		++this.size;
		if (this.size > this.loadCap) {
			this.grow();
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public T remove(int x, int y, int z) {

		int index = getIndex(x, y, z);

		XYZAddressable bucket = this.buckets[index];

		// find the bucket containing the element at the given position
		while (bucket != null) {

			// if the correct bucket was found, remove its content
			if (bucket.getX() == x && bucket.getY() == y && bucket.getZ() == z) {
				--this.size;
				this.collapseSlot(index);
				return (T) bucket;
			}

			index = getNextIndex(index);
			bucket = this.buckets[index];
		}

		// nothing was removed
		return null;
	}

	@SuppressWarnings("unchecked")
	public T get(int x, int y, int z) {

		int index = getIndex(x, y, z);

		XYZAddressable bucket = this.buckets[index];
		while (bucket != null) {
			if (bucket.getX() == x && bucket.getY() == y && bucket.getZ() == z) {
				return (T) bucket;
			}

			index = getNextIndex(index);
			bucket = this.buckets[index];
		}

		return null;
	}


	private void grow() {

		// save old array
		XYZAddressable[] oldBuckets = this.buckets;

		// double the size!
		this.buckets = new XYZAddressable[this.buckets.length * 2];
		this.refreshFields();

		for (XYZAddressable oldBucket : oldBuckets) {

			// skip empty buckets
			if (oldBucket == null) {
				continue;
			}

			// find the first empty bucket starting at the desired index
			int index = getIndex(oldBucket.getX(), oldBucket.getY(), oldBucket.getZ());
			XYZAddressable bucket = this.buckets[index];
			while (bucket != null) {
				index = getNextIndex(index);
				bucket = this.buckets[index = getNextIndex(index)];
			}

			this.buckets[index] = oldBucket;
		}
	}

	private void collapseSlot(int hole) {

		int currentIndex = hole;
		while (true) {
			currentIndex = getNextIndex(currentIndex);

			XYZAddressable bucket = this.buckets[currentIndex];

			// If there exists no element at the given index, there is nothing to fill the hole with.
			if (bucket == null) {
				this.buckets[hole] = null;
				return;
			}

			// get the correct index for the current bucket
			int targetIndex = getIndex(bucket.getX(), bucket.getY(), bucket.getZ());

			// If the hole lies to the left of the currentIndex and to the right of the targetIndex, move the current
			// element. These if conditions are necessary due to the bucket array wrapping around.
			if (hole < currentIndex) {
				if (targetIndex <= hole || currentIndex < targetIndex) {
					this.buckets[hole] = bucket;
					hole = currentIndex;
				}
			} else {
				if (hole >= targetIndex && targetIndex > currentIndex) {
					this.buckets[hole] = bucket;
					hole = currentIndex;
				}
			}
		}
	}

	private void refreshFields() {
		// We need that 1 extra space, make shore it will be there
		this.loadCap = Math.min(this.buckets.length - 1, (int) (this.buckets.length * this.loadFactor));
		this.mask = this.buckets.length - 1;
	}


	// Interface: Iterable<T> ------------------------------------------------------------------------------------------

	public Iterator<T> iterator() {

		int start;
		for (start = 0; start < buckets.length; start++) {
			if (buckets[start] != null) {
				break;
			}
		}

		final int f = start; // hacks just so I could use an anonymous class :P

		return new Iterator<T>() {
			int at = f;

			@Override
			public boolean hasNext() {
				return at < buckets.length;
			}

			@Override
			@SuppressWarnings("unchecked")
			public T next() {
				T ret = (T) buckets[at];
				for (at++; at < buckets.length; at++) {
					if (buckets[at] != null) {
						break;
					}
				}
				return ret;
			}
		};
	}

}