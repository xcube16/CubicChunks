package cubicchunks.server.experimental;

import com.google.common.collect.AbstractIterator;

import cubicchunks.util.Coords;
import cubicchunks.util.XYZMap;
import cubicchunks.world.column.Column;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by thomas on 10/21/16.
 */
public class PlayerCubeTracker implements  IPCM_Vanilla {

	private XYZMap<TrackerUnit> unitsMap = new XYZMap<>(0.75F, 4000);
	private List<TrackerUnit>   units   = new ArrayList<>();

	public PlayerCubeTracker() {

	}
	/**
	 * updates all the player instances that need to be updated
	 */
	public void tick();

	/**
	 * Called when a block changes and needs to be resent to players that can see the block
	 *
	 * @param pos The position of the block that changed
	 */
	public void markBlockForUpdate(BlockPos pos) {
		TrackerUnit unit = this.getUnit(
			Coords.blockToCube(pos.getX()),
			Coords.blockToCube(pos.getY()),
			Coords.blockToCube(pos.getZ()));

		if (unit != null) {
			unit.blockChanged(pos);
		}
	}

	/**
	 * Adds a player
	 *
	 * @param player the player to add
	 */
	public void addPlayer(EntityPlayerMP player);

	/**
	 * Removes a player
	 *
	 * @param player the player being removed
	 */
	public void removePlayer(EntityPlayerMP player);

	/**
	 * THIS METHOD WAS MISS NAMED BY MCP!!! IT IS NOT A SPECIAL CASE!!!<br/>
	 * This method is called when a player moves, and should be used to update cubes around them
	 *
	 * @param player the player that moved
	 */
	public void updateMountedMovingPlayer(EntityPlayerMP player);

	/**
	 * Checks to see if a player can see a Column. This method is called by EntityTrackerEntry.
	 * TODO: EntityTrackerEntry should use Cubes not Columns
	 *
	 * @param player The player in quest
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return weather or not {@code player} can see the Column
	 */
	public boolean isPlayerWatchingChunk(EntityPlayerMP player, int columnX, int columnZ);

	/**
	 * Called when the view distance changes
	 *
	 * @param radius the new view distance
	 */
	public void setPlayerViewRadius(int radius);

	/**
	 * Returns the WorldServer associated with this PlayerManager
	 */
	public WorldServer getWorldServer();

	// ====================
	// ===== garbage ======
	// ====================

	/**
	 * Called by World when it saves Chunks, and a NO-OP is safe in that case.
	 * For mod support, (maybe a minimap needs to know what Chunks can be seen by players),
	 * this should be implemented to as 'can a players see Column'
	 *
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return Weather any player can see the Column
	 */
	public boolean contains(int columnX, int columnZ) {
		return false;
	}

	/**
	 * Used only in WorldEntitySpawner, and that is not used in Cubic Chunks!
	 * Implementations can just return null
	 *
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return Garbage :P
	 */
	@Nullable
	public PlayerChunkMapEntry getEntry(int columnX, int columnZ) {
		return null;
	}

	/**
	 * just throw an unsupported exception
	 */
	public void addEntry(PlayerChunkMapEntry entry) {
		throw new UnsupportedOperationException();
	}

	/**
	 * just throw an unsupported exception
	 */
	public void removeEntry(PlayerChunkMapEntry entry) {
		throw new UnsupportedOperationException();
	}

	// ================================================
	// ===== Hooks that don't have to do with PCM =====
	// ================================================

	/**
	 * Gets a list of Columns that should be ticked...
	 * Implement this to use the ticket system
	 *
	 * @return A list of Columns that should be ticked
	 */
	@Override
	@Deprecated // just a hook so we can tell vanilla what Columns to tick
	public Iterator<Chunk> getChunkIterator() {
		// GIVE TICKET SYSTEM FULL CONTROL
		Iterator<Chunk> chunkIt = this.cubeCache.getLoadedChunks().iterator();
		return new AbstractIterator<Chunk>() {
			@Override protected Chunk computeNext() {
				while (chunkIt.hasNext()) {
					Column column = (Column)chunkIt.next();
					if(column.shouldTick()) { // shouldTick is true when there Cubes with tickets the request to be ticked
						return column;
					}
				}
				return this.endOfData();
			}
		};
	}

	// ===============================
	// ======= Private Methods =======
	// ===============================

	@Nullable
	private TrackerUnit getUnit(int cubeX, int cubeY, int cubeZ) {
		return unitsMap.get(cubeX, cubeY, cubeZ);
	}
}
