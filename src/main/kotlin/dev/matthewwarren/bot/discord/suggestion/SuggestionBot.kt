package dev.matthewwarren.bot.discord.suggestion

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.ShutdownEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import org.json.JSONObject
import java.io.File
import java.util.*
import kotlin.system.exitProcess

lateinit var bot: JDA
    private set

const val botDefaultPrefix = "sb!"

val saveFile = File("saveData.json")

val joinedGuilds = mutableMapOf<String, GuildData>()
val suggestions = PriorityQueue<SuggestionMetaData>()

var shutdownMode = ExitMode.SHUTDOWN

fun main()
{
    //Logger.getLogger(OkHttpClient::class.java.name).level = Level.FINE
    val token = File("token").readText()
    bot = JDABuilder.create(token, GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
        .addEventListeners(UtilityListener(), MessageListener())
        .build()
        .awaitReady()
    
    load()
    Runtime.getRuntime().addShutdownHook(Thread(::save))
    
    while(true)
    {
        try
        {
            commandLine(bot)
        }
        catch(e: Exception)
        {
            e.printStackTrace()
        }
    }
}

fun load() {
    if(!saveFile.exists())
        return
    
    val saveData = JSONObject(saveFile.readText())
    
    if(saveData.has("guildData"))
        saveData.getJSONArray("guildData").map {GuildData.fromJSON(it as JSONObject)}.forEach {
            if(bot.getGuildById(it.guildId)?.isMember(bot.selfUser) == true)
                joinedGuilds[it.guildId] = it
        }
    
    if(saveData.has("suggestions"))
        saveData.getJSONArray("suggestions").map {SuggestionMetaData.fromJSON(it as JSONObject)}.let(suggestions::addAll)
}

fun save()
{
    val saveData = JSONObject()
    
    saveData.put("guildData", joinedGuilds.values.map(GuildData::toJSON))
    saveData.put("suggestions", suggestions.map(SuggestionMetaData::toJSON))
    
    saveFile.writeText(saveData.toString(4))
}

class UtilityListener: ListenerAdapter()
{
    override fun onReady(event: ReadyEvent)
    {
        event.jda.isAutoReconnect = true
        println("Logged in as ${event.jda.selfUser.name}\n${event.jda.selfUser.id}\n-----------------------------")
        event.jda.presence.activity = Activity.playing("DM me suggestions or complaints")
    }
    
    override fun onShutdown(event: ShutdownEvent)
    {
        save()
        exitProcess(shutdownMode.ordinal)
    }
    
    override fun onGuildJoin(event: GuildJoinEvent) {
        if(event.guild.id !in joinedGuilds)
            joinedGuilds[event.guild.id] = GuildData(event.guild)
    }
}

class MessageListener: ListenerAdapter()
{
    override fun onMessageReceived(event: MessageReceivedEvent)
    {
        if(event.author.isBot)
            return
        
        val isPrivateChannel = !event.channelType.isGuild
        val botPrefix = if(isPrivateChannel) botDefaultPrefix else joinedGuilds[event.guild.id]!!.botPrefix
        val tokenizer = Tokenizer(event.message.contentRaw, botPrefix)
        if(!tokenizer.hasNext())
            return
        
        val firstToken = tokenizer.next()
        if(firstToken.tokenType == TokenType.COMMAND) {
            runCommand(firstToken.tokenValue, tokenizer, event.message)
        }
        else if(isPrivateChannel) {
            val suggestion = event.message
            try {
                val content = suggestion.contentRaw.replace("@everyone", "@\u200Beveryone")
                val suggestionChannels = bot.guilds.filter {it.isMember(event.author)}.map {joinedGuilds[it.id]!!.suggestionChannelId}.map {
                    bot.getTextChannelById(it)!!
                }
                suggestionChannels.forEach {textChannel ->
                    textChannel.sendMessage("**A suggestion/complaint has been submitted with id ${suggestion.id}.**\n$content").queue {
                        event.channel.sendMessage("Thank you for making a suggestion or complaint. It has been anonymously forwarded to the moderation team").queue()
                        if(suggestion.attachments.isNotEmpty())
                            event.channel.sendMessage("1 or more attachments were found in your message. Attachments are not sent as part of a suggestion. Use links instead.").queue()
                    }
                }
                suggestions.add(SuggestionMetaData(System.currentTimeMillis(), suggestion.id, suggestion.channel.id))
            }
            catch(iae: IllegalArgumentException) {
                event.channel.sendMessage("Your suggestion or complaint was unable to be forwarded. Try a shorter message. If the problem persists contact the moderation team").queue()
            }
            
            // keep suggestions for 1 week
            suggestions.removeIf {System.currentTimeMillis() - it.timestamp > 604800_000}
        }
    }
}