package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class SupportTelegramBot: TelegramLongPollingBot() {
    override fun getBotToken(): String = "6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"

    override fun getBotUsername() :String = " session_support_bot"

    override fun onUpdateReceived(update: Update) {
        val text = update.message.text
        val chatId = update.message.chatId
        println(text)
        println(chatId)
        println(update.message)

    }
}

