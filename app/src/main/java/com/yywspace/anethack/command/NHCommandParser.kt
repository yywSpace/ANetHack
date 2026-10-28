package com.yywspace.anethack.command

import java.io.PushbackReader
import java.io.StringReader

object NHCommandParser {
    /*
    key command: -
    extended command: #read
    sequence command: #engrave#-L"Elbereth"
        key command in sequence: -
        extended command in sequence: #read#
        line command in sequence: L"Elbereth"
    menu match in sequence: M"regex"  (e.g. aM"unicorn horn")
    number prefix: 20s、d20a  (数字绑到它后面那个按键的 numberPrefix 上)
    */
    fun parseNHCommand(commandText:String):List<NHCommand> {
        val commands = mutableListOf<NHCommand>()
        parseCommands(commandText, commands)
        return commands
    }

    /**
     * 逐字符解析：`#…#` 扩展命令、`L"…"` 行输入、`M"…"` 菜单匹配，
     * 其余字符都是按键，连续数字作为前缀绑到后面那个按键上。
     */
    private fun parseCommands(commandText:String, commands: MutableList<NHCommand>) {
        val reader = PushbackReader(StringReader(commandText), commandText.length)
        var next = readChar(reader)
        while (next != null) {
            when {
                next.isDigit() -> {
                    reader.unread(next.code)
                    commands.addAll(parseKeyWithPrefix(reader))
                    next = readChar(reader)
                }
                next == '#' -> {
                    commands.add(parseExtendedCommand(reader))
                    next = readChar(reader)
                }
                next == 'L' -> {
                    commands.add(parseLineCommand(reader) ?: NHKeyCommand('L'))
                    next = readChar(reader)
                }
                next == 'M' -> {
                    commands.add(parseMenuMatchCommand(reader) ?: NHKeyCommand('M'))
                    next = readChar(reader)
                }
                else -> {
                    commands.add(NHKeyCommand(next))
                    next = readChar(reader)
                }
            }
        }
    }

    /**
     * 读"数字前缀 + 后面的按键"。
     *
     * 有数字、后面又跟着一个普通按键时，返回一条带 [NHCommand.numberPrefix] 的按键指令；
     * 数字后面没有按键、数字是 0（0 不当前缀，原样发给游戏由它忽略）、
     * 或者后面是 [SPECIAL_CHARS] 里的字符（数字前缀对它们不适用）时，
     * 数字按位作为普通按键返回，并把后面那个字符退回 reader 交给调用方继续处理。
     */
    private fun parseKeyWithPrefix(reader: PushbackReader): List<NHCommand> {
        val prefix = readNumber(reader)
        val key = readChar(reader)
        val special = key != null && key in SPECIAL_CHARS
        if (key == null || special || prefix <= 0) {
            key?.let { reader.unread(it.code) }
            return prefix.toString().map { NHKeyCommand(it) }
        }
        return listOf(NHKeyCommand(key, prefix))
    }

    /** 读出当前位置的连续数字（没有数字时返回 0） */
    private fun readNumber(reader: PushbackReader): Int {
        val digits = StringBuilder()
        var next = readChar(reader)
        while (next != null && next.isDigit()) {
            digits.append(next)
            next = readChar(reader)
        }
        // 非数字字符不属于数字，退回到 reader
        next?.apply { reader.unread(code) }
        return digits.toString().toIntOrNull() ?: 0
    }

    private fun readChar(reader:PushbackReader):Char? {
        val next = reader.read()
        if (next == -1)
            return null
        return next.toChar()
    }

    // extended command: #read#
    private fun parseExtendedCommand(reader:PushbackReader):NHExtendCommand {
        val extendedStr = StringBuilder("#")
        var next = readChar(reader)
        while (next != null && next != '#') {
            extendedStr.append(next)
            next = readChar(reader)
        }
        return NHExtendCommand(extendedStr.toString())
    }

    // line command: L"Elbereth"
    private fun parseLineCommand(reader:PushbackReader):NHLineCommand? {
        val extendedStr = StringBuilder()
        // 第二个字符为空或不为"则回退及返回
        var next = readChar(reader)
        if (next == null || next != '"') {
            next?.apply {
                reader.unread(code)
            }
            return null
        }
        // 第三个字符开始向后读取
        next = readChar(reader)
        while (next != null && next != '"') {
            extendedStr.append(next)
            next = readChar(reader)
        }
        if (next != '"') {
            // 不满足格式全部回退，但不回退字符L
            reader.unread(extendedStr.toString().toCharArray())
            reader.unread('"'.code)
            return null
        } else
            return NHLineCommand(extendedStr.toString())
    }

    // menu match: M"regex"
    private fun parseMenuMatchCommand(reader:PushbackReader):NHMenuCommand? {
        var next = readChar(reader)
        // 第二个字符为空或不为"则回退并返回
        if (next == null || next != '"') {
            next?.apply {
                reader.unread(code)
            }
            return null
        }
        val pattern = StringBuilder()
        next = readChar(reader)
        while (next != null && next != '"') {
            pattern.append(next)
            next = readChar(reader)
        }
        if (next != '"') {
            // 不满足格式，回退已读内容（前导的 M 不回退）
            reader.unread(pattern.toString().toCharArray())
            reader.unread('"'.code)
            return null
        }
        // key 用 ESC 兜底：万一被当成按键取走（序列里没有菜单），只是取消当前动作
        return NHMenuCommand(27.toChar(), emptyList(), 0, pattern.toString())
    }

    /** 这些字符开启特殊语法（扩展命令、行输入、菜单匹配），数字前缀对它们不适用 */
    private val SPECIAL_CHARS = listOf('#', 'L', 'M')
}
