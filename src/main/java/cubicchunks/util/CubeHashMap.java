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

import cubicchunks.world.cube.Cube;

import java.util.Iterator;

public class CubeHashMap implements Iterable<Cube> {

	private Cube[] buckets;

	/**
	 * Number of key-value pairs in this table
	 */
	private int size;

	/**
	 * The load factor
	 */
	private float factor;

	/**
	 * Avoid any floating point operations by caching buckets.length * factor
	 * When size > cap, we resize buckets
	 */
	private int cap;

	private int mask;

	/**
	 * Creates a new hash table with {@code factor} load factor, and a size of 2 ^ {@code power}
	 *
	 * @param factor the load factor
	 * @param power the size as a 2 ^ {@code power}
	 */
	public CubeHashMap(float factor, int power) {
		if (factor > 1.0) {
			throw new IllegalArgumentException(
					"You really dont want to be using a " + factor + " load factor with this hash table!");
		}

		this.factor = factor;
		buckets = new Cube[1 << power];

		refreshFields();
	}

	/**
	 * Adds a key-value pair to this table
	 *
	 * @param value the new value
	 *
	 * @return the old value if any
	 */
	public Cube put(Cube value) {
		int x = value.getX();
		int y = value.getY();
		int z = value.getZ();
		int index = hash(x, y, z) & mask;

		Cube bucket = buckets[index];
		while (bucket != null) { // find the closest empty space
			if (bucket.getX() == x && bucket.getY() == y && bucket.getZ() == z) {
				buckets[index] = value;
				justPut();
				return bucket;
			}

			bucket = buckets[index = (index + 1) & mask]; // use mask so we will wrap around
		}
		buckets[index] = value;

		justPut();
		return null;
	}

	/**
	 * Removes a key-value pair from this table
	 *
	 * @param x key x
	 * @param y key y
	 * @param z key z
	 *
	 * @return the old value
	 */
	public Cube remove(int x, int y, int z) {
		int index = hash(x, y, z) & mask;

		Cube bucket = buckets[index];
		while (bucket != null) { // search until we hit an empty space
			if (bucket.getX() == x && bucket.getY() == y && bucket.getZ() == z) { // found it!
				collapseSlot(index); //
				return bucket;
			}

			bucket = buckets[index = (index + 1) & mask]; // use mask so we will wrap around
		}
		return null; // nothing was removed
	}

	/**
	 * Gets the value at x, y, z
	 *
	 * @param x key x
	 * @param y key y
	 * @param z key z
	 *
	 * @return the value if any
	 */
	public Cube get(int x, int y, int z) {
		int index = hash(x, y, z) & mask;

		Cube bucket = buckets[index];
		while (bucket != null) { // search until we hit an empty space
			if (bucket.getX() == x && bucket.getY() == y && bucket.getZ() == z) { // found it!
				return bucket;
			}

			bucket = buckets[index = (index + 1) & mask]; // use mask so we will wrap around
		}

		return null; // nothing was found
	}

	/**
	 * @return the number of key-value pairs in this table
	 */
	public int getSize() {
		return size;
	}

	public Iterator<Cube> iterator() {
		return new Iterator<Cube>() {
			int at = 0;
			int next = 0;

			@Override public boolean hasNext() {
				if(next > at){
					return true;
				}
				for (next++; next < buckets.length; next++) {
					if (buckets[next] != null) {
						return true;
					}
				}
				return false;
			}

			@Override public Cube next() {
				if(next > at){
					at = next;
					return buckets[at];
				}
				for (next++; next < buckets.length; next++) {
					if (buckets[next] != null) {
						at = next;
						return buckets[at];
					}
				}
				return null;
			}

			//TODO: WARNING: risk of iterating over the same item more than once if this is used
			//               do to items wrapping back around form the front of the buckets array
			@Override public void remove(){
				collapseSlot(at);
				next = at = at - 1; // There could be a new item in the removed bucket
			}
		};
	}

	private void justPut() {
		size++;

		if (size > cap) { // resize!

			Cube[] oldBuckets = buckets; // save old array

			buckets = new Cube[buckets.length*2]; // double the size!
			refreshFields();

			for (int i = 0; i < oldBuckets.length; i++) {
				Cube oldbucket = oldBuckets[i];

				if (oldbucket == null) { // empty bucket
					continue;
				}

				// 'put' the node
				int index = hash(oldbucket.getX(), oldbucket.getY(), oldbucket.getZ()) & mask;

				Cube bucket = buckets[index];
				while (bucket != null) { // find the closest empty space
					bucket = buckets[index = (index + 1) & mask]; // use mask so we will wrap around
				}
				buckets[index] = oldbucket;
				// end put
			}
		}
	}

	private void collapseSlot(int hole) {
		size--;

		int at = hole;
		while (true) {
			at = (at + 1) & mask;

			Cube cube = buckets[at];
			if (cube == null) {
				buckets[hole] = null; // null out the hole only when we need to
				return;
			}
			int ats_goal = hash(cube.getX(), cube.getY(), cube.getZ()) & mask;
			if (hole < at) { // normal
				if (ats_goal <= hole || at < ats_goal) { // at's goal is slightly before the hole
					// OR at's goal wraps around backwards
					// (so its also before the hole)!
					buckets[hole] = cube; // fill the hole
					hole = at;            // there is a new hole
				}
			} else { // wrap around!
				if (hole >= ats_goal && ats_goal > at) { // at's goal is slightly before the hole (wrapped around)
					// AND at's goal wraps around backwards
					buckets[hole] = cube; // fill the hole
					hole = at;            // there is a new hole
				}
			}
		}
	}

	private void refreshFields() {
		// we need that 1 extra space, make shore it will be there
		cap = Math.min(buckets.length - 1, (int) (buckets.length*factor));
		mask = buckets.length - 1;
	}

	private static int hash(int x, int y, int z){
		final int prime = 1183822147;
		int hash = prime; hash += x;
		hash *= prime;
		hash += y;
		hash *= prime;
		hash += z;
		hash *= prime;
		return hash;
	}
}