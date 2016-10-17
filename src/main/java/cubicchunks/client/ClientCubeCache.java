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
package cubicchunks.client;

import cubicchunks.util.CubeCoords;
import cubicchunks.util.XYZMap;
import cubicchunks.util.ReflectionUtil;
import cubicchunks.world.ICubeCache;
import cubicchunks.world.ICubicWorldClient;
import cubicchunks.world.column.BlankColumn;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.BlankCube;
import cubicchunks.world.cube.Cube;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

//TODO: break off ICubeCache
public class ClientCubeCache extends ChunkProviderClient implements ICubeCache {

	private ICubicWorldClient world;
	private Cube blankCube;
	private XYZMap<Cube> cubeMap = new XYZMap<>(0.7f, 13);

	public ClientCubeCache(ICubicWorldClient world) {
		super((World) world);

		this.world = world;
		ReflectionUtil.setFieldValueSrg(this, "field_73238_a", new BlankColumn(this, world, 0, 0));
		this.blankCube = new BlankCube((Column) blankChunk);
	}

	@Override
	@Nullable
	public Column getLoadedChunk(int x, int z) {
		return (Column) super.getLoadedChunk(x, z);
	}

	@Override
	public Column provideChunk(int x, int z) {
		return (Column) super.provideChunk(x, z);
	}

	@Override
	public Column loadChunk(int cubeX, int cubeZ) {
		Column column = new Column(this, this.world, cubeX, cubeZ);   // make a new one
		this.chunkMapping.put(ChunkPos.asLong(cubeX, cubeZ), column); // add it to the cache

		// fire a forge event... make mods happy :)
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.world.ChunkEvent.Load(column));

		column.setChunkLoaded(true);
		return column;
	}

	//===========================
	//========Cube stuff=========
	//===========================

	/**
	 * This is like ChunkProviderClient.loadChunk()
	 * It is used when the server sends a new Cube to this client,
	 * and the network handler wants us to create a new Cube.
	 *
	 * @return a newly created and cached cube
	 */
	public Cube loadCube(Column column, int cubeY) {
		Cube cube = new Cube(column, cubeY); // auto added to column
		column.addCube(cube);
		this.cubeMap.put(cube);

		return cube;
	}

	/**
	 * This is like ChunkProviderClient.unloadChunk()
	 * It is used when the server tells the client to unload a Cube.
	 */
	public void unloadCube(int cubeX, int cubeY, int cubeZ) {
		cubeMap.remove(cubeX, cubeY, cubeZ);
		Column column = getLoadedChunk(cubeX, cubeZ);
		if (column != null) {
			column.removeCube(cubeY);
		}
	}

	@Override
	public Cube getCube(int cubeX, int cubeY, int cubeZ) {
		Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
		if (cube == null) {
			return blankCube;
		}
		return cube;
	}

	@Override
	public Cube getCube(CubeCoords coords) {
		return getCube(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	@Override
	public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
		return cubeMap.get(cubeX, cubeY, cubeZ);
	}

	@Override
	public Cube getLoadedCube(CubeCoords coords) {
		return getLoadedCube(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	@Override
	public String makeString() {
		return "MultiplayerChunkCache: " + this.chunkMapping.values()
				.stream()
				.map(c -> ((Column) c).getLoadedCubes().size())
				.reduce((a, b) -> a + b)
				.orElse(-1) + "/" + this.chunkMapping.size();
	}
}
