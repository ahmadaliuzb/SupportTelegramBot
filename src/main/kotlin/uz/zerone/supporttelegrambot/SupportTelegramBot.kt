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

    override fun getBotUsername(): String = "session_support_bot"

    override fun getBotToken() = "6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"

    override fun onUpdateReceived(update: Update) {
        when {
            update.hasCallbackQuery() -> callbackQueryHandler.handle(update.callbackQuery, this)
            update.hasMessage() -> messageHandler.handle(update.message, this)
            update.hasEditedMessage() -> editedMessageHandler.handle(update.editedMessage, this)
        }
    }

}


