package com.prayeroverheads;

import java.awt.Color;
import java.nio.ByteBuffer;
import net.runelite.api.Client;

/**
 * The public API exposes the config index but not the client's decoded hitsplat
 * definition. This deliberately small decoder reads only archive 32's stable
 * fields so the overlay can reuse the same sprites and animation values.
 */
final class HitsplatDefinition
{
	private static final int HITSPLAT_ARCHIVE = 32;
	int leftSprite = -1;
	int middleSprite = -1;
	int rightSprite = -1;
	int iconSprite = -1;
	int textColor = 0xFFFFFF;
	int scrollX;
	int scrollY;
	int textOffsetY;
	int fadeStartCycle = -1;
	int varbitId = -1;
	int varpId = -1;
	int[] transforms;

	private Color awtTextColor;

	/**
	 * {@link #textColor} as an opaque AWT colour, built once per definition rather than
	 * once per splat per frame.
	 */
	Color textColorAwt()
	{
		if (awtTextColor == null)
		{
			awtTextColor = new Color(textColor | 0xFF000000, true);
		}
		return awtTextColor;
	}

	static HitsplatDefinition load(Client client, int id)
	{
		byte[] data = client.getIndexConfig().loadData(HITSPLAT_ARCHIVE, id);
		return data == null ? null : decode(data);
	}

	static HitsplatDefinition decode(byte[] data)
	{
		try
		{
			HitsplatDefinition definition = new HitsplatDefinition();
			ByteBuffer input = ByteBuffer.wrap(data);
			while (input.hasRemaining())
			{
				int opcode = unsignedByte(input);
				switch (opcode)
				{
					case 0:
						return definition;
					case 1:
						readBigSmart(input); // font id; RuneLite does not expose cache fonts
						break;
					case 2:
						definition.textColor = (unsignedByte(input) << 16)
							| (unsignedByte(input) << 8) | unsignedByte(input);
						break;
					case 3:
						definition.leftSprite = readBigSmart(input);
						break;
					case 4:
						definition.iconSprite = readBigSmart(input);
						break;
					case 5:
						definition.middleSprite = readBigSmart(input);
						break;
					case 6:
						definition.rightSprite = readBigSmart(input);
						break;
					case 7:
						definition.scrollX = input.getShort();
						break;
					case 8:
						readString(input);
						break;
					case 9:
						// Display duration. Unused: the overlay takes the splat's lifetime
						// from Hitsplat#getDisappearsOnGameCycle, which the client has
						// already resolved. Still consumed to keep the buffer aligned.
						unsignedShort(input);
						break;
					case 10:
						definition.scrollY = input.getShort();
						break;
					case 11:
						definition.fadeStartCycle = 0;
						break;
					case 12:
						unsignedByte(input);
						break;
					case 13:
						definition.textOffsetY = input.getShort();
						break;
					case 14:
						definition.fadeStartCycle = unsignedShort(input);
						break;
					case 17:
					case 18:
						readTransforms(input, definition, opcode == 18);
						break;
					default:
						return null; // Unknown cache revision: use the overlay fallback.
				}
			}
		}
		catch (RuntimeException ex)
		{
			return null;
		}
		return null;
	}

	private static int readBigSmart(ByteBuffer input)
	{
		if (input.get(input.position()) < 0)
		{
			return input.getInt() & Integer.MAX_VALUE;
		}
		int value = unsignedShort(input);
		return value == 32767 ? -1 : value;
	}

	private static void readString(ByteBuffer input)
	{
		if (input.get() != 0)
		{
			throw new IllegalArgumentException("Invalid cache string");
		}
		while (input.get() != 0)
		{
			// Find the terminator. Decoding is unnecessary because the amount replaces %1.
		}
	}

	int transformedId(Client client)
	{
		if (transforms == null)
		{
			return -1;
		}
		int selector = varbitId >= 0 ? client.getVarbitValue(varbitId)
			: varpId >= 0 ? client.getVarpValue(varpId) : -1;
		return selector >= 0 && selector < transforms.length - 1
			? transforms[selector] : transforms[transforms.length - 1];
	}

	private static void readTransforms(ByteBuffer input, HitsplatDefinition definition, boolean hasDefault)
	{
		definition.varbitId = nullableUnsignedShort(input);
		definition.varpId = nullableUnsignedShort(input);
		int defaultId = -1;
		if (hasDefault)
		{
			defaultId = nullableUnsignedShort(input);
		}
		int lastIndex = unsignedByte(input);
		definition.transforms = new int[lastIndex + 2];
		for (int i = 0; i <= lastIndex; i++)
		{
			definition.transforms[i] = nullableUnsignedShort(input);
		}
		definition.transforms[lastIndex + 1] = defaultId;
	}

	private static int nullableUnsignedShort(ByteBuffer input)
	{
		int value = unsignedShort(input);
		return value == 0xFFFF ? -1 : value;
	}

	private static int unsignedByte(ByteBuffer input)
	{
		return input.get() & 0xFF;
	}

	private static int unsignedShort(ByteBuffer input)
	{
		return input.getShort() & 0xFFFF;
	}
}
