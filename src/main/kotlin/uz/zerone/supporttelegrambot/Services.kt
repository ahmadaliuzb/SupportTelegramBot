package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup


/**
17/07/2023 - 1:36 PM
Created by Akhmadali
 */

@Service
class MessageService() {
    fun start(update: Update) {
        val message = update.message
        if (message?.text.equals("/start")) {
            val chatId = getChatId(update)
            var sendMessage = SendMessage(chatId,"Tilni tanlang!" +
                    "Choose languagle!")
//            sendMessage.replyMarkup=
        }
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