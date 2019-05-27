/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.items;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.energy.immersiveflux.IFluxProvider;
import blusunrize.immersiveengineering.api.energy.immersiveflux.IFluxReceiver;
import blusunrize.immersiveengineering.api.energy.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.old.ImmersiveNetHandler;
import blusunrize.immersiveengineering.api.energy.wires.old.ImmersiveNetHandler.AbstractConnection;
import blusunrize.immersiveengineering.api.tool.ITool;
import blusunrize.immersiveengineering.common.util.ChatUtils;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.Utils;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class ItemVoltmeter extends ItemIEBase implements ITool
{
	public ItemVoltmeter()
	{
		super("voltmeter", new Properties().maxStackSize(1));
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
	{
		super.addInformation(stack, worldIn, tooltip, flagIn);
		if(ItemNBTHelper.hasKey(stack, "linkingPos"))
		{
			int[] link = stack.getOrCreateTag().getIntArray("linkingPos");
			if(link.length > 3)
				tooltip.add(new TextComponentTranslation(Lib.DESC_INFO+"attachedToDim", link[1], link[2], link[3], link[0]));
		}

	}

	@Override
	public boolean isTool(ItemStack item)
	{
		return true;
	}

	@Override
	public EnumActionResult onItemUse(ItemUseContext context)
	{
		World world = context.getWorld();
		BlockPos pos = context.getPos();
		EnumFacing side = context.getFace();
		EntityPlayer player = context.getPlayer();
		ItemStack stack = context.getItem();
		TileEntity tileEntity = world.getTileEntity(pos);
		if((player==null||!player.isSneaking())&&(tileEntity instanceof IFluxReceiver||tileEntity instanceof IFluxProvider))
		{
			int max = 0;
			int stored = 0;
			if(tileEntity instanceof IFluxReceiver)
			{
				max = ((IFluxReceiver)tileEntity).getMaxEnergyStored(side);
				stored = ((IFluxReceiver)tileEntity).getEnergyStored(side);
			}
			else
			{
				max = ((IFluxProvider)tileEntity).getMaxEnergyStored(side);
				stored = ((IFluxProvider)tileEntity).getEnergyStored(side);
			}
			if(max > 0)
				ChatUtils.sendServerNoSpamMessages(player, new TextComponentTranslation(Lib.CHAT_INFO+"energyStorage", stored, max));
			return EnumActionResult.SUCCESS;
		}
		if(player!=null&&player.isSneaking()&&tileEntity instanceof IImmersiveConnectable)
		{
			if(!ItemNBTHelper.hasKey(stack, "linkingPos"))
			{
				ItemNBTHelper.setString(stack, "linkingDim", world.getDimension().getType().toString());
				ItemNBTHelper.setIntArray(stack, "linkingPos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
			}
			else
			{
				String dim = ItemNBTHelper.getString(stack, "linkingDim");
				if(dim.equals(world.getDimension().getType().toString()))
				{
					int[] array = ItemNBTHelper.getIntArray(stack, "linkingPos");
					BlockPos linkPos = new BlockPos(array[0], array[1], array[2]);
					TileEntity tileEntityLinkingPos = world.getTileEntity(linkPos);
					IImmersiveConnectable nodeHere = (IImmersiveConnectable)tileEntity;
					IImmersiveConnectable nodeLink = (IImmersiveConnectable)tileEntityLinkingPos;
					if(nodeLink!=null)
					{
						Set<AbstractConnection> connections = ImmersiveNetHandler.INSTANCE.getIndirectEnergyConnections(Utils.toCC(nodeLink), world, true);
						for(AbstractConnection con : connections)
							if(Utils.toCC(nodeHere).equals(con.end))
								player.sendMessage(new TextComponentTranslation(Lib.CHAT_INFO+"averageLoss", Utils.formatDouble(con.getAverageLossRate()*100, "###.000")));
					}
				}
				ItemNBTHelper.remove(stack, "linkingPos");
				ItemNBTHelper.remove(stack, "linkingDim");
			}
			return EnumActionResult.SUCCESS;
		}
		return EnumActionResult.PASS;
	}
}
