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
package cubicchunks.debug;

import net.minecraft.command.ServerCommandManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.ParametersAreNonnullByDefault;

import mcp.MethodsReturnNonnullByDefault;

import static cubicchunks.debug.DebugTools.CUBIC_CHUNKS_DEBUG_TAB;
import static cubicchunks.debug.DebugTools.itemRelightSkyBlock;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class DebugProxy {

	public void initItems() {
		new DebugCapability().register();

		GameRegistry.register(itemRelightSkyBlock);
		itemRelightSkyBlock.setCreativeTab(CUBIC_CHUNKS_DEBUG_TAB);
	}

	void initServer(MinecraftServer server) {
		ServerCommandManager commands = (ServerCommandManager) server.getCommandManager();
		commands.registerCommand(new DebugCommand());
	}

	/**
	 * Adds the cubic debug capability to a player
	 */
	@SubscribeEvent
	public void capabilityThingy(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof EntityPlayerMP) {
			event.addCapability(DebugCapability.KEY, new DebugCapability.Attachment());
		}
	}
}
