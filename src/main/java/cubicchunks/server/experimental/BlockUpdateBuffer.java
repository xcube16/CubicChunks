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
package cubicchunks.server.experimental;

import gnu.trove.list.array.TShortArrayList;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.ForgeModContainer;

import java.util.Arrays;

import cubicchunks.util.AddressTools;

public class BlockUpdateBuffer {

	private short[] changedBlocks = new short[ForgeModContainer.clumpingThreshold / 2];
	private int changes = 0;

	private final TShortArrayList changedTiles = new TShortArrayList(0);

	public void track(BlockPos pos, boolean hasTile){
		short localPos = AddressTools.getLocalAddress(pos.getX(), pos.getY(), pos.getZ());

		if (!hasTile) {
			this.changedTiles.remove(localPos);
		}else if (!changedTiles.contains(localPos)) {
			changedTiles.add(localPos);
		}

		if (changes < changedBlocks.length) {

			for (int i = 0; i < this.changes; ++i) {
				if (this.changedBlocks[i] == localPos) {
					return;
				}
			}
			this.changedBlocks[this.changes] = localPos;
		}
		this.changes++;
	}

	public short getBlock(int index){
		return changedBlocks[index];
	}

	public short getTile(int index) {
		return changedTiles.get(index);
	}

	public void clear(){
		changes = 0;
		changedTiles.clear(0);
	}

	public short[] getChangedBlocks() {
		return Arrays.copyOf(changedBlocks, changes);
	}

	public short[] getChangedTiles(){
		return changedTiles.toArray();
	}

	/**
	 * Gets the number of changes or Integer.MAX_VALUE if there where too many changes to track
	 *
	 * @return the number of changes or Integer.MAX_VALUE
	 */
	public int getChanges(){
		return changes > changedBlocks.length ? Integer.MAX_VALUE : changes;
	}

	/**
	 * Gets the number of tile entities that changed
	 *
	 * @return the number of tile entity changes
	 */
	public int getTiles(){
		return changedTiles.size();
	}
}
