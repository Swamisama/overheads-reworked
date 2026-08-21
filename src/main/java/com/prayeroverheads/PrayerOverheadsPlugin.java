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
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Renderable;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.entityhider.EntityHiderConfig;
import net.runelite.client.plugins.entityhider.EntityHiderPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Overheads Reworked",
	description = "Replace the large overhead protection prayer bubbles with a subtle display",
	tags = {"prayer", "overhead", "hide", "raid", "declutter"}
)
public class PrayerOverheadsPlugin extends Plugin
{
	/**
	 * Vanilla stacks at most four splats on an actor at once, in four fixed slots.
	 */
	static final int MAX_HITSPLATS = 4;

	@Inject
	private Client client;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private PartyService partyService;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PrayerOverheadsOverlay overlay;

	@Inject
	private PrayerOverheadsSceneOverlay sceneOverlay;

	@Inject
	private PrayerOverheadsConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	private EntityHiderConfig entityHiderConfig;
	private Plugin entityHiderPlugin;

	// The listener runs on the client thread for every renderable of every frame,
	// so config is read from plain fields rather than through the proxy.
	private boolean hideSelf2D;
	private boolean hideParty2D;
	private boolean hideOthers2D;
	private boolean onlyWhilePraying;
	private boolean replaceSmite;
	private boolean replaceRedemption;
	private boolean replaceRetribution;
	private boolean keepHitsplats;

	// RenderCallback has only default methods, so it is not a functional interface.
	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean drawingUI)
		{
			return shouldDraw(renderable, drawingUI);
		}
	};

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
		entityHiderConfig = configManager.getConfig(EntityHiderConfig.class);
		findEntityHiderPlugin();
		overlayManager.add(sceneOverlay);
		overlayManager.add(overlay);
		renderCallbackManager.register(renderCallback);
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(renderCallback);
		overlayManager.remove(overlay);
		overlayManager.remove(sceneOverlay);
		overlay.clearCaches();
		sceneOverlay.clearCaches();
		trackedHitsplats.clear();
		entityHiderPlugin = null;
		entityHiderConfig = null;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		// Recorded only for actors already being hidden: if the actor's 2D block is
		// visible, the client is drawing its hitsplat itself.
		if (!keepHitsplats || !shouldHide2D(event.getActor()))
		{
			return;
		}

		int slot = nextHitsplatSlot(event.getActor());
		if (slot < 0)
		{
			// All four vanilla slots are occupied; the client would not draw a fifth either.
			return;
		}

		trackedHitsplats.add(new TrackedHitsplat(event.getActor(), event.getHitsplat(), client.getGameCycle(), slot));
	}

	/**
	 * The lowest slot this actor is not already using, or -1 if all are taken. Assigning
	 * the slot once and holding it for the splat's lifetime keeps it in place when an
	 * earlier splat expires — an index into a per-frame list would shift it mid-flight.
	 */
	private int nextHitsplatSlot(Actor actor)
	{
		boolean[] taken = new boolean[MAX_HITSPLATS];
		for (TrackedHitsplat tracked : trackedHitsplats)
		{
			if (tracked.actor == actor)
			{
				taken[tracked.slot] = true;
			}
		}

		for (int slot = 0; slot < MAX_HITSPLATS; slot++)
		{
			if (!taken[slot])
			{
				return slot;
			}
		}

		return -1;
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
		final int appliedOnGameCycle;
		/** Fixed for this splat's lifetime; see {@link #nextHitsplatSlot(Actor)}. */
		final int slot;

		TrackedHitsplat(Actor actor, Hitsplat hitsplat, int appliedOnGameCycle, int slot)
		{
			this.actor = actor;
			this.hitsplat = hitsplat;
			this.appliedOnGameCycle = appliedOnGameCycle;
			this.slot = slot;
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
		onlyWhilePraying = config.onlyWhilePraying();
		replaceSmite = config.replaceSmite();
		replaceRedemption = config.replaceRedemption();
		replaceRetribution = config.replaceRetribution();
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
	 *
	 * <p>The plugin is player-only by design: NPCs keep their vanilla 2D block in full.
	 */
	boolean shouldHide2D(Actor actor)
	{
		if (!(actor instanceof Player))
		{
			return false;
		}

		Player player = (Player) actor;
		HeadIcon icon = getHeadIcon(player);
		if (icon != null && !shouldReplace(icon))
		{
			// Leave the complete vanilla 2D pass intact for excluded prayers.
			return false;
		}

		if (!hideCategory(player))
		{
			return false;
		}

		return !onlyWhilePraying || icon != null;
	}

	private boolean shouldReplace(HeadIcon icon)
	{
		switch (icon)
		{
			case SMITE:
				return replaceSmite;
			case REDEMPTION:
				return replaceRedemption;
			case RETRIBUTION:
				return replaceRetribution;
			default:
				return true;
		}
	}

	/**
	 * Entity Hider owns visibility. Its render listener suppresses the model/2D
	 * passes, but overlays run later and cannot observe that combined listener
	 * result, so mirror its player-category decision before drawing replacements.
	 */
	boolean isPlayerHiddenByEntityHider(Player player)
	{
		if (entityHiderConfig == null || !isEntityHiderActive() || player.getName() == null)
		{
			return false;
		}

		Player localPlayer = client.getLocalPlayer();
		if (player == localPlayer)
		{
			return entityHiderConfig.hideLocalPlayer() || entityHiderConfig.hideLocalPlayer2D();
		}

		if (entityHiderConfig.hideAttackers() && player.getInteracting() == localPlayer)
		{
			return true;
		}

		String name = player.getName();
		if (partyService.isInParty() && partyService.getMemberByDisplayName(name) != null)
		{
			return entityHiderConfig.hidePartyMembers();
		}
		if (player.isFriend())
		{
			return entityHiderConfig.hideFriends();
		}
		if (player.isFriendsChatMember())
		{
			return entityHiderConfig.hideFriendsChatMembers();
		}
		if (player.isClanMember())
		{
			return entityHiderConfig.hideClanChatMembers();
		}
		if (client.getIgnoreContainer().findByName(name) != null)
		{
			return entityHiderConfig.hideIgnores();
		}

		return entityHiderConfig.hideOthers() || entityHiderConfig.hideOthers2D();
	}

	private boolean isEntityHiderActive()
	{
		if (entityHiderPlugin == null)
		{
			findEntityHiderPlugin();
		}
		return entityHiderPlugin != null && pluginManager.isPluginActive(entityHiderPlugin);
	}

	private void findEntityHiderPlugin()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin instanceof EntityHiderPlugin)
			{
				entityHiderPlugin = plugin;
				return;
			}
		}
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
	 * The overhead icon a player is currently showing, or null for none.
	 */
	HeadIcon getHeadIcon(Player player)
	{
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

	private HeadIcon activeProtectionPrayer()
	{
		if (isPrayerActive(Prayer.PROTECT_FROM_MELEE))
		{
			return HeadIcon.MELEE;
		}
		if (isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
		{
			return HeadIcon.RANGED;
		}
		if (isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
		{
			return HeadIcon.MAGIC;
		}
		return null;
	}

	/**
	 * {@code Client.isPrayerActive} is deprecated; the prayer's own varbit is the
	 * supported public equivalent.
	 */
	private boolean isPrayerActive(Prayer prayer)
	{
		return client.getVarbitValue(prayer.getVarbit()) == 1;
	}
}
