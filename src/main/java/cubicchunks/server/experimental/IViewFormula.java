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

import net.minecraft.entity.player.EntityPlayerMP;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.util.XYZFunction;
import mcp.MethodsReturnNonnullByDefault;

/**
 * A formula that can be used to calculate the area visible for a player
 * This must be immutable, next is used to advance the area to a new location
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public interface IViewFormula {

	/**
	 * Capture another state snapshot into a new IViewFormula if there was significant change
	 *
	 * @param player the player to capture the new state form
	 * @return A new StateSnapshot or null
	 */
	@Nullable
	IViewFormula next(EntityPlayerMP player);

	/**
	 * Compute all the points within the area
	 *
	 * @param output the computed points should be passed to this function, with no duplicates
	 */
	void computePositions(XYZFunction output);

	/**
	 * Checks to see if a point is contained within the area at {@code state}
	 *
	 * @param x the x coordinate
	 * @param y the y coordinate
	 * @param z the z coordinate
	 * @return true if the point is within the area
	 */
	boolean contains(int x, int y, int z);
}
