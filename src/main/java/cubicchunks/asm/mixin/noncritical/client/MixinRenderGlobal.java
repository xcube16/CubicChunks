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

package cubicchunks.asm.mixin.noncritical.client;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;


/**
 * Fixes a bug that causes Cubes to render vary slow in 1.10
 * This also effects Vanilla, but lets fix it anyway as it becomes even more
 * of a problem do the the long distances that players can fall
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

	// the goal ratio of Cubes Per Frame to be rendered
	private static final float CPF_GOAL_RATIO = 0.05f;
	private static final long ADJUSTMENT_TIME_NANO = 100000; // tenth of a millisecond

	/**
	 * The Cubes that need to be processed by the worker threads before we can upload
	 */
	@Shadow private Set<RenderChunk> chunksToUpdate;

	/**
	 * Has a queue of Cubes to be uploaded (ChunkRenderDispatcher.queueChunkUploads)
	 */
	@Shadow private ChunkRenderDispatcher renderDispatcher;

	private long overtime = 0;
	private int lastToUpdateLeftovers = 0;
	private int lastToUploadLeftovers = 0;

	private int lastToUpdateAmount = 0;
	private int lastToUploadAmount = 0;

	/**
	 * Replace the first System.nanoTime() call to cancel out the time spent
	 * before rendering (client world ticks and stuff)
	 * As far as I can tell this has no noticeable effect on fps (does not make more lag)
	 *
	 * CHECKED: 1.10.2-12.18.1.2092
	 */
	@ModifyVariable(method = "updateChunks",
	                at = @At(value = "HEAD"),
	                require = 1)
	public long addOvertime(long finishTimeNano) {

		if (!renderDispatcher.hasNoFreeRenderBuilders() && lastToUploadAmount != 0) {
			float updateRatio = (float) (lastToUpdateAmount - lastToUpdateLeftovers) / lastToUpdateAmount;
			float uploadRatio = (float) (lastToUploadAmount - lastToUploadLeftovers) / lastToUploadAmount;
			overtime = Math.max(
				((CPF_GOAL_RATIO - updateRatio) > 0.0f || (CPF_GOAL_RATIO - uploadRatio) > 0.0f ? 1 : -1)
					*ADJUSTMENT_TIME_NANO + overtime,
				0);
		}

		lastToUpdateAmount = chunksToUpdate.size();
		lastToUploadAmount = renderDispatcher.queueChunkUploads.size();
		return finishTimeNano + overtime;
	}

	@Inject(method = "updateChunks",
	        at = @At(value = "RETURN"),
	        require = 1)
	public void pollEffects(long finishTimeNano, CallbackInfo cbi) {
		lastToUpdateLeftovers = chunksToUpdate.size();
		lastToUploadLeftovers = renderDispatcher.queueChunkUploads.size();
	}
}
