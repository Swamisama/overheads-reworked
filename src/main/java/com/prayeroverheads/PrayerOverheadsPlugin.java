package com.prayeroverheads;

import com.google.inject.Provides;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Prayer Overheads Reworked",
	description = "Hide or restyle the large overhead protection prayer bubbles",
	tags = {"prayer", "overhead", "hide", "raid", "declutter"},
	// Dev only, so cold-boot tests can't miss the plugin; drop for hub submission
	enabledByDefault = true
)
public class PrayerOverheadsPlugin extends Plugin
{
	// Overhead icons are 25x33 in the cache; a same-size canvas keeps any
	// dimension-based anchoring in the client unchanged.
	private static final int ICON_WIDTH = 25;
	private static final int ICON_HEIGHT = 33;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PrayerOverheadsConfig config;

	@Provides
	PrayerOverheadsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrayerOverheadsConfig.class);
	}

	@Override
	protected void startUp()
	{
		clientThread.invoke(this::applyBubbleMode);
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(this::removeOverride);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (PrayerOverheadsConfig.GROUP.equals(event.getGroup()))
		{
			clientThread.invoke(this::applyBubbleMode);
		}
	}

	private void applyBubbleMode()
	{
		switch (config.bubbleMode())
		{
			case VANILLA:
				removeOverride();
				break;
			case HIDDEN:
				// Pixel value 0 is treated as fully transparent by the client's sprite blitter
				setOverride(makeSolidSprite(0));
				break;
			case MAGENTA_TEST:
				setOverride(makeSolidSprite(0xFF00FF));
				break;
		}
	}

	private void setOverride(SpritePixels sprite)
	{
		log.info("Overriding HEADICONS_PRAYER sprite group ({})", SpriteID.HEADICONS_PRAYER);
		client.getSpriteOverrides().put(SpriteID.HEADICONS_PRAYER, sprite);
		client.getWidgetSpriteCache().reset();
	}

	private void removeOverride()
	{
		client.getSpriteOverrides().remove(SpriteID.HEADICONS_PRAYER);
		client.getWidgetSpriteCache().reset();
	}

	private SpritePixels makeSolidSprite(int rgb)
	{
		int[] pixels = new int[ICON_WIDTH * ICON_HEIGHT];
		Arrays.fill(pixels, rgb);
		return client.createSpritePixels(pixels, ICON_WIDTH, ICON_HEIGHT);
	}
}
