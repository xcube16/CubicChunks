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

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;
import java.util.Iterator;

public interface IPCM_Vanilla {




	/**
	 * Returns the WorldServer associated with this PlayerManager
	 */
	WorldServer getWorldServer();

	/**
	 * Gets a list of Columns that should be ticked...
	 * Implement this to use the ticket system
	 *
	 * @return A list of Columns that should be ticked
	 */
	Iterator<Chunk> getChunkIterator();

	/**
	 * updates all the player instances that need to be updated
	 */
	void tick();

	/**
	 * Called by World when it saves Chunks, and a NO-OP is safe in that case.
	 * For mod support, (maybe a minimap needs to know what Chunks can be seen by players),
	 * this should be implemented to as 'can a players see Column'
	 *
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return Weather any player can see the Column
	 */
	boolean contains(int columnX, int columnZ);

	/**
	 * Used only in WorldEntitySpawner, and that is not used in Cubic Chunks!
	 * Implementations can just return null
	 *
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return Garbage :P
	 */
	@Nullable
	PlayerChunkMapEntry getEntry(int columnX, int columnZ);

	/**
	 * Called when a block changes and needs to be resent to players that can see the block
	 *
	 * @param pos The position of the block that changed
	 */
	void markBlockForUpdate(BlockPos pos);

	/**
	 * Adds a player
	 *
	 * @param player the player to add
	 */
	void addPlayer(EntityPlayerMP player);

	/**
	 * Removes a player
	 *
	 * @param player the player being removed
	 */
	void removePlayer(EntityPlayerMP player);

	/**
	 * THIS METHOD WAS MISS NAMED BY MCP!!! IT IS NOT A SPECIAL CASE!!!<br/>
	 * This method is called when a player moves, and should be used to update cubes around them
	 *
	 * @param player the player that moved
	 */
	void updateMountedMovingPlayer(EntityPlayerMP player);

	/**
	 * Checks to see if a player can see a Column. This method is called by EntityTrackerEntry.
	 * TODO: EntityTrackerEntry should use Cubes not Columns
	 *
	 * @param player The player in quest
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return weather or not {@code player} can see the Column
	 */
	boolean isPlayerWatchingChunk(EntityPlayerMP player, int columnX, int columnZ);

	/**
	 * Called when the view distance changes
	 *
	 * @param radius the new view distance
	 */
	void setPlayerViewRadius(int radius);

	/**
	 * just throw an unsupported exception
	 */
	void addEntry(PlayerChunkMapEntry entry);

	/**
	 * just throw an unsupported exception
	 */
	void removeEntry(PlayerChunkMapEntry entry);
}
