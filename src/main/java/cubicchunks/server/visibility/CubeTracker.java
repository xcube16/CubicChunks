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

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.network.PacketCube;
import cubicchunks.network.PacketCubeBlockChange;
import cubicchunks.network.PacketDispatcher;
import cubicchunks.network.PacketUnloadCube;
import cubicchunks.server.chunkio.async.ICubeRequest;
import cubicchunks.util.CubePos;
import cubicchunks.util.Flushable;
import cubicchunks.util.XYZAddressable;
import cubicchunks.util.ticket.ITicket;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.cube.Cube;
import mcp.MethodsReturnNonnullByDefault;

/**
 * Cubic Chunks version of PlayerChunkMapEntry
 *
 * This class tracks a cube, and synchronizes changes to all players watching the Cube
 * (load/unload, block changes)
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CubeTracker implements XYZAddressable, ICubeRequest, ITicket, Flushable {

	private PlayerCubeTracker tracker;

	private CubePos pos;
	private Cube cube = null;
	private IProviderExtras.Requirement requestLevel = null;

	// buffers small to medium numbers of block changes
	private BlockUpdateBuffer changeBuffer = new BlockUpdateBuffer();

	// the players that can see this Cube
	private List<EntityPlayerMP> players = Lists.newArrayListWithExpectedSize(1);

	CubeTracker(PlayerCubeTracker tracker, EntityPlayerMP player, CubePos pos) {
		this.tracker = tracker;
		this.pos = pos;

		addPlayer(player);
	}

	/**
	 * Adds a player to this CubeTracker
	 * The player will now be able to see the Cube
	 *
	 * @param player the player that should see the Cube
	 */
	void addPlayer(EntityPlayerMP player) {
		if (this.players.contains(player)) {
			throw new IllegalStateException("Failed to add player. " + player + " already is in cube at " + pos);
		}

		players.add(player);

		if (checkRequirement(tracker.getPlayerReq(player))) {
			// cube is already ready, so send it
			PacketDispatcher.sendTo(new PacketCube(this.cube), player);
		}
	}

	/**
	 * Removes a player from this CubeTracker
	 * The player will no longer be able to see the Cube
	 *
	 * @param player the player that should not see the Cube
	 */
	void removePlayer(EntityPlayerMP player){
		if (!players.remove(player)) {
			throw new IllegalStateException("Failed to remove player. " + player + " is not in cube at " + pos);
		}

		if (cube == null) {
			if(players.isEmpty()){
				tracker.getProvider().cancelAsyncCube(this); // cancel the request if any
				tracker.removeCubeTracker(this);
			}
		} else {
			PacketDispatcher.sendTo(new PacketUnloadCube(pos), player);

			if (players.isEmpty()) {
				tracker.getProvider().cancelAsyncCube(this); // cancel the request if any
				cube.getTickets().remove(this);
				tracker.removeCubeTracker(this);
			}
		}
	}

	/**
	 * Checks to see if the cube we have meets the Requirement
	 *
	 * @param newReq the Requirement
	 * @return true if the cube immediately meets the Requirement, and is not null
	 */
	boolean checkRequirement(IProviderExtras.Requirement newReq) {
		if (requestLevel == null || requestLevel.compareTo(newReq) < 0) {
			requestLevel = newReq;
			tracker.getProvider().getCubeAsync(pos, newReq, this);
			return false;
		}
		return cube != null;
	}

	/**
	 * Queue's a block change to be sent to players that are viewing the Cube
	 *
	 * @param pos the position of the block
	 */
	void blockChanged(BlockPos pos) {
		if(cube != null){
			if (this.changeBuffer.getChanges() == 0) {
				this.tracker.needsFlush(this); // the first block change is being added
			}
			IBlockState state = cube.getBlockState(pos);
			changeBuffer.track(pos, state.getBlock().hasTileEntity(state));
		}
	}

	// =================================
	// ======= Interface Methods =======
	// =================================

	/**
	 * Sends all queued up changes to the players that are watching the Cube
	 */
	@Override public void flush() {
		if (cube == null || changeBuffer.getChanges() == 0) {
			return;
		}

		if (changeBuffer.getChanges() == 1) { // only one change
			// get the only block change in the buffer
			BlockPos blockpos = cube.localAddressToBlockPos(changeBuffer.getBlock(0));

			this.sendPacket(new SPacketBlockChange(this.tracker.getWorldServer(), blockpos));

			IBlockState state = this.tracker.getWorldServer().getBlockState(blockpos);
			if (state.getBlock().hasTileEntity(state)) {
				this.sendBlockEntity(this.tracker.getWorldServer().getTileEntity(blockpos));
			}
		} else if(changeBuffer.getChanges() == Integer.MAX_VALUE) { // many changes... but not too many
			List<TileEntity> newTiles = new ArrayList<>();
			for (int i = 0;i < changeBuffer.getTiles();i++) {
				BlockPos blockpos = cube.localAddressToBlockPos(changeBuffer.getTile(i));
				newTiles.add(this.tracker.getWorldServer().getTileEntity(blockpos));
			}

			sendPacket(new PacketCube(cube, newTiles));
		} else { // OMG! Lots of blocks changed! Resend the whole Cube!
			sendPacket(new PacketCubeBlockChange(cube, changeBuffer.getChangedBlocks()));

			for (int i = 0;i < changeBuffer.getTiles();i++) {
				BlockPos blockpos = cube.localAddressToBlockPos(changeBuffer.getTile(i));
				sendBlockEntity(this.tracker.getWorldServer().getTileEntity(blockpos));
			}
		}

		changeBuffer.clear();
	}

	@Override public int getX() {
		return pos.getX();
	}

	@Override public int getY() {
		return pos.getY();
	}

	@Override public int getZ() {
		return pos.getZ();
	}

	/**
	 * Called by the async getter when the Cube is ready
	 */
	@Override public void accept(@Nullable Cube cube) {
		this.cube = cube;
		if (cube != null) {
			cube.getTickets().add(this);
			sendToPlayers();
		}
	}

	/**
	 * Gets the priority of the Cube
	 * Cubes closer to a player will get higher priority than cubes far away
	 *
	 * @return A value from 0.0f to 1.0f
	 */
	@Override public float getPriority() {
		float closest = Float.MAX_VALUE;
		for (EntityPlayerMP player : players) {
			int dx = player.chunkCoordX - getX();
			int dy = player.chunkCoordY - getY();
			int dz = player.chunkCoordZ - getZ();

			float close = dx*dx + dy*dy + dz*dz;
			if (close < closest) {
				closest = close;
			}
		}
		return 1.0f / (closest + 1.0f);
	}

	/**
	 * @return true if the Cube should be ticked
	 */
	@Override public boolean shouldTick() {
		return true;
	}

	// ===============================
	// ======= Private Methods =======
	// ===============================

	private void sendToPlayers() {
		changeBuffer.clear();
		sendPacket(new PacketCube(this.cube));
	}

	private void sendBlockEntity(@Nullable TileEntity be) {
		if (be != null) {
			SPacketUpdateTileEntity spacketupdatetileentity = be.getUpdatePacket();

			if (spacketupdatetileentity != null) {
				this.sendPacket(spacketupdatetileentity);
			}
		}
	}

	/**
	 * Sends an {@link IMessage} to all players watching the Cube
	 *
	 * @param msg the {@link IMessage} to send
	 */
	private void sendPacket(IMessage msg) {
		for (EntityPlayerMP player : players) {
			PacketDispatcher.sendTo(msg, player);
		}
	}

	/**
	 * Sends a packet to all players watching the Cube
	 *
	 * @param packet the packet to send
	 */
	private void sendPacket(Packet<?> packet) {
		for (EntityPlayerMP player : players) {
			player.connection.sendPacket(packet);
		}
	}
}
