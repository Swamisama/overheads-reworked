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
	String GROUP = "prayeroverheads";

	enum PrayerDisplay
	{
		NONE,
		TILE,
		OUTLINE,
		MINI_ICON
	}

	@ConfigSection(
		name = "Hide vanilla overheads",
		description = "Which actors lose the vanilla 2D overhead block (prayer bubble, chat, health bar)",
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
		keyName = "hideNpc2D",
		name = "Hide NPC overheads",
		description = "Hide the vanilla 2D overhead block on NPCs",
		section = hideSection,
		position = 3
	)
	default boolean hideNpc2D()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onlyWhilePraying",
		name = "Only while praying",
		description = "Only hide an actor's 2D block while it actually has an overhead prayer icon. "
			+ "Off hides it unconditionally, which is steadier but also affects actors that aren't praying.",
		section = hideSection,
		position = 4
	)
	default boolean onlyWhilePraying()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prayerDisplay",
		name = "Display",
		description = "How an active protection prayer is shown instead of the vanilla bubble",
		section = displaySection,
		position = 0
	)
	default PrayerDisplay prayerDisplay()
	{
		return PrayerDisplay.TILE;
	}

	@Alpha
	@ConfigItem(
		keyName = "meleeColor",
		name = "Protect from Melee",
		description = "Colour used for Protect from Melee (and Deflect Melee)",
		section = displaySection,
		position = 1
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
		position = 2
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
		position = 3
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
		position = 4
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
		position = 5
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
		position = 6
	)
	@Range(min = 1, max = 8)
	default int outlineWidth()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "iconScale",
		name = "Mini icon size",
		description = "Size of the redrawn prayer icon, as a percentage of the prayer book sprite (Mini icon display only)",
		section = displaySection,
		position = 7
	)
	@Range(min = 25, max = 150)
	default int iconScale()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "keepChatText",
		name = "Overhead chat",
		description = "Redraw overhead chat text for actors whose vanilla block is hidden",
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
		description = "Redraw the health bar for actors whose vanilla block is hidden",
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
		description = "Redraw hitsplats for actors whose vanilla block is hidden",
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
		description = "Extra height above the actor for redrawn overhead elements, in world units",
		section = redrawSection,
		position = 4
	)
	@Range(min = -100, max = 200)
	default int heightOffset()
	{
		return 0;
	}
}
