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
package cubicchunks.server;

import cubicchunks.CubicChunks;
import cubicchunks.server.chunkio.CubeIO;
import cubicchunks.util.CubeCoords;
import cubicchunks.util.CubeHashMap;
import cubicchunks.util.ticket.ITicket;
import cubicchunks.world.ICubeCache;
import cubicchunks.world.ICubicWorldServer;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.Cube;
import cubicchunks.worldgen.generator.ICubeGenerator;
import cubicchunks.worldgen.generator.ICubePrimer;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.apache.logging.log4j.Logger;

import javax.annotation.Detainted;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * This is CubicChunks equivalent of ChunkProviderServer, it loads and unloads Cubes and Columns.
 * <p>
 * There are a few necessary changes to the way vanilla methods work:
 * * Because loading a Chunk (Column) doesn't make much sense with CubicChunks,
 * all methods that load Chunks, actually load  an empry column with no blocks in it
 * (there may be some entities that are not in any Cube yet).
 * * dropChunk method is not supported. Columns are unloaded automatically when the last cube is unloaded
 */
public class ServerCubeCache extends ChunkProviderServer implements ICubeCache, IProviderExtras{

	private static final Logger log = CubicChunks.LOGGER;

	private ICubicWorldServer worldServer;
	private CubeIO cubeIO;

	// TODO: Use a better hash map!
	private CubeHashMap cubemap = new CubeHashMap(0.7f, 13);

	private ICubeGenerator   cubeGen;

	public ServerCubeCache(ICubicWorldServer worldServer, ICubeGenerator cubeGen) {
		super((WorldServer) worldServer,
				worldServer.getSaveHandler().getChunkLoader(worldServer.getProvider()), // forge uses this in
				null); // safe to null out IChunkGenerator (Note: lets hope mods don't touch it, ik its public)

		this.cubeGen   = cubeGen;

		this.worldServer = worldServer;
		this.cubeIO = new CubeIO(worldServer);
	}

	@Override
	@Detainted
	public void unload(Chunk chunk) {
		//ignore, ChunkGc unloads cubes
	}

	@Override
	@Detainted
	public void unloadAllChunks() {
		//ignore, ChunkGc unloads cubes
	}

	/**
	 * Vanilla method, returns a Chunk (Column) only of it's already loaded.
	 */
	@Override
	@Nullable
	public Column getLoadedChunk(int columnX, int columnZ) {
		return (Column) this.id2ChunkMap.get(ChunkPos.asLong(columnX, columnZ));
	}

	/**
	 * Loads Chunk (Column) if it can be loaded from disk, or returns already loaded one.
	 * Doesn't generate new Columns.
	 */
	@Override
	@Nullable
	public Column loadChunk(int columnX, int columnZ) {
		return this.loadChunk(columnX, columnZ, null);
	}

	/**
	 * Load chunk asynchronously. Currently CubicChunks only loads synchronously.
	 */
	@Override
	@Nullable
	public Column loadChunk(int columnX, int columnZ, Runnable runnable) {
		Column column = this.getColumn(columnX, columnZ, /*Requirement.LOAD*/Requirement.LIGHT);
		if (runnable == null) {                          // TODO: Set this to LOAD when PlayerCubeMap works
			return column;
		}
		runnable.run();
		return column;
	}

	/**
	 * If this Column is already loaded - returns it.
	 * Loads from disk if possible, otherwise generates new Column.
	 */
	@Override
	public Column provideChunk(int cubeX, int cubeZ) {
		return getColumn(cubeX, cubeZ, Requirement.GENERATE);
	}

	@Override
	public boolean saveChunks(boolean alwaysTrue) {
		for(Cube cube : cubemap){ // save cubes
			if (cube.needsSaving()) {
				this.cubeIO.saveCube(cube);
			}
		}
		for(Chunk chunk : id2ChunkMap.values()){ // save columns
			Column column = (Column)chunk;
			// save the column
			if (column.needsSaving(alwaysTrue)) {
				this.cubeIO.saveColumn(column);
			}
		}

		return true;
	}

	@Override
	public boolean unloadQueuedChunks() {
		// NOTE: the return value is completely ignored
		// NO-OP, This is called by WorldServer's tick() method every tick
		return false;
	}

	@Override
	public String makeString() {
		return "ServerCubeCache: " + this.id2ChunkMap.size() + " columns, "
				+ this.cubemap.getSize() + " cubes";
	}

	@Override
	public List<Biome.SpawnListEntry> getPossibleCreatures(@Nonnull final EnumCreatureType type, @Nonnull final BlockPos pos) {
		return cubeGen.getPossibleCreatures(type, pos);
	}

	@Nullable
	public BlockPos getStrongholdGen(@Nonnull World worldIn, @Nonnull String name, @Nonnull BlockPos pos) {
		return cubeGen.getClosestStructure(name, pos);
	}

	// getLoadedChunkCount() in ChunkProviderServer is fine - CHECKED: 1.10.2-12.18.1.2092

	@Override
	public boolean chunkExists(int cubeX, int cubeZ) {
		return this.id2ChunkMap.get(ChunkPos.asLong(cubeX, cubeZ)) != null;
	}

	//==============================
	//=====CubicChunks methods======
	//==============================

	@Override
	public Cube getCube(int cubeX, int cubeY, int cubeZ) {
		return getCube(cubeX, cubeY, cubeZ, Requirement.GENERATE);
	}

	@Override
	public Cube getCube(CubeCoords coords) {
		return getCube(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	@Override
	public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
		return cubemap.get(cubeX, cubeY, cubeZ);
	}

	@Override
	public Cube getLoadedCube(CubeCoords coords) {
		return getLoadedCube(coords.getCubeX(), coords.getCubeY(), coords.getCubeZ());
	}

	@Override
	@Nullable
	public Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement req) {
		Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
		if(req == Requirement.CACHE || 
				(cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
			return cube;
		}

		// try to get the Column
		Column column = getColumn(cubeX, cubeZ, req);
		if(column == null) {
			return cube; // Column did not reach req, so Cube also does not
		}

		if(cube == null) {
			// try to load the Cube
			try {
				worldServer.getProfiler().startSection("cubeIOLoad");
				cube = this.cubeIO.loadCubeAndAddToColumn(column, cubeY);
			} catch (IOException ex) {
				log.error("Unable to load cube {}, {}, {}", cubeX, cubeY, cubeZ, ex);
				return null;
			} finally {
				worldServer.getProfiler().endSection();
			}

			if(cube != null) {
				column.addCube(cube);
				cubemap.put(cube); // cache the Cube
				cube.onLoad();     // init the Cube

				if(req.compareTo(Requirement.GENERATE) <= 0) {
					return cube;
				}
			}else if(req == Requirement.LOAD) {
				return null;
			}
		}

		if(cube == null) {
			// generate the Cube
			ICubePrimer primer = cubeGen.generateCube(cubeX, cubeY, cubeZ);
			cube = new Cube(column, cubeY, primer);

			column.addCube(cube);
			cubemap.put(cube); // cache the Cube
			this.worldServer.getFirstLightProcessor().initializeSkylight(cube); // init sky light, (does not require any other cubes, just OpacityIndex)
			cube.onLoad(); // init the Cube

			if(req.compareTo(Requirement.GENERATE) <= 0) {
				return cube;
			}
		}

		// forced full population of this Cube!
		if(!cube.isFullyPopulated()) {
			cubeGen.getPopulationRequirement(cube).forEachPoint((x, y, z) -> {
				Cube popcube = getCube(x + cubeX, y + cubeY, z + cubeZ);
				if(!popcube.isPopulated()) {
					cubeGen.populate(popcube);
					popcube.setPopulated(true);
				}
			});
			cube.setFullyPopulated(true);
		}
		if(req == Requirement.POPULATE) {
			return cube;
		}

		//TODO: Direct skylight might have changed and even Cubes that have there
		//      initial light done, there might be work to do for a cube that just loaded
		if(!cube.isInitialLightingDone()) {
			for(int x = -2;x <= 2;x++) {
				for(int z = -2;z <= 2;z++) {
					for(int y = 2;y >= -2;y--) {
						if(x != 0 || y != 0 || z != 0) {
							// FirstLightProcessor is so soft and fluffy that it can't even ask for Cubes correctly!
							getCube(x + cubeX, y + cubeY, z + cubeZ);
						}
					}
				}
			}
			this.worldServer.getFirstLightProcessor().diffuseSkylight(cube);
		}

		return cube;
	}

	@Override
	@Nullable
	public Column getColumn(int columnX, int columnZ, Requirement req) {
		Column column = getLoadedChunk(columnX, columnZ);
		if(column != null || req == Requirement.CACHE) {
			return column;
		}

		try {
			column = this.cubeIO.loadColumn(columnX, columnZ);
		} catch (IOException ex) {
			log.error("Unable to load column ({},{})", columnX, columnZ, ex);
			return null;
		}
		if(column != null) {
			id2ChunkMap.put(ChunkPos.asLong(columnX, columnZ), column);
			column.setLastSaveTime(this.worldServer.getTotalWorldTime()); // the column was just loaded
			column.onChunkLoad();
			return column;
		}else if(req == Requirement.LOAD) {
			return null;
		}

		column = new Column(this, worldServer, columnX, columnZ);
		cubeGen.generateColumn(column);

		id2ChunkMap.put(ChunkPos.asLong(columnX, columnZ), column);
		column.setLastSaveTime(this.worldServer.getTotalWorldTime()); // the column was just generated
		column.onChunkLoad();
		return column;
	}

	public String dumpLoadedCubes() {
		StringBuilder sb = new StringBuilder(10000).append("\n");
		for (Chunk chunk : this.id2ChunkMap.values()) {
			Column column = (Column)chunk;
			if (column == null) {
				sb.append("column = null\n");
				continue;
			}
			sb.append("Column[").append(column.getX()).append(", ").append(column.getZ()).append("] {");
			boolean isFirst = true;
			for (Cube cube : column.getLoadedCubes()) {
				if (!isFirst) {
					sb.append(", ");
				}
				isFirst = false;
				if (cube == null) {
					sb.append("cube = null");
					continue;
				}
				sb.append("Cube[").append(cube.getY()).append("]");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	public void flush() {
		this.cubeIO.flush();
	}

	void takeOutGarbage(){

		Iterator<Cube> cubeIt = cubemap.iterator();
		while(cubeIt.hasNext()) {
			if(tryUnloadCube(cubeIt.next())) {
				cubeIt.remove();
			}
		}

		Iterator<Chunk> columnIt = id2ChunkMap.values().iterator();
		while(columnIt.hasNext()) {
			if(tryUnloadColumn((Column)columnIt.next())) {
				columnIt.remove();
			}
		}
	}

	private boolean tryUnloadCube(Cube cube) {
		if(!cube.getTickets().canUnload()){
			return false; // There are tickets
		}

		// unload the Cube!
		cube.onUnload();
		cube.getColumn().removeCube(cube.getY());

		if(cube.needsSaving()) { // save the Cube, if it needs saving
			this.cubeIO.saveCube(cube);
		}
		return true;
	}

	private boolean tryUnloadColumn(Column column) {
		if(column.hasLoadedCubes()){
			return false; // It has loaded Cubes in it
			              // (Cubes are to Columns, as tickets are to Cubes... in a way)
		}
		column.unloaded = true; // flag as unloaded (idk, maybe vanilla uses this somewhere)

		// unload the Column!
		column.onChunkUnload();

		if(column.needsSaving(true)) { // save the Column, if it needs saving
			this.cubeIO.saveColumn(column);
		}
		return true;
	}
}
