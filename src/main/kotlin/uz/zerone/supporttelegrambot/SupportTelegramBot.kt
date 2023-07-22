package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class SupportTelegramBot(
    private val messageHandler: MessageHandler,
    private val callbackQueryHandler: CallbackQueryHandler,
    private val editedMessageHandler: EditedMessageHandler,

    ) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "zeroone4bot"

    override fun getBotToken() = "6044983688:AAFbj2YiwmJcT8l6IaaSVKEbEH9YKFuqrAo"

    override fun onUpdateReceived(update: Update) {
        when {
            update.hasCallbackQuery() -> callbackQueryHandler.handle(update.callbackQuery,this)
            update.hasMessage() -> messageHandler.handle(update.message, this)
            update.hasEditedMessage() -> editedMessageHandler.handle(update.editedMessage, this)
        }
    }

}


