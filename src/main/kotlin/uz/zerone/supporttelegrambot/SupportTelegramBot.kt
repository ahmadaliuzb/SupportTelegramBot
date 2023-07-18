package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove

@Service
class SupportTelegramBot(
    private val messageService: MessageService,
    private val userService: UserService
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "session_support_bot"

    override fun getBotToken() = "6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"

    override fun onUpdateReceived(update: Update) {
        val user = userService.getOrCreateUser(update)
        when (user.botStep) {
            BotStep.START -> {
                if (update.hasMessage()) {
                    if (update.message.hasText()) {
                        if (update.message.text.equals("/start")) {
                            execute(messageService.start(update))
                        }
                    }
                }
            }
            BotStep.CHOOSE_LANGUAGE -> {
                if (update.hasCallbackQuery()) {
                    execute(messageService.chooseLanguage(update))
                }
            }
            BotStep.SHARE_CONTACT -> {
                if (update.hasMessage()) {
                    if (update.message.hasContact()) {
                        execute(messageService.confirmContact(update))
                        deleteReplyMarkup(update.message.chatId.toString())
                    }
                }
            }
            BotStep.ONLINE -> {
                if(user.role==Role.USER)
                execute(messageService.createSession(update))
                else {
                  //
                }
            }
            BotStep.OFFLINE -> TODO()
            BotStep.IN_SESSION -> TODO()
            BotStep.ASSESSMENT -> TODO()
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



