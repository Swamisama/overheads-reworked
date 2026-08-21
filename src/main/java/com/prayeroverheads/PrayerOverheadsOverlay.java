package com.prayeroverheads;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * Redraws the wanted 2D elements for every player whose vanilla block the draw
 * listener suppressed. Scene-level prayer replacements live in a separate overlay
 * so this UI can render after the scene and actor overhead passes.
 */
class PrayerOverheadsOverlay extends Overlay
{
	private static final int HEALTH_BAR_WIDTH = 30;
	private static final int HEALTH_BAR_HEIGHT = 5;
	private static final int HEALTH_BAR_RAISE = 15;
	private static final int ELEMENT_GAP = 2;
	private static final Color HEALTH_BAR_LOST = new Color(0xFF, 0x00, 0x00);
	private static final Color HEALTH_BAR_REMAINING = new Color(0x00, 0xFF, 0x00);
	private static final Color CHAT_TEXT = new Color(0xFF, 0xFF, 0x00);

	private static final int HITSPLAT_WIDTH = 21;
	private static final int HITSPLAT_HEIGHT = 15;
	// Vanilla splats sit above the player's midpoint rather than on it.
	private static final int HITSPLAT_RAISE = 45;

	private static final Color HITSPLAT_BLOCK = new Color(0x00, 0x64, 0xC8);
	private static final Color HITSPLAT_POISON = new Color(0x00, 0x96, 0x00);
	private static final Color HITSPLAT_VENOM = new Color(0x00, 0x50, 0x00);
	private static final Color HITSPLAT_DISEASE = new Color(0xC8, 0x96, 0x00);
	private static final Color HITSPLAT_HEAL = new Color(0x9B, 0x30, 0xC8);
	private static final Color HITSPLAT_DRAIN = new Color(0x64, 0x64, 0xC8);
	private static final Color HITSPLAT_CYAN = new Color(0x00, 0xB4, 0xB4);
	private static final Color HITSPLAT_ORANGE = new Color(0xE0, 0x78, 0x00);
	private static final Color HITSPLAT_YELLOW = new Color(0xC8, 0xC8, 0x00);
	private static final Color HITSPLAT_WHITE = new Color(0x96, 0x96, 0x96);
	private static final Color HITSPLAT_DAMAGE = new Color(0xC8, 0x00, 0x00);

	private final Client client;
	private final PrayerOverheadsPlugin plugin;
	private final PrayerOverheadsConfig config;
	private final SpriteManager spriteManager;

	private final TextComponent textComponent = new TextComponent();
	private final Map<Integer, HitsplatDefinition> hitsplatDefinitions = new HashMap<>();
	private final Map<Long, BufferedImage> compactOverheads = new HashMap<>();

	@Inject
	PrayerOverheadsOverlay(Client client, PrayerOverheadsPlugin plugin, PrayerOverheadsConfig config,
		SpriteManager spriteManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.spriteManager = spriteManager;

		setLayer(OverlayLayer.UNDER_WIDGETS);
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

		List<ChatEntry> chatEntries = new ArrayList<>();
		for (Player player : worldView.players())
		{
			if (plugin.shouldHide2D(player) && !plugin.isPlayerHiddenByEntityHider(player))
			{
				renderPlayer(graphics, player, chatEntries);
			}
		}

		layoutChatEntries(chatEntries);
		renderChatEntries(graphics, chatEntries);

		return null;
	}

	private void renderPlayer(Graphics2D graphics, Player player, List<ChatEntry> chatEntries)
	{
		HeadIcon icon = plugin.getHeadIcon(player);
		if (config.keepChatText())
		{
			collectChatEntry(graphics, player, chatEntries);
		}

		int stackedHeight = 0;
		if (config.keepHealthBar())
		{
			stackedHeight = renderHealthBar(graphics, player);
		}

		if (config.keepSkull())
		{
			stackedHeight += renderSkull(graphics, player, stackedHeight);
		}

		if (icon != null && config.showCompactOverhead())
		{
			renderCompactOverhead(graphics, player, icon, stackedHeight);
		}

		if (config.keepHitsplats())
		{
			renderHitsplats(graphics, player);
		}
	}

	private void renderHitsplats(Graphics2D graphics, Player player)
	{
		// Vanilla uses four staggered slots rather than a single horizontal row. The
		// slot is fixed when the splat is tracked, so a splat does not move when an
		// earlier one expires.
		Point anchor = null;
		for (PrayerOverheadsPlugin.TrackedHitsplat tracked : plugin.getTrackedHitsplats())
		{
			if (tracked.actor != player)
			{
				continue;
			}

			if (anchor == null)
			{
				anchor = headPoint(graphics, player, player.getLogicalHeight() / 2 + HITSPLAT_RAISE);
				if (anchor == null)
				{
					return;
				}
			}

			int slot = tracked.slot;
			int xOffset = slot == 2 ? -15 : slot == 3 ? 15 : 0;
			int yOffset = slot == 1 ? -20 : slot >= 2 ? -10 : 0;
			renderHitsplat(graphics, tracked, anchor.getX() + xOffset, anchor.getY() + yOffset);
		}
	}

	/**
	 * Drops the sprite and definition caches. Called on shutdown so an unused plugin
	 * holds no decoded cache data or scaled images.
	 */
	void clearCaches()
	{
		hitsplatDefinitions.clear();
		compactOverheads.clear();
	}

	private void renderHitsplat(Graphics2D graphics, PrayerOverheadsPlugin.TrackedHitsplat tracked, int centerX, int centerY)
	{
		Hitsplat hitsplat = tracked.hitsplat;
		HitsplatDefinition definition = hitsplatDefinition(hitsplat.getHitsplatType());
		if (definition != null && definition.transforms != null)
		{
			int transformedId = definition.transformedId(client);
			definition = transformedId < 0 ? null : hitsplatDefinition(transformedId);
		}

		if (definition == null || !renderDefinedHitsplat(graphics, tracked, definition, centerX, centerY))
		{
			renderFallbackHitsplat(graphics, hitsplat, centerX, centerY);
		}
	}

	private HitsplatDefinition hitsplatDefinition(int id)
	{
		HitsplatDefinition definition = hitsplatDefinitions.get(id);
		if (!hitsplatDefinitions.containsKey(id))
		{
			definition = HitsplatDefinition.load(client, id);
			hitsplatDefinitions.put(id, definition);
		}
		return definition;
	}

	private boolean renderDefinedHitsplat(Graphics2D graphics, PrayerOverheadsPlugin.TrackedHitsplat tracked,
		HitsplatDefinition definition, int centerX, int centerY)
	{
		BufferedImage left = sprite(definition.leftSprite);
		BufferedImage icon = sprite(definition.iconSprite);
		BufferedImage middle = sprite(definition.middleSprite);
		BufferedImage right = sprite(definition.rightSprite);
		if (left == null && icon == null && middle == null && right == null)
		{
			return false;
		}

		Font originalFont = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		String amount = Integer.toString(tracked.hitsplat.getAmount());
		int textWidth = metrics.stringWidth(amount);
		int middleWidth = middle == null ? textWidth + 4 : middle.getWidth();
		int repeats = middle == null ? 1 : Math.max(1, (textWidth + middleWidth - 1) / middleWidth);
		int width = imageWidth(left) + imageWidth(icon) + repeats * middleWidth + imageWidth(right);
		int height = Math.max(metrics.getHeight(), Math.max(imageHeight(left), Math.max(imageHeight(icon),
			Math.max(imageHeight(middle), imageHeight(right)))));

		int elapsed = Math.max(0, client.getGameCycle() - tracked.appliedOnGameCycle);
		int duration = Math.max(1, tracked.hitsplat.getDisappearsOnGameCycle() - tracked.appliedOnGameCycle);
		float progress = Math.min(1f, elapsed / (float) duration);
		int x = centerX - width / 2 + Math.round(definition.scrollX * progress);
		int y = centerY - height / 2 - Math.round(definition.scrollY * progress);
		float opacity = 1f;
		if (definition.fadeStartCycle >= 0 && elapsed > definition.fadeStartCycle)
		{
			opacity = Math.max(0f, 1f - (elapsed - definition.fadeStartCycle)
				/ (float) Math.max(1, duration - definition.fadeStartCycle));
		}

		Composite originalComposite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
		int cursor = x;
		cursor = drawSprite(graphics, left, cursor, y, height);
		cursor = drawSprite(graphics, icon, cursor, y, height);
		int textAreaX = cursor;
		if (middle != null)
		{
			for (int i = 0; i < repeats; i++)
			{
				graphics.drawImage(middle, cursor, y + (height - middle.getHeight()) / 2, null);
				cursor += middleWidth;
			}
		}
		else
		{
			cursor += middleWidth;
		}
		drawSprite(graphics, right, cursor, y, height);

		int textX = textAreaX + (repeats * middleWidth - textWidth) / 2;
		int baseline = y + (height - metrics.getHeight()) / 2 + metrics.getAscent() + definition.textOffsetY;
		graphics.setColor(Color.BLACK);
		graphics.drawString(amount, textX + 1, baseline + 1);
		graphics.setColor(definition.textColorAwt());
		graphics.drawString(amount, textX, baseline);
		graphics.setComposite(originalComposite);
		graphics.setFont(originalFont);
		return true;
	}

	private void renderFallbackHitsplat(Graphics2D graphics, Hitsplat hitsplat, int centerX, int centerY)
	{
		int x = centerX - HITSPLAT_WIDTH / 2;
		int y = centerY - HITSPLAT_HEIGHT / 2;
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

	private BufferedImage sprite(int spriteId)
	{
		return spriteId < 0 ? null : spriteManager.getSprite(spriteId, 0);
	}

	private int drawSprite(Graphics2D graphics, BufferedImage sprite, int x, int y, int height)
	{
		if (sprite == null)
		{
			return x;
		}
		graphics.drawImage(sprite, x, y + (height - sprite.getHeight()) / 2, null);
		return x + sprite.getWidth();
	}

	private int imageWidth(BufferedImage image)
	{
		return image == null ? 0 : image.getWidth();
	}

	private int imageHeight(BufferedImage image)
	{
		return image == null ? 0 : image.getHeight();
	}

	/**
	 * Approximates the vanilla hitsplat palette. The client keys these off sprite ids
	 * that the API does not expose, so the mapping is by hitsplat type instead.
	 */
	private Color hitsplatColor(int type)
	{
		if (type == HitsplatID.BLOCK_ME || type == HitsplatID.BLOCK_OTHER || type == HitsplatID.DISEASE_BLOCKED)
		{
			return HITSPLAT_BLOCK;
		}
		if (type == HitsplatID.POISON)
		{
			return HITSPLAT_POISON;
		}
		if (type == HitsplatID.VENOM)
		{
			return HITSPLAT_VENOM;
		}
		if (type == HitsplatID.DISEASE)
		{
			return HITSPLAT_DISEASE;
		}
		if (type == HitsplatID.HEAL)
		{
			return HITSPLAT_HEAL;
		}
		if (type == HitsplatID.PRAYER_DRAIN || type == HitsplatID.SANITY_DRAIN || type == HitsplatID.SANITY_RESTORE)
		{
			return HITSPLAT_DRAIN;
		}
		if (type == HitsplatID.DAMAGE_ME_CYAN || type == HitsplatID.DAMAGE_OTHER_CYAN
			|| type == HitsplatID.DAMAGE_MAX_ME_CYAN || type == HitsplatID.CYAN_UP || type == HitsplatID.CYAN_DOWN)
		{
			return HITSPLAT_CYAN;
		}
		if (type == HitsplatID.DAMAGE_ME_ORANGE || type == HitsplatID.DAMAGE_OTHER_ORANGE
			|| type == HitsplatID.DAMAGE_MAX_ME_ORANGE || type == HitsplatID.BURN)
		{
			return HITSPLAT_ORANGE;
		}
		if (type == HitsplatID.DAMAGE_ME_YELLOW || type == HitsplatID.DAMAGE_OTHER_YELLOW
			|| type == HitsplatID.DAMAGE_MAX_ME_YELLOW)
		{
			return HITSPLAT_YELLOW;
		}
		if (type == HitsplatID.DAMAGE_ME_WHITE || type == HitsplatID.DAMAGE_OTHER_WHITE
			|| type == HitsplatID.DAMAGE_MAX_ME_WHITE)
		{
			return HITSPLAT_WHITE;
		}

		// Everything else, including the plain and max-hit damage splats.
		return HITSPLAT_DAMAGE;
	}

	private void renderCompactOverhead(Graphics2D graphics, Player player, HeadIcon icon, int stackedHeight)
	{
		int scale = config.iconScale();
		long cacheKey = ((long) icon.ordinal() << 32) | (scale & 0xFFFFFFFFL);
		BufferedImage sprite = compactOverheads.get(cacheKey);
		if (sprite == null)
		{
			BufferedImage original = spriteManager.getSprite(
				net.runelite.api.gameval.SpriteID.HEADICONS_PRAYER, icon.ordinal());
			if (original == null)
			{
				return;
			}
			int width = Math.max(1, original.getWidth() * scale / 100);
			int height = Math.max(1, original.getHeight() * scale / 100);
			sprite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D scaledGraphics = sprite.createGraphics();
			scaledGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			scaledGraphics.drawImage(original, 0, 0, width, height, null);
			scaledGraphics.dispose();
			compactOverheads.put(cacheKey, sprite);
		}

		Point anchor = headPoint(graphics, player, player.getLogicalHeight() + config.heightOffset()
			+ HEALTH_BAR_RAISE + stackedHeight + sprite.getHeight());
		if (anchor == null)
		{
			return;
		}

		graphics.drawImage(sprite, anchor.getX() - sprite.getWidth() / 2, anchor.getY(), null);
	}

	private void collectChatEntry(Graphics2D graphics, Player player, List<ChatEntry> entries)
	{
		// The public API exposes the text but not the public-chat mode used by the
		// vanilla renderer. Never reveal a remote player's text that their chat filter
		// might have suppressed, so only the local player's own speech is redrawn.
		if (player != client.getLocalPlayer())
		{
			return;
		}

		String text = player.getOverheadText();
		if (text == null || text.isEmpty() || player.getOverheadCycle() <= 0)
		{
			return;
		}

		Point anchor = headPoint(graphics, player, player.getLogicalHeight() + config.heightOffset());
		if (anchor == null)
		{
			return;
		}

		FontMetrics metrics = graphics.getFontMetrics(FontManager.getRunescapeBoldFont());
		entries.add(new ChatEntry(text, anchor.getX(), anchor.getY(),
			metrics.stringWidth(text) / 2, metrics.getAscent()));
	}

	private void renderChatEntries(Graphics2D graphics, List<ChatEntry> entries)
	{
		Font originalFont = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeBoldFont());
		for (ChatEntry entry : entries)
		{
			int x = entry.centerX - entry.halfWidth;
			graphics.setColor(Color.BLACK);
			graphics.drawString(entry.text, x + 1, entry.y + 1);
			graphics.setColor(CHAT_TEXT);
			graphics.drawString(entry.text, x, entry.y);
		}
		graphics.setFont(originalFont);
	}

	static void layoutChatEntries(List<ChatEntry> entries)
	{
		for (int i = 0; i < entries.size(); i++)
		{
			ChatEntry current = entries.get(i);
			boolean moved;
			do
			{
				moved = false;
				for (int j = 0; j < i; j++)
				{
					ChatEntry previous = entries.get(j);
					if (current.y + 2 > previous.y - previous.height
						&& current.y - current.height < previous.y + 2
						&& current.centerX - current.halfWidth < previous.centerX + previous.halfWidth
						&& current.centerX + current.halfWidth > previous.centerX - previous.halfWidth
						&& previous.y - previous.height < current.y)
					{
						current.y = previous.y - previous.height;
						moved = true;
					}
				}
			}
			while (moved);
		}
	}

	private int renderHealthBar(Graphics2D graphics, Player player)
	{
		int ratio = player.getHealthRatio();
		int scale = player.getHealthScale();
		// -1 is the client's own "bar not currently shown" signal; mirror it.
		if (ratio < 0 || scale <= 0)
		{
			return 0;
		}

		Point anchor = headPoint(graphics, player,
			player.getLogicalHeight() + config.heightOffset() + HEALTH_BAR_RAISE);
		if (anchor == null)
		{
			return 0;
		}

		int x = anchor.getX() - HEALTH_BAR_WIDTH / 2;
		int y = anchor.getY();
		int remaining = healthBarFillWidth(ratio, scale, HEALTH_BAR_WIDTH);

		graphics.setColor(HEALTH_BAR_LOST);
		graphics.fillRect(x, y, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT);
		graphics.setColor(HEALTH_BAR_REMAINING);
		graphics.fillRect(x, y, remaining, HEALTH_BAR_HEIGHT);
		return HEALTH_BAR_HEIGHT + ELEMENT_GAP;
	}

	static int healthBarFillWidth(int ratio, int scale, int width)
	{
		if (ratio < 0 || scale <= 0 || width <= 0)
		{
			return 0;
		}
		long clampedRatio = Math.min((long) ratio, (long) scale);
		int fill = (int) (clampedRatio * width / scale);
		return ratio > 0 ? Math.max(1, fill) : 0;
	}

	private int renderSkull(Graphics2D graphics, Player player, int stackedHeight)
	{
		int skullIcon = player.getSkullIcon();
		if (skullIcon < 0)
		{
			return 0;
		}

		BufferedImage sprite = spriteManager.getSprite(net.runelite.api.gameval.SpriteID.HEADICONS_PK, skullIcon);
		if (sprite == null)
		{
			return 0;
		}

		Point anchor = headPoint(graphics, player, player.getLogicalHeight() + config.heightOffset()
			+ HEALTH_BAR_RAISE + stackedHeight + sprite.getHeight());
		if (anchor == null)
		{
			return 0;
		}

		graphics.drawImage(sprite, anchor.getX() - sprite.getWidth() / 2, anchor.getY(), null);
		return sprite.getHeight() + ELEMENT_GAP;
	}

	static final class ChatEntry
	{
		final String text;
		final int centerX;
		final int halfWidth;
		final int height;
		int y;

		ChatEntry(String text, int centerX, int y, int halfWidth, int height)
		{
			this.text = text;
			this.centerX = centerX;
			this.y = y;
			this.halfWidth = halfWidth;
			this.height = height;
		}
	}

	static Point headPoint(Graphics2D graphics, Player player, int zOffset)
	{
		// Actor projection accounts for the player's world view, footprint tile
		// height, and animation height offset. A raw tile projection does not.
		return player.getCanvasTextLocation(graphics, "", zOffset);
	}

}
