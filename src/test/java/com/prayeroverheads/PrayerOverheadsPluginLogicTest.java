package com.prayeroverheads;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.Ignore;
import net.runelite.api.NPC;
import net.runelite.api.NameableContainer;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.entityhider.EntityHiderConfig;
import net.runelite.client.plugins.entityhider.EntityHiderPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PrayerOverheadsPluginLogicTest
{
	@Mock
	private Client client;

	@Mock
	private RenderCallbackManager renderCallbackManager;

	@Mock
	private PartyService partyService;

	@Mock
	private OverlayManager overlayManager;

	@Mock
	private PrayerOverheadsOverlay overlay;

	@Mock
	private PrayerOverheadsSceneOverlay sceneOverlay;

	@Mock
	private ConfigManager configManager;

	@Mock
	private PluginManager pluginManager;

	@Mock
	private EntityHiderConfig entityHiderConfig;

	@Mock
	private Player localPlayer;

	@Spy
	private TestConfig config = new TestConfig();

	@InjectMocks
	private PrayerOverheadsPlugin plugin;

	@Before
	public void setUp()
	{
		lenient().when(client.getLocalPlayer()).thenReturn(localPlayer);
		lenient().when(localPlayer.getName()).thenReturn("Local");
		lenient().when(configManager.getConfig(EntityHiderConfig.class)).thenReturn(entityHiderConfig);
		lenient().when(pluginManager.getPlugins()).thenReturn(Collections.emptyList());
	}

	// ---------------------------------------------------------------- shouldHide2D

	@Test
	public void hidesEachPlayerCategoryOnlyWhenItsToggleIsOn()
	{
		Player other = player("Other", HeadIcon.MELEE);
		when(localPlayer.getOverheadIcon()).thenReturn(HeadIcon.MELEE);
		startUp();

		assertTrue("local player", plugin.shouldHide2D(localPlayer));
		assertTrue("other player", plugin.shouldHide2D(other));

		config.hideSelf2D = false;
		config.hideOthers2D = false;
		reloadConfig();

		assertFalse("local player with toggle off", plugin.shouldHide2D(localPlayer));
		assertFalse("other player with toggle off", plugin.shouldHide2D(other));
	}

	@Test
	public void partyMembersFollowThePartyToggleRatherThanTheOthersToggle()
	{
		Player member = player("Member", HeadIcon.MAGIC);
		when(partyService.isInParty()).thenReturn(true);
		when(partyService.getMemberByDisplayName("Member"))
			.thenReturn(org.mockito.Mockito.mock(PartyMember.class));

		config.hideOthers2D = false;
		startUp();

		assertTrue("party toggle on, others off", plugin.shouldHide2D(member));

		config.hideParty2D = false;
		reloadConfig();

		assertFalse("both toggles off", plugin.shouldHide2D(member));
	}

	@Test
	public void unresolvedPlayerNamesAreNeverHidden()
	{
		// The client leaves getName() null until a player is resolved; the party
		// lookup NPEs on those, so they must be rejected before it runs.
		Player unresolved = player(null, HeadIcon.MELEE);
		// Lenient: reaching this stub at all would mean the guard ran too late.
		lenient().when(partyService.isInParty()).thenReturn(true);
		startUp();

		assertFalse(plugin.shouldHide2D(unresolved));
		verify(partyService, never()).getMemberByDisplayName(any());
	}

	@Test
	public void nonPlayerActorsAreNeverHidden()
	{
		// The plugin is player-only: NPCs must keep their vanilla 2D block in full.
		NPC npc = org.mockito.Mockito.mock(NPC.class);
		startUp();

		assertFalse(plugin.shouldHide2D(npc));
		// The head icon must not even be computed for a non-player.
		org.mockito.Mockito.verifyNoInteractions(npc);
	}

	@Test
	public void onlyWhilePrayingGatesHidingOnAVisibleOverhead()
	{
		Player idle = player("Idle", null);
		startUp();

		assertFalse("praying-only, no overhead", plugin.shouldHide2D(idle));

		config.onlyWhilePraying = false;
		reloadConfig();

		assertTrue("unconditional hiding", plugin.shouldHide2D(idle));
	}

	// ---------------------------------------------------------------- shouldReplace

	@Test
	public void excludedPrayersKeepTheirVanillaBlockUntilOptedIn()
	{
		Player smiter = player("Smiter", HeadIcon.SMITE);
		Player redeemer = player("Redeemer", HeadIcon.REDEMPTION);
		Player retributor = player("Retributor", HeadIcon.RETRIBUTION);
		startUp();

		assertFalse("smite", plugin.shouldHide2D(smiter));
		assertFalse("redemption", plugin.shouldHide2D(redeemer));
		assertFalse("retribution", plugin.shouldHide2D(retributor));

		config.replaceSmite = true;
		config.replaceRedemption = true;
		config.replaceRetribution = true;
		reloadConfig();

		assertTrue("smite opted in", plugin.shouldHide2D(smiter));
		assertTrue("redemption opted in", plugin.shouldHide2D(redeemer));
		assertTrue("retribution opted in", plugin.shouldHide2D(retributor));
	}

	@Test
	public void protectionPrayersAreAlwaysRoutedToTheReplacementDisplay()
	{
		startUp();

		for (HeadIcon icon : new HeadIcon[]{HeadIcon.MELEE, HeadIcon.RANGED, HeadIcon.MAGIC,
			HeadIcon.DEFLECT_MELEE, HeadIcon.DEFLECT_RANGE, HeadIcon.DEFLECT_MAGE})
		{
			assertTrue(icon.name(), plugin.shouldHide2D(player("P" + icon.ordinal(), icon)));
		}
	}

	@Test
	public void theThreeExcludedPrayersDefaultToNotBeingReplaced()
	{
		// Stated requirement: opting in must be explicit.
		PrayerOverheadsConfig defaults = new PrayerOverheadsConfig() { };
		assertFalse("replaceSmite", defaults.replaceSmite());
		assertFalse("replaceRedemption", defaults.replaceRedemption());
		assertFalse("replaceRetribution", defaults.replaceRetribution());
	}

	// ------------------------------------------------- isPlayerHiddenByEntityHider

	@Test
	public void entityHiderIsIgnoredWhileItsPluginIsInactive()
	{
		Player other = player("Other", HeadIcon.MELEE);
		// Lenient: an inactive Entity Hider must not be consulted at all.
		lenient().when(entityHiderConfig.hideOthers()).thenReturn(true);
		startUp();

		assertFalse(plugin.isPlayerHiddenByEntityHider(other));
		verifyNoInteractions(entityHiderConfig);
	}

	@Test
	public void entityHiderCategoriesAreCheckedInCoreOrder()
	{
		Player other = player("Other", HeadIcon.MELEE);
		activateEntityHider();
		startUp();

		assertFalse("no category enabled", plugin.isPlayerHiddenByEntityHider(other));

		// A friend matches before the generic "others" bucket, so an enabled
		// hideOthers must not hide a friend whose own category is off.
		when(other.isFriend()).thenReturn(true);
		// Lenient: the friend branch returns first, so this must stay unread.
		lenient().when(entityHiderConfig.hideOthers()).thenReturn(true);
		assertFalse("friend short-circuits hideOthers", plugin.isPlayerHiddenByEntityHider(other));

		when(entityHiderConfig.hideFriends()).thenReturn(true);
		assertTrue("friend category enabled", plugin.isPlayerHiddenByEntityHider(other));
	}

	@Test
	public void attackersAreCheckedBeforeAnyNameBasedCategory()
	{
		Player attacker = player("Attacker", HeadIcon.MELEE);
		when(attacker.getInteracting()).thenReturn(localPlayer);
		when(entityHiderConfig.hideAttackers()).thenReturn(true);
		activateEntityHider();
		startUp();

		assertTrue(plugin.isPlayerHiddenByEntityHider(attacker));
	}

	@Test
	public void localPlayerUsesEitherOfItsOwnTwoToggles()
	{
		activateEntityHider();
		startUp();

		assertFalse("neither toggle", plugin.isPlayerHiddenByEntityHider(localPlayer));

		when(entityHiderConfig.hideLocalPlayer2D()).thenReturn(true);
		assertTrue("2D toggle alone", plugin.isPlayerHiddenByEntityHider(localPlayer));
	}

	@Test
	public void unresolvedNamesAreNotTreatedAsHidden()
	{
		Player unresolved = player(null, HeadIcon.MELEE);
		// Lenient: the null-name guard returns before any category is read.
		lenient().when(entityHiderConfig.hideOthers()).thenReturn(true);
		activateEntityHider();
		startUp();

		assertFalse(plugin.isPlayerHiddenByEntityHider(unresolved));
	}

	// ------------------------------------------------------- hitsplat lifecycle

	@Test
	public void hitsplatsAreOnlyTrackedForPlayersWhoseBlockIsHidden()
	{
		Player hidden = player("Hidden", HeadIcon.MELEE);
		Player visible = player("Visible", null); // not praying, so not hidden
		startUp();

		applyHitsplat(hidden, 10, 100);
		applyHitsplat(visible, 10, 100);

		assertEquals(1, plugin.getTrackedHitsplats().size());
		assertEquals(hidden, plugin.getTrackedHitsplats().get(0).actor);
	}

	@Test
	public void hitsplatsAreNotTrackedWhileTheRedrawIsDisabled()
	{
		Player hidden = player("Hidden", HeadIcon.MELEE);
		config.keepHitsplats = false;
		startUp();

		applyHitsplat(hidden, 10, 100);

		assertTrue(plugin.getTrackedHitsplats().isEmpty());
	}

	@Test
	public void slotsAreAssignedOnceAndSurviveAnEarlierSplatExpiring()
	{
		Player target = player("Target", HeadIcon.MELEE);
		startUp();

		// Two splats land together; the second takes the raised slot.
		when(client.getGameCycle()).thenReturn(0);
		applyHitsplat(target, 10, 30);
		applyHitsplat(target, 20, 100);

		assertEquals(0, slotOf(target, 10));
		assertEquals(1, slotOf(target, 20));

		// The first expires. The survivor must keep slot 1 rather than sliding
		// down to slot 0, which is what indexing a per-frame list produced.
		when(client.getGameCycle()).thenReturn(50);
		plugin.onClientTick(null);

		assertEquals(1, plugin.getTrackedHitsplats().size());
		assertEquals(1, slotOf(target, 20));
	}

	@Test
	public void freedSlotsAreReusedByLaterSplats()
	{
		Player target = player("Target", HeadIcon.MELEE);
		startUp();

		when(client.getGameCycle()).thenReturn(0);
		applyHitsplat(target, 10, 30);
		applyHitsplat(target, 20, 100);

		when(client.getGameCycle()).thenReturn(50);
		plugin.onClientTick(null);
		applyHitsplat(target, 30, 200);

		assertEquals(0, slotOf(target, 30));
		assertEquals(1, slotOf(target, 20));
	}

	@Test
	public void slotsAreTrackedPerPlayer()
	{
		Player first = player("First", HeadIcon.MELEE);
		Player second = player("Second", HeadIcon.MELEE);
		startUp();

		applyHitsplat(first, 10, 100);
		applyHitsplat(second, 20, 100);

		assertEquals(0, slotOf(first, 10));
		assertEquals(0, slotOf(second, 20));
	}

	@Test
	public void aFifthSimultaneousSplatIsDropped()
	{
		Player target = player("Target", HeadIcon.MELEE);
		startUp();

		for (int i = 0; i < 6; i++)
		{
			applyHitsplat(target, i, 100);
		}

		assertEquals(PrayerOverheadsPlugin.MAX_HITSPLATS, plugin.getTrackedHitsplats().size());
		for (PrayerOverheadsPlugin.TrackedHitsplat tracked : plugin.getTrackedHitsplats())
		{
			assertTrue("slot in range", tracked.slot >= 0
				&& tracked.slot < PrayerOverheadsPlugin.MAX_HITSPLATS);
		}
	}

	@Test
	public void expiredSplatsAreRemovedOnTheCycleTheClientWouldDropThem()
	{
		Player target = player("Target", HeadIcon.MELEE);
		startUp();

		when(client.getGameCycle()).thenReturn(0);
		applyHitsplat(target, 10, 30);

		when(client.getGameCycle()).thenReturn(29);
		plugin.onClientTick(null);
		assertEquals("one cycle early", 1, plugin.getTrackedHitsplats().size());

		when(client.getGameCycle()).thenReturn(30);
		plugin.onClientTick(null);
		assertTrue("on the disappear cycle", plugin.getTrackedHitsplats().isEmpty());
	}

	@Test
	public void leavingTheLoggedInStateDropsStaleActorReferences()
	{
		Player target = player("Target", HeadIcon.MELEE);
		startUp();
		applyHitsplat(target, 10, 100);

		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOADING);
		plugin.onGameStateChanged(event);

		assertTrue(plugin.getTrackedHitsplats().isEmpty());
	}

	@Test
	public void shutdownReleasesTheCallbackAndCachedState()
	{
		Player target = player("Target", HeadIcon.MELEE);
		startUp();
		applyHitsplat(target, 10, 100);

		plugin.shutDown();

		assertTrue(plugin.getTrackedHitsplats().isEmpty());
		org.mockito.Mockito.verify(renderCallbackManager).unregister(any());
		org.mockito.Mockito.verify(overlayManager).add(sceneOverlay);
		org.mockito.Mockito.verify(overlayManager).add(overlay);
		org.mockito.Mockito.verify(overlayManager).remove(overlay);
		org.mockito.Mockito.verify(overlayManager).remove(sceneOverlay);
		org.mockito.Mockito.verify(overlay).clearCaches();
		org.mockito.Mockito.verify(sceneOverlay).clearCaches();
		// Entity Hider state is dropped too, so a restart re-resolves it.
		assertFalse(plugin.isPlayerHiddenByEntityHider(target));
	}

	// ------------------------------------------------------------------- helpers

	private void startUp()
	{
		plugin.startUp();
	}

	private void reloadConfig()
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(PrayerOverheadsConfig.GROUP);
		plugin.onConfigChanged(event);
	}

	private void activateEntityHider()
	{
		EntityHiderPlugin entityHider = org.mockito.Mockito.mock(EntityHiderPlugin.class);
		List<Plugin> plugins = Arrays.asList(entityHider);
		when(pluginManager.getPlugins()).thenReturn(plugins);
		when(pluginManager.isPluginActive(entityHider)).thenReturn(true);

		NameableContainer<Ignore> ignores = ignoreContainer();
		lenient().when(client.getIgnoreContainer()).thenReturn(ignores);
	}

	@SuppressWarnings("unchecked")
	private NameableContainer<Ignore> ignoreContainer()
	{
		return org.mockito.Mockito.mock(NameableContainer.class);
	}

	private Player player(String name, HeadIcon icon)
	{
		Player player = org.mockito.Mockito.mock(Player.class);
		lenient().when(player.getName()).thenReturn(name);
		lenient().when(player.getOverheadIcon()).thenReturn(icon);
		return player;
	}

	private void applyHitsplat(Actor actor, int amount, int disappearsOnGameCycle)
	{
		Hitsplat hitsplat = org.mockito.Mockito.mock(Hitsplat.class);
		lenient().when(hitsplat.getAmount()).thenReturn(amount);
		lenient().when(hitsplat.getDisappearsOnGameCycle()).thenReturn(disappearsOnGameCycle);

		HitsplatApplied event = new HitsplatApplied();
		event.setActor(actor);
		event.setHitsplat(hitsplat);
		plugin.onHitsplatApplied(event);
	}

	private int slotOf(Actor actor, int amount)
	{
		for (PrayerOverheadsPlugin.TrackedHitsplat tracked : plugin.getTrackedHitsplats())
		{
			if (tracked.actor == actor && tracked.hitsplat.getAmount() == amount)
			{
				return tracked.slot;
			}
		}
		throw new AssertionError("no tracked hitsplat of " + amount + " on " + actor);
	}

	/**
	 * The real interface defaults, with per-test overrides. Using the interface's own
	 * defaults means a changed default shows up here rather than being shadowed.
	 */
	private static class TestConfig implements PrayerOverheadsConfig
	{
		Boolean hideSelf2D = true;
		Boolean hideParty2D = true;
		Boolean hideOthers2D = true;
		Boolean onlyWhilePraying = true;
		Boolean replaceSmite;
		Boolean replaceRedemption;
		Boolean replaceRetribution;
		Boolean keepHitsplats = true;

		@Override
		public boolean hideSelf2D()
		{
			return hideSelf2D != null ? hideSelf2D : PrayerOverheadsConfig.super.hideSelf2D();
		}

		@Override
		public boolean hideParty2D()
		{
			return hideParty2D != null ? hideParty2D : PrayerOverheadsConfig.super.hideParty2D();
		}

		@Override
		public boolean hideOthers2D()
		{
			return hideOthers2D != null ? hideOthers2D : PrayerOverheadsConfig.super.hideOthers2D();
		}

		@Override
		public boolean onlyWhilePraying()
		{
			return onlyWhilePraying != null ? onlyWhilePraying : PrayerOverheadsConfig.super.onlyWhilePraying();
		}

		@Override
		public boolean replaceSmite()
		{
			return replaceSmite != null ? replaceSmite : PrayerOverheadsConfig.super.replaceSmite();
		}

		@Override
		public boolean replaceRedemption()
		{
			return replaceRedemption != null ? replaceRedemption : PrayerOverheadsConfig.super.replaceRedemption();
		}

		@Override
		public boolean replaceRetribution()
		{
			return replaceRetribution != null ? replaceRetribution : PrayerOverheadsConfig.super.replaceRetribution();
		}

		@Override
		public boolean keepHitsplats()
		{
			return keepHitsplats != null ? keepHitsplats : PrayerOverheadsConfig.super.keepHitsplats();
		}
	}
}
