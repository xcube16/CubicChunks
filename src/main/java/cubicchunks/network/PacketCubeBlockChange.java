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
package cubicchunks.network;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.cursors.IntCursor;

import gnu.trove.TShortCollection;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.util.AddressTools;
import cubicchunks.util.CubePos;
import cubicchunks.world.cube.Cube;
import io.netty.buffer.ByteBuf;
import mcp.MethodsReturnNonnullByDefault;

import static net.minecraftforge.fml.common.network.ByteBufUtils.readVarInt;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class PacketCubeBlockChange implements IMessage {

	int[] heightValues;
	CubePos cubePos;
	short[] localAddresses;
	IBlockState[] blockStates;

	public PacketCubeBlockChange() {
	}

	public PacketCubeBlockChange(Cube cube, TShortCollection localAddresses) {
		this(cube, localAddresses.toArray());
	}

	public PacketCubeBlockChange(Cube cube, short[] localAddresses) {
		this.cubePos = cube.getCoords();
		this.localAddresses = localAddresses;
		this.blockStates = new IBlockState[localAddresses.length];
		int i = localAddresses.length - 1;
		IntSet xzAddresses = new IntHashSet();
		for (; i >= 0; i--) {
			int localAddress = this.localAddresses[i];
			int x = AddressTools.getLocalX(localAddress);
			int y = AddressTools.getLocalY(localAddress);
			int z = AddressTools.getLocalZ(localAddress);
			this.blockStates[i] = cube.getBlockState(x, y, z);
			xzAddresses.add(x | z << 4);
		}
		// TODO: Don't send height map changes here
		this.heightValues = new int[xzAddresses.size()];
		i = 0;
		for (IntCursor v : xzAddresses) {
			int height = cube.getColumn().getOpacityIndex().getTopBlockY(v.value & 0xF, v.value >> 4);
			v.value |= height << 8;
			heightValues[i] = v.value;
			i++;
		}
	}

	@SuppressWarnings("deprecation") // Forge thinks we are trying to register a block or something :P
	@Override
	public void fromBytes(ByteBuf in) {
		this.cubePos = new CubePos(in.readInt(), in.readInt(), in.readInt());
		short numBlocks = in.readShort();
		localAddresses = new short[numBlocks];
		blockStates = new IBlockState[numBlocks];

		for (int i = 0; i < numBlocks; i++) {
			localAddresses[i] = in.readShort();
			blockStates[i] = Block.BLOCK_STATE_IDS.getByValue(readVarInt(in, 4));
		}
		int numHmapChanges = in.readUnsignedByte();
		heightValues = new int[numHmapChanges];
		for (int i = 0; i < numHmapChanges; i++) {
			heightValues[i] = in.readInt();
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void toBytes(ByteBuf out) {
		out.writeInt(cubePos.getX());
		out.writeInt(cubePos.getY());
		out.writeInt(cubePos.getZ());
		out.writeShort(localAddresses.length);
		for (int i = 0; i < localAddresses.length; i++) {
			out.writeShort(localAddresses[i]);
			ByteBufUtils.writeVarInt(out, Block.BLOCK_STATE_IDS.get(blockStates[i]), 4);
		}
		out.writeByte(heightValues.length);
		for (int v : heightValues) {
			out.writeInt(v);
		}
	}

	public static class Handler extends AbstractClientMessageHandler<PacketCubeBlockChange> {

		@Nullable @Override
		public IMessage handleClientMessage(EntityPlayer player, PacketCubeBlockChange message, MessageContext ctx) {
			ClientHandler.getInstance().handle(message);
			return null;
		}
	}
}
