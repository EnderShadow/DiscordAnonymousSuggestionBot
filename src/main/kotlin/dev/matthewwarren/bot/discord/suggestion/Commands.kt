package dev.matthewwarren.bot.discord.suggestion

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
                channel?.sendMessage("The mods have replied to your suggestion: $replyContent")?.queue {
                    sourceMessage.channel.sendMessage("Your reply was successfully sent").queue()
                }
            } ?: sourceMessage.channel.sendMessage("Unable to reply to the suggestion because it's either been purged from cache, the user does not exist, or I cannot send a DM to them").queue()
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