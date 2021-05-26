package dev.matthewwarren.bot.discord.suggestion

import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.*

fun runCommand(command: String, tokenizer: Tokenizer, sourceMessage: Message)
{
    if(!sourceMessage.channelType.isGuild)
        Command[command].takeIf {it.allowedInPrivateChannel}?.invoke(tokenizer, sourceMessage)
    else
        Command[command].let {
            if(!it.requiresAdmin || isServerAdmin(sourceMessage.member!!))
                it(tokenizer, sourceMessage)
            else
                sourceMessage.channel.sendMessage("${sourceMessage.member!!.asMention} You don't have permission to run this command.").queue()
        }
}

@Suppress("unused")
sealed class Command(val prefix: String, val requiresAdmin: Boolean = false, val allowedInPrivateChannel: Boolean = false)
{
    companion object
    {
        private val commands = mutableMapOf<String, Command>()
        private val noopCommand: Command
        
        init
        {
            Command::class.sealedSubclasses.asSequence().map {it.constructors.first().call()}.forEach {commands[it.prefix] = it}
            noopCommand = commands.remove("noop")!!
        }
        
        operator fun get(prefix: String) = commands.getOrDefault(prefix, noopCommand)
    }
    
    abstract fun helpMessage(botPrefix: String): String
    abstract operator fun invoke(tokenizer: Tokenizer, sourceMessage: Message)
    
    class NoopCommand: Command("noop", allowedInPrivateChannel = true)
    {
        override fun helpMessage(botPrefix: String) = ""
        override fun invoke(tokenizer: Tokenizer, sourceMessage: Message) {}
    }
    
    class Say: Command("say", true)
    {
        override fun helpMessage(botPrefix: String) = """`${botPrefix}say` __Makes the bot say something__
            |
            |**Usage:** ${botPrefix}say [text]
            |              ${botPrefix}say [text] !tts
            |
            |**Examples:**
            |`${botPrefix}say hello world` makes the bot say 'hello world'
            |`${botPrefix}say hello world !tts` makes the bot say 'hello world' with tts
        """.trimMargin()
        
        override fun invoke(tokenizer: Tokenizer, sourceMessage: Message)
        {
            var content = tokenizer.remainingTextAsToken.tokenValue
            val tts = content.endsWith("!tts")
            if(tts)
                content = content.substring(0, content.length - 4).trim()
            if(content.isNotEmpty())
            {
                sourceMessage.channel.sendMessage(content).tts(tts).queue()
                sourceMessage.delete().queue()
                println("${sourceMessage.author.name} made me say \"$content\"")
            }
            else
            {
                sourceMessage.channel.sendMessage("I can't say blank messages").queue()
            }
        }
    }
    
    class Reply: Command("reply", true)
    {
        override fun helpMessage(botPrefix: String) = """`${botPrefix}reply` __Replies to a suggestion__
            |
            |**Usage:** ${botPrefix}reply [suggestion_id] [message]
            |
            |**Examples:**
            |`${botPrefix}reply 1234567890 Your suggestion is bad and you should feel bad.` makes the bot reply to the suggestion with id 1234567890 with 'Your suggestion is bad and you should feel bad.'
        """.trimMargin()
        
        override fun invoke(tokenizer: Tokenizer, sourceMessage: Message)
        {
            if(!tokenizer.hasNext())
            {
                sourceMessage.channel.sendMessage("You must specify a suggestion id").queue()
                return
            }
            val suggestionIdToken = tokenizer.next()
            if(suggestionIdToken.tokenType != TokenType.NUMBER) {
                sourceMessage.channel.sendMessage("You must specify a valid suggestion id").queue()
            }
            
            val suggestionId = suggestionIdToken.tokenValue
            val replyContent = tokenizer.remainingTextAsToken.rawValue
            suggestions.firstOrNull {it.suggestionMessageId == suggestionId}?.let {
                val channel = bot.getPrivateChannelById(it.userChannelId) ?: bot.openPrivateChannelById(it.userChannelId).complete()
                channel?.sendMessage("The mods from ${sourceMessage.guild.name} have replied to your suggestion: $replyContent")?.queue {
                    sourceMessage.channel.sendMessage("Your reply was successfully sent").queue()
                }
            } ?: sourceMessage.channel.sendMessage("Unable to reply to the suggestion because it's either been purged from cache, the user does not exist, or I cannot send a DM to them").queue()
        }
    }
    
    class Admin: Command("admin", true) {
        override fun helpMessage(botPrefix: String) = """`${botPrefix}admin` __Used for managing the roles that can manage the bot__
            |
            |**Usage:** ${botPrefix}admin list
            |              ${botPrefix}admin add [role] ...
            |              ${botPrefix}admin remove [role] ...
            |
            |The server owner can always administrate the bot
            |
            |**Examples:**
            |`${botPrefix}admin list` lists the roles that can currently manage the bot
            |`${botPrefix}admin add @Admin @Moderator` adds the @Admin and @Moderator role to the list of roles that can administrate the bot
        """.trimMargin()
        
        override fun invoke(tokenizer: Tokenizer, sourceMessage: Message) {
            if(isServerAdmin(sourceMessage.member!!) && tokenizer.hasNext()) {
                val guildData = joinedGuilds[sourceMessage.guild.id]!!
                when(tokenizer.next().tokenValue) {
                    "list" -> {
                        val messageBuilder = if(guildData.adminRoleIds.isNotEmpty())
                            MessageBuilder(guildData.adminRoleIds.map(sourceMessage.guild::getRoleById).joinToString(" ") {it!!.asMention})
                        else
                            MessageBuilder("No roles are registered as a bot admin")
                        sourceMessage.channel.sendMessage(messageBuilder.build()).queue()
                    }
                    "add" -> {
                        if(tokenizer.hasNext()) {
                            guildData.adminRoleIds.addAll(tokenizer.asSequence().filter {it.tokenType == TokenType.ROLE}.mapNotNull {it.tokenValue})
                            sourceMessage.channel.sendMessage("The admin roles have been updated").queue()
                            save()
                        }
                    }
                    "remove" -> {
                        if(guildData.adminRoleIds.removeAll(tokenizer.asSequence().filter {it.tokenType == TokenType.ROLE}.mapNotNull {it.tokenValue})) {
                            sourceMessage.channel.sendMessage("The admin roles have been updated").queue()
                            save()
                        }
                    }
                }
            }
        }
    }
    
    class SuggestionChannel: Command("suggestionChannel", true) {
        override fun helpMessage(botPrefix: String) = """`${botPrefix}suggestionChannel` __Gets or sets the suggestion channel for the server__
            |
            |**Usage:** ${botPrefix}suggestionChannel
            |              ${botPrefix}suggestionChannel [channel]
            |
            |**Examples:**
            |`${botPrefix}suggestionChannel` gets the suggestion channel for the server
            |`${botPrefix}suggestionChannel #channel` sets the suggestion channel for the server to #channel
        """.trimMargin()
        
        override fun invoke(tokenizer: Tokenizer, sourceMessage: Message) {
            if(isServerAdmin(sourceMessage.member!!)) {
                if(tokenizer.hasNext()) {
                    joinedGuilds[sourceMessage.guild.id]!!.suggestionChannelId = tokenizer.remainingTextAsToken.tokenValue
                    sourceMessage.channel.sendMessage("The suggestion channel has been updated").queue()
                    save()
                }
                else {
                    sourceMessage.channel.sendMessage("The current suggestion channel is ${sourceMessage.guild.getTextChannelById(joinedGuilds[sourceMessage.guild.id]!!.suggestionChannelId)!!.asMention}").queue()
                }
            }
        }
    }
    
    class BotPrefix: Command("botPrefix", true) {
        override fun helpMessage(botPrefix: String) = """`${botPrefix}botPrefix` __Gets or sets the bot prefix for the server__
            |
            |**Usage:** ${botPrefix}botPrefix
            |              ${botPrefix}botPrefix [new bot prefix]
            |
            |**Examples:**
            |`${botPrefix}botPrefix` gets the initial role for the server
            |`${botPrefix}botPrefix @member` sets the initial role for the server to the @member role
            |`${botPrefix}initialRole none` sets the initial role for the server to no role
        """.trimMargin()
        
        override fun invoke(tokenizer: Tokenizer, sourceMessage: Message) {
            if(isServerAdmin(sourceMessage.member!!)) {
                if(tokenizer.hasNext()) {
                    joinedGuilds[sourceMessage.guild.id]!!.botPrefix = tokenizer.remainingTextAsToken.tokenValue
                    sourceMessage.channel.sendMessage("The bot prefix has been updated").queue()
                    save()
                }
                else {
                    sourceMessage.channel.sendMessage("The current bot prefix is ${joinedGuilds[sourceMessage.guild.id]!!.botPrefix}").queue()
                }
            }
        }
    }
    
    class Help: Command("help", allowedInPrivateChannel = true)
    {
        override fun helpMessage(botPrefix: String) = """`${botPrefix}help` __Displays a list of commands. Provide a command to get its info__
            |
            |**Usage:** ${botPrefix}help
            |              ${botPrefix}help [command]
            |
            |**Examples:**
            |`${botPrefix}help` displays a list of all commands
            |`${botPrefix}help say` displays the help info for the say command
        """.trimMargin()
        
        override fun invoke(tokenizer: Tokenizer, sourceMessage: Message) {
            val (adminCommands, normalCommands) = commands.values.splitAndMap(Command::requiresAdmin) {it.prefix}
            val botPrefix = joinedGuilds[sourceMessage.guild.id]!!.botPrefix
            val message = if(!tokenizer.hasNext()) {
                """```bash
                    |'command List'```
                    |
                    |Use `!help [command]` to get more info on a specific command, for example: `${botPrefix}help say`
                    |
                    |**Standard Commands**
                    |${normalCommands.joinToString(" ") {"`$it`"}}
                    |
                    |**Admin Commands**
                    |${adminCommands.joinToString(" ") {"`$it`"}}
                """.trimMargin()
            }
            else {
                val command = tokenizer.next().tokenValue
                commands[command]?.helpMessage(botPrefix) ?: "Command '$command' was not found."
            }
            sourceMessage.channel.sendMessage(message).queue()
        }
    }
}