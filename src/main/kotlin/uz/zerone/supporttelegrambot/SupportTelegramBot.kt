package uz.zerone.supporttelegrambot

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow

@Service
class SupportTelegramBot(

    private val messageService: MessageService
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "session_support_bot"

    override fun getBotToken() ="6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"

    override fun onUpdateReceived(update: Update) {

       execute(messageService.confirmContact(update))
    }
}



