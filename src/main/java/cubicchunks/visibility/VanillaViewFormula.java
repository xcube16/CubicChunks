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
package cubicchunks.visibility;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import cubicchunks.util.Coords;
import cubicchunks.util.XYZFunction;
import cubicchunks.world.ICubicWorldServer;
import cubicchunks.world.cube.Cube;
import mcp.MethodsReturnNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class VanillaViewFormula implements IViewFormula {

	private final int posX;
	private final int posY;
	private final int posZ;

	private final int cubeX;
	private final int cubeY;
	private final int cubeZ;

	private final int radius;
	private final int vertical;

	public VanillaViewFormula(EntityPlayerMP player) {
		this(player.getServerWorld().getMinecraftServer().getPlayerList().getViewDistance(),
			((ICubicWorldServer) player.getServerWorld()).getPlayerCubeMap().getVerticalView(),
			 player);
	}

	private VanillaViewFormula(int radius, int vertical, EntityPlayerMP player) {
		this.radius = radius;
		this.vertical = vertical;

		this.posX = (int) player.posX;
		this.posY = (int) player.posY;
		this.posZ = (int) player.posZ;

		this.cubeX = Coords.blockToCube(this.posX);
		this.cubeY = Coords.blockToCube(this.posY);
		this.cubeZ = Coords.blockToCube(this.posZ);
	}

	@Override
	@Nullable
	public IViewFormula next(EntityPlayerMP player) {
		// did the view distance change?
		WorldServer world = player.getServerWorld();
		int newRadius = world.getMinecraftServer().getPlayerList().getViewDistance();
		int newVertical = ((ICubicWorldServer) world).getPlayerCubeMap().getVerticalView();
		if (radius != newRadius || vertical != newVertical) {
			return new VanillaViewFormula(newRadius, newVertical, player);
		}

		// did the player move far enough to matter?
		int blockDX = (int) player.posX - posX;
		int blockDY = (int) player.posY - posY;
		int blockDZ = (int) player.posZ - posZ;

		int distanceSquared = blockDX*blockDX + blockDY*blockDY + blockDZ*blockDZ;
		if (distanceSquared >= Cube.SIZE * Cube.SIZE) {
			return new VanillaViewFormula(radius, vertical, player);
		}

		return null; // did not move much, so don't take a new snapshot
	}

	@Override
	public void computePositions(XYZFunction output) {
		for (int x = cubeX - radius; x <= cubeX + radius; x++) {
			for (int y = cubeY - vertical; y <= cubeY + vertical; y++) {
				for (int z = cubeZ - radius; z <= cubeZ + radius; z++) {
					output.apply(x, y, z);
				}
			}
		}
	}

	@Override public boolean contains(int x, int y, int z) {
		return x >= cubeX - radius && x <= cubeX + radius
			&& y >= cubeY - vertical && y <= cubeY + vertical
			&& z >= cubeZ - radius && z <= cubeZ + radius;
	}
}
