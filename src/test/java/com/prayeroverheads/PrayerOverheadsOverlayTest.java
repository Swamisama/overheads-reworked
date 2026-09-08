package com.prayeroverheads;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PrayerOverheadsOverlayTest
{
	@Test
	public void displayDefaultsAreComposableAndOpacityPreservesRgb()
	{
		PrayerOverheadsConfig config = new PrayerOverheadsConfig() { };
		assertFalse(config.showTile());
		assertTrue(config.showOutline());
		assertFalse(config.showCompactOverhead());

		Color source = new Color(10, 20, 30, 200);
		Color half = PrayerOverheadsSceneOverlay.withHighlightOpacity(source, 50);
		assertEquals(10, half.getRed());
		assertEquals(20, half.getGreen());
		assertEquals(30, half.getBlue());
		assertEquals(100, half.getAlpha());
	}

	@Test
	public void sceneHighlightsAndRedrawnUiUseSeparateLayers()
	{
		Client client = mock(Client.class);
		PrayerOverheadsPlugin plugin = mock(PrayerOverheadsPlugin.class);
		PrayerOverheadsConfig config = new PrayerOverheadsConfig() { };
		PrayerOverheadsSceneOverlay sceneOverlay = new PrayerOverheadsSceneOverlay(
			client, plugin, config, mock(ModelOutlineRenderer.class));
		PrayerOverheadsOverlay uiOverlay = new PrayerOverheadsOverlay(
			client, plugin, config, mock(SpriteManager.class));

		assertEquals(OverlayLayer.ABOVE_SCENE, sceneOverlay.getLayer());
		assertEquals(OverlayLayer.UNDER_WIDGETS, uiOverlay.getLayer());
	}

	@Test
	public void headPointUsesThePlayersActorAwareProjection()
	{
		Graphics2D graphics = mock(Graphics2D.class);
		Player player = mock(Player.class);
		Point projected = new Point(123, 45);
		when(player.getCanvasTextLocation(graphics, "", 215)).thenReturn(projected);

		assertSame(projected, PrayerOverheadsOverlay.headPoint(graphics, player, 215));
		verify(player).getCanvasTextLocation(graphics, "", 215);
	}

	@Test
	public void overlappingChatIsPushedAboveEarlierEntries()
	{
		PrayerOverheadsOverlay.ChatEntry first = entry(100, 100, 30, 12);
		PrayerOverheadsOverlay.ChatEntry second = entry(105, 99, 30, 12);
		PrayerOverheadsOverlay.ChatEntry third = entry(102, 98, 30, 12);
		List<PrayerOverheadsOverlay.ChatEntry> entries = new ArrayList<>(Arrays.asList(first, second, third));

		PrayerOverheadsOverlay.layoutChatEntries(entries);

		assertEquals(100, first.y);
		assertEquals(88, second.y);
		assertEquals(76, third.y);
	}

	@Test
	public void horizontallySeparateChatKeepsItsProjectedHeight()
	{
		PrayerOverheadsOverlay.ChatEntry first = entry(50, 100, 10, 12);
		PrayerOverheadsOverlay.ChatEntry second = entry(100, 100, 10, 12);

		PrayerOverheadsOverlay.layoutChatEntries(Arrays.asList(first, second));

		assertEquals(100, second.y);
	}

	@Test
	public void healthBarFillIsClampedAndKeepsOnePositivePixel()
	{
		assertEquals(0, PrayerOverheadsOverlay.healthBarFillWidth(-1, 10, 30));
		assertEquals(0, PrayerOverheadsOverlay.healthBarFillWidth(1, 0, 30));
		assertEquals(0, PrayerOverheadsOverlay.healthBarFillWidth(0, 10, 30));
		assertEquals(1, PrayerOverheadsOverlay.healthBarFillWidth(1, 100, 30));
		assertEquals(15, PrayerOverheadsOverlay.healthBarFillWidth(5, 10, 30));
		assertEquals(30, PrayerOverheadsOverlay.healthBarFillWidth(20, 10, 30));
		assertEquals(0, PrayerOverheadsOverlay.healthBarFillWidth(5, 10, 0));
	}

	private static PrayerOverheadsOverlay.ChatEntry entry(int x, int y, int halfWidth, int height)
	{
		return new PrayerOverheadsOverlay.ChatEntry("text", x, y, halfWidth, height);
	}
}
