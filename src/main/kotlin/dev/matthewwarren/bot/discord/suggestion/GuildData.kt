package dev.matthewwarren.bot.discord.suggestion

import net.dv8tion.jda.api.entities.Guild
import org.json.JSONObject

class GuildData private constructor(val guildId: String, val adminRoleIds: MutableList<String>, var suggestionChannelId: String, var botPrefix: String) {
    companion object {
        fun fromJSON(jsonObject: JSONObject): GuildData {
            val guildId = jsonObject.getString("guildId")
            @Suppress("UNCHECKED_CAST")
            val adminRoleIds = jsonObject.getJSONArray("adminRoleIds") as List<String>
            val suggestionChannelId = jsonObject.getString("suggestionChannelid")
            val botPrefix = jsonObject.getString("botPrefix")
            
            return GuildData(guildId, adminRoleIds.toMutableList(), suggestionChannelId, botPrefix)
        }
    }
    
    constructor(guild: Guild): this(guild.id, mutableListOf(), guild.defaultChannel?.id ?: "", botDefaultPrefix)
    
    fun toJSON(): JSONObject {
        val jsonObject = JSONObject()
        
        jsonObject.put("guildId", guildId)
        jsonObject.put("adminRoleIds", adminRoleIds)
        jsonObject.put("suggestionChannelid", suggestionChannelId)
        jsonObject.put("botPrefix", botPrefix)
        
        return jsonObject
    }
}