package dev.matthewwarren.bot.discord.suggestion

private val whiteSpaceRegex = Regex("\\s")
private val numberRegex = Regex("-?[0-9]+(\\.[0-9]+)?([eE][0-9]+)?")

fun toToken(botPrefix: String, textUnit: String): Token
{
    return when
    {
        textUnit.startsWith(botDefaultPrefix) -> Token(TokenType.COMMAND, textUnit.substring(botDefaultPrefix.length), textUnit)
        textUnit.startsWith(botPrefix) -> Token(TokenType.COMMAND, textUnit.substring(botPrefix.length), textUnit)
        textUnit.matches(Regex("<@[0-9]+>")) -> Token(TokenType.USER, textUnit.substring(2, textUnit.length - 1), textUnit)
        textUnit.matches(Regex("<@![0-9]+>")) -> Token(TokenType.USER, textUnit.substring(3, textUnit.length - 1), textUnit)
        textUnit.matches(Regex("<@&[0-9]+>")) -> Token(TokenType.ROLE, textUnit.substring(3, textUnit.length - 1), textUnit)
        textUnit.matches(Regex("<#[0-9]+>")) -> Token(TokenType.TEXT_CHANNEL, textUnit.substring(2, textUnit.length - 1), textUnit)
        textUnit.matches(Regex("[0-9]+-[0-9]+")) -> Token(TokenType.RANGE, textUnit)
        else -> {
            if(textUnit.matches(numberRegex))
                Token(TokenType.NUMBER, textUnit)
            else
                Token(TokenType.TEXT, textUnit)
        }
    }
}

class Tokenizer(text: String, private val botPrefix: String): Iterator<Token>
{
    private var remainingText = text.trim()
    
    val remainingTextAsToken: Token
        get()
        {
            var text = remainingText
            if(text.startsWith('"') && text.endsWith('"'))
                text = text.substring(1, text.length - 1)
            var index = 0
            while(index < text.length)
            {
                if(text[index] == '\\' && index + 1 < text.length)
                {
                    when(text[index + 1])
                    {
                        't' -> text = text.substring(0, index) + '\t' + text.substring(index + 2)
                        'n' -> text = text.substring(0, index) + '\n' + text.substring(index + 2)
                        '\\' -> text = text.substring(0, index) + '\\' + text.substring(index + 2)
                        '"' -> text = text.substring(0, index) + '"' + text.substring(index + 2)
                        'u' ->
                        {
                            val codePoint = text.substring(index + 2, index + 6).toInt(16)
                            text = text.substring(0, index) + codePoint.toChar() + text.substring(index + 6)
                        }
                    }
                }
                index++
            }
            return toToken(botPrefix, text)
        }
    
    lateinit var currentToken: Token
        private set
    
    override fun hasNext() = remainingText.isNotBlank()
    
    override fun next(): Token
    {
        var index = 0
        var quoted = false
        while(remainingText.isNotBlank())
        {
            if(!quoted)
            {
                if(index == remainingText.length || remainingText[index].isWhitespace())
                {
                    currentToken = toToken(botPrefix, remainingText.substring(0, index))
                    remainingText = remainingText.substring(index).trim()
                    return currentToken
                }
                else if(remainingText[index] == '\\')
                {
                    if(index + 1 < remainingText.length)
                    {
                        when(remainingText[index + 1])
                        {
                            't' -> remainingText = remainingText.substring(0, index) + '\t' + remainingText.substring(index + 2)
                            'n' -> remainingText = remainingText.substring(0, index) + '\n' + remainingText.substring(index + 2)
                            '\\' -> remainingText = remainingText.substring(0, index) + '\\' + remainingText.substring(index + 2)
                            '"' -> remainingText = remainingText.substring(0, index) + '"' + remainingText.substring(index + 2)
                            'u' ->
                            {
                                val codePoint = remainingText.substring(index + 2, index + 6).toIntOrNull(16)
                                if(codePoint != null)
                                    remainingText = remainingText.substring(0, index) + codePoint.toChar() + remainingText.substring(index + 6)
                            }
                        }
                    }
                    index++
                }
                else
                {
                    if(remainingText[index] == '"')
                    {
                        if(index == 0)
                        {
                            quoted = true
                        }
                        else
                        {
                            currentToken = toToken(botPrefix, remainingText.substring(0, index))
                            remainingText = remainingText.substring(index)
                            return currentToken
                        }
                    }
                    index++
                }
            }
            else
            {
                @Suppress("CascadeIf")
                if(index == remainingText.length)
                {
                    println("Improperly matched quotations")
                    currentToken = toToken(botPrefix, remainingText)
                    remainingText = ""
                    return currentToken
                }
                else if(remainingText[index] == '\\')
                {
                    if(index + 1 < remainingText.length)
                    {
                        when(remainingText[index + 1])
                        {
                            't' -> remainingText = remainingText.substring(0, index) + '\t' + remainingText.substring(index + 2)
                            'n' -> remainingText = remainingText.substring(0, index) + '\n' + remainingText.substring(index + 2)
                            '\\' -> remainingText = remainingText.substring(0, index) + '\\' + remainingText.substring(index + 2)
                            '"' -> remainingText = remainingText.substring(0, index) + '"' + remainingText.substring(index + 2)
                            'u' ->
                            {
                                val codePoint = remainingText.substring(index + 2, index + 6).toIntOrNull(16)
                                if(codePoint != null)
                                    remainingText = remainingText.substring(0, index) + codePoint.toChar() + remainingText.substring(index + 6)
                            }
                        }
                    }
                    index++
                }
                else if(remainingText[index] == '"')
                {
                    currentToken = toToken(botPrefix, remainingText.substring(1, index))
                    remainingText = remainingText.substring(index + 1).trim()
                    return currentToken
                }
                else
                {
                    index++
                }
            }
        }
        
        if(index > 0) {
            currentToken = toToken(botPrefix, remainingText.substring(0, index))
            remainingText = ""
            return currentToken
        }
        throw NoSuchElementException()
    }
}

data class Token(val tokenType: TokenType, val tokenValue: String, val rawValue: String = tokenValue)
{
    val objValue: Any? by lazy {
        try {
            when(tokenType) {
            
                TokenType.COMMAND -> Command[tokenValue]
                TokenType.USER -> try {
                    bot.retrieveUserById(tokenValue).complete()
                }
                catch(_: Exception) {
                    null
                }
                TokenType.ROLE -> bot.getRoleById(tokenValue)
                TokenType.TEXT_CHANNEL -> bot.getTextChannelById(tokenValue)
                TokenType.TEXT -> tokenValue
                TokenType.NUMBER -> tokenValue.toLongOrNull() ?: tokenValue.toDouble()
                TokenType.RANGE -> {
                    val nums = tokenValue.split("-")
                    nums[0].toLong()..nums[1].toLong()
                }
            }
        }
        catch(_: Exception) {
            println("Token objValue parsing failed\n Raw value: $rawValue\n Token value: $tokenValue\n Token type: $tokenType")
            null
        }
    }
}

enum class TokenType
{
    COMMAND, USER, ROLE, TEXT_CHANNEL, TEXT, NUMBER, RANGE
}