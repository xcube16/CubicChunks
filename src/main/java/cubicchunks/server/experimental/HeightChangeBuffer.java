package cubicchunks.server.experimental;

import net.minecraftforge.common.ForgeModContainer;

import java.util.Arrays;

import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.util.AddressTools;
import mcp.MethodsReturnNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class HeightChangeBuffer {

	private byte[] changedHeights = new byte[ForgeModContainer.clumpingThreshold / 8];
	private int changes = 0;

	public void track(int localX, int localZ){
		byte localPos = AddressTools.getLocalAddress(localX, localZ);

		if (changes < changedHeights.length) {

			for (int i = 0; i < this.changes; ++i) {
				if (this.changedHeights[i] == localPos) {
					return;
				}
			}
			this.changedHeights[this.changes] = localPos;
		}
		this.changes++;
	}

	public short getHeight(int index){
		return changedHeights[index];
	}

	public void clear(){
		changes = 0;
	}

	public byte[] getChangedHeights() {
		return Arrays.copyOf(changedHeights, changes);
	}

	/**
	 * Gets the number of changes or Integer.MAX_VALUE if there where too many changes to track
	 *
	 * @return the number of changes or Integer.MAX_VALUE
	 */
	public int getChanges(){
		return changes > changedHeights.length ? Integer.MAX_VALUE : changes;
	}
}
