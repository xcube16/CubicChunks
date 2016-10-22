package cubicchunks.server.experimental;

import gnu.trove.list.array.TShortArrayList;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.ForgeModContainer;

import java.util.Arrays;

import cubicchunks.util.AddressTools;

public class BlockUpdateBuffer {

	private short[] changedBlocks = new short[ForgeModContainer.clumpingThreshold];
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
		return changes > ForgeModContainer.clumpingThreshold ? Integer.MAX_VALUE : changes;
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
