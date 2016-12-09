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
package cubicchunks.world;

import net.minecraft.util.math.ChunkPos;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.server.experimental.ICubeRequest;
import cubicchunks.util.CubePos;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.Cube;
import mcp.MethodsReturnNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface IProviderExtras {

	/**
	 * Retrieve a column. The work done to retrieve the column is specified by the {@link Requirement} <code>req</code>
	 *
	 * @param columnX Column x position
	 * @param columnZ Column z position
	 * @param req Work done to retrieve the column
	 *
	 * @return the column, or <code>null</code> if no column could be created with the specified requirement level
	 */
	@Nullable
	Column getColumn(int columnX, int columnZ, Requirement req);

	/**
	 * Retrieve a cube. The work done to retrieve the cube is specified by {@link Requirement} <code>req</code>
	 *
	 * @param cubeX the cube's x coordinate
	 * @param cubeY the cube's y coordinate
	 * @param cubeZ the cube's z coordinate
	 * @param req what the requirements are before you get the Cube
	 *
	 * @return the Cube or null if no Cube could be found or created
	 */
	@Nullable
	Cube getCube(int cubeX, int cubeY, int cubeZ, Requirement req);

	/**
	 * Submits a request to get a Column.
	 * Columns associated with an async Cube request take priority.<br/>
	 * The callback will be called on the main thread
	 *
	 * @param pos the coordinates of the Column
	 * @param req the requirement level
	 * @param callback a callback that will be fired when the Column is ready
	 */
	void getColumnAsync(@Nonnull ChunkPos pos, @Nonnull Requirement req, @Nonnull Consumer<Column> callback);

	/**
	 * Submits a request to get a Cube.
	 * The following priority is not mandatory, but balanced effort it is recommended.
	 *
	 * @param pos the coordinates of the Cube
	 * @param req the requirement level
	 * @param callback a callback that will be fired when the Cube is ready
	 */
	void getCubeAsync(@Nonnull CubePos pos, @Nonnull Requirement req, @Nonnull ICubeRequest callback);

	/**
	 * Cancels a request for a Column.
	 * After a call to this method {@code callback} must not be called.
	 *
	 * @param callback the callback from a previous getColumnAsync() to cancel
	 */
	void cancelAsyncColumn(@Nonnull Consumer<Column> callback);

	/**
	 * Cancels a request for a Cube.
	 * After a call to this method {@code callback} must not be called.
	 *
	 * @param callback the callback from a previous getCubeAsync() to cancel
	 */
	void cancelAsyncCube(@Nonnull ICubeRequest callback);

	/**
	 * Called as a hint that async Cube requests may need resorting
	 */
	void sortAsyncHint();

	/**
	 * The effort made to retrieve a cube or column. Any further work should not be done, and returning
	 * <code>null</code> is acceptable in those cases
	 */
	enum Requirement {
		// Warning, don't modify order of these constants - ordinals are used in comparisons
		// TODO write a custom compare method
		/**
		 * Only retrieve the cube/column if it is already cached
		 */
		GET_CACHED,
		/**
		 * Load the cube/column from disk, if necessary
		 */
		LOAD,
		/**
		 * Generate the cube/column, if necessary
		 */
		GENERATE,
		/**
		 * Populate the cube/column, if necessary
		 */
		POPULATE,
		/**
		 * Generate lighting information for the cube, if necessary
		 */
		LIGHT
	}
}
