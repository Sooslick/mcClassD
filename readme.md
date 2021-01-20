# Class D: Manhunt game mode plugin

A game mode for Spigot-based Minecraft servers in which one player becomes the Victim
and the rest become Hunters.

[Gameplay](#Gameplay)  
[Features](#Features)  
[Setup](#Setup)  
[For developers](#For-developers)

### Gameplay

During the lobby stage players propose themselves as the Victim and vote to start. 
The game begins as soon as enough players vote. 
One of the candidates (or a random player if nobody proposed themselves) will be the Victim, 
and the rest will be Hunters. Victim spawns at random location, 
and they must complete the game mode's objective whereas Hunters have a goal to kill the Victim
and spawn at some distance from them.

### Features

* __Customizable spawn settings__

`spawnDistance` - distance between Victim and Hunters spawns  
`potionHandicap` - brief start buff for the Victim  
`startInventory` - items that will be given once to every player

* __Victim selection__

The Victim is chosen by random, but priority is given to players that proposed 
themselves using `/manhunt suggest` command. Those who don't want to be a Victim 
and sent the `/manhunt exclude` command have the lowest priority.

* __Victim detection__

`compassUpdates` - frequency of Hunters' compasses updates. 
If enabled, Hunters will receive a compass on every respawn. 
This compass is pointing to Victim's location or the point where the Victim teleported to another world.  
`hideVictimNametagAboveHunters` - if the team of Hunters is too big, Victim's nametag become hidden, 
and Victim cannot be spotted through the wall.  
`enableVictimGlowing` - permanent Glowing effect for the Victim which may be removed for a while only by milk.

* __Victim's instinct__

Victim receives a text notification when one of Hunters gets too close.

* __Latecomers__

If the game has already started, players who are late still have the ability to send the join request using 
`/manhunt joinrequest` command. Only Victim can accept these requests as the late players can only become Hunters.

* __Replayability__

Each round, random spawn points are picked, 
and when the round ends, the plugin will clean most of the stuff left by players, such as chests, 
beds, portals and dropped items. They will be removed and cannot be used in the next game. 
Nonetheless, we recommend creating a new world for every single game.

* __Custom game modes__

A lot of various game modes based on simple Manhunt rules can be made using the plugin's functionality. 
By default, a couple game modes are available:

*Classic Any%*: just Minecraft. The only objective for the Victim is killing the Ender Dragon by any means.

*Escape the Wall*: play area is restricted by an unpassable wall of bedrock with a few 
exit spots made of obsidian. Victim's objective is to pass through the Wall.

*3* ??? (countdown)

### Setup

##### Installing and configuring

1. Put the downloaded .jar file in your server's `/plugins` folder.  
2. Restart your server. _We do not recommend using 
`/reload` command because it may cause issues in plugins' work_  
3. Configure the generated `config.yml` in `/plugins/ClassD`. 
Settings will be applied after restarting the server or after finishing the game's round.

##### Permissions

All commands and features are available for every player except this feature:  
`classd.force.start` - for starting immediately with any number of players.

##### Commands

* `/manhunt help` - show detailed info about game mode, rules and list of commands
* `/manhunt votestart` - vote to start the game. Alias is `/mh v`
* `/manhunt suggest` - propose yourself as Victim. Alias is `/mh s`
* `/manhunt exclude` - give up the role of Victim. Alias is `/mh e`
* `/manhunt joinrequest` - send the game join request
* `/manhunt accept` - accept received join requests. Alias is `/y`
* `/manhunt start` - force game start



### For developers

##### Build from sources

The project doesn't use any of build automation tools such as Maven, so after cloning you should properly 
set the project settings, add spigot.jar of required version to libraries and configure the artifact building.

##### Localisation

The `Messages` class contains all messages that appeare in the game chat.

##### Custom game modes

If you want to create a custom game mode, 
you should create a class implementing `GameModeBase` interface and its methods:

`onIdle` - called at plugin loading and after round ending. 
Use this method for initialization and environment preparing;  
`onPreStart` - late initialization. Called when enough players vote to start. 
Forced start DOES NOT skip this stage, but sets its duration to zero.  
`onGame` - called at game start after Victim and Hunters role distribution and world environment settings.  
`tick` - the ingame event called every second.  
`unload` - the method called instead of `onIdle` when `preferredGamemode` in config is changed. 
Here you must cancel all scheduled tasks, unregister your events and rollback changes if required.

If the game mode utilizes a custom config, 
you should create a class implementing `GameModeConfig` and 
override `getConfig` of your `GameModeBase`. Override `readConfig` method to read your config.
This method is automatically called before `onIdle` so you don't need to reload config after every round.

All core and gameplay variables are stored in main plugin instance. 
To access it, you should use `Engine.getInstance()`. Victory conditions for the Victim 
are not defined by default, so you must implement them in your game mode - call the engine's `triggerEndgame`.

To see an example, check the source code of The Wall game mode, which is located in 
`ru.sooslick.outlaw.gamemode.wall`

##### Enable custom game mode

After completing the custom game mode you must insert it into `gamemodes` list in your 
`config.yml` as following:  
`gamemodeName: fully.qualified.class.name`  
and then change the `preferredGamemode` to yours.

If your game mode is implemented as an independent plugin, you must specify `loadbefore` 
in your `plugin.yml` because first game mode loading is initiated in `onEnable`. 
Otherwise, it will probably produce `NoClassDefFoundError` and switch to default Any% mode.