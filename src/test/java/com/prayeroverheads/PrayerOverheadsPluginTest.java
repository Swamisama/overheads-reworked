package com.prayeroverheads;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PrayerOverheadsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		// Bolt launches this jar with launcher-style args (-J-D... JVM passthrough,
		// --configure) that the client's option parser rejects; drop them.
		List<String> clientArgs = new ArrayList<>();
		for (String arg : args)
		{
			if (!arg.startsWith("-J") && !arg.equals("--configure"))
			{
				clientArgs.add(arg);
			}
		}
		// Dev convenience: when a Jagex Launcher/Bolt session is present, dump it to
		// credentials.properties so a plain `gradlew run` can log in without a launcher.
		// No-op when launched without a session. Do not ship this in the hub build.
		clientArgs.add("--insecure-write-credentials");

		ExternalPluginManager.loadBuiltin(PrayerOverheadsPlugin.class);
		RuneLite.main(clientArgs.toArray(new String[0]));
	}
}
