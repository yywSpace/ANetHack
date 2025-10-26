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
            } catch (e: NumberFormatException) {
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

    // S#engrave#-L"Elbereth"
    // TODO 适配数字前缀，涉及Menu、Question
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
                else -> {
                    // 如果为数字则抛弃，待适配
                    if (!Character.isDigit(next))
                        commands.add(NHKeyCommand(next))
                }
            }
            next = readChar(reader)
        }
        return commands
    }
}