package com.prayeroverheads;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PrayerOverheadsConfig.GROUP)
public interface PrayerOverheadsConfig extends Config
{
	String GROUP = "prayeroverheads";

	enum BubbleMode
	{
		VANILLA,
		HIDDEN,
		MAGENTA_TEST
	}

	@ConfigItem(
		keyName = "bubbleMode",
		name = "Overhead bubbles",
		description = "How overhead prayer bubbles are displayed. Vanilla leaves them untouched, Hidden blanks them, "
			+ "Magenta test replaces them with a solid magenta square to verify the sprite override is reaching them.",
		position = 0
	)
	default BubbleMode bubbleMode()
	{
		return BubbleMode.MAGENTA_TEST;
	}
}
