/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.items;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.api.wires.*;
import blusunrize.immersiveengineering.api.wires.utils.WireLink;
import blusunrize.immersiveengineering.api.wires.utils.WirecoilUtils;
import blusunrize.immersiveengineering.common.network.MessageObstructedConnection;
import blusunrize.immersiveengineering.common.util.Utils;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static blusunrize.immersiveengineering.api.wires.utils.WireUtils.findObstructingBlocks;
import static blusunrize.immersiveengineering.api.wires.utils.WirecoilUtils.clearWireLink;
import static blusunrize.immersiveengineering.api.wires.utils.WirecoilUtils.hasWireLink;

public class WireCoilItem extends IEBaseItem implements IWireCoil
{
	@Nonnull
	private final WireType type;

	public WireCoilItem(@Nonnull WireType type)
	{
		super(new Properties());
		this.type = type;
	}

	@Override
	public WireType getWireType(ItemStack stack)
	{
		return type;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> list, ITooltipFlag flag)
	{
		if(WireType.REDSTONE_CATEGORY.equals(type.getCategory()))
		{
			list.add(new TranslationTextComponent(Lib.DESC_FLAVOUR+"coil.redstone"));
			list.add(new TranslationTextComponent(Lib.DESC_FLAVOUR+"coil.construction1"));
		}
		else if(WireType.STRUCTURE_CATEGORY.equals(type.getCategory()))
		{
			list.add(new TranslationTextComponent(Lib.DESC_FLAVOUR+"coil.construction0"));
			list.add(new TranslationTextComponent(Lib.DESC_FLAVOUR+"coil.construction1"));
		}
		if(hasWireLink(stack))
		{
			WireLink link = WireLink.readFromItem(stack);
			list.add(new TranslationTextComponent(Lib.DESC_INFO+"attachedToDim", link.cp.getX(),
					link.cp.getY(), link.cp.getZ(), link.dimension));
		}
	}

	@Override
	public ActionResultType onItemUse(ItemUseContext ctx)
	{
		return WirecoilUtils.doCoilUse(this, ctx.getPlayer(), ctx.getWorld(), ctx.getPos(), ctx.getHand(), ctx.getFace(),
				(float)ctx.getHitVec().x, (float)ctx.getHitVec().y, (float)ctx.getHitVec().z);
	}


	public static ActionResultType doCoilUse(
			IWireCoil coil, PlayerEntity player, World world, BlockPos pos, Hand hand, Direction side,
			float hitX, float hitY, float hitZ
	)
	{
		TileEntity tileEntity = world.getTileEntity(pos);
		if(tileEntity instanceof IImmersiveConnectable&&((IImmersiveConnectable)tileEntity).canConnect())
		{
			ItemStack stack = player.getHeldItem(hand);
			TargetingInfo targetHere = new TargetingInfo(side, hitX-pos.getX(), hitY-pos.getY(), hitZ-pos.getZ());
			WireType wire = coil.getWireType(stack);
			BlockPos masterPos = ((IImmersiveConnectable)tileEntity).getConnectionMaster(wire, targetHere);
			BlockPos offsetHere = pos.subtract(masterPos);
			tileEntity = world.getTileEntity(masterPos);
			if(!(tileEntity instanceof IImmersiveConnectable)||!((IImmersiveConnectable)tileEntity).canConnect())
				return ActionResultType.PASS;
			IImmersiveConnectable iicHere = (IImmersiveConnectable)tileEntity;
			ConnectionPoint cpHere = iicHere.getTargetedPoint(targetHere, offsetHere);

			if(cpHere==null||!((IImmersiveConnectable)tileEntity).canConnectCable(wire, cpHere, offsetHere)||
					!coil.canConnectCable(stack, tileEntity))
			{
				if(!world.isRemote)
					player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"wrongCable"), true);
				return ActionResultType.FAIL;
			}

			if(!world.isRemote)
			{
				if(!hasWireLink(stack))
				{
					WireLink link = WireLink.create(cpHere, world, offsetHere, targetHere);
					link.writeToItem(stack);
				}
				else
				{
					final WireLink otherLink = WireLink.readFromItem(stack);
					TileEntity tileEntityLinkingPos = world.getTileEntity(otherLink.cp.getPosition());
					int distanceSq = (int)Math.ceil(otherLink.cp.getPosition().distanceSq(
							masterPos.getX(), masterPos.getY(), masterPos.getZ(), false
					));
					int maxLengthSq = coil.getMaxLength(stack); //not squared yet
					maxLengthSq *= maxLengthSq;
					if(!otherLink.dimension.equals(world.getDimensionKey()))
						player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"wrongDimension"), true);
					else if(otherLink.cp.getPosition().equals(masterPos))
						player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"sameConnection"), true);
					else if(distanceSq > maxLengthSq)
						player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"tooFar"), true);
					else
					{
						if(!(tileEntityLinkingPos instanceof IImmersiveConnectable))
							player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"invalidPoint"), true);
						else
						{
							IImmersiveConnectable iicLink = (IImmersiveConnectable)tileEntityLinkingPos;
							if(!((IImmersiveConnectable)tileEntityLinkingPos).canConnectCable(wire, otherLink.cp, otherLink.offset)||
									!((IImmersiveConnectable)tileEntityLinkingPos).getConnectionMaster(wire, otherLink.target).equals(otherLink.cp.getPosition())||
									!coil.canConnectCable(stack, tileEntityLinkingPos))
							{
								player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"invalidPoint"), true);
							}
							else
							{
								GlobalWireNetwork net = GlobalWireNetwork.getNetwork(world);
								boolean connectionExists = false;
								LocalWireNetwork localA = net.getLocalNet(cpHere);
								LocalWireNetwork localB = net.getLocalNet(otherLink.cp);
								if(localA==localB)
								{
									Collection<Connection> outputs = localA.getConnections(cpHere);
									if(outputs!=null)
										for(Connection con : outputs)
											if(!con.isInternal()&&con.getOtherEnd(cpHere).equals(otherLink.cp))
												connectionExists = true;
								}
								if(connectionExists)
									player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"connectionExists"), true);
								else
								{
									Set<BlockPos> ignore = new HashSet<>();
									ignore.addAll(iicHere.getIgnored(iicLink));
									ignore.addAll(iicLink.getIgnored(iicHere));
									Connection tempConn = new Connection(wire, cpHere, otherLink.cp);
									Set<BlockPos> failedReasons = findObstructingBlocks(world, tempConn, ignore);
									if(failedReasons.isEmpty())
									{
										Connection conn = new Connection(wire, cpHere, otherLink.cp);
										net.addConnection(conn);

										iicHere.connectCable(wire, cpHere, iicLink, otherLink.cp);
										iicLink.connectCable(wire, otherLink.cp, iicHere, cpHere);
										Utils.unlockIEAdvancement(player, "main/connect_wire");

										if(!player.abilities.isCreativeMode)
											coil.consumeWire(stack, (int)Math.sqrt(distanceSq));
										((TileEntity)iicHere).markDirty();
										//TODO is this needed with the new sync system?
										world.addBlockEvent(masterPos, ((TileEntity)iicHere).getBlockState().getBlock(), -1, 0);
										BlockState state = world.getBlockState(masterPos);
										world.notifyBlockUpdate(masterPos, state, state, 3);
										((TileEntity)iicLink).markDirty();
										world.addBlockEvent(otherLink.cp.getPosition(), tileEntityLinkingPos.getBlockState().getBlock(), -1, 0);
										state = world.getBlockState(otherLink.cp.getPosition());
										world.notifyBlockUpdate(otherLink.cp.getPosition(), state, state, 3);
									}
									else
									{
										player.sendStatusMessage(new TranslationTextComponent(Lib.CHAT_WARN+"cantSee"), true);
										ImmersiveEngineering.packetHandler.send(
												PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity)player),
												new MessageObstructedConnection(tempConn, failedReasons)
										);
									}
								}
							}
						}
					}
					clearWireLink(stack);
				}
			}
			return ActionResultType.SUCCESS;
		}
		return ActionResultType.PASS;
	}
}