package com.prayeroverheads;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.SpriteID;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Redraws, for every actor whose vanilla 2D block the draw listener suppressed, the
 * prayer display plus whichever 2D elements the user chose to keep.
 */
class PrayerOverheadsOverlay extends Overlay
{
	private static final int HEALTH_BAR_WIDTH = 30;
	private static final int HEALTH_BAR_HEIGHT = 4;
	// The vanilla bar sits just above the head rather than at the model's logical top.
	private static final int HEALTH_BAR_RAISE = 25;
	private static final Color HEALTH_BAR_LOST = new Color(0x8B, 0x00, 0x00);
	private static final Color HEALTH_BAR_REMAINING = new Color(0x00, 0xC8, 0x00);
	private static final Color CHAT_TEXT = new Color(0xFF, 0xFF, 0x00);

	private static final int HITSPLAT_WIDTH = 21;
	private static final int HITSPLAT_HEIGHT = 15;
	private static final int MAX_HITSPLATS = 4;

	private final Client client;
	private final PrayerOverheadsPlugin plugin;
	private final PrayerOverheadsConfig config;
	private final SpriteManager spriteManager;
	private final ModelOutlineRenderer outlineRenderer;

	private final TextComponent textComponent = new TextComponent();

	@Inject
	PrayerOverheadsOverlay(Client client, PrayerOverheadsPlugin plugin, PrayerOverheadsConfig config,
		SpriteManager spriteManager, ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;
		this.outlineRenderer = outlineRenderer;

		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		for (Player player : worldView.players())
		{
			if (plugin.shouldHide2D(player))
			{
				renderActor(graphics, player);
			}
		}

		for (NPC npc : worldView.npcs())
		{
			if (plugin.shouldHide2D(npc))
			{
				renderActor(graphics, npc);
			}
		}

		return null;
	}

	private void renderActor(Graphics2D graphics, Actor actor)
	{
		HeadIcon icon = plugin.getHeadIcon(actor);
		if (icon != null)
		{
			renderPrayerDisplay(graphics, actor, icon);
		}

		int height = actor.getLogicalHeight() + config.heightOffset();

		if (config.keepChatText())
		{
			renderChatText(graphics, actor, height);
		}

		if (config.keepHealthBar())
		{
			renderHealthBar(graphics, actor, height);
		}

		if (config.keepSkull() && actor instanceof Player)
		{
			renderSkull(graphics, (Player) actor, height);
		}

		if (config.keepHitsplats())
		{
			renderHitsplats(graphics, actor);
		}
	}

	private void renderHitsplats(Graphics2D graphics, Actor actor)
	{
		List<Hitsplat> splats = null;
		for (PrayerOverheadsPlugin.TrackedHitsplat tracked : plugin.getTrackedHitsplats())
		{
			if (tracked.actor != actor)
			{
				continue;
			}

			if (splats == null)
			{
				splats = new ArrayList<>(MAX_HITSPLATS);
			}
			splats.add(tracked.hitsplat);
			if (splats.size() == MAX_HITSPLATS)
			{
				break;
			}
		}

		if (splats == null)
		{
			return;
		}

		// Hitsplats sit on the model rather than above it, and stack in a centred row.
		Point anchor = headPoint(actor, actor.getLogicalHeight() / 2);
		if (anchor == null)
		{
			return;
		}

		int totalWidth = splats.size() * HITSPLAT_WIDTH;
		int x = anchor.getX() - totalWidth / 2;
		int y = anchor.getY() - HITSPLAT_HEIGHT / 2;

		for (Hitsplat splat : splats)
		{
			renderHitsplat(graphics, splat, x, y);
			x += HITSPLAT_WIDTH;
		}
	}

	private void renderHitsplat(Graphics2D graphics, Hitsplat hitsplat, int x, int y)
	{
		graphics.setColor(hitsplatColor(hitsplat.getHitsplatType()));
		graphics.fillOval(x, y, HITSPLAT_WIDTH - 1, HITSPLAT_HEIGHT - 1);

		String amount = Integer.toString(hitsplat.getAmount());
		int textWidth = graphics.getFontMetrics().stringWidth(amount);
		int baseline = y + (HITSPLAT_HEIGHT + graphics.getFontMetrics().getAscent()) / 2 - 1;

		textComponent.setText(amount);
		textComponent.setColor(Color.WHITE);
		textComponent.setPosition(new java.awt.Point(x + (HITSPLAT_WIDTH - textWidth) / 2, baseline));
		textComponent.render(graphics);
	}

	/**
	 * Approximates the vanilla hitsplat palette. The client keys these off sprite ids
	 * that the API does not expose, so the mapping is by hitsplat type instead.
	 */
	private Color hitsplatColor(int type)
	{
		if (type == HitsplatID.BLOCK_ME || type == HitsplatID.BLOCK_OTHER || type == HitsplatID.DISEASE_BLOCKED)
		{
			return new Color(0x00, 0x64, 0xC8);
		}
		if (type == HitsplatID.POISON)
		{
			return new Color(0x00, 0x96, 0x00);
		}
		if (type == HitsplatID.VENOM)
		{
			return new Color(0x00, 0x50, 0x00);
		}
		if (type == HitsplatID.DISEASE)
		{
			return new Color(0xC8, 0x96, 0x00);
		}
		if (type == HitsplatID.HEAL)
		{
			return new Color(0x9B, 0x30, 0xC8);
		}
		if (type == HitsplatID.PRAYER_DRAIN || type == HitsplatID.SANITY_DRAIN || type == HitsplatID.SANITY_RESTORE)
		{
			return new Color(0x64, 0x64, 0xC8);
		}
		if (type == HitsplatID.DAMAGE_ME_CYAN || type == HitsplatID.DAMAGE_OTHER_CYAN
			|| type == HitsplatID.DAMAGE_MAX_ME_CYAN || type == HitsplatID.CYAN_UP || type == HitsplatID.CYAN_DOWN)
		{
			return new Color(0x00, 0xB4, 0xB4);
		}
		if (type == HitsplatID.DAMAGE_ME_ORANGE || type == HitsplatID.DAMAGE_OTHER_ORANGE
			|| type == HitsplatID.DAMAGE_MAX_ME_ORANGE || type == HitsplatID.BURN)
		{
			return new Color(0xE0, 0x78, 0x00);
		}
		if (type == HitsplatID.DAMAGE_ME_YELLOW || type == HitsplatID.DAMAGE_OTHER_YELLOW
			|| type == HitsplatID.DAMAGE_MAX_ME_YELLOW)
		{
			return new Color(0xC8, 0xC8, 0x00);
		}
		if (type == HitsplatID.DAMAGE_ME_WHITE || type == HitsplatID.DAMAGE_OTHER_WHITE
			|| type == HitsplatID.DAMAGE_MAX_ME_WHITE)
		{
			return new Color(0x96, 0x96, 0x96);
		}

		// Everything else, including the plain and max-hit damage splats.
		return new Color(0xC8, 0x00, 0x00);
	}

	private void renderPrayerDisplay(Graphics2D graphics, Actor actor, HeadIcon icon)
	{
		Color color = colorFor(icon);

		switch (config.prayerDisplay())
		{
			case TILE:
				renderTile(graphics, actor, color);
				break;
			case OUTLINE:
				outlineRenderer.drawOutline(actor, config.outlineWidth(), color, 0);
				break;
			case MINI_ICON:
				renderMiniIcon(graphics, actor, icon);
				break;
			case NONE:
			default:
				break;
		}
	}

	private void renderTile(Graphics2D graphics, Actor actor, Color color)
	{
		LocalPoint location = actor.getLocalLocation();
		if (location == null)
		{
			return;
		}

		int size = 1;
		if (actor instanceof NPC)
		{
			NPCComposition composition = ((NPC) actor).getTransformedComposition();
			if (composition != null)
			{
				size = composition.getSize();
			}
		}

		Polygon poly = size > 1
			? Perspective.getCanvasTileAreaPoly(client, location, size)
			: Perspective.getCanvasTilePoly(client, location);
		if (poly == null)
		{
			return;
		}

		graphics.setColor(color);
		graphics.fill(poly);

		int borderWidth = config.tileBorderWidth();
		if (borderWidth > 0)
		{
			// The configured alpha is tuned for a fill; the border reads better opaque.
			Stroke original = graphics.getStroke();
			graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue()));
			graphics.setStroke(new BasicStroke(borderWidth));
			graphics.draw(poly);
			graphics.setStroke(original);
		}
	}

	private void renderMiniIcon(Graphics2D graphics, Actor actor, HeadIcon icon)
	{
		int spriteId = spriteFor(icon);
		if (spriteId == -1)
		{
			return;
		}

		BufferedImage sprite = spriteManager.getSprite(spriteId, 0);
		if (sprite == null)
		{
			return;
		}

		int scale = config.iconScale();
		int width = Math.max(1, sprite.getWidth() * scale / 100);
		int height = Math.max(1, sprite.getHeight() * scale / 100);

		Point anchor = headPoint(actor, actor.getLogicalHeight() + config.heightOffset() + height);
		if (anchor == null)
		{
			return;
		}

		graphics.drawImage(sprite, anchor.getX() - width / 2, anchor.getY(), width, height, null);
	}

	private void renderChatText(Graphics2D graphics, Actor actor, int height)
	{
		String text = actor.getOverheadText();
		if (text == null || text.isEmpty() || actor.getOverheadCycle() <= 0)
		{
			return;
		}

		Point anchor = headPoint(actor, height + 40);
		if (anchor == null)
		{
			return;
		}

		int width = graphics.getFontMetrics().stringWidth(text);
		textComponent.setText(text);
		textComponent.setColor(CHAT_TEXT);
		textComponent.setPosition(new java.awt.Point(anchor.getX() - width / 2, anchor.getY()));
		textComponent.render(graphics);
	}

	private void renderHealthBar(Graphics2D graphics, Actor actor, int height)
	{
		int ratio = actor.getHealthRatio();
		int scale = actor.getHealthScale();
		// -1 is the client's own "bar not currently shown" signal; mirror it.
		if (ratio < 0 || scale <= 0)
		{
			return;
		}

		Point anchor = headPoint(actor, height + HEALTH_BAR_RAISE);
		if (anchor == null)
		{
			return;
		}

		int x = anchor.getX() - HEALTH_BAR_WIDTH / 2;
		int y = anchor.getY();
		int remaining = Math.min(HEALTH_BAR_WIDTH, HEALTH_BAR_WIDTH * ratio / scale);

		graphics.setColor(HEALTH_BAR_LOST);
		graphics.fillRect(x, y, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		graphics.setColor(HEALTH_BAR_REMAINING);
		graphics.fillRect(x, y, remaining, HEALTH_BAR_HEIGHT);
	}

	private void renderSkull(Graphics2D graphics, Player player, int height)
	{
		int skullIcon = player.getSkullIcon();
		if (skullIcon < 0)
		{
			return;
		}

		BufferedImage sprite = spriteManager.getSprite(net.runelite.api.gameval.SpriteID.HEADICONS_PK, skullIcon);
		if (sprite == null)
		{
			return;
		}

		Point anchor = headPoint(player, height + 20 + sprite.getHeight());
		if (anchor == null)
		{
			return;
		}

		graphics.drawImage(sprite, anchor.getX() - sprite.getWidth() / 2, anchor.getY(), null);
	}

	private Point headPoint(Actor actor, int zOffset)
	{
		LocalPoint location = actor.getLocalLocation();
		if (location == null)
		{
			return null;
		}

		return Perspective.localToCanvas(client, location, client.getTopLevelWorldView().getPlane(), zOffset);
	}

	private Color colorFor(HeadIcon icon)
	{
		switch (icon)
		{
			case MELEE:
			case DEFLECT_MELEE:
				return config.meleeColor();
			case RANGED:
			case DEFLECT_RANGE:
				return config.rangedColor();
			case MAGIC:
			case DEFLECT_MAGE:
				return config.magicColor();
			default:
				return config.otherColor();
		}
	}

	private int spriteFor(HeadIcon icon)
	{
		switch (icon)
		{
			case MELEE:
			case DEFLECT_MELEE:
				return SpriteID.PRAYER_PROTECT_FROM_MELEE;
			case RANGED:
			case DEFLECT_RANGE:
				return SpriteID.PRAYER_PROTECT_FROM_MISSILES;
			case MAGIC:
			case DEFLECT_MAGE:
				return SpriteID.PRAYER_PROTECT_FROM_MAGIC;
			case RETRIBUTION:
				return SpriteID.PRAYER_RETRIBUTION;
			case SMITE:
				return SpriteID.PRAYER_SMITE;
			case REDEMPTION:
				return SpriteID.PRAYER_REDEMPTION;
			default:
				return -1;
		}
	}
}
