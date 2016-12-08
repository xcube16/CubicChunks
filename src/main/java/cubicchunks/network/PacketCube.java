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

import com.google.common.collect.Iterables;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.util.CubePos;
import cubicchunks.world.IHeightMap;
import cubicchunks.world.cube.Cube;
import io.netty.buffer.ByteBuf;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class PacketCube implements IMessage {

	private int[] heightMap;
	private CubePos cubePos;
	private byte[] data;

	private boolean preserveTEs;
	private List<NBTTagCompound> tileEntityTags;

	public PacketCube() {
	}

	public PacketCube(Cube cube, Collection<TileEntity> tileEntities) {
		this.cubePos = cube.getCoords();
		this.data = new byte[WorldEncoder.getEncodedSize(cube)];
		PacketBuffer out = new PacketBuffer(WorldEncoder.createByteBufForWrite(this.data));

		WorldEncoder.encodeCube(out, cube);

		if (tileEntities == null) {
			tileEntities = cube.getTileEntityMap().values();
		}else{
			preserveTEs = true;
		}

		this.tileEntityTags = new ArrayList<>(tileEntities.size());
		this.tileEntityTags.addAll(tileEntities.stream().map(TileEntity::getUpdateTag).collect(Collectors.toList()));
		this.heightMap = new int[Cube.SIZE*Cube.SIZE];
		IHeightMap heightmap = cube.getColumn().getOpacityIndex();
		for (int localX = 0; localX < Cube.SIZE; localX++) {
			for (int localZ = 0; localZ < Cube.SIZE; localZ++) {
				this.heightMap[index(localX, localZ)] = heightmap.getTopBlockY(localX, localZ);
			}
		}
	}

	public PacketCube(Cube cube) {
		this(cube, null);
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		this.cubePos = new CubePos(buf.readInt(), buf.readInt(), buf.readInt());
		this.preserveTEs = buf.readBoolean();
		this.data = new byte[buf.readInt()];
		buf.readBytes(this.data);
		int numTiles = buf.readInt();
		this.tileEntityTags = new ArrayList<>(numTiles);
		for (int i = 0; i < numTiles; i++) {
			this.tileEntityTags.add(ByteBufUtils.readTag(buf));
		}
		this.heightMap = new int[Cube.SIZE*Cube.SIZE];
		for (int i = 0; i < Cube.SIZE*Cube.SIZE; i++) {
			heightMap[i] = buf.readInt();
		}
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(cubePos.getX());
		buf.writeInt(cubePos.getY());
		buf.writeInt(cubePos.getZ());
		buf.writeBoolean(this.preserveTEs);
		buf.writeInt(this.data.length);
		buf.writeBytes(this.data);
		buf.writeInt(this.tileEntityTags.size());
		for (NBTTagCompound tag : this.tileEntityTags) {
			ByteBufUtils.writeTag(buf, tag);
		}
		for (int i = 0; i < Cube.SIZE*Cube.SIZE; i++) {
			buf.writeInt(heightMap[i]);
		}
	}

	CubePos getCubePos() {
		return cubePos;
	}

	byte[] getData() {
		return data;
	}

	Iterable<NBTTagCompound> getTileEntityTags() {
		return Iterables.unmodifiableIterable(this.tileEntityTags);
	}

	public boolean preserveTileEntities() {
		return preserveTEs;
	}

	public static class Handler extends AbstractClientMessageHandler<PacketCube> {
		@Nullable @Override
		public IMessage handleClientMessage(EntityPlayer player, PacketCube message, MessageContext ctx) {
			ClientHandler.getInstance().handle(message);
			return null;
		}
	}

	private static int index(int x, int z) {
		return x << 4 | z;
	}
}
