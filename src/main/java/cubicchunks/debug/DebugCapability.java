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

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import cubicchunks.CubicChunks;
import cubicchunks.world.IProviderExtras;

public class DebugCapability implements Capability.IStorage<DebugCapability.Attachment>, Callable<DebugCapability.Attachment> {

	public static final ResourceLocation KEY = new ResourceLocation(CubicChunks.MODID, "debug");

	@CapabilityInject(Attachment.class)
	public static Capability<Attachment> CAPABILITY;

	public void register() {
		CapabilityManager.INSTANCE.register(
			Attachment.class,
			this,
			this);
	}

	@Override public Attachment call() throws Exception {
		return new Attachment();
	}

	@Override
	public NBTBase writeNBT(Capability<Attachment> capability, Attachment instance, EnumFacing side) {
		return null;
	}

	@Override
	public void readNBT(Capability<Attachment> capability, Attachment instance, EnumFacing side, NBTBase nbt) {}

	public static class Attachment implements ICapabilitySerializable<NBTTagByte> {

		private IProviderExtras.Requirement requirement;

		public Attachment(){
			requirement = IProviderExtras.Requirement.LIGHT;
		}

		public void setRequirement(IProviderExtras.Requirement req) {
			requirement = req;
		}

		public IProviderExtras.Requirement getRequirement() {
			return requirement;
		}

		@Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
			return capability == CAPABILITY;
		}

		@Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
			return capability == CAPABILITY ? (T)this : null;
		}

		@Override public NBTTagByte serializeNBT() {
			return new NBTTagByte((byte)requirement.ordinal());
		}

		@Override public void deserializeNBT(NBTTagByte nbt) {
			requirement = IProviderExtras.Requirement.values()[nbt.getByte()];
		}
	}
}
