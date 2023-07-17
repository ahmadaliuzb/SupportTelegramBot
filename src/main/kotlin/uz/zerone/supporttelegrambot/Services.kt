package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.springframework.beans.factory.annotation.Value
import org.telegram.telegrambots.bots.TelegramLongPollingBot


/**
17/07/2023 - 1:36 PM
Created by Akhmadali
 */

@Service
class MessageService(
) {

    fun start(update: Update) :SendMessage{
        var sendMessage = SendMessage()
class MessageService(var userRepository: UserRepository) {
    fun start(update: Update) :SendMessage{
        val message = update.message
        if (message.text.equals("/start")) {
        if (message?.text.equals("/contact")) {
            val chatId = getChatId(update)
             sendMessage = SendMessage(
                chatId,"Tilni tanlang!" +
                    "Choose languagle!")
            sendLanguageSelection(sendMessage)
            val keyboard = ReplyKeyboardMarkup()
            val contactRequestButton = KeyboardButton("Share contact")
            keyboard.keyboard = listOf(listOf(contactRequestButton)) as MutableList<KeyboardRow>
            val sendMessage = SendMessage(chatId, "Please share your contact number:")
            sendMessage.replyMarkup = keyboard
           return sendMessage
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

        return SendMessage()
    }
    fun confirmContact(update: Update):SendMessage{
        var sendMessage=SendMessage()
        if (update.hasMessage()) {
            val message = update.message
            if (update.message?.text.equals("/start")) {
                val markup = ReplyKeyboardMarkup()
                val contactRequestButton = KeyboardButton("Share contact")
                val keyboardRow = KeyboardRow()
                contactRequestButton.requestContact = true
                val rowList = mutableListOf<KeyboardRow>()
                keyboardRow.add(contactRequestButton)
                rowList.add(keyboardRow)
                markup.keyboard = rowList
                markup.selective = true
                markup.resizeKeyboard = true
                sendMessage = SendMessage(update.message?.chatId.toString(), "Please share your contact number:")
                sendMessage.replyMarkup = markup
            }
            if (message.hasContact()) {
                var user = User(
                    message.from.id.toString(),
                    message.from.userName,
                    message.contact.toString(),
                    Role.USER,
                    true,
                    Language.UZ
                )
                userRepository.save(user)
            }
        }
        return sendMessage
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