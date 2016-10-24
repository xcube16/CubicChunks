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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import cubicchunks.world.column.Column;
import io.netty.buffer.ByteBuf;

public class PacketHeightChanges implements IMessage {

	public ChunkPos pos;

	public byte[] localAddresses;
	public int[] heightValues;

	public PacketHeightChanges() {
	}

	public PacketHeightChanges(Column column, byte[] localAddresses) {
		this.pos = column.getChunkCoordIntPair();

		this.localAddresses = localAddresses;
		this.heightValues = new int[localAddresses.length];

		int[] heightMap = column.getOpacityIndex().getHeightmap();
		for(int i = 0;i < localAddresses.length;i++) {
			this.heightValues[i] = heightMap[localAddresses[i] & 0xFF];
		}
	}

	@Override
	public void fromBytes(ByteBuf in) {
		pos = new ChunkPos(in.readInt(), in.readInt());

		int size = in.readUnsignedByte();
		localAddresses = new byte[size];
		heightValues = new int[size];

		in.readBytes(localAddresses);
		for(int i = 0;i < size;i++){
			heightValues[i] = in.readInt();
		}
	}

	@Override
	public void toBytes(ByteBuf out) {
		out.writeInt(pos.chunkXPos);
		out.writeInt(pos.chunkZPos);

		out.writeByte(localAddresses.length);

		out.writeBytes(localAddresses);
		for (int v : heightValues) {
			out.writeInt(v);
		}
	}

	public static class Handler extends AbstractClientMessageHandler<PacketHeightChanges> {

		@Override
		public IMessage handleClientMessage(EntityPlayer player, PacketHeightChanges message, MessageContext ctx) {
			ClientHandler.getInstance().handle(message);
			return null;
		}
	}
}
