package dev.matthewwarren.bot.discord.suggestion

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import java.util.*

fun commandLine(bot: JDA)
{
    val scanner = Scanner(System.`in`)
    lineLoop@while(true)
    {
        val line = scanner.nextLine()
        val tokenizer = Tokenizer(line, botDefaultPrefix)
        if(!tokenizer.hasNext())
            continue
    
        when(tokenizer.next().tokenValue)
        {
            "shutdown" -> shutdownBot(bot)
            "reload" -> reloadBot(bot)
            "ping" -> println("pong")
            "resetPresence" -> {
                bot.presence.activity = Activity.playing("DM me suggestions or complaints")
                println("Presence has been reset")
            }
        }
    }
}