package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Component
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

@Component
class SupportTelegramBot: TelegramLongPollingBot() {
    override fun getBotToken(): String = "6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"

    override fun getBotUsername() :String = "session_support_bot"

    override fun onUpdateReceived(update: Update) {
        val sendMessage = SendMessage()
        val chatId = update.message.chatId
        sendMessage.chatId = chatId.toString()

        sendMessage.text = "Hello world"

        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val inlineKeyboardButtonsRow = ArrayList<InlineKeyboardButton>()
        inlineKeyboardButtonsRow.add(InlineKeyboardButton.builder()
            .text("Button1")
            .callbackData("button1")
            .build())
        inlineKeyboardButtonsRow.add(InlineKeyboardButton.builder().text("Button")
            .callbackData("button2")
            .build())
        val inlineKeyboardButtons = ArrayList<List<InlineKeyboardButton>>()
        inlineKeyboardButtons.add(inlineKeyboardButtonsRow)
        inlineKeyboardMarkup.keyboard = inlineKeyboardButtons

        sendMessage.replyMarkup = inlineKeyboardMarkup
        execute(sendMessage)

    }
}

