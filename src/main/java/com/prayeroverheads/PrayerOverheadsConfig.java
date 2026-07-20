package com.prayeroverheads;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(PrayerOverheadsConfig.GROUP)
public interface PrayerOverheadsConfig extends Config
{
	String GROUP = "overheadsreworked";

	@ConfigSection(
		name = "Hide vanilla overheads",
		description = "Which players lose the vanilla 2D overhead block (prayer bubble, chat, health bar)",
		position = 0
	)
	String hideSection = "hide";

	@ConfigSection(
		name = "Prayer display",
		description = "What replaces the hidden prayer bubble",
		position = 1
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Redraw",
		description = "2D elements to draw back after hiding the vanilla block",
		position = 2
	)
	String redrawSection = "redraw";

	@ConfigItem(
		keyName = "hideSelf2D",
		name = "Hide own overhead",
		description = "Hide the vanilla 2D overhead block on your own player",
		section = hideSection,
		position = 0
	)
	default boolean hideSelf2D()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideParty2D",
		name = "Hide party overheads",
		description = "Hide the vanilla 2D overhead block on RuneLite party members",
		section = hideSection,
		position = 1
	)
	default boolean hideParty2D()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideOthers2D",
		name = "Hide other players' overheads",
		description = "Hide the vanilla 2D overhead block on all other players",
		section = hideSection,
		position = 2
	)
	default boolean hideOthers2D()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onlyWhilePraying",
		name = "Only while praying",
		description = "Only hide a player's 2D block while they actually have an overhead prayer icon. "
			+ "Off hides it unconditionally, which is steadier but also affects players who aren't praying.",
		section = hideSection,
		position = 3
	)
	default boolean onlyWhilePraying()
	{
		return true;
	}

	@ConfigItem(
		keyName = "replaceSmite",
		name = "Replace Smite",
		description = "Apply the plugin's replacement display to Smite. Off keeps Smite's vanilla overhead icon.",
		section = hideSection,
		position = 4
	)
	default boolean replaceSmite()
	{
		return false;
	}

	@ConfigItem(
		keyName = "replaceRedemption",
		name = "Replace Redemption",
		description = "Apply the plugin's replacement display to Redemption. Off keeps Redemption's vanilla overhead icon.",
		section = hideSection,
		position = 5
	)
	default boolean replaceRedemption()
	{
		return false;
	}

	@ConfigItem(
		keyName = "replaceRetribution",
		name = "Replace Retribution",
		description = "Apply the plugin's replacement display to Retribution. Off keeps Retribution's vanilla overhead icon.",
		section = hideSection,
		position = 6
	)
	default boolean replaceRetribution()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTile",
		name = "Tile highlight",
		description = "Tint the tile beneath a player using their active overhead prayer colour",
		section = displaySection,
		position = 0
	)
	default boolean showTile()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOutline",
		name = "Character outline",
		description = "Outline the player using their active overhead prayer colour; can be combined with the tile",
		section = displaySection,
		position = 1
	)
	default boolean showOutline()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showCompactOverhead",
		name = "Compact overhead",
		description = "Draw a scaled copy of the original overhead prayer, including its coloured background",
		section = displaySection,
		position = 2
	)
	default boolean showCompactOverhead()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightOpacity",
		name = "Highlight opacity",
		description = "Overall opacity of tile and character highlights, as a percentage of the prayer colour alpha",
		section = displaySection,
		position = 3
	)
	@Range(min = 0, max = 100)
	default int highlightOpacity()
	{
		return 100;
	}

	@Alpha
	@ConfigItem(
		keyName = "meleeColor",
		name = "Protect from Melee",
		description = "Colour used for Protect from Melee (and Deflect Melee)",
		section = displaySection,
		position = 4
	)
	default Color meleeColor()
	{
		return new Color(0xE6, 0x3E, 0x31, 0x3C);
	}

	@Alpha
	@ConfigItem(
		keyName = "rangedColor",
		name = "Protect from Missiles",
		description = "Colour used for Protect from Missiles (and Deflect Ranged)",
		section = displaySection,
		position = 5
	)
	default Color rangedColor()
	{
		return new Color(0x52, 0xC4, 0x1A, 0x3C);
	}

	@Alpha
	@ConfigItem(
		keyName = "magicColor",
		name = "Protect from Magic",
		description = "Colour used for Protect from Magic (and Deflect Magic)",
		section = displaySection,
		position = 6
	)
	default Color magicColor()
	{
		return new Color(0x2F, 0x81, 0xF7, 0x3C);
	}

	@Alpha
	@ConfigItem(
		keyName = "otherColor",
		name = "Other overheads",
		description = "Colour used for non-protection overheads (Retribution, Smite, Redemption, combo icons)",
		section = displaySection,
		position = 7
	)
	default Color otherColor()
	{
		return new Color(0xE8, 0xD4, 0x4D, 0x3C);
	}

	@ConfigItem(
		keyName = "tileBorderWidth",
		name = "Tile border width",
		description = "Outline width of the underfoot tile, in pixels. 0 draws fill only.",
		section = displaySection,
		position = 8
	)
	@Range(max = 8)
	default int tileBorderWidth()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline width",
		description = "Model outline width, in pixels (Outline display only)",
		section = displaySection,
		position = 9
	)
	@Range(min = 1, max = 8)
	default int outlineWidth()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "iconScale",
		name = "Compact overhead size",
		description = "Size of the compact original overhead, as a percentage of its normal size",
		section = displaySection,
		position = 10
	)
	@Range(min = 25, max = 150)
	default int iconScale()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "keepChatText",
		name = "Overhead chat",
		description = "Redraw overhead chat text for players whose vanilla block is hidden",
		section = redrawSection,
		position = 0
	)
	default boolean keepChatText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepHealthBar",
		name = "Health bar",
		description = "Redraw the health bar for players whose vanilla block is hidden",
		section = redrawSection,
		position = 1
	)
	default boolean keepHealthBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepHitsplats",
		name = "Hitsplats",
		description = "Redraw hitsplats for players whose vanilla block is hidden",
		section = redrawSection,
		position = 2
	)
	default boolean keepHitsplats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "keepSkull",
		name = "Skull icon",
		description = "Redraw the PK skull for players whose vanilla block is hidden",
		section = redrawSection,
		position = 3
	)
	default boolean keepSkull()
	{
		return true;
	}

	@ConfigItem(
		keyName = "heightOffset",
		name = "Height offset",
		description = "Extra height above the player for redrawn overhead elements, in world units",
		section = redrawSection,
		position = 4
	)
	@Range(min = -100, max = 200)
	default int heightOffset()
	{
		return 0;
	}
}
