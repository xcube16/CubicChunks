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

import com.google.common.collect.Lists;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.network.PacketColumn;
import cubicchunks.network.PacketDispatcher;
import cubicchunks.network.PacketHeightChanges;
import cubicchunks.network.PacketUnloadColumn;
import cubicchunks.util.Flushable;
import cubicchunks.util.XZAddressable;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.column.Column;
import mcp.MethodsReturnNonnullByDefault;

/**
 * This class tracks a Column, and synchronizes changes to all players watching the Column
 * (load/unload, height map changes)
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ColumnTracker implements XZAddressable, Consumer<Column>, Flushable {

	private PlayerCubeTracker tracker;

	private ChunkPos pos;
	private Column column = null;
	private IProviderExtras.Requirement requestLevel = null;

	private HeightChangeBuffer changeBuffer = new HeightChangeBuffer();

	private Map<EntityPlayerMP, PlayerColumnView> playerColumns = new HashMap<>();
	private List<PlayerColumnView> players = Lists.newArrayListWithExpectedSize(1);

	ColumnTracker(PlayerCubeTracker tracker, ChunkPos pos) {
		this.tracker = tracker;
		this.pos = pos;
	}

	/**
	 * Adds a Cube to a player's view of the Column
	 * This should be called when a player is added to a CubeTracker
	 *
	 * @param player the player
	 * @param cubeY the y coordinate of the Cube
	 */
	void addCubeToPlayer(EntityPlayerMP player, int cubeY) {
		getOrAddPlayer(player).addCube(cubeY);
	}

	/**
	 * Removes a Cube from a player's view of the Column
	 * This should be called when a player is removed form a CubeTracker
	 *
	 * @param player the player
	 * @param cubeY the y coordinate of the Cube
	 */
	void removeCubeFromPlayer(EntityPlayerMP player, int cubeY) {
		playerColumns.get(player).removeCube(cubeY);
	}

	/**
	 * Queue's a height map change to be sent to players that are viewing the Column
	 *
	 * @param localX the x local coordinate of the block
	 * @param localZ the z local coordinate of the block
	 */
	void heightChanged(int localX, int localZ) {
		if (column != null) {
			if (this.changeBuffer.getChanges() == 0) {
				this.tracker.needsFlush(this); // the first block change is being added
			}
			changeBuffer.track(localX, localZ);
		}
	}

	/**
	 * Checks to see if the Column we have meets the Requirement
	 *
	 * @param newReq the Requirement
	 * @return true if the Column immediately meets the Requirement, and is not null
	 */
	boolean checkRequirement(IProviderExtras.Requirement newReq){
		if (requestLevel == null || requestLevel.compareTo(newReq) < 0) {
			requestLevel = newReq;
			tracker.getProvider().getColumnAsync(pos, newReq, this);
			return false;
		}
		return column != null;
	}

	/**
	 * Check to see if a player watching the Column
	 *
	 * @param player the player to check for
	 * @return true if the player is watching the Column
	 */
	boolean isPlayerWatching(EntityPlayerMP player) {
		return column != null && playerColumns.get(player) != null;
	}

	// =================================
	// ======= Interface Methods =======
	// =================================

	/**
	 * Sends all queued up changes to the players that are watching the Column
	 */
	@Override public void flush() {
		if (changeBuffer.getChanges() == Integer.MAX_VALUE) {
			sendPacket(new PacketColumn(column, false));
		} else {
			sendPacket(new PacketHeightChanges(column, changeBuffer.getChangedHeights()));
		}
		changeBuffer.clear();
	}

	@Override public int getX() {
		return pos.chunkXPos;
	}

	@Override public int getZ() {
		return pos.chunkZPos;
	}

	/**
	 * Called by the async getter when the Column is ready
	 */
	@Override public void accept(@Nullable Column column) {
		this.column = column;

		if (column != null) {
			changeBuffer.clear();
			sendPacket(new PacketColumn(column));
		}
	}

	// ===============================
	// ======= Private Methods =======
	// ===============================

	/**
	 * Gets a Player's view of this Column, or creates one
	 *
	 * @param player the player we want a view for
	 * @return the player's view of the Column
	 */
	private PlayerColumnView getOrAddPlayer(EntityPlayerMP player){
		PlayerColumnView pt = playerColumns.get(player);
		if (pt == null) {

			pt = new PlayerColumnView(player);
			playerColumns.put(player, pt);
			players.add(pt);

			if (checkRequirement(tracker.getPlayerReq(player))) {
				// column is already ready, so send it
				PacketDispatcher.sendTo(new PacketColumn(this.column), player);
			}
		}
		return pt;
	}

	private void removePlayer(EntityPlayerMP player) {
		players.remove(playerColumns.remove(player));


		if (this.column == null) {
			if (players.isEmpty()) {
				tracker.getProvider().cancelAsyncColumn(this); // cancel the request if any
				tracker.removeColumnTracker(this);
			}
		} else {
			PacketDispatcher.sendTo(new PacketUnloadColumn(pos), player);

			if (players.isEmpty()) {
				tracker.getProvider().cancelAsyncColumn(this); // cancel the request if any
				tracker.removeColumnTracker(this);
			}
		}
	}


	private void sendPacket(IMessage msg) {
		for (PlayerColumnView player : players) {
			PacketDispatcher.sendTo(msg, player.player);
		}
	}

	private class PlayerColumnView {

		int maxCubeY = Integer.MIN_VALUE;
		int minCubeY = Integer.MIN_VALUE;

		EntityPlayerMP player;

		private PlayerColumnView(EntityPlayerMP player) {
			this.player = player;
		}

		private void addCube(int cubeY) {
			if(maxCubeY == Integer.MIN_VALUE){
				maxCubeY = minCubeY = cubeY;
			}else if(cubeY < minCubeY){
				minCubeY = cubeY;
			}else if(cubeY > maxCubeY){
				maxCubeY = cubeY;
			}
		}

		private void removeCube(int cubeY) {
			if (maxCubeY == cubeY) {
				do {
					maxCubeY--;
				} while (maxCubeY >= minCubeY && tracker.getCubeTracker(getX(), maxCubeY, getZ()) == null);
			} else if (minCubeY == cubeY) {
				do {
					minCubeY++;
				} while (maxCubeY >= minCubeY && tracker.getCubeTracker(getX(), minCubeY, getZ()) == null);
			} else {
				return;
			}

			if(maxCubeY < minCubeY) {
				// the player can no longer see cubes in this column
				removePlayer(player);
			}
		}
	}
}
