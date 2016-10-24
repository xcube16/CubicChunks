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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;

import cubicchunks.debug.DebugCapability;
import cubicchunks.server.CubeProviderServer;
import cubicchunks.util.Coords;
import cubicchunks.util.CubePos;
import cubicchunks.util.XYZMap;
import cubicchunks.util.XZMap;
import cubicchunks.world.ICubicWorldServer;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.column.Column;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PlayerCubeTracker extends PlayerChunkMap {

	private XYZMap<TrackerUnit> unitsMap = new XYZMap<>(0.75F, 4000);
	private XZMap<TrackerUnit2D> unit2DsMap = new XZMap<>(0.75F, 500);

	// A list of all units to be flushed on the next tick
	private List<Flushable> flushQueue = Lists.newArrayList();

	private Map<EntityPlayerMP, IViewFormula> formulas = new HashMap<>();

	private CubeProviderServer provider;

	public PlayerCubeTracker(ICubicWorldServer worldServer) {
		super((WorldServer) worldServer);
		this.provider = worldServer.getCubeCache();
	}

	/**
	 * Marks a position's height so it will be re-sent to clients
	 *
	 * @param x the x coordinate in block coordinates
	 * @param z the z coordinate in block coordinates
	 */
	public void markHeightForUpdate(int x, int z) {
		TrackerUnit2D unit = this.getUnit2D(
			Coords.blockToCube(x),
			Coords.blockToCube(z));

		if (unit != null) {
			unit.heightChanged(
				Coords.blockToLocal(x),
				Coords.blockToLocal(z));
		}
	}

	IProviderExtras getProvider() {
		return provider;
	}

	@Nullable
	TrackerUnit getUnit(int cubeX, int cubeY, int cubeZ) {
		return unitsMap.get(cubeX, cubeY, cubeZ);
	}

	void needsFlush(Flushable unit) {
		flushQueue.add(unit);
	}

	void removeUnit(TrackerUnit unit) {
		unitsMap.remove(unit);
		flushQueue.remove(unit);
	}

	void removeUnit2D(TrackerUnit2D unit) {
		unit2DsMap.remove(unit);
		flushQueue.remove(unit);
	}

	IProviderExtras.Requirement getPlayerReq(EntityPlayerMP player) {
		if(player.hasCapability(DebugCapability.CAPABILITY, null)){
			return player.getCapability(DebugCapability.CAPABILITY, null).getRequirement();
		} else {
			return IProviderExtras.Requirement.LIGHT;
		}
	}

	// =================================
	// ======= Interface Methods =======
	// =================================

	/**
	 * Does what ever needs to be done on world tick
	 * This method is called by WorldServer.tick() just after updating blocks
	 */
	@Override
	public void tick() {
		for (Flushable unit : this.flushQueue) {
			unit.flush();
		}
		this.flushQueue.clear();
	}

	/**
	 * Called when a block changes and needs to be resent to players that can see the block
	 *
	 * @param pos The position of the block that changed
	 */
	@Override
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
	@Override
	public void addPlayer(EntityPlayerMP player) {
		IViewFormula formula = new VanillaViewFormula(player);
		formulas.put(player, formula);

		formula.computePositions((x, y, z) -> addPlayerToUnit(player, x, y, z));
		//TODO: provide re-sort hint to async cube getting system
	}

	/**
	 * Removes a player
	 *
	 * @param player the player being removed
	 */
	@Override
	public void removePlayer(EntityPlayerMP player) {
		// getUnit() should not return null, if it does, its an inconsistency and should crash hard!
		formulas.remove(player).computePositions((x, y, z) ->
			removePlayerFromUnit(player, x, y, z));
		//TODO: provide re-sort hint to async cube getting system (maybe its not important here?)
	}

	/**
	 * THIS METHOD WAS MISS NAMED BY MCP!!! IT IS NOT A SPECIAL CASE!!!<br/>
	 * This method is called when a player moves, and should be used to update cubes around them
	 *
	 * @param player the player that moved
	 */
	@Override
	public void updateMovingPlayer(EntityPlayerMP player) {
		IViewFormula oldView = formulas.get(player);
		IViewFormula newView = oldView.next(player);
		if(newView == null){
			return;
		}

		updateView(player, oldView, newView);

		formulas.put(player, newView);
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
	@Override
	public boolean isPlayerWatchingChunk(EntityPlayerMP player, int columnX, int columnZ) {
		TrackerUnit2D unit = getUnit2D(columnX, columnZ);
		return unit != null && unit.isPlayerWatching(player);
	}

	/**
	 * Called when the view distance changes
	 *
	 * @param radius the new view distance
	 */
	@Override
	public void setPlayerViewRadius(int radius) {
		if(formulas == null) {
			return; // PlayerChunkMap's constructor calls this method, so this is a work-a-round
		}

		for(Map.Entry<EntityPlayerMP, IViewFormula> entry : formulas.entrySet()){
			EntityPlayerMP player = entry.getKey();
			IViewFormula oldView = entry.getValue();
			IViewFormula newView = new VanillaViewFormula(radius, player);

			updateView(player, oldView, newView);

			entry.setValue(newView);
		}
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

	/**
	 * Used only in WorldEntitySpawner, and that is not used in Cubic Chunks!
	 * Implementations can just return null
	 *
	 * @param columnX the X coord of the Column
	 * @param columnZ the Z coord of the Column
	 * @return Garbage :P
	 */
	@Nullable
	@Override
	@Deprecated
	public PlayerChunkMapEntry getEntry(int columnX, int columnZ) {
		return null;
	}

	/**
	 * just throw an unsupported exception
	 */
	@Override
	@Deprecated
	public void entryChanged(PlayerChunkMapEntry entry) {
		throw new UnsupportedOperationException();
	}

	/**
	 * just throw an unsupported exception
	 */
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

	@Nullable
	private TrackerUnit2D getUnit2D(int columnX, int columnZ) {
		return unit2DsMap.get(columnX, columnZ);
	}

	private void addPlayerToUnit(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
		TrackerUnit2D unit2d = getUnit2D(cubeX, cubeZ);
		if(unit2d == null) {
			unit2d = new TrackerUnit2D(this, new ChunkPos(cubeX, cubeZ));
			unit2DsMap.put(unit2d);
		}
		unit2d.addUnitToPlayer(player, cubeY);

		TrackerUnit unit = getUnit(cubeX, cubeY, cubeZ);
		if(unit == null){
			unit = new TrackerUnit(this, player, new CubePos(cubeX, cubeY, cubeZ));
			unitsMap.put(unit);
		} else {
			unit.addPlayer(player);
		}
	}

	/**
	 * Just a helper method so other parts dont need to bother with Column trackers
	 */
	private void removePlayerFromUnit(EntityPlayerMP player, int cubeX, int cubeY, int cubeZ) {
		TrackerUnit unit = getUnit(cubeX, cubeY, cubeZ);
		getUnit2D(cubeX, cubeZ).removeUnitFromPlayer(player, unit);
		unit.removePlayer(player);
	}

	private void updateView(EntityPlayerMP player, IViewFormula oldView, IViewFormula newView) {
		// find cubes that don't show up on the newView (droped Cubes)
		oldView.computePositions((x, y, z) -> {
			if(!newView.contains(x, y, z)) {
				removePlayerFromUnit(player, x, y, z);
			}
		});

		// find the cubes that don't show up on aPlayerView (new Cubes)
		newView.computePositions((x, y, z) -> {
			if(!oldView.contains(x, y, z)){
				addPlayerToUnit(player, x, y, z);
			}
		});
	}
}
