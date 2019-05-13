package events.Christmas;

import l2server.gameserver.Announcements;
import l2server.gameserver.GmListTable;
import l2server.gameserver.datatables.ItemTable;
import l2server.gameserver.datatables.SkillTable;
import l2server.gameserver.model.L2Skill;
import l2server.gameserver.model.L2World;
import l2server.gameserver.model.actor.L2Character;
import l2server.gameserver.model.actor.L2Npc;
import l2server.gameserver.model.actor.instance.L2PcInstance;
import l2server.gameserver.model.quest.Quest;
import l2server.gameserver.model.quest.QuestTimer;
import l2server.gameserver.network.NpcStringId;
import l2server.gameserver.network.serverpackets.NpcHtmlMessage;
import l2server.gameserver.network.serverpackets.NpcSay;
import l2server.gameserver.network.serverpackets.PlaySound;
import l2server.gameserver.network.serverpackets.SocialAction;
import l2server.gameserver.util.Util;
import l2server.util.Rnd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author LasTravel
 *         <p>
 *         Little custom Christmas event
 */

public class Christmas extends Quest
{
	//Config
	private static final boolean _exChangeOnly = false;
	private static final int _startInvasionEach = 5; //Hours
	private static final int _timeToEndInvasion = 10; //Minutes
	private static final int _rewardRandomPlayerEach = 3; //Hours
	private static final int _santaTalksEach = 12; //Hours
	private static final int _santaId = 50014;
	private static final int _secondSantaId = 104;
	private static final int[] _invaderIds = {80198, 80199, 80498, 80499};

	//Vars
	private static Long _nextInvasion;
	private static Long _nextSantaReward;
	private static String _lastSantaRewardedName;
	private static L2PcInstance _player;
	private static L2Npc _santa;
	private static boolean _isUnderInvasion = false;
	private Map<Integer, invaderInfo> _attackInfo = new HashMap<Integer, invaderInfo>();
	private ArrayList<L2Character> _invaders = new ArrayList<L2Character>();
	private ArrayList<String> _rewardedPlayers = new ArrayList<String>(); //IP based

	private static final int[][] _randomRewards = {
			//Item Id, ammount
			{57, 5000000}, // Adena
			{36414, 5}, // Dragon Claw
			{37559, 5} //Shiny Coin
	};

	public Christmas(int id, String name, String descr)
	{
		super(id, name, descr);

		//Spawn Santa's

		//addSpawn(_santaId, -78307, 247921, -3303, 24266, false, 0);



		addStartNpc(_santaId);
		addTalkId(_santaId);
		addFirstTalkId(_santaId);

		for (int mob : _invaderIds)
		{
			addAttackId(mob);
			addKillId(mob);
		}

		addFirstTalkId(_secondSantaId);

		startQuestTimer("santas_talks", _santaTalksEach * 3600000, null, null, true);

		if (!_exChangeOnly)
		{
			_nextInvasion = System.currentTimeMillis() + _startInvasionEach * 3600000;
			_nextSantaReward = System.currentTimeMillis() + _rewardRandomPlayerEach * 3600000;

			startQuestTimer("start_invasion", _startInvasionEach * 3600000, null, null);
			startQuestTimer("santas_random_player_reward", _rewardRandomPlayerEach * 3600000, null, null);
		}
	}

	private class invaderInfo
	{
		private Long _attackedTime;
		private int _playerId;
		private String _externalIP;
		private String _internalIP;

		private invaderInfo(int playerId, String externalIP, String internalIP)
		{
			_playerId = playerId;
			_externalIP = externalIP;
			_internalIP = internalIP;
			setAttackedTime();
		}

		private long getAttackedTime()
		{
			return _attackedTime;
		}

		private void setAttackedTime()
		{
			_attackedTime = System.currentTimeMillis();
		}

		private int getPlayerId()
		{
			return _playerId;
		}

		private String getExternalIP()
		{
			return _externalIP;
		}

		private String getInternalIP()
		{
			return _internalIP;
		}

		private void updateInfo(int playerId, String externalIP, String internalIP)
		{
			_playerId = playerId;
			_externalIP = externalIP;
			_internalIP = internalIP;
			setAttackedTime();
		}
	}

	@Override
	public String onAttack(L2Npc npc, L2PcInstance player, int damage, boolean isPet, L2Skill skill)
	{
		if (!_isUnderInvasion)
		{
			player.doDie(npc);
			return "";
		}

		synchronized (_attackInfo)
		{
			invaderInfo info = _attackInfo.get(npc.getObjectId()); //Get the attack info from this npc

			int sameIPs = 0;
			int underAttack = 0;

			for (Map.Entry<Integer, invaderInfo> _info : _attackInfo.entrySet())
			{
				if (_info == null)
				{
					continue;
				}

				invaderInfo i = _info.getValue();
				if (i == null)
				{
					continue;
				}

				if (System.currentTimeMillis() < i.getAttackedTime() + 5000)
				{
					if (i.getPlayerId() == player.getObjectId())
					{
						underAttack++;
					}
					if (i.getExternalIP().equalsIgnoreCase(player.getExternalIP()) &&
							i.getInternalIP().equalsIgnoreCase(player.getInternalIP()))
					{
						sameIPs++;
					}
					if (underAttack > 1 || sameIPs > 1)
					{
						player.doDie(npc);
						if (underAttack > 1)
						{
							npc.broadcastPacket(new NpcSay(npc.getObjectId(), 0, npc.getTemplate().TemplateId,
									player.getName() + " you cant attack more than one mob at same time!"));
						}
						if (sameIPs > 1)
						{
							npc.broadcastPacket(new NpcSay(npc.getObjectId(), 0, npc.getTemplate().TemplateId,
									player.getName() + " dualbox is not allowed here!"));
						}
						return "";
					}
				}
			}

			if (info == null) //Don't exist any info from this npc
			{
				//Add the correct info
				info = new invaderInfo(player.getObjectId(), player.getExternalIP(), player.getInternalIP());
				//Insert to the map
				_attackInfo.put(npc.getObjectId(), info);
			}
			else
			{
				//Already exists information for this NPC
				//Check if the attacker is the same as the stored
				if (info.getPlayerId() != player.getObjectId())
				{
					//The attacker is not same
					//If the last attacked stored info +10 seconds is bigger than the current time, this mob is currently attacked by someone
					if (info.getAttackedTime() + 5000 > System.currentTimeMillis())
					{
						player.doDie(npc);
						npc.broadcastPacket(new NpcSay(npc.getObjectId(), 0, npc.getTemplate().TemplateId,
								player.getName() + " don't attack mobs from other players!"));
						return "";
					}
					else
					{
						//Add new information, none is currently attacking this NPC
						info.updateInfo(player.getObjectId(), player.getExternalIP(), player.getInternalIP());
					}
				}
				else
				{
					//player id is the same, update the attack time
					info.setAttackedTime();
				}
			}
		}
		return super.onAttack(npc, player, damage, isPet, skill);
	}

	@Override
	public String onKill(L2Npc npc, L2PcInstance player, boolean isPet)
	{
		synchronized (_attackInfo)
		{
			invaderInfo info = _attackInfo.get(npc.getObjectId()); //Get the attack info
			if (info != null)
			{
				_attackInfo.remove(npc.getObjectId()); //Delete the stored info for this npc
			}
		}

		if (_isUnderInvasion)
		{
			L2Npc inv = addSpawn(_invaderIds[Rnd.get(_invaderIds.length)], npc.getX() + Rnd.get(100),
					npc.getY() + Rnd.get(100), npc.getZ(), 0, false, 0);
			_invaders.add(inv);
		}
		return super.onKill(npc, player, isPet);
	}

	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player)
	{
		StringBuilder tb = new StringBuilder();
		tb.append("<html><center><font color=\"3D81A8\">MEUUUH!</font></center><br1>Hohohoh! Hi " +
				player.getName() +
				" If you give me some <font color=LEVEL>Milk</font> I can give you some gifts! Take all what you can!<br>");

		if (_exChangeOnly)
		{
			tb.append(
					"This event is currently working in exchange mode, there are no more invasions or free gifts, you can only exchange your Milk.<br>");
		}
		else
		{
			tb.append(
					"You can get <font color=LEVEL>Milk</font> while participating in the <font color=LEVEL>Cow invasion</font> each <font color=LEVEL>" +
							_startInvasionEach + "</font> hours.<br>");
			tb.append(
					"<font color=LEVEL>King of the cows</font> will also visit <font color=LEVEL>randomly</font> each <font color=LEVEL>" +
							_rewardRandomPlayerEach +
							"</font> hours an <font color=LEVEL>active</font> player and will give special random gifts!<br>");
			tb.append(getNextInvasionTime() + "<br1>");
			tb.append(getNextSantaRewardTime() + "<br1>");
			tb.append("<font color=LEVEL>Last random player rewarded: " +
					(_lastSantaRewardedName == null ? "None Yet" : _lastSantaRewardedName) + "</font><br>");
			if (_isUnderInvasion)
			{
				tb.append("<font color=\"3D81A8\">Available Actons:</font><br>");
				tb.append(
						"<a action=\"bypass -h Quest Christmas teleport_to_fantasy\"><font color=c2dceb>Teleport to Fantasy Island.</font></a><br1>");
			}
		}

		tb.append("<br>");
		tb.append("<font color=\"3D81A8\">Available Gifts:</font><br1>");
		tb.append("<a action=\"bypass -h npc_" + npc.getObjectId() +
				"_multisell christmas_event_shop\"><font color=c2dceb>View the event shop.</font></a><br1>");
		tb.append("<br>");

		/*if (!_exChangeOnly)
		{
			tb.append("<font color=\"3D81A8\">Free Effects:</font><br1>");
			tb.append(
					"<a action=\"bypass -h Quest Christmas eventEffect 16419\"><font color=c2dceb>Receive the Stocking Fairy's Blessingt buff!</font></a><br1>");
			tb.append(
					"<a action=\"bypass -h Quest Christmas eventEffect 16420\"><font color=c2dceb>Receive the Tree Fairy's Blessing buff!</font></a><br1>");
			tb.append(

					"<a action=\"bypass -h Quest Christmas eventEffect 16421\"><font color=c2dceb>Receive the Snowman Fairy's Blessing buff!</font></a><br1>");
			tb.append("<br>");
			tb.append("<font color=\"3D81A8\">Christmas Music:</font><br1>");
			tb.append(
					"<a action=\"bypass -h Quest Christmas eventMusic\"><font color=c2dceb>Play some music!</font></a><br1>");
			tb.append("<br>");
		}*/

		//GMPart
		if (player.isGM())
		{
			tb.append("<center>~~~~ For GM's Only ~~~~</center> <br1>");
			tb.append("<a action=\"bypass -h Quest Christmas start_invasion_gm\">Start new Invasion</a><br1>");
			tb.append("<a action=\"bypass -h Quest Christmas end_invasion_gm_force\">Force Stop Invasion</a><br1>");
			tb.append(
					"<a action=\"bypass -h Quest Christmas santas_random_player_reward_gm\">Give random reward to a player</a>");
		}

		tb.append("</body></html>");

		NpcHtmlMessage msg = new NpcHtmlMessage(_santaId);
		msg.setHtml(tb.toString());
		player.sendPacket(msg);

		return "";
	}

	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		if (event.equalsIgnoreCase("santas_talks"))
		{
			if (_exChangeOnly)
			{
				Announcements.getInstance().announceToAll(
						"King of the Cows: Ho-ho-ho! I need tons of Milk! You have only few days more for get some gifts before I go to my home! Ho-ho-ho! Ho-ho-ho!");
			}
			else
			{
				Announcements.getInstance().announceToAll(
						"King of the Cows: Ho-ho-ho! I need tons of Milk! Please collect them in Cow Invasion for me! You can find me at any Town! Ho-ho-ho! Ho-ho-ho!");
			}
		}
		else if (event.equalsIgnoreCase("teleport_to_fantasy"))
		{
			player.teleToLocation(-59004, -56889, -2032, true);
		}
		else if (event.startsWith("eventEffect"))
		{
			int effect = Integer.valueOf(event.replace("eventEffect", "").trim());
			if (effect == 16419 || effect == 16420 || effect == 16421)
			{
				SkillTable.getInstance().getInfo(effect, 1).getEffects(player, player);
			}
		}
		else if (event.equalsIgnoreCase("eventMusic"))
		{
			int rnd = Rnd.get(4) + 1;
			player.sendPacket(new PlaySound(1, "CC_0" + rnd, 0, 0, 0, 0, 0));
		}
		else if (event.startsWith("santas_random_player_reward"))
		{
			if (_exChangeOnly)
			{
				return "";
			}

			List<L2PcInstance> playerList = new ArrayList<L2PcInstance>();
			for (L2PcInstance pl : L2World.getInstance().getAllPlayersArray())
			{
				if (pl != null && pl.isOnline() && pl.isInCombat() && !pl.isInsideZone(L2Character.ZONE_PEACE) &&
						!pl.isFlyingMounted() && pl.getClient() != null && !pl.getClient().isDetached())
				{
					if (_rewardedPlayers.contains(pl.getExternalIP()))
					{
						continue;
					}

					playerList.add(pl);
				}
			}

			if (!playerList.isEmpty())
			{
				_player = playerList.get(Rnd.get(playerList.size()));
				if (_player != null)
				{
					_rewardedPlayers.add(_player.getExternalIP());
					_lastSantaRewardedName = _player.getName();

					int locx = (int) (_player.getX() + Math.pow(-1, Rnd.get(1, 2)) * 50);
					int locy = (int) (_player.getY() + Math.pow(-1, Rnd.get(1, 2)) * 50);
					int heading = Util.calculateHeadingFrom(locx, locy, _player.getX(), _player.getY());

					_santa = addSpawn(_secondSantaId, locx, locy, _player.getZ(), heading, false, 30000);

					startQuestTimer("santas_reward_1", 5000, null, null);

					if (!event.equalsIgnoreCase("santas_random_player_reward_gm"))
					{
						startQuestTimer("santas_random_player_reward", _rewardRandomPlayerEach * 3600000, null, null);
						_nextSantaReward = System.currentTimeMillis() + _rewardRandomPlayerEach * 3600000;
					}
					//System.out.println("CHRISTMAS REWARDING: " + _player.getName());
					GmListTable.broadcastMessageToGMs(
							"Christmas: Rewarding: " + _player.getName() + " IP: " + _player.getExternalIP());
				}
			}
		}
		else if (event.equalsIgnoreCase("santas_reward_1"))
		{
			final NpcSay msg = new NpcSay(_santa.getObjectId(), 0, _santa.getNpcId(), NpcStringId.I_HAVE_A_GIFT_FOR_S1);
			msg.addStringParameter(_player.getName());

			_santa.broadcastPacket(msg);

			startQuestTimer("santas_reward_2", 5000, null, null);
		}
		else if (event.equalsIgnoreCase("santas_reward_2"))
		{
			//Select random reward
			int reward[] = _randomRewards[Rnd.get(_randomRewards.length)];
			_santa.broadcastPacket(new SocialAction(_santa.getObjectId(), 2));
			_santa.broadcastPacket(new NpcSay(_santa.getObjectId(), 0, _santa.getNpcId(),
					NpcStringId.TAKE_A_LOOK_AT_THE_INVENTORY_I_HOPE_YOU_LIKE_THE_GIFT_I_GAVE_YOU));

			int rndCount = Rnd.get(1, reward[1]);
			_player.addItem("Christmas", reward[0], rndCount, _player, true);

			GmListTable.broadcastMessageToGMs(
					"Christmas: Player: " + _player.getName() + " rewarded with: " + rndCount + " " +
							ItemTable.getInstance().getTemplate(reward[0]).getName());

			_player = null;
		}
		else if (event.startsWith("start_invasion"))
		{
			if (_isUnderInvasion)
			{
				return "";
			}

			_isUnderInvasion = true;

			int radius = 1000;
			for (int a = 0; a < 2; a++)
			{
				for (int i = 0; i < 50; i++)
				{
					int x = (int) (radius * Math.cos(i * 0.618));
					int y = (int) (radius * Math.sin(i * 0.618));

					L2Npc inv =
							addSpawn(_invaderIds[Rnd.get(_invaderIds.length)], -59718 + x, -56909 + y, -2029 + 20, -1,
									false, 0, false, 0);
					_invaders.add(inv);
				}
				radius += 300;
			}

			Announcements.getInstance().announceToAll("Fantasy Island is under Cow Invasion!");
			Announcements.getInstance().announceToAll("Don't attack mobs from other players!");
			Announcements.getInstance().announceToAll("Dualbox is not allowed on the event!");
			Announcements.getInstance()
					.announceToAll("The invasion will lasts for: " + _timeToEndInvasion + " minute(s)!");

			startQuestTimer(event.equalsIgnoreCase("start_invasion") ? "end_invasion" : "end_invasion_gm",
					_timeToEndInvasion * 60000, null, null);
		}
		else if (event.startsWith("end_invasion"))
		{
			_isUnderInvasion = false;

			if (event.equalsIgnoreCase("end_invasion_gm_force"))
			{
				QuestTimer timer = getQuestTimer("end_invasion_gm", null, null);
				if (timer != null)
				{
					timer.cancel();
				}
			}

			for (L2Character chara : _invaders)
			{
				if (chara == null)
				{
					continue;
				}
				chara.deleteMe();
			}

			_invaders.clear();
			_attackInfo.clear();

			Announcements.getInstance().announceToAll("The invasion has been ended!");

			//Only schedule the next invasion if is not started by a GM
			if (!event.startsWith("end_invasion_gm"))
			{
				startQuestTimer("start_invasion", _startInvasionEach * 3600000, null, null);
				_nextInvasion = System.currentTimeMillis() + _startInvasionEach * 3600000;
			}
		}
		return "";
	}

	private static String getNextInvasionTime()
	{
		Long remainingTime = (_nextInvasion - System.currentTimeMillis()) / 1000;
		int hours = (int) (remainingTime / 3600);
		int minutes = (int) (remainingTime % 3600 / 60);

		if (minutes < 0)
		{
			return "<font color=LEVEL>Next Invasion in: Currently under invasion!</font>";
		}

		return "<font color=LEVEL>Next Invasion in: " + hours + " hours and " + minutes + " minutes!</font>";
	}

	private static String getNextSantaRewardTime()
	{
		Long remainingTime = (_nextSantaReward - System.currentTimeMillis()) / 1000;
		int hours = (int) (remainingTime / 3600);
		int minutes = (int) (remainingTime % 3600 / 60);

		if (minutes < 0)
		{
			return "<font color=LEVEL>Next King of the Cows Reward in: Currently rewarding a player!</font>";
		}

		return "<font color=LEVEL>Next King of the Cows Reward: " + hours + " hours and " + minutes + " minutes!</font>";
	}

	@Override
	public int getOnKillDelay(int npcId)
	{
		return 0;
	}

	public static void main(String[] args)
	{
		new Christmas(-1, "Christmas", "events");
	}
}