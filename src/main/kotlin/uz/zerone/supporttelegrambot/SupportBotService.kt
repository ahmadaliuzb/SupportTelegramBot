package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.bots.AbsSender

interface MessageHandler {
    fun handle(message: Message, sender: AbsSender)
}

interface CallbackQueryHandler {
    fun handle(callbackQuery: CallbackQuery, sender: AbsSender)
}

@Service
class MessageHandlerImpl() : MessageHandler {
    override fun handle(message: Message, sender: AbsSender) {

    }
}

@Service
class CallbackQueryHandlerImpl() : CallbackQueryHandler {
    override fun handle(callbackQuery: CallbackQuery, sender: AbsSender) {

    }
}