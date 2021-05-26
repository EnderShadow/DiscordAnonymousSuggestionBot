package dev.matthewwarren.bot.discord.suggestion

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Member
import org.json.JSONObject

fun isServerAdmin(member: Member) = member.isOwner || member.roles.map {it.id}.intersect(joinedGuilds[member.guild.id]!!.adminRoleIds).isNotEmpty() || member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER)

fun reloadBot(bot: JDA)
{
    shutdownMode = ExitMode.RELOAD
    bot.shutdown()
}

fun shutdownBot(bot: JDA)
{
    shutdownMode = ExitMode.SHUTDOWN
    bot.shutdown()
}

/**
 * All items matching the filter are put into the first list. All items not matching the filter are put into the second list
 */
inline fun <reified T, reified U> Collection<T>.splitAndMap(filter: (T) -> Boolean, mapper: (T) -> (U)): Pair<List<U>, List<U>>
{
    val l1 = mutableListOf<U>()
    val l2 = mutableListOf<U>()
    forEach {
        if(filter(it))
            l1.add(mapper(it))
        else
            l2.add(mapper(it))
    }
    return Pair(l1, l2)
}

data class SuggestionMetaData(val timestamp: Long, val suggestionMessageId: String, val userChannelId: String): Comparable<SuggestionMetaData> {
    companion object {
        fun fromJSON(jsonObject: JSONObject): SuggestionMetaData {
            val timestamp = jsonObject.getLong("timestamp")
            val suggestionMessageId = jsonObject.getString("suggestionMessageId")
            val userChannelId = jsonObject.getString("userChannelId")
            
            return SuggestionMetaData(timestamp, suggestionMessageId, userChannelId)
        }
    }
    
    override fun compareTo(other: SuggestionMetaData): Int {
        return timestamp.compareTo(other.timestamp)
    }
    
    fun toJSON(): JSONObject {
        val jsonObj = JSONObject()
        
        jsonObj.put("timestamp", timestamp)
        jsonObj.put("suggestionMessageId", suggestionMessageId)
        jsonObj.put("userChannelId", userChannelId)
        
        return jsonObj
    }
}