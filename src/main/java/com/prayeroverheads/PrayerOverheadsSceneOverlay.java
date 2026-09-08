package com.prayeroverheads;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Draws the scene-level prayer replacements. These stay immediately above the
 * scene so they cannot cover actor overheads or the plugin's redrawn 2D UI.
 */
class PrayerOverheadsSceneOverlay extends Overlay
{
	private final Client client;
	private final PrayerOverheadsPlugin plugin;
	private final PrayerOverheadsConfig config;
	private final ModelOutlineRenderer outlineRenderer;

	// Render-path scratch state: the inputs repeat every frame, so the derived
	// AWT objects are memoised rather than reallocated per player per frame.
	private final Map<Long, Color> highlightColors = new HashMap<>();
	private Stroke tileBorderStroke;
	private int tileBorderStrokeWidth = -1;

	@Inject
	PrayerOverheadsSceneOverlay(Client client, PrayerOverheadsPlugin plugin, PrayerOverheadsConfig config,
		ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
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
			if (!plugin.shouldHide2D(player) || plugin.isPlayerHiddenByEntityHider(player))
			{
				continue;
			}

			HeadIcon icon = plugin.getHeadIcon(player);
			if (icon != null)
			{
				renderPrayerDisplay(graphics, player, icon);
			}
		}

		return null;
	}

	void clearCaches()
	{
		highlightColors.clear();
		tileBorderStroke = null;
		tileBorderStrokeWidth = -1;
	}

	private void renderPrayerDisplay(Graphics2D graphics, Player player, HeadIcon icon)
	{
		Color color = highlightColor(colorFor(icon), config.highlightOpacity());
		if (config.showTile())
		{
			renderTile(graphics, player, color);
		}
		if (config.showOutline())
		{
			outlineRenderer.drawOutline(player, config.outlineWidth(), color, config.outlineFeather());
		}
	}

	private Color highlightColor(Color color, int opacityPercent)
	{
		long key = ((long) color.getRGB() << 32) | (opacityPercent & 0xFFFFFFFFL);
		return highlightColors.computeIfAbsent(key, k -> withHighlightOpacity(color, opacityPercent));
	}

	private void renderTile(Graphics2D graphics, Player player, Color color)
	{
		Polygon poly = player.getCanvasTilePoly();
		if (poly == null)
		{
			return;
		}

		graphics.setColor(color);
		graphics.fill(poly);

		int borderWidth = config.tileBorderWidth();
		if (borderWidth > 0)
		{
			Stroke original = graphics.getStroke();
			graphics.setColor(color);
			graphics.setStroke(tileBorderStroke(borderWidth));
			graphics.draw(poly);
			graphics.setStroke(original);
		}
	}

	private Stroke tileBorderStroke(int width)
	{
		if (tileBorderStroke == null || tileBorderStrokeWidth != width)
		{
			tileBorderStroke = new BasicStroke(width);
			tileBorderStrokeWidth = width;
		}
		return tileBorderStroke;
	}

	static Color withHighlightOpacity(Color color, int opacityPercent)
	{
		int alpha = color.getAlpha() * opacityPercent / 100;
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
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
}
