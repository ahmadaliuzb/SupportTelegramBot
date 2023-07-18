package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove

@Service
class SupportTelegramBot(

    private val messageService: MessageService
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "zeroone4bot"

    override fun getBotToken() = "6044983688:AAFbj2YiwmJcT8l6IaaSVKEbEH9YKFuqrAo"

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage()) {
            if (update.message.hasText()) {
                if (update.message.text.equals("/start")) {
                    execute(messageService.start(update))
                }
            } else if (update.message.hasContact()) {
                execute(messageService.confirmContact(update))
                deleteReplyMarkup(update.message.chatId.toString())
            }
        } else if (update.hasCallbackQuery()) {
            execute(messageService.chooseLanguage(update))
        }


    }

    fun deleteReplyMarkup(chatId: String) {
        val sendMessage = SendMessage(
            chatId,
            "\uD83D\uDCAB"
        )
        sendMessage.replyMarkup = ReplyKeyboardRemove(true)
        val message = execute(sendMessage)
        execute(DeleteMessage(chatId, message.messageId))
    }
}



