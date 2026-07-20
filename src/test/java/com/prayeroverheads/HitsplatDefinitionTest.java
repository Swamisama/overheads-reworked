package com.prayeroverheads;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class HitsplatDefinitionTest
{
	@Test
	public void decodesSpriteAndAnimationFields() throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(bytes);
		output.writeByte(2);
		output.write(new byte[]{0x12, 0x34, 0x56});
		writeBigSmart(output, 3, 1358);
		writeBigSmart(output, 4, 1359);
		writeBigSmart(output, 5, 1360);
		writeBigSmart(output, 6, 1361);
		output.writeByte(7);
		output.writeShort(-12);
		// Display duration: skipped by the decoder, so the fields after it must still line up.
		output.writeByte(9);
		output.writeShort(80);
		output.writeByte(10);
		output.writeShort(24);
		output.writeByte(13);
		output.writeShort(-3);
		output.writeByte(14);
		output.writeShort(55);
		output.writeByte(0);

		HitsplatDefinition definition = HitsplatDefinition.decode(bytes.toByteArray());

		assertEquals(0x123456, definition.textColor);
		assertEquals(1358, definition.leftSprite);
		assertEquals(1359, definition.iconSprite);
		assertEquals(1360, definition.middleSprite);
		assertEquals(1361, definition.rightSprite);
		assertEquals(-12, definition.scrollX);
		assertEquals(24, definition.scrollY);
		assertEquals(-3, definition.textOffsetY);
		assertEquals(55, definition.fadeStartCycle);
	}

	@Test
	public void textColorIsExposedAsAnOpaqueAwtColorAndMemoised()
	{
		HitsplatDefinition definition = HitsplatDefinition.decode(new byte[]{
			2, 0x12, 0x34, 0x56,
			0
		});

		Color color = definition.textColorAwt();
		assertEquals(0x12, color.getRed());
		assertEquals(0x34, color.getGreen());
		assertEquals(0x56, color.getBlue());
		assertEquals(255, color.getAlpha());
		// The render path calls this per splat per frame; it must not reallocate.
		assertSame(color, definition.textColorAwt());
	}

	@Test
	public void rejectsUnknownOrTruncatedDefinitions()
	{
		assertNull(HitsplatDefinition.decode(new byte[]{99, 0}));
		assertNull(HitsplatDefinition.decode(new byte[]{2, 1}));
	}

	@Test
	public void decodesTransformedDefinitionIds()
	{
		HitsplatDefinition definition = HitsplatDefinition.decode(new byte[]{
			18,
			0, 10,       // varbit
			(byte) 0xFF, (byte) 0xFF, // no varp
			0, 42,       // default definition
			1,           // highest selector index
			0, 20,
			(byte) 0xFF, (byte) 0xFF,
			0
		});

		assertEquals(10, definition.varbitId);
		assertEquals(-1, definition.varpId);
		assertEquals(20, definition.transforms[0]);
		assertEquals(-1, definition.transforms[1]);
		assertEquals(42, definition.transforms[2]);
	}

	private static void writeBigSmart(DataOutputStream output, int opcode, int value) throws Exception
	{
		output.writeByte(opcode);
		output.writeShort(value);
	}
}
