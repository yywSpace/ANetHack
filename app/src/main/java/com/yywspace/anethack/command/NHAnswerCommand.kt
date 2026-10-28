package com.yywspace.anethack.command

/** 问题窗口的回答：key 是回答字符，answerCount 是数字回答（如"多少"这类提问的数值） */
class NHAnswerCommand(key:Char, val answerCount:Int = 1) : NHKeyCommand(key)
