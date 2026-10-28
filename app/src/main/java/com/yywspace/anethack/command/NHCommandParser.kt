package com.yywspace.anethack.command

import java.io.PushbackReader
import java.io.StringReader
import java.lang.Integer.parseInt

object NHCommandParser {
    /*
    key command: -
    extended command: #read
    sequence command: S#engrave#-L"Elbereth"
        key command in sequence: -
        extended command in sequence: #read#
        line command in sequence: L"Elbereth"
    menu match in sequence: M"regex"  (e.g. SaM"unicorn horn")
    */
    fun parseNHCommand(command:String):List<NHCommand> {
        val commands = mutableListOf<NHCommand>()
        if(command.startsWith("#")) {
            commands.add(NHExtendCommand(command))
        } else if(command.startsWith("S")) {
            commands.addAll(parseSequenceCommands(command))
        } else {
            try {
                val key = parseInt(command).toChar()
                commands.add(NHKeyCommand(key))
            } catch (_: NumberFormatException) {
                commands.add(NHKeyCommand(command.firstOrNull()?:27.toChar()))
            }
        }
        return commands
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

    // S#engrave#-L"Elbereth"
    // TODO 适配数字前缀，涉及Menu、Question，向Sd20a这样的指令并不能达到预期
    private fun parseSequenceCommands(command:String):List<NHCommand> {
        val commands = mutableListOf<NHCommand>()
        val reader = PushbackReader(StringReader(command), command.length)
        readChar(reader) // 排除序列标识S
        var next = readChar(reader)
        while (next != null) {
            when(next) {
                '#' -> {
                    commands.add(parseExtendedCommand(reader))
                }
                'L' -> {
                    val lineCmd = parseLineCommand(reader)
                    if (lineCmd != null)
                        commands.add(lineCmd)
                    else
                        commands.add(NHKeyCommand('L'))
                }
                'M' -> {
                    val menuCmd = parseMenuMatchCommand(reader)
                    if (menuCmd != null)
                        commands.add(menuCmd)
                    else
                        commands.add(NHKeyCommand('M'))
                }
                else -> {
                    commands.add(NHKeyCommand(next))
                }
            }
            next = readChar(reader)
        }
        return commands
    }
}