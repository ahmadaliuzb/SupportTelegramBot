package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton


/**
17/07/2023 - 1:36 PM
Created by Akhmadali
 */

@Service
class MessageService(
    var userRepository: UserRepository,
    var languageRepository: LanguageRepository
) {
    fun start(update: Update): SendMessage {
        val chatId = getChatId(update)
        var sendMessage = SendMessage(
            chatId, "Tilni tanlang!" +
                    "Choose languagle!"
        )
        return sendLanguageSelection(sendMessage)
    }

    fun sendLanguageSelection(sendMessage: SendMessage): SendMessage {
        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val inlineKeyboardButtonsRow = ArrayList<InlineKeyboardButton>()
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder()
                .text("O`zbek tili \uD83C\uDDFA\uD83C\uDDFF ")
                .callbackData("uzbek")
                .build()
        )
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("Русский \uD83C\uDDF7\uD83C\uDDFA")
                .callbackData("russian")
                .build()
        )

        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("English \uD83C\uDDEC\uD83C\uDDE7")
                .callbackData("english")
                .build()
        )
        val inlineKeyboardButtons = ArrayList<List<InlineKeyboardButton>>()
        inlineKeyboardButtons.add(inlineKeyboardButtonsRow)
        inlineKeyboardMarkup.keyboard = inlineKeyboardButtons

        sendMessage.replyMarkup = inlineKeyboardMarkup



        return sendMessage
    }

    fun confirmContact(update: Update): SendMessage {


        val message = update.message

        val sendMessage =
            SendMessage(update.message.chatId.toString(), "Thank you")

        val user = userRepository.findByTelegramId(message.from.id.toString())
        user.phoneNumber = message.contact.phoneNumber
        userRepository.save(user)

        return sendMessage
    }

    fun chooseLanguage(update: Update): SendMessage {
        val data = update.callbackQuery.data

        val languageList = mutableListOf<Language>()

        when (data) {
            "english" -> {
                languageList.add(languageRepository.findByLanguageEnum(LanguageEnum.ENG))
            }

            "uzbek" -> {
                languageList.add(languageRepository.findByLanguageEnum(LanguageEnum.UZ))

            }

            "russian" -> {
                languageList.add(languageRepository.findByLanguageEnum(LanguageEnum.RU))

            }
        }


        var user = User(
            update.callbackQuery.from.id.toString(),
            update.callbackQuery.from.userName,
            null,
            Role.USER,
            true,
            languageList
        )
        userRepository.save(user)

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
        val sendMessage =
            SendMessage(update.callbackQuery.message.chatId.toString(), "Please share your contact")
        sendMessage.replyMarkup = markup


        return sendMessage

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
//                val keyboard = ReplyKeyboardMarkup()
//                val contactRequestButton = KeyboardButton("Share contact")
//                keyboard.keyboard = listOf(listOf(contactRequestButton)) as MutableList<KeyboardRow>
//                val sendMessage = SendMessage(chatId, "Please share your contact number:")
//                sendMessage.replyMarkup = keyboard
//                return sendMessage
}

class FileService() {

}

class ContentService() {

}

class MessageCreator() {
}