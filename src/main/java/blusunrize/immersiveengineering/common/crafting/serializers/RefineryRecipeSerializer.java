/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.crafting.serializers;

import blusunrize.immersiveengineering.api.ApiUtils;
import blusunrize.immersiveengineering.api.crafting.FluidTagInput;
import blusunrize.immersiveengineering.api.crafting.IERecipeSerializer;
import blusunrize.immersiveengineering.api.crafting.RefineryRecipe;
import blusunrize.immersiveengineering.common.blocks.IEBlocks.Multiblocks;
import blusunrize.immersiveengineering.common.config.IEServerConfig;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;

public class RefineryRecipeSerializer extends IERecipeSerializer<RefineryRecipe>
{
	@Override
	public ItemStack getIcon()
	{
		return new ItemStack(Multiblocks.refinery);
	}

	@Override
	public RefineryRecipe readFromJson(ResourceLocation recipeId, JsonObject json)
	{
		FluidStack output = ApiUtils.jsonDeserializeFluidStack(GsonHelper.getAsJsonObject(json, "result"));
		FluidTagInput input0 = FluidTagInput.deserialize(GsonHelper.getAsJsonObject(json, "input0"));
		FluidTagInput input1 = FluidTagInput.deserialize(GsonHelper.getAsJsonObject(json, "input1"));
		int energy = GsonHelper.getAsInt(json, "energy");
		RefineryRecipe recipe = new RefineryRecipe(recipeId, output, input0, input1, energy);
		recipe.modifyTimeAndEnergy(()->1, IEServerConfig.MACHINES.refineryConfig);
		return recipe;
	}

	@Nullable
	@Override
	public RefineryRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer)
	{
		FluidStack output = buffer.readFluidStack();
		FluidTagInput input0 = FluidTagInput.read(buffer);
		FluidTagInput input1 = FluidTagInput.read(buffer);
		int energy = buffer.readInt();
		return new RefineryRecipe(recipeId, output, input0, input1, energy);
	}

	@Override
	public void toNetwork(FriendlyByteBuf buffer, RefineryRecipe recipe)
	{
		buffer.writeFluidStack(recipe.output);
		recipe.input0.write(buffer);
		recipe.input1.write(buffer);
		buffer.writeInt(recipe.getTotalProcessEnergy());
	}
}
