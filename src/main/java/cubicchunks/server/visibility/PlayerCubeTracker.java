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
package cubicchunks.server.visibility;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.debug.DebugCapability;
import cubicchunks.server.CubeProviderServer;
import cubicchunks.util.Coords;
import cubicchunks.util.CubePos;
import cubicchunks.util.Flushable;
import cubicchunks.util.XYZMap;
import cubicchunks.util.XZMap;
import cubicchunks.visibility.IViewFormula;
import cubicchunks.visibility.VanillaViewFormula;
import cubicchunks.world.ICubicWorldServer;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.column.Column;

/**
 * This class keeps track of what parts of the world can be seen by players,
 * and sends updates to players accordingly
 * Compatibility for PlayerChunkMap
 */
@ParametersAreNonnullByDefault
//@MethodsReturnNonnullByDefault vary annoying for getCubeTracker() and getColumnTacker()
// as there nullability depends on the args
public class PlayerCubeTracker extends PlayerChunkMap {

	private XYZMap<CubeTracker> cubeTrackers = new XYZMap<>(0.75F, 4000);
	private XZMap<ColumnTracker> columnTrackers = new XZMap<>(0.75F, 500);

	// A list of all trackers to be flushed on the next tick
	private List<Flushable> flushQueue = Lists.newArrayList();

	private Map<EntityPlayerMP, IViewFormula> formulas = new HashMap<>();

	private CubeProviderServer provider;

	public PlayerCubeTracker(ICubicWorldServer worldServer) {
		super((WorldServer) worldServer);
		this.provider = worldServer.getCubeCache();
	}

	/**
	 * Marks a position's height to be re-sent to clients
	 *
	 * @param x the x coordinate in block coordinates
	 * @param z the z coordinate in block coordinates
	 */
	public void markHeightForUpdate(int x, int z) {
		ColumnTracker unit = this.getColumnTracker(
			Coords.blockToCube(x),
			Coords.blockToCube(z));

		if (unit != null) {
			unit.heightChanged(
				Coords.blockToLocal(x),
				Coords.blockToLocal(z));
		}
	}

	/**
	 * @return the provider used to get Cubes and Columns
	 */
	@Nonnull
	IProviderExtras getProvider() {
		return provider;
	}

	CubeTracker getCubeTracker(int cubeX, int cubeY, int cubeZ) {
		return cubeTrackers.get(cubeX, cubeY, cubeZ);
	}

	/**
	 * Queue's a Flushable (Cube or Column tracker) to be flushed
	 */
	void needsFlush(Flushable tracker) {
		flushQueue.add(tracker);
	}

	void removeCubeTracker(CubeTracker tracker) {
		cubeTrackers.remove(tracker);
		flushQueue.remove(tracker);
	}

	void removeColumnTracker(ColumnTracker tracker) {
		columnTrackers.remove(tracker);
		flushQueue.remove(tracker);
	}

	/**
	 * Get a player's Requirement Cube/Column level
	 * (some spectators cant generate Cubes so this is useful for that and more)
	 *
	 * @param player the player
	 * @return the requirement level to be used when getting Cubes/Columns
	 */
	@Nonnull
	IProviderExtras.Requirement getPlayerReq(EntityPlayerMP player) {
		if(player.hasCapability(DebugCapability.CAPABILITY, null)){
			return player.getCapability(DebugCapability.CAPABILITY, null).getRequirement();
		} else {
			return IProviderExtras.Requirement.LIGHT;
		}
	}

	/**
	 * Checks the Requirement level for all Cubes that {@code player} can see
	 * This should be called when a player's Requirement changes
	 *
	 * @param player the player to refresh
	 */
	public void refreshPlayerReq(EntityPlayerMP player) {
		IViewFormula formula = formulas.get(player);
		if (formula == null) {
			return;
		}

		IProviderExtras.Requirement req = getPlayerReq(player);
		formula.computePositions((x, y, z) -> {
			getColumnTracker(x, z).checkRequirement(req);
			getCubeTracker(x, y, z).checkRequirement(req);
		});
	}

	// =================================
	// ======= Interface Methods =======
	// =================================

	/**
	 * Does what ever needs to be done on world tick
	 * This method is called by WorldServer.tick() just after updating blocks
	 */
	@Override public void tick() {
		for (Flushable tracker : this.flushQueue) {
			tracker.flush();
		}
		this.flushQueue.clear();
	}

	/**
	 * Called when a block changes and needs to be resent to players that can see the block
	 *
	 * @param pos The position of the block that changed
	 */
	@Override public void markBlockForUpdate(BlockPos pos) {
		CubeTracker unit = this.getCubeTracker(
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
	@Override public void addPlayer(EntityPlayerMP player) {
		IViewFormula formula = new VanillaViewFormula(player);
		formulas.put(player, formula);

		formula.computePositions((x, y, z) -> addPlayerToTracker(player, x, y, z));
		//TODO: provide re-sort hint to async cube getting system
	}

	/**
	 * Removes a player
	 *
	 * @param player the player being removed
	 */
	@Override public void removePlayer(EntityPlayerMP player) {
		// getCubeTracker() should not return null, if it does, its an inconsistency and should crash hard!
		formulas.remove(player).computePositions((x, y, z) ->
			removePlayerFromTracker(player, x, y, z));
		//TODO: provide re-sort hint to async cube getting system (maybe its not important here?)
	}

	/**
	 * THIS METHOD WAS MISS NAMED BY MCP!!! IT IS NOT A SPECIAL CASE!!!<br/>
	 * This method is called when a player moves, and should be used to update cubes around them
	 *
	 * @param player the player that moved
	 */
	@Override public void updateMovingPlayer(EntityPlayerMP player) {
		if(updateView(player, formulas.get(player).next(player))){
			return;
		}
		//TODO: provide re-sort hint to async cube getting system
	}

	/**
	 * Checks to see if a player can see a Column. This method is called by EntityTrackerEntry.
	 * TODO: EntityTrackerEntry should use Cubes not Columns
	 *
	 * @param player The player in quest
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return weather or not {@code player} can see the Column
	 */
	@Override public boolean isPlayerWatchingChunk(EntityPlayerMP player, int columnX, int columnZ) {
		ColumnTracker tracker = getColumnTracker(columnX, columnZ);
		return tracker != null && tracker.isPlayerWatching(player);
	}

	/**
	 * Called by when the view distance changes
	 *
	 * @param radius Any number you want! It will be ignored! :P
	 */
	@Override public void setPlayerViewRadius(int radius) {
		if(formulas == null) {
			return; // PlayerChunkMap's constructor calls this method, so this is a work-a-round
		}

		formulas.entrySet().forEach(entry -> {
			EntityPlayerMP player = entry.getKey();
			updateView(player, entry.getValue().next(player));
		});
		//TODO: provide re-sort hint to async cube getting system (maybe its not important here?)
	}

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
	@Override
	@Deprecated
	public boolean contains(int columnX, int columnZ) {
		return false;
	}

	@Nullable
	@Override
	@Deprecated
	public PlayerChunkMapEntry getEntry(int columnX, int columnZ) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void entryChanged(PlayerChunkMapEntry entry) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
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
	@Nonnull
	public Iterator<Chunk> getChunkIterator() {
		// GIVE TICKET SYSTEM FULL CONTROL
		Iterator<Chunk> chunkIt = this.provider.getLoadedChunks().iterator();
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

	private ColumnTracker getColumnTracker(int columnX, int columnZ) {
		return columnTrackers.get(columnX, columnZ);
	}

	/**
	 * Adds a player to a CubeTracker at the given location
	 * (players are added to ColumnTrackers automatically)
	 *
	 * @param player the player that should see the Cube
	 * @param cubeX the x coordinate of the Cube
	 * @param cubeY the y coordinate of the Cube
	 * @param cubeZ the z coordinate of the Cube
	 */
	private void addPlayerToTracker(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
		ColumnTracker columnTracker = getColumnTracker(cubeX, cubeZ);
		if(columnTracker == null) {
			columnTracker = new ColumnTracker(this, new ChunkPos(cubeX, cubeZ));
			columnTrackers.put(columnTracker);
		}
		columnTracker.addCubeToPlayer(player, cubeY);

		CubeTracker cubeTracker = getCubeTracker(cubeX, cubeY, cubeZ);
		if(cubeTracker == null){
			cubeTracker = new CubeTracker(this, player, new CubePos(cubeX, cubeY, cubeZ));
			cubeTrackers.put(cubeTracker);
		} else {
			cubeTracker.addPlayer(player);
		}
	}

	/**
	 * Just a helper method so other parts don't need to bother with Column trackers
	 */
	private void removePlayerFromTracker(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
		CubeTracker tracker = getCubeTracker(cubeX, cubeY, cubeZ);
		getColumnTracker(cubeX, cubeZ).removeCubeFromPlayer(player, tracker.getY());
		tracker.removePlayer(player);
	}

	/**
	 * Updates a player's visible area
	 *
	 * @param player The player
	 * @param newView The new view formula
	 * @return true if the update was successful
	 */
	private boolean updateView(EntityPlayerMP player, @Nullable IViewFormula newView) {
		if (newView == null) {
			return false;
		}
		IViewFormula oldView = formulas.get(player);

		// find cubes that don't show up on the newView (droped Cubes)
		oldView.computePositions((x, y, z) -> {
			if(!newView.contains(x, y, z)) {
				removePlayerFromTracker(player, x, y, z);
			}
		});

		// find the cubes that don't show up on aPlayerView (new Cubes)
		newView.computePositions((x, y, z) -> {
			if(!oldView.contains(x, y, z)){
				addPlayerToTracker(player, x, y, z);
			}
		});

		formulas.put(player, newView);
		return true;
	}
}
