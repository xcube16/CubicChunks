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

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Detainted;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.server.chunkio.CubeIO;
import cubicchunks.server.chunkio.async.forge.AsyncWorldIOExecutor;
import cubicchunks.server.chunkio.async.ICubeRequest;
import cubicchunks.util.CubePos;
import cubicchunks.util.XYZMap;
import cubicchunks.world.ICubeProvider;
import cubicchunks.world.ICubicWorldServer;
import cubicchunks.world.IProviderExtras;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.Cube;
import cubicchunks.worldgen.generator.ICubeGenerator;
import cubicchunks.worldgen.generator.ICubePrimer;
import mcp.MethodsReturnNonnullByDefault;

/**
 * This is CubicChunks equivalent of ChunkProviderServer, it loads and unloads Cubes and Columns.
 * <p>
 * There are a few necessary changes to the way vanilla methods work:
 * * Because loading a Chunk (Column) doesn't make much sense with CubicChunks,
 * all methods that load Chunks, actually load  an empry column with no blocks in it
 * (there may be some entities that are not in any Cube yet).
 * * dropChunk method is not supported. Columns are unloaded automatically when the last cube is unloaded
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class CubeProviderServer extends ChunkProviderServer implements ICubeProvider, IProviderExtras {

	@Nonnull private ICubicWorldServer worldServer;
	@Nonnull private CubeIO cubeIO;

	// TODO: Use a better hash map!
	@Nonnull private XYZMap<Cube> cubeMap = new XYZMap<>(0.7f, 8000);

	@Nonnull private ICubeGenerator cubeGen;

	public CubeProviderServer(ICubicWorldServer worldServer, ICubeGenerator cubeGen) {
		super((WorldServer) worldServer,
			worldServer.getSaveHandler().getChunkLoader(worldServer.getProvider()), // forge uses this in
			null); // safe to null out IChunkGenerator (Note: lets hope mods don't touch it, ik its public)

		this.cubeGen = cubeGen;

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
	@Nullable @Override
	public Column getLoadedColumn(int columnX, int columnZ) {
		return (Column) this.id2ChunkMap.get(ChunkPos.asLong(columnX, columnZ));
	}

	@Nullable @Override
	public Column getLoadedChunk(int columnX, int columnZ) {
		return getLoadedColumn(columnX, columnZ);
	}

	/**
	 * Loads Chunk (Column) if it can be loaded from disk, or returns already loaded one.
	 * Doesn't generate new Columns.
	 */
	@Nullable @Override
	public Column loadChunk(int columnX, int columnZ) {
		return this.loadChunk(columnX, columnZ, null);
	}

	/**
	 * Load chunk asynchronously. Currently CubicChunks only loads synchronously.
	 */
	@Nullable @Override
	public Column loadChunk(int columnX, int columnZ, @Nullable Runnable runnable) {
		// TODO: Set this to LOAD when PlayerCubeMap works
		if (runnable == null) {
			return getColumn(columnX, columnZ, /*Requirement.LOAD*/Requirement.LIGHT);
		}

		// TODO here too
		asyncGetColumn(columnX, columnZ, Requirement.LIGHT, col -> runnable.run());
		return null;
	}

	/**
	 * If this Column is already loaded - returns it.
	 * Loads from disk if possible, otherwise generates new Column.
	 */
	@Override
	public Column provideColumn(int cubeX, int cubeZ) {
		return getColumn(cubeX, cubeZ, Requirement.GENERATE);
	}

	@Override
	public Column provideChunk(int cubeX, int cubeZ) {
		return provideColumn(cubeX, cubeZ);
	}

	@Override
	public boolean saveChunks(boolean alwaysTrue) {
		for (Cube cube : cubeMap) { // save cubes
			if (cube.needsSaving()) {
				this.cubeIO.saveCube(cube);
			}
		}
		for (Chunk chunk : id2ChunkMap.values()) { // save columns
			Column column = (Column) chunk;
			// save the column
			if (column.needsSaving(alwaysTrue)) {
				this.cubeIO.saveColumn(column);
			}
		}

		return true;
	}

	@Override
	public boolean tick() {
		// NOTE: the return value is completely ignored
		// NO-OP, This is called by WorldServer's tick() method every tick
		return false;
	}

	@Override
	public String makeString() {
		return "CubeProviderServer: " + this.id2ChunkMap.size() + " columns, "
			+ this.cubeMap.getSize() + " cubes";
	}

	@Override
	public List<Biome.SpawnListEntry> getPossibleCreatures(final EnumCreatureType type, final BlockPos pos) {
		return cubeGen.getPossibleCreatures(type, pos);
	}

	@Nullable @Override
	public BlockPos getStrongholdGen(World worldIn, String name, BlockPos pos, boolean flag) {
		return cubeGen.getClosestStructure(name, pos, flag);
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
	public Cube getCube(CubePos coords) {
		return getCube(coords.getX(), coords.getY(), coords.getZ());
	}

	@Nullable @Override
	public Cube getLoadedCube(int cubeX, int cubeY, int cubeZ) {
		return cubeMap.get(cubeX, cubeY, cubeZ);
	}

	@Nullable @Override
	public Cube getLoadedCube(CubePos coords) {
		return getLoadedCube(coords.getX(), coords.getY(), coords.getZ());
	}

	/**
	 * Load a cube, asynchronously. The work done to retrieve the column is specified by the
	 * {@link Requirement} <code>req</code>
	 *
	 * @param cubeX Cube x position
	 * @param cubeY Cube y position
	 * @param cubeZ Cube z position
	 * @param req Work done to retrieve the column
	 * @param callback Callback to be called when the load finishes. Note that <code>null</code> can be passed to the
	 * callback if the work specified by <code>req</code> is not sufficient to provide a cube
	 *
	 * @see #getCube(int, int, int, Requirement) for the synchronous equivalent to this method
	 */
	public void asyncGetCube(int cubeX, int cubeY, int cubeZ, Requirement req, Consumer<Cube> callback) {
		Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
		if (req == Requirement.GET_CACHED || (cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
			callback.accept(cube);
			return;
		}

		if (cube == null) {
			AsyncWorldIOExecutor.queueCubeLoad(worldServer, cubeIO, this, cubeX, cubeY, cubeZ, loaded -> {
				Column col = getLoadedColumn(cubeX, cubeZ);
				if (col != null) {
					onCubeLoaded(loaded, col);
					loaded = postCubeLoadAttempt(cubeX, cubeY, cubeZ, loaded, col, req);
				}
				callback.accept(loaded);
			});
		}
	}

	@Nullable @Override
	public Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement req) {

		Cube cube = getLoadedCube(cubeX, cubeY, cubeZ);
		if (req == Requirement.GET_CACHED ||
			(cube != null && req.compareTo(Requirement.GENERATE) <= 0)) {
			return cube;
		}

		// try to get the Column
		Column column = getColumn(cubeX, cubeZ, req);
		if (column == null) {
			return cube; // Column did not reach req, so Cube also does not
		}

		if (cube == null) {
			cube = AsyncWorldIOExecutor.syncCubeLoad(worldServer, cubeIO, this, cubeX, cubeY, cubeZ);
			onCubeLoaded(cube, column);
		}

		return postCubeLoadAttempt(cubeX, cubeY, cubeZ, cube, column, req);
	}

	/**
	 * After successfully loading a cube, add it to it's column and the lookup table
	 *
	 * @param cube The cube that was loaded
	 * @param column The column of the cube
	 */
	private void onCubeLoaded(@Nullable Cube cube, Column column) {
		if (cube != null) {
			cubeMap.put(cube); // cache the Cube
			//synchronous loading may cause it to be called twice when async loading has been already queued
			//because AsyncWorldIOExecutor only executes one task for one cube and because only saving a cube
			//can modify one that is being loaded, it's impossible to end up with 2 versions of the same cube
			//This is only to prevents multiple callbacks for the same queued load from adding the same cube twice.
			if (!column.getLoadedCubes().contains(cube)) {
				column.addCube(cube);
				cube.onLoad(); // init the Cube
			}
		}
	}

	/**
	 * Process a recently loaded cube as per the specified effort level.
	 *
	 * @param cubeX Cube x position
	 * @param cubeY Cube y position
	 * @param cubeZ Cube z positon
	 * @param cube The loaded cube, if loaded, else <code>null</code>
	 * @param column The column of the cube
	 * @param req Work done on the cube
	 *
	 * @return The processed cube, or <code>null</code> if the effort level is not sufficient to provide a cube
	 */
	@Nullable
	private Cube postCubeLoadAttempt(int cubeX, int cubeY, int cubeZ, @Nullable Cube cube, Column column, Requirement req) {
		// Fast path - Nothing to do here
		if (req == Requirement.LOAD) return cube;
		if (req == Requirement.GENERATE && cube != null) return cube;

		if (cube == null) {
			// generate the Cube
			cube = generateCube(cubeX, cubeY, cubeZ, column);
			if (req == Requirement.GENERATE) {
				return cube;
			}
		}

		if (!cube.isFullyPopulated()) {
			// forced full population of this cube
			populateCube(cube);
			if (req == Requirement.POPULATE) {
				return cube;
			}
		}

		//TODO: Direct skylight might have changed and even Cubes that have there
		//      initial light done, there might be work to do for a cube that just loaded
		if (!cube.isInitialLightingDone()) {
			calculateDiffuseSkylight(cube);
		}

		return cube;
	}


	/**
	 * Generate a cube at the specified position
	 *
	 * @param cubeX Cube x position
	 * @param cubeY Cube y position
	 * @param cubeZ Cube z position
	 * @param column Column of the cube
	 *
	 * @return The generated cube
	 */
	private Cube generateCube(int cubeX, int cubeY, int cubeZ, Column column) {
		ICubePrimer primer = cubeGen.generateCube(cubeX, cubeY, cubeZ);
		Cube cube = new Cube(column, cubeY, primer);

		this.worldServer.getFirstLightProcessor()
			.initializeSkylight(cube); // init sky light, (does not require any other cubes, just ServerHeightMap)
		onCubeLoaded(cube, column);
		return cube;
	}

	/**
	 * Populate a cube at the specified position, generating surrounding cubes as necessary
	 *
	 * @param cube The cube to populate
	 */
	private void populateCube(Cube cube) {
		int cubeX = cube.getX();
		int cubeY = cube.getY();
		int cubeZ = cube.getZ();

		cubeGen.getPopulationRequirement(cube).forEachPoint((x, y, z) -> {
			Cube popcube = getCube(x + cubeX, y + cubeY, z + cubeZ);
			if (!popcube.isPopulated()) {
				cubeGen.populate(popcube);
				popcube.setPopulated(true);
			}
		});
		cube.setFullyPopulated(true);
	}

	/**
	 * Initialize skylight for the cube at the specified position, generating surrounding cubes as needed.
	 *
	 * @param cube The cube to light up
	 */
	private void calculateDiffuseSkylight(Cube cube) {
		int cubeX = cube.getX();
		int cubeY = cube.getY();
		int cubeZ = cube.getZ();

		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 2; y >= -2; y--) {
					if (x != 0 || y != 0 || z != 0) {
						getCube(x + cubeX, y + cubeY, z + cubeZ);
					}
				}
			}
		}
		this.worldServer.getFirstLightProcessor().diffuseSkylight(cube);
	}


	/**
	 * Retrieve a column, asynchronously. The work done to retrieve the column is specified by the
	 * {@link Requirement} <code>req</code>
	 *
	 * @param columnX Column x position
	 * @param columnZ Column z position
	 * @param req Work done to retrieve the column
	 * @param callback Callback to be called when the column has finished loading. Note that the returned column is not
	 * guaranteed to be non-null
	 *
	 * @see CubeProviderServer#getColumn(int, int, Requirement) for the synchronous variant of this method
	 */
	public void asyncGetColumn(int columnX, int columnZ, Requirement req, Consumer<Column> callback) {
		Column column = getLoadedColumn(columnX, columnZ);
		if (column != null || req == Requirement.GET_CACHED) {
			callback.accept(column);
			return;
		}

		AsyncWorldIOExecutor.queueColumnLoad(worldServer, cubeIO, columnX, columnZ, col -> {
			col = postProcessColumn(columnX, columnZ, col, req);
			callback.accept(col);
		});
	}

	@Nullable @Override
	public Column getColumn(int columnX, int columnZ, Requirement req) {
		Column column = getLoadedColumn(columnX, columnZ);
		if (column != null || req == Requirement.GET_CACHED) {
			return column;
		}

		column = AsyncWorldIOExecutor.syncColumnLoad(worldServer, cubeIO, columnX, columnZ);
		column = postProcessColumn(columnX, columnZ, column, req);

		return column;
	}

	public void getColumnAsync(@Nonnull ChunkPos pos, @Nonnull Requirement req, @Nonnull Consumer<Column> callback) {
		// STUB, TODO: async
		callback.accept(getColumn(pos.chunkXPos, pos.chunkZPos, req));
	}

	public void getCubeAsync(@Nonnull CubePos pos, @Nonnull Requirement req, @Nonnull ICubeRequest callback) {
		// STUB, TODO: async
		callback.accept(getCube(pos.getX(), pos.getY(), pos.getZ(), req));
	}

	public void cancelAsyncColumn(@Nonnull Consumer<Column> callback) {
		// STUB, TODO: cancel async
	}

	public void cancelAsyncCube(@Nonnull ICubeRequest callback) {
		// STUB, TODO: cancel async
	}

	public void sortAsyncHint() {
		// STUB
	}

	/**
	 * After loading a column, do work on it, where the work required is specified by <code>req</code>
	 *
	 * @param columnX X position of the column
	 * @param columnZ Z position of the column
	 * @param column The loaded column, or <code>null</code> if the column couldn't be loaded
	 * @param req The amount of work to be done on the cube
	 *
	 * @return The postprocessed column, or <code>null</code>
	 */
	@Nullable
	private Column postProcessColumn(int columnX, int columnZ, @Nullable Column column, Requirement req) {
		Column loaded = getLoadedColumn(columnX, columnZ);
		if (loaded != null) {
			if (column != null && loaded != column) {
				throw new IllegalStateException("Duplicate column at " + columnX + ", " + columnZ + "!");
			}
			return loaded;
		}
		if (column != null) {
			id2ChunkMap.put(ChunkPos.asLong(columnX, columnZ), column);
			column.setLastSaveTime(this.worldServer.getTotalWorldTime()); // the column was just loaded
			column.onChunkLoad();
			return column;
		} else if (req == Requirement.LOAD) {
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
			Column column = (Column) chunk;
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

	Iterator<Cube> cubesIterator() {
		return cubeMap.iterator();
	}

	@SuppressWarnings("unchecked")
	Iterator<Column> columnsIterator() {
		return (Iterator<Column>) (Object) id2ChunkMap.values().iterator();
	}

	boolean tryUnloadCube(Cube cube) {
		if (!cube.getTickets().canUnload()) {
			return false; // There are tickets
		}

		// unload the Cube!
		cube.onUnload();

		if (cube.needsSaving()) { // save the Cube, if it needs saving
			this.cubeIO.saveCube(cube);
		}

		cube.getColumn().removeCube(cube.getY());
		return true;
	}

	boolean tryUnloadColumn(Column column) {
		if (column.hasLoadedCubes()) {
			return false; // It has loaded Cubes in it
			// (Cubes are to Columns, as tickets are to Cubes... in a way)
		}
		column.unloaded = true; // flag as unloaded (idk, maybe vanilla uses this somewhere)

		// unload the Column!
		column.onChunkUnload();

		if (column.needsSaving(true)) { // save the Column, if it needs saving
			this.cubeIO.saveColumn(column);
		}
		return true;
	}
}
