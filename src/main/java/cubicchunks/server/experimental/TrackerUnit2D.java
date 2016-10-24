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

import com.google.common.collect.Lists;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import cubicchunks.network.PacketColumn;
import cubicchunks.network.PacketDispatcher;
import cubicchunks.network.PacketHeightChanges;
import cubicchunks.network.PacketUnloadColumn;
import cubicchunks.util.XZAddressable;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.column.Column;

public class TrackerUnit2D implements XZAddressable, Consumer<Column>, Flushable {

	private PlayerCubeTracker tracker;

	private ChunkPos pos;
	private Column column = null;
	private IProviderExtras.Requirement requestLevel = null;

	private HeightChangeBuffer changeBuffer = new HeightChangeBuffer();

	private Map<EntityPlayerMP, PlayerColumnTracker> playerColumns = new HashMap<>();
	private List<PlayerColumnTracker> players = Lists.newArrayListWithExpectedSize(1);

	TrackerUnit2D(PlayerCubeTracker tracker, ChunkPos pos){
		this.tracker = tracker;
		this.pos = pos;
	}

	private PlayerColumnTracker getOrAddPlayer(EntityPlayerMP player){
		PlayerColumnTracker pt = playerColumns.get(player);
		if(pt == null){

			pt = new PlayerColumnTracker(player);
			playerColumns.put(player, pt);
			players.add(pt);

			if(checkRequirement(tracker.getPlayerReq(player))){
				// column is already ready, so send it
				PacketDispatcher.sendTo(new PacketColumn(this.column), player);
			}
		}
		return pt;
	}

	void addUnitToPlayer(EntityPlayerMP player, int cubeY) {
		getOrAddPlayer(player).addUnit(cubeY);
	}

	void removeUnitFromPlayer(EntityPlayerMP player, TrackerUnit unit) {
		playerColumns.get(player).removeUnit(unit);
	}

	void heightChanged(int localX, int localZ) {
		if(column != null){
			if (this.changeBuffer.getChanges() == 0) {
				this.tracker.needsFlush(this); // the first block change is being added
			}
			changeBuffer.track(localX, localZ);
		}
	}

	/**
	 * Checks to see if the column we have meets the Requirement
	 *
	 * @param newReq the Requirement
	 * @return true if the column immediately meets the Requirement, and is not null
	 */
	boolean checkRequirement(IProviderExtras.Requirement newReq){
		if(requestLevel == null || requestLevel.compareTo(newReq) < 0){
			requestLevel = newReq;
			tracker.getProvider().getColumnAsync(pos, newReq, this);
			return false;
		}
		return column != null;
	}

	boolean isPlayerWatching(EntityPlayerMP player) {
		return column != null && playerColumns.get(player) != null;
	}

	// =================================
	// ======= Interface Methods =======
	// =================================

	public void flush() {
		if(changeBuffer.getChanges() == Integer.MAX_VALUE) {
			sendPacket(new PacketColumn(column, false));
		}else{
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

	@Override public void accept(Column column) {
		this.column = column;

		sendPacket(new PacketColumn(column));
	}

	// ===============================
	// ======= Private Methods =======
	// ===============================

	private void removePlayer(EntityPlayerMP player){
		players.remove(playerColumns.remove(player));


		if(this.column == null){
			if(players.isEmpty()){
				tracker.getProvider().cancelAsyncColumn(this); // cancel the request if any
				tracker.removeUnit2D(this);
			}
		}else{
			PacketDispatcher.sendTo(new PacketUnloadColumn(pos), player);

			if(players.isEmpty()){
				tracker.getProvider().cancelAsyncColumn(this); // cancel the request if any
				tracker.removeUnit2D(this);
			}
		}
	}


	private void sendPacket(IMessage msg) {
		for (PlayerColumnTracker player : players) {
			PacketDispatcher.sendTo(msg, player.player);
		}
	}

	private class PlayerColumnTracker {

		int maxCubeY = Integer.MIN_VALUE;
		int minCubeY = Integer.MIN_VALUE;

		EntityPlayerMP player;

		public PlayerColumnTracker(EntityPlayerMP player) {
			this.player = player;
		}

		public void addUnit(int cubeY) {
			if(maxCubeY == Integer.MIN_VALUE){
				maxCubeY = minCubeY = cubeY;
			}else if(cubeY < minCubeY){
				minCubeY = cubeY;
			}else if(cubeY > maxCubeY){
				maxCubeY = cubeY;
			}
		}

		public void removeUnit(TrackerUnit unit) {
			if (maxCubeY == unit.getY()) {
				do {
					maxCubeY--;
				} while (maxCubeY >= minCubeY && tracker.getUnit(getX(), maxCubeY, getZ()) == null);
			} else if (minCubeY == unit.getY()) {
				do {
					minCubeY++;
				} while (maxCubeY >= minCubeY && tracker.getUnit(getX(), minCubeY, getZ()) == null);
			} else {
				return;
			}

			if(maxCubeY < minCubeY){
				// the player can no longer see cubes in this column
				removePlayer(player);
			}
		}
	}
}
