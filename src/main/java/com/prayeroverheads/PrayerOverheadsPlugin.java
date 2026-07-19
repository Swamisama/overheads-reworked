package com.prayeroverheads;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Renderable;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.Hooks;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Prayer Overheads Reworked",
	description = "Replace the large overhead protection prayer bubbles with a subtle display",
	tags = {"prayer", "overhead", "hide", "raid", "declutter"}
)
public class PrayerOverheadsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private Hooks hooks;

	@Inject
	private PartyService partyService;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PrayerOverheadsOverlay overlay;

	@Inject
	private PrayerOverheadsConfig config;

	// The listener runs on the client thread for every renderable of every frame,
	// so config is read from plain fields rather than through the proxy.
	private boolean hideSelf2D;
	private boolean hideParty2D;
	private boolean hideOthers2D;
	private boolean hideNpc2D;
	private boolean onlyWhilePraying;
	private boolean keepHitsplats;

	private final Hooks.RenderableDrawListener drawListener = this::shouldDraw;

	/**
	 * Hitsplats applied to actors whose 2D block is hidden, held until the game cycle
	 * the client would have removed them on. Only a few live at once — they expire
	 * within a couple of ticks — so a flat list beats a per-actor map here.
	 */
	private final List<TrackedHitsplat> trackedHitsplats = new ArrayList<>();

	@Provides
	PrayerOverheadsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrayerOverheadsConfig.class);
	}

	@Override
	protected void startUp()
	{
		cacheConfig();
		overlayManager.add(overlay);
		hooks.registerRenderableDrawListener(drawListener);
	}

	@Override
	protected void shutDown()
	{
		hooks.unregisterRenderableDrawListener(drawListener);
		overlayManager.remove(overlay);
		trackedHitsplats.clear();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		// Recorded only for actors already being hidden: if the actor's 2D block is
		// visible, the client is drawing its hitsplat itself.
		if (keepHitsplats && shouldHide2D(event.getActor()))
		{
			trackedHitsplats.add(new TrackedHitsplat(event.getActor(), event.getHitsplat()));
		}
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		int cycle = client.getGameCycle();
		trackedHitsplats.removeIf(tracked -> cycle >= tracked.hitsplat.getDisappearsOnGameCycle());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Actor references do not survive a scene reload.
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			trackedHitsplats.clear();
		}
	}

	List<TrackedHitsplat> getTrackedHitsplats()
	{
		return trackedHitsplats;
	}

	static class TrackedHitsplat
	{
		final Actor actor;
		final Hitsplat hitsplat;

		TrackedHitsplat(Actor actor, Hitsplat hitsplat)
		{
			this.actor = actor;
			this.hitsplat = hitsplat;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (PrayerOverheadsConfig.GROUP.equals(event.getGroup()))
		{
			cacheConfig();
		}
	}

	private void cacheConfig()
	{
		hideSelf2D = config.hideSelf2D();
		hideParty2D = config.hideParty2D();
		hideOthers2D = config.hideOthers2D();
		hideNpc2D = config.hideNpc2D();
		onlyWhilePraying = config.onlyWhilePraying();
		keepHitsplats = config.keepHitsplats();
	}

	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		// drawingUI is the actor's 2D pass (overhead chat, health bar, prayer bubble,
		// skull) as one block. The 3D model pass must never be touched.
		if (!drawingUI || !(renderable instanceof Actor))
		{
			return true;
		}

		return !shouldHide2D((Actor) renderable);
	}

	/**
	 * Whether this actor's vanilla 2D block is suppressed right now. Used both by the
	 * draw listener and by the overlay, so the two always agree on the same frame.
	 */
	boolean shouldHide2D(Actor actor)
	{
		if (actor instanceof NPC)
		{
			return hideNpc2D && (!onlyWhilePraying || getHeadIcon(actor) != null);
		}

		if (!(actor instanceof Player))
		{
			return false;
		}

		Player player = (Player) actor;
		if (!hideCategory(player))
		{
			return false;
		}

		return !onlyWhilePraying || getHeadIcon(player) != null;
	}

	private boolean hideCategory(Player player)
	{
		if (player == client.getLocalPlayer())
		{
			return hideSelf2D;
		}

		// Names are null for players the client hasn't resolved yet; party lookup NPEs on those.
		String name = player.getName();
		if (name == null)
		{
			return false;
		}

		if (hideParty2D && partyService.isInParty() && partyService.getMemberByDisplayName(name) != null)
		{
			return true;
		}

		return hideOthers2D;
	}

	/**
	 * The overhead icon an actor is currently showing, or null for none.
	 */
	HeadIcon getHeadIcon(Actor actor)
	{
		if (actor instanceof Player)
		{
			Player player = (Player) actor;
			if (player == client.getLocalPlayer())
			{
				// The local overhead icon field lags a tick behind the prayer being
				// activated; the prayer varbits do not.
				HeadIcon active = activeProtectionPrayer();
				if (active != null)
				{
					return active;
				}
			}

			return player.getOverheadIcon();
		}

		if (actor instanceof NPC)
		{
			return npcHeadIcon((NPC) actor);
		}

		return null;
	}

	private HeadIcon activeProtectionPrayer()
	{
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MELEE))
		{
			return HeadIcon.MELEE;
		}
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
		{
			return HeadIcon.RANGED;
		}
		if (client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
		{
			return HeadIcon.MAGIC;
		}
		return null;
	}

	/**
	 * NPCs carry overheads as parallel (archive, sprite) id arrays with no HeadIcon
	 * convenience. Frames of the prayer archive map onto HeadIcon in declaration order.
	 */
	private HeadIcon npcHeadIcon(NPC npc)
	{
		int[] archives = npc.getOverheadArchiveIds();
		short[] sprites = npc.getOverheadSpriteIds();
		if (archives == null || sprites == null)
		{
			return null;
		}

		HeadIcon[] icons = HeadIcon.values();
		for (int i = 0; i < archives.length && i < sprites.length; i++)
		{
			if (archives[i] != SpriteID.HEADICONS_PRAYER)
			{
				continue;
			}

			int frame = sprites[i];
			if (frame >= 0 && frame < icons.length)
			{
				return icons[frame];
			}
		}

		return null;
	}
}
