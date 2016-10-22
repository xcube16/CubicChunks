package cubicchunks.server.experimental;

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

import cubicchunks.network.PacketCube;
import cubicchunks.network.PacketCubeBlockChange;
import cubicchunks.network.PacketDispatcher;
import cubicchunks.network.PacketUnloadCube;
import cubicchunks.util.AddressTools;
import cubicchunks.util.Coords;
import cubicchunks.util.CubePos;
import cubicchunks.util.XYZAddressable;
import cubicchunks.util.ticket.ITicket;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.cube.Cube;

public class TrackerUnit implements XYZAddressable, ICubeRequest, ITicket {

	private PlayerCubeTracker tracker;

	private CubePos pos;
	private Cube cube = null;
	private IProviderExtras.Requirement requestLevel = null;

	private BlockUpdateBuffer changeBuffer = new BlockUpdateBuffer();

	private List<EntityPlayerMP> players = Lists.newArrayListWithExpectedSize(1);

	public TrackerUnit(PlayerCubeTracker tracker, EntityPlayerMP player, CubePos pos){
		this.tracker = tracker;
		this.pos = pos;

		addPlayer(player);
	}

	void addPlayer(EntityPlayerMP player){
		if (this.players.contains(player)) {
			throw new IllegalStateException("Failed to add player. " + player + " already is in cube at " + coords);
		}

		checkRequirement(tracker.getPlayerReq(player));

		players.add(player);
		if(cube != null){
			sendToPlayer(player);
		}
	}

	void checkRequirement(IProviderExtras.Requirement newReq){
		if(requestLevel == null || requestLevel.compareTo(newReq) < 0){
			tracker.getCubeProvider().getCubeAsync(this, newReq);
		}
	}

	void removePlayer(EntityPlayerMP player){
		if(!players.remove(player)){
			throw new IllegalStateException("Failed to remove player. " + player + " is not in cube at " + coords);
		}

		if(cube == null){
			if(players.isEmpty()){
				tracker.getCubeProvider().cancelAsyncCube(this); // cancel the request if any
				tracker.removeUnit(this);
			}
		}else{
			PacketDispatcher.sendTo(new PacketUnloadCube(coords.getAddress()), player);

			if(players.isEmpty()){
				cube.getTickets().remove(this);
				tracker.removeUnit(this);
			}
		}
	}

	void blockChanged(BlockPos pos) {
		if(cube != null){
			IBlockState state = cube.getBlockState(pos);
			changeBuffer.track(pos, state.getBlock().hasTileEntity(state));
		}
	}

	void flush(){
		if(cube == null || changeBuffer.getChanges() == 0){
			return;
		}

		if (changeBuffer.getChanges() == 1) {
			// get the only block change in the buffer
			BlockPos blockpos = cube.localAddressToBlockPos(changeBuffer.getBlock(0));

			this.sendPacket(new SPacketBlockChange(this.tracker.getWorldServer(), blockpos));

			IBlockState state = this.tracker.getWorldServer().getBlockState(blockpos);
			if (state.getBlock().hasTileEntity(state)) {
				this.sendBlockEntity(this.tracker.getWorldServer().getTileEntity(blockpos));
			}
		} else if(changeBuffer.getChanges() == Integer.MAX_VALUE) {
			List<TileEntity> newTiles = new ArrayList<>();
			for (int i = 0;i < changeBuffer.getTiles();i++) {
				BlockPos blockpos = cube.localAddressToBlockPos(changeBuffer.getTile(i));
				newTiles.add(this.tracker.getWorldServer().getTileEntity(blockpos));
			}

			sendPacket(new PacketCube(cube, newTiles));
		}else{
			sendPacket(new PacketCubeBlockChange(cube, changeBuffer.getChangedBlocks()));

			for (int i = 0;i < changeBuffer.getTiles();i++) {
				BlockPos blockpos = cube.localAddressToBlockPos(changeBuffer.getTile(i));
				sendBlockEntity(this.tracker.getWorldServer().getTileEntity(blockpos));
			}
		}

		changeBuffer.clear();
	}

	// =================================
	// ======= Interface Methods =======
	// =================================

	@Override
	public int getX() {
		return pos.getX();
	}

	@Override
	public int getY() {
		return pos.getY();
	}

	@Override
	public int getZ() {
		return pos.getZ();
	}

	@Override
	public void accept(Cube cube) {
		this.cube = cube;
		cube.getTickets().add(this);

		sendToPlayers();
	}

	@Override
	public float getPriroity() {
		float lowest = 1024.0f;
		for(EntityPlayerMP player : players){
			int dx = player.chunkCoordX - getX();
			int dy = player.chunkCoordY - getY();
			int dz = player.chunkCoordZ - getZ();

			float priority = dx*dx + dy*dy + dz*dz;
			if(priority < lowest){
				lowest = priority;
			}
		}
		return lowest;
	}

	@Override
	public boolean shouldTick() {
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
	 * Sends an {@link IMessage} to all players viewing this cube
	 *
	 * @param msg the {@link IMessage} to send
	 */
	private void sendPacket(IMessage msg) {
		for (EntityPlayerMP player : players) {
			PacketDispatcher.sendTo(msg, player);
		}
	}

	/**
	 * Sends a packet to all players viewing this cube
	 *
	 * @param packet the packet to send
	 */
	private void sendPacket(Packet<?> packet) {
		for (EntityPlayerMP player : players) {
			player.connection.sendPacket(packet);
		}
	}
}
