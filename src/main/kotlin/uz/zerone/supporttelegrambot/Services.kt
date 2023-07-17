package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton


/**
17/07/2023 - 1:36 PM
Created by Akhmadali
 */

@Service
class MessageService(
) {

    fun start(update: Update) :SendMessage{
        var sendMessage = SendMessage()
        val message = update.message
        if (message.text.equals("/start")) {
            val chatId = getChatId(update)
             sendMessage = SendMessage(
                chatId,"Tilni tanlang!" +
                    "Choose languagle!")
            sendLanguageSelection(sendMessage)
        }
         return sendMessage
    }

    private fun sendLanguageSelection(sendMessage: SendMessage) {
        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val inlineKeyboardButtonsRow = ArrayList<InlineKeyboardButton>()
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder()
                .text("O`zbek tili \uD83C\uDDFA\uD83C\uDDFF ")
                .callbackData("button1")
                .build())
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("Русский \uD83C\uDDF7\uD83C\uDDFA")
                .callbackData("button2")
                .build())

        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("English \uD83C\uDDEC\uD83C\uDDE7")
                .callbackData("button2")
                .build())
        val inlineKeyboardButtons = ArrayList<List<InlineKeyboardButton>>()
        inlineKeyboardButtons.add(inlineKeyboardButtonsRow)
        inlineKeyboardMarkup.keyboard = inlineKeyboardButtons

        sendMessage.replyMarkup = inlineKeyboardMarkup

    }
}

fun getChatId(update: Update): String {
    return if (update.hasMessage())
        update.message.chatId.toString()
    else update.callbackQuery.message?.chatId.toString()
}

class UserService() {

}

class FileService() {

}

class ContentService() {

}

class MessageCreator(){

}