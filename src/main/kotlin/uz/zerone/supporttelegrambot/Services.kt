package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow


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
            var sendMessage = SendMessage(
                chatId, "Tilni tanlang!" +
                        "Choose languagle!"
            )
//            sendMessage.replyMarkup=
        }
    }

    fun generateInlineMarkup(user: User): InlineKeyboardMarkup {
        var markup = InlineKeyboardMarkup()
        var inKeyboardButton = InlineKeyboardButton()
        if (user.botStep == BotStep.CHOOSE_LANGUAGE) {

        } else if (user.botStep == BotStep.CHOOSE_LANGUAGE) {
        }
        return  markup
    }

    fun generateReplyMarkup(user: User): ReplyKeyboardMarkup {
        val markup = ReplyKeyboardMarkup()
        val rowList = mutableListOf<KeyboardRow>()
        val row1 = KeyboardRow()
        val row1Button1 = KeyboardButton()
        if (user.botStep == BotStep.ONLINE && user.role == Role.OPERATOR) {
            val row2 = KeyboardRow()
            row1Button1.text = "Close"
            val row1Button2 = KeyboardButton()
            row1Button2.text="Close and offline"
            row1.add(row1Button1)
            row2.add(row1Button2)
            rowList.add(row1)
            rowList.add(row2)
        } else if (user.botStep == BotStep.OFFLINE && user.role == Role.OPERATOR) {
            row1Button1.text = "OFF"
            val row1Button2 = KeyboardButton()
            row1Button2.text="ON"
            row1.add(row1Button1)
            row1.add(row1Button2)
            rowList.add(row1)
        } else {
            row1Button1.text = "OFF"
            //CONTACT
        }
        markup.keyboard=rowList
        markup.selective=true
        markup.resizeKeyboard = true
        return markup
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

class MessageCreator() {

}