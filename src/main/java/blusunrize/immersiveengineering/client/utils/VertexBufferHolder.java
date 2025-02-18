/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 *
 */

package blusunrize.immersiveengineering.client.utils;

import blusunrize.immersiveengineering.api.client.IVertexBufferHolder;
import blusunrize.immersiveengineering.api.utils.ResettableLazy;
import blusunrize.immersiveengineering.common.config.IEClientConfig;
import blusunrize.immersiveengineering.common.util.IELogger;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.common.util.NonNullSupplier;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.mojang.blaze3d.vertex.DefaultVertexFormat.*;

public class VertexBufferHolder implements IVertexBufferHolder
{
	public static final VertexFormat BUFFER_FORMAT = new VertexFormat(ImmutableList.of(
			ELEMENT_POSITION, ELEMENT_COLOR, ELEMENT_UV0, ELEMENT_NORMAL, ELEMENT_PADDING
	));
	private static final Lazy<Boolean> HAS_OPTIFINE = Lazy.of(() -> {
		try
		{
			Class.forName("net.optifine.Config");
			IELogger.logger.warn(
					"OptiFine detected! Automatically disabling VBOs, this will make windmills and some"+
							" other objects render much less efficiently"
			);
			return true;
		} catch(Exception x)
		{
			return false;
		}
	});
	//TODO also sort by buffer to get rid of bindBuffer calls?
	private static final Map<RenderType, List<BufferedJob>> JOBS = new IdentityHashMap<>();
	private final ResettableLazy<VertexBuffer> buffer;
	private final ResettableLazy<List<BakedQuad>> quads;

	private VertexBufferHolder(NonNullSupplier<List<BakedQuad>> quads)
	{
		this.quads = new ResettableLazy<>(quads);
		this.buffer = new ResettableLazy<>(
				() -> {
					VertexBuffer vb = new VertexBuffer(BUFFER_FORMAT);
					Tesselator tes = Tesselator.getInstance();
					BufferBuilder bb = tes.getBuilder();
					bb.begin(GL11.GL_QUADS, BUFFER_FORMAT);
					renderToBuilder(bb, new PoseStack(), 0, 0, false);
					bb.end();
					vb.upload(bb);
					return vb;
				},
				VertexBuffer::close
		);
	}

	public static void addToAPI()
	{
		IVertexBufferHolder.CREATE.setValue(VertexBufferHolder::new);
	}

	@Override
	public void render(RenderType type, int light, int overlay, MultiBufferSource directOut, PoseStack transform, boolean inverted)
	{
		if(IEClientConfig.enableVBOs.get()&&!HAS_OPTIFINE.get())
			JOBS.computeIfAbsent(type, t -> new ArrayList<>())
					.add(new BufferedJob(this, light, overlay, transform, inverted));
		else
			renderToBuilder(directOut.getBuffer(type), transform, light, overlay, inverted);
	}

	@Override
	public void reset()
	{
		buffer.reset();
		quads.reset();
	}

	private void renderToBuilder(VertexConsumer builder, PoseStack transform, int light, int overlay, boolean inverted)
	{
		if(inverted)
			builder = new InvertingVertexBuffer(4, builder);
		for(BakedQuad quad : quads.get())
			builder.putBulkData(transform.last(), quad, 1, 1, 1, light, overlay);
	}

	//Called from aftertesr.js
	public static void afterTERRendering()
	{
		if(!JOBS.isEmpty())
		{
			for(Entry<RenderType, List<BufferedJob>> typeEntry : JOBS.entrySet())
			{
				RenderType type = typeEntry.getKey();
				type.setupRenderState();
				boolean inverted = false;
				for(BufferedJob job : typeEntry.getValue())
				{
					RenderSystem.glMultiTexCoord2f(33986, 16*LightTexture.block(job.light), 16*LightTexture.sky(job.light));
					RenderSystem.glMultiTexCoord2f(33985, job.overlay&0xffff, job.overlay >>> 16);
					if(job.inverted&&!inverted)
						GL11.glCullFace(GL11.GL_FRONT);
					else if(!job.inverted&&inverted)
						GL11.glCullFace(GL11.GL_BACK);
					inverted = job.inverted;
					VertexBuffer buffer = job.buffer.buffer.get();
					buffer.bind();
					BUFFER_FORMAT.setupBufferState(0);
					buffer.draw(job.transform, GL11.GL_QUADS);
				}
				if(inverted)
					GL11.glCullFace(GL11.GL_BACK);
				type.clearRenderState();
			}
			VertexBuffer.unbind();
			BUFFER_FORMAT.clearBufferState();
			JOBS.clear();
		}
	}

	private static class BufferedJob
	{
		private final VertexBufferHolder buffer;
		private final int light;
		private final int overlay;
		private final Matrix4f transform;
		private final boolean inverted;

		private BufferedJob(VertexBufferHolder buffer, int light, int overlay, PoseStack transform, boolean inverted)
		{
			this.buffer = buffer;
			this.light = light;
			this.overlay = overlay;
			this.transform = transform.last().pose();
			this.inverted = inverted;
		}
	}
}
