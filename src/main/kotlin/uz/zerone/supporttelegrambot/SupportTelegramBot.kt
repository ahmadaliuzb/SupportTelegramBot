package uz.zerone.supporttelegrambot

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession


class SupportTelegramBot() : TelegramLongPollingBot(
    "6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"
) {

    override fun getBotUsername(): String {
        return "https://t.me/session_support_bot"
    }

    override fun onUpdateReceived(update: Update?) {
        TODO("Not yet implemented")
    }
}

