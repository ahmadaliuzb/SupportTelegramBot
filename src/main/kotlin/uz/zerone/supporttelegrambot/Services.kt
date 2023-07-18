package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import javax.transaction.Transactional


/**
17/07/2023 - 1:36 PM
Created by Akhmadali
 */

@Service
@Transactional
class MessageService(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val userService: UserService,
    private val sessionRepository: SessionRepository,
    private val sessionService: SessionService,
    private val messageRepository: MessageRepository
) {
    fun start(update: Update): SendMessage {
        val chatId = getChatId(update)
        val sendMessage = SendMessage(
            chatId, "Tilni tanlang!" +
                    "Choose languagle!"
        )
        val user = userService.getOrCreateUser(update)
        user.botStep = BotStep.CHOOSE_LANGUAGE
        userRepository.save(user)
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
            SendMessage(update.message.chatId.toString(), "Thank you, you can start messaging!")

        val user = userService.getOrCreateUser(update)
        user.phoneNumber = message.contact.phoneNumber
        user.botStep = BotStep.ONLINE
        userRepository.save(user)

        return sendMessage
    }


    fun chooseLanguage(update: Update): SendMessage {
        val data = update.callbackQuery.data
        val user = userService.getOrCreateUser(update)
        val languageList = mutableListOf<Language>()
        when (data) {
            "english" -> {
                languageList.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.ENG))
            }

            "uzbek" -> {
                languageList.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
            }

            "russian" -> {
                languageList.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.RU))
            }
        }

        user.languageList?.set(0, languageList[0])
        user.botStep = BotStep.SHARE_CONTACT
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

        val sendMessage = SendMessage(update.callbackQuery.message.chatId.toString(), "Please share your contact")
        sendMessage.replyMarkup = markup

        return sendMessage

    }

    fun generateInlineMarkup(user: User): InlineKeyboardMarkup {
        var markup = InlineKeyboardMarkup()
        var inKeyboardButton = InlineKeyboardButton()
        if (user.botStep == BotStep.CHOOSE_LANGUAGE) {
//
        } else if (user.botStep == BotStep.CHOOSE_LANGUAGE) {
            //
        }
        return markup
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
            row1Button2.text = "Close and offline"
            row1.add(row1Button1)
            row2.add(row1Button2)
            rowList.add(row1)
            rowList.add(row2)
        } else if (user.botStep == BotStep.OFFLINE && user.role == Role.OPERATOR) {
            row1Button1.text = "OFF"
            val row1Button2 = KeyboardButton()
            row1Button2.text = "ON"
            row1.add(row1Button1)
            row1.add(row1Button2)
            rowList.add(row1)
        } else {
            row1Button1.text = "OFF"
            //CONTACT
        }
        markup.keyboard = rowList
        markup.selective = true
        markup.resizeKeyboard = true
        return markup
    }

    fun createSession(update: Update): SendMessage {
        val user = userService.getOrCreateUser(update)
        var sendMessage = SendMessage()
        user.languageList?.let {
            val operatorList =
                userRepository.findByOnlineTrueAndRoleAndLanguageListContains(Role.OPERATOR.name, it[0].languageEnum.name)
            if (operatorList.isEmpty()) {
                sessionRepository.save(Session(user, null, true, null))
                sendMessage = SendMessage(user.telegramId, "Soon Operator will connect with you. Please wait!")
            } else {
                val operator = operatorList[0]
                sessionService.getOrCreateSession(user, operator)
                operator.botStep = BotStep.IN_SESSION
                userRepository.save(user)
                sendMessage = SendMessage(user.telegramId, "You are connected with Operator")
            }
            user.botStep = BotStep.IN_SESSION
            userRepository.save(user)
        }
        return sendMessage
    }

    fun createMessage(update: Update) {
            val telegramId = update.message.from.id
            val user = userRepository.findByTelegramId(telegramId.toString())

            val messageId = update.message.messageId

            val parentMessage = update.message.replyToMessage
            if (parentMessage != null) {
                val parentDBMessage = messageRepository.findByIdAndDeletedFalse(parentMessage.messageId.toLong())
                val saveMessage =
                    Message(parentDBMessage, messageId, null, user, MessageType.TEXT, true, update.message.text)
                messageRepository.save(saveMessage)
            }

            val saveMessage = Message(null, messageId, null, user, MessageType.TEXT, true, update.message.text)
            messageRepository.save(saveMessage)
    }

}

fun getChatId(update: Update): String {
    return if (update.hasMessage())
        update.message.chatId.toString()
    else update.callbackQuery.message?.chatId.toString()
}


@Service
class UserService(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository
) {

    fun getOrCreateUser(update: Update): User {
        val chatId = getChatId(update)
        if (!userRepository.existsByTelegramIdAndDeletedFalse(chatId)) {
            return userRepository.save(
                User(
                    chatId,
                    update.message.from.id.toString(),
                    update.message.from.userName,
                    BotStep.START,
                    Role.USER,
                    true,
                    mutableListOf(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
                )
            )
        }

        return userRepository.findByTelegramIdAndDeletedFalse(chatId)

    }
}


@Service
class SessionService(
    private val sessionRepository: SessionRepository
) {
    fun getOrCreateSession(user: User, operator: User): Session {
        operator.id?.let {
            user.id?.let { uId ->
                val optionalSession = sessionRepository.findByUserIdAndOperatorId(uId, it)
                if (optionalSession.isPresent) return optionalSession.get()
            }
        }
        return sessionRepository.save(Session(user, operator, true, null))
    }
}


@Service
class FileService(
    private val fileRepository: FileRepository,
    private val messageRepository: MessageRepository,
    private val messageService: MessageService
) {
    fun createFile(update: Update, name: String, contentType: ContentType) {
        val message =
            messageRepository.findByTelegramMessageIdAndDeletedFalse(update.message.messageId)

        val file = File(
            name, contentType, message
        )
        fileRepository.save(file)
    }

}
