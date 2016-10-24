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

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import cubicchunks.world.IProviderExtras;

public class DebugCommand extends CommandBase {

	@Override
	public String getName() {
		return "cuberequirement";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "Set the requirement level for cubes sent to you";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (!(sender instanceof EntityPlayerMP)) {
			throw new WrongUsageException("Only players can use this command");
		}
		if (args.length < 1) {
			((EntityPlayerMP) sender).getCapability(DebugCapability.CAPABILITY, null).setRequirement(
				IProviderExtras.Requirement.valueOf(args[0])
			);
			notifyCommandListener(sender, this, "Cube requirement set to {}", args[0]);
		} else {
			((EntityPlayerMP) sender).getCapability(DebugCapability.CAPABILITY, null).setRequirement(
				IProviderExtras.Requirement.LIGHT
			);
			notifyCommandListener(sender, this, "Cube requirement reset");
		}
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
		if(args.length == 1) {
			IProviderExtras.Requirement[] requirements = IProviderExtras.Requirement.values();
			String[] strings = new String[requirements.length];
			for(int i = 0;i < strings.length;i++) {
				strings[i] = requirements[i].name();
			}
			return getListOfStringsMatchingLastWord(args, strings);
		}
		return Collections.emptyList();
	}
}
