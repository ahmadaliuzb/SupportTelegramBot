package uz.zerone.supporttelegrambot

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.bots.AbsSender
import java.io.File
import java.io.FileOutputStream
import javax.transaction.Transactional

interface MessageHandler {
    fun handle(message: Message, sender: AbsSender)
}

interface CallbackQueryHandler {
    fun handle(callbackQuery: CallbackQuery, sender: AbsSender)
}

@Service
@Transactional
class MessageHandlerImpl(
    private val userBotService: UserBotService,
    private val userRepository: UserRepository,
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,
    private val sessionBotService: SessionBotService,
    private val messageRepository: MessageRepository,
    private val fileBotService: FileBotService,
    private val sessionRepository: SessionRepository
) : MessageHandler {
    override fun handle(message: Message, sender: AbsSender) {
        val user = userBotService.getOrCreateUser(message)
        when (user.botStep) {
            BotStep.START -> {
                if (message.hasText()) {
                    if (message.text.equals("/start")) {
                        sender.execute(start(message))
                    }
                }
            }

            BotStep.SHARE_CONTACT -> {
                if (message.hasContact()) {
                    sender.execute(userBotService.confirmContact(message))
                    keyboardReplyMarkupHandler.deleteReplyMarkup(message.chatId.toString(), sender)
                }
            }

            BotStep.OFFLINE -> {
                if (message.text == "ON") {
                    val sendMessage = SendMessage(user.telegramId, "✅ You are online ✅")
//                    keyboardReplyMarkupHandler.deleteReplyMarkup(message.chatId.toString(), sender)
                    sender.execute(sendMessage)
                    user.botStep = BotStep.ONLINE
                    userRepository.save(user)
                    val sessionsList = sessionRepository.findByActiveTrueAndOperatorIsNullOrderByCreatedDateAsc()
                    if (sessionsList.isNotEmpty())
                        keyboardReplyMarkupHandler.findWaitingUsers(message, sender, sessionsList)
                }
            }

            BotStep.ONLINE -> {
                if (user.role == Role.USER) {
                    sessionBotService.createSession(message, sender)
                } else if (user.role == Role.OPERATOR) {
                    if (message.text == "OFF") {
                        val chatId = userBotService.getChatId(message)
                        val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)
                        operator.online = false
                        operator.botStep = BotStep.OFFLINE
                        userRepository.save(operator)
                        val sendMessage = SendMessage(chatId, "‼ You are offline ‼")
                        sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
                        sender.execute(sendMessage)
                    }
                }
            }

            BotStep.IN_SESSION -> {
                val session = sessionBotService.findSessionByUserOrOperator(null, user.telegramId)
                fileBotService.saveMessageAndFile(message, sender, false, null, session)
            }

            BotStep.FULL_SESSION -> {
                if (user.role == Role.USER) {

                    val session = sessionBotService.findSessionByUserOrOperator(null, user.telegramId)
                    fileBotService.saveMessageAndFile(message, sender, true, session.operator!!.telegramId, session)

                } else if (user.role == Role.OPERATOR) {
                    when (message.text) {
                        "Close" -> {

                            val chatId = userBotService.getChatId(message)
                            val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)


                            operator.botStep = BotStep.ONLINE
                            userRepository.save(operator)

                            val session = sessionBotService.findSessionByUserOrOperator(operator.telegramId, null)
                            //Akh
                            val savedUser = session.user
                            savedUser.botStep = BotStep.ASSESSMENT
                            userRepository.save(savedUser)

                            sender.execute(
                                sendRateSelection(
                                    SendMessage(
                                        savedUser.telegramId,
                                        "\uD83D\uDCDD Please choose rate \uD83D\uDCDD"
                                    ),
                                    session.id
                                )
                            )

                            //Akh
                            session.active = false
                            sessionBotService.save(session)


                            val sendMessage = SendMessage(chatId, "‼ You are disconnected ‼")
                            sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
                            sender.execute(sendMessage)

                            val sessionsList =
                                sessionRepository.findByActiveTrueAndOperatorIsNullOrderByCreatedDateAsc()
                            if (sessionsList.isNotEmpty())
                                keyboardReplyMarkupHandler.findWaitingUsers(message, sender, sessionsList)
                        }

                        "Close and offline" -> {
                            val operator = userRepository.findByTelegramIdAndDeletedFalse(user.telegramId)
                            operator.online = false
                            operator.botStep = BotStep.OFFLINE
                            userRepository.save(operator)

                            val session = sessionBotService.findSessionByUserOrOperator(operator.telegramId, null)

                            //Akh
                            val savedUser = session.user
                            savedUser.botStep = BotStep.ASSESSMENT
                            userRepository.save(savedUser)

                            sender.execute(
                                sendRateSelection(
                                    SendMessage(
                                        savedUser.telegramId,
                                        "\uD83D\uDCDD Please choose rate \uD83D\uDCDD"
                                    ),
                                    session.id
                                )
                            )

                            //Akh

                            session.active = false
                            sessionBotService.save(session)


                            val sendMessage = SendMessage(user.telegramId, "‼ You are offline ‼")
                            sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
                            sender.execute(sendMessage)
                        }

                        else -> {
                            val session = sessionBotService.findSessionByUserOrOperator(user.telegramId, null)
                            fileBotService.saveMessageAndFile(message, sender, true, session.user.telegramId, session)
                        }
                    }
                }
            }

            BotStep.ASSESSMENT -> TODO()
            else -> {}
        }
    }


    fun start(message: Message): SendMessage {
        val chatId = userBotService.getChatId(message)
        val sendMessage = SendMessage(
            chatId, "🤖 Salom! Men qo'llab-quvvatlash \nbotiman. Sizga yordam bermoqchiman! \nQaysi tilda javob berasiz?"
        )
        val user = userBotService.getOrCreateUser(message)
        user.botStep = BotStep.CHOOSE_LANGUAGE
        userRepository.save(user)
        return sendLanguageSelection(sendMessage)
    }

    fun sendRateSelection(sendMessage: SendMessage, sessionId: Long?): SendMessage {
        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val inlineKeyboardButtonsRow = ArrayList<InlineKeyboardButton>()
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder()
                .text("1")
                .callbackData("1$sessionId")
                .build()
        )
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("2")
                .callbackData("2$sessionId")
                .build()
        )

        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("3")
                .callbackData("3$sessionId")
                .build()
        )
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("4")
                .callbackData("4$sessionId")
                .build()
        )
        inlineKeyboardButtonsRow.add(
            InlineKeyboardButton.builder().text("5")
                .callbackData("5$sessionId")
                .build()
        )
        val inlineKeyboardButtons = ArrayList<List<InlineKeyboardButton>>()
        inlineKeyboardButtons.add(inlineKeyboardButtonsRow)
        inlineKeyboardMarkup.keyboard = inlineKeyboardButtons

        sendMessage.replyMarkup = inlineKeyboardMarkup
        return sendMessage
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


    fun createMessage(message: Message, session: Session, messageType: MessageType) {
        val telegramId = message.from.id
        val user = userRepository.findByTelegramId(telegramId.toString())

        val messageId = message.messageId

        val saveMessage = Message(null, messageId, session, user, messageType, true, message.text)
        messageRepository.save(saveMessage)
    }

}

@Service
@Transactional
class CallbackQueryHandlerImpl(
    private val userBotService: UserBotService,
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val sessionRepository: SessionRepository,
    @Lazy
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,
) : CallbackQueryHandler {
    override fun handle(callbackQuery: CallbackQuery, sender: AbsSender) {
        val user = userBotService.getOrCreateUser(callbackQuery)
        when (user.botStep) {
            BotStep.CHOOSE_LANGUAGE -> {
                sender.execute(chooseLanguage(callbackQuery))
            }

            BotStep.ASSESSMENT -> sender.execute(chooseRate(callbackQuery))
            else -> {}
        }
    }


    fun chooseRate(callbackQuery: CallbackQuery): SendMessage {
        val data = callbackQuery.data
        val updatedSession = sessionRepository.findByIdAndDeletedFalse(data.substring(1).toLong())
        updatedSession!!.rate = data.elementAt(0).toString().toShort()

        val updatedUser = updatedSession.user

        updatedUser.botStep = BotStep.ONLINE

        //Savol berish va sozlash buttonlarini yuborish

        return  SendMessage(callbackQuery.message.chatId.toString(), "\uD83D\uDE0A Thank you \uD83D\uDE0A")
    }

    fun chooseLanguage(callbackQuery: CallbackQuery): SendMessage {
        val data = callbackQuery.data
        val user = userBotService.getOrCreateUser(callbackQuery)
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


        val sendMessage = SendMessage(callbackQuery.message.chatId.toString(), "\uD83D\uDCE9 Please share your contact \uD83D\uDCE9")
        sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)

        return sendMessage

    }
}

@Service
@Transactional
class UserBotService(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository
) {

    fun getOrCreateUser(message: Message): User {
        val chatId = getChatId(message)
        if (!userRepository.existsByTelegramIdAndDeletedFalse(chatId)) {
            return userRepository.save(
                User(
                    chatId,
                    message.from.userName,
                    null,
                    BotStep.START,
                    Role.USER,
                    true,
                    mutableListOf(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
                )
            )
        }

        return userRepository.findByTelegramIdAndDeletedFalse(chatId)
    }

    fun getOrCreateUser(callbackQuery: CallbackQuery): User {
        val chatId = getChatId(callbackQuery)
        if (!userRepository.existsByTelegramIdAndDeletedFalse(chatId)) {
            return userRepository.save(
                User(
                    chatId,
                    callbackQuery.from.userName,
                    null,
                    BotStep.START,
                    Role.USER,
                    true,
                    mutableListOf(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
                )
            )
        }

        return userRepository.findByTelegramIdAndDeletedFalse(chatId)
    }

    fun getChatId(message: Message): String = message.chatId.toString()
    fun getChatId(callbackQuery: CallbackQuery): String = callbackQuery.message.chatId.toString()

    fun confirmContact(message: Message): SendMessage {
        val sendMessage = SendMessage(message.chatId.toString(), "✅ Thank you, you can start messaging! ✅")
        val user = getOrCreateUser(message)
        user.phoneNumber = message.contact.phoneNumber
        user.botStep = BotStep.ONLINE
        userRepository.save(user)
        return sendMessage
    }


}


@Service
@Transactional
class KeyboardReplyMarkupHandler(
    private val userBotService: UserBotService,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
) {
    fun deleteReplyMarkup(chatId: String, sender: AbsSender) {
        val sendMessage = SendMessage(
            chatId,
            "\uD83D\uDCAB"
        )
        sendMessage.replyMarkup = ReplyKeyboardRemove(true)
        val message = sender.execute(sendMessage)
        sender.execute(DeleteMessage(chatId, message.messageId))
    }

    fun findWaitingUsers(message: Message, sender: AbsSender, sessionsList: MutableList<Session>) {
        val chatId = userBotService.getChatId(message)
        val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)

        val session = sessionsList[0]
        for (lang in operator.languageList!!) {
            if (lang.languageEnum == session.user.languageList!![0].languageEnum) {
                val messages = messageRepository.findBySessionIdAndSessionUserId(session.id!!, session.user.id!!)
                for (s_message in messages) {
                    operator.botStep = BotStep.FULL_SESSION
                    userRepository.save(operator)

                    when (s_message.messageType) {
                        MessageType.VIDEO -> {
                            val file = fileRepository.findByMessageId(s_message.id!!)
                            val sendVideo =
                                SendVideo(chatId, InputFile(File(file.path)))
                            sendVideo.replyMarkup = generateReplyMarkup(operator)
                            sender.execute(sendVideo)
                        }

                        MessageType.AUDIO -> {
                            val file = fileRepository.findByMessageId(s_message.id!!)
                            val sendAudio =
                                SendAudio(chatId, InputFile(File(file.path)))
                            sendAudio.replyMarkup = generateReplyMarkup(operator)
                            sender.execute(sendAudio)
                        }

                        MessageType.PHOTO -> {
                            val file = fileRepository.findByMessageId(s_message.id!!)
                            val sendPhoto =
                                SendPhoto(chatId, InputFile(File(file.path)))
                            sendPhoto.replyMarkup = generateReplyMarkup(operator)
                            sender.execute(sendPhoto)
                        }

                        MessageType.DOCUMENT -> {
                            val file = fileRepository.findByMessageId(s_message.id!!)
                            val sendDocument =
                                SendDocument(
                                    chatId,
                                    InputFile(File(file.path))
                                )
                            sendDocument.replyMarkup = generateReplyMarkup(operator)
                            sender.execute(sendDocument)
                        }

                        MessageType.TEXT -> {
                            val sendMessage = s_message.text?.let { text -> SendMessage(chatId, text) }
                            if (sendMessage != null) {
                                sendMessage.replyMarkup = generateReplyMarkup(operator)
                            }
                            sender.execute(sendMessage)
                        }

                        else -> {}
                    }
                    session.user.botStep = BotStep.FULL_SESSION
                    userRepository.save(session.user)

                    session.operator = operator
                    sessionRepository.save(session)
                }
                break
            }
        }

        userRepository.save(operator)
    }

    fun generateReplyMarkup(user: User): ReplyKeyboardMarkup {
        val markup = ReplyKeyboardMarkup()
        val rowList = mutableListOf<KeyboardRow>()
        val row1 = KeyboardRow()
        val row1Button1 = KeyboardButton()
        if (user.botStep == BotStep.FULL_SESSION && user.role == Role.OPERATOR) {
            val row2 = KeyboardRow()
            row1Button1.text = "Close"
            val row1Button2 = KeyboardButton()
            row1Button2.text = "Close and offline"
            row1.add(row1Button1)
            row1.add(row1Button2)
            rowList.add(row1)
            rowList.add(row2)
        } else if (user.botStep == BotStep.ONLINE && user.role == Role.OPERATOR) {
            row1Button1.text = "OFF"
            val row1Button2 = KeyboardButton()
            row1Button2.text = "ON"
            row1.add(row1Button1)
            row1.add(row1Button2)
            rowList.add(row1)
        } else if (user.botStep == BotStep.OFFLINE && user.role == Role.OPERATOR) {
            row1Button1.text = "ON"
            row1Button1
            row1.add(row1Button1)
            rowList.add(row1)
        } else if (user.botStep == BotStep.SHARE_CONTACT) {
            val contactRequestButton = KeyboardButton("Share contact")
            val keyboardRow = KeyboardRow()
            contactRequestButton.requestContact = true
            keyboardRow.add(contactRequestButton)
            rowList.add(keyboardRow)
        }
        markup.keyboard = rowList
        markup.selective = true
        markup.resizeKeyboard = true
        return markup
    }

}


@Service
@Transactional
class SessionBotService(
    private val userBotService: UserBotService,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    @Lazy
    private val fileBotService: FileBotService,
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,
) {


    fun createSession(message: Message, sender: AbsSender) {
        val user = userBotService.getOrCreateUser(message)
        user.languageList?.let {
            val operatorList = userRepository.findByOnlineTrueAndRoleAndLanguageListContains(
                Role.OPERATOR.name,
                it[0].languageEnum.name, BotStep.ONLINE.name
            )

            val session: Session

            if (operatorList.isEmpty()) {
                session = Session(user, null, true, null, null)
                session.active = true
                sessionRepository.save(session)

                user.botStep = BotStep.IN_SESSION
                fileBotService.saveMessageAndFile(message, sender, false, null, session)

                val sendMessage = SendMessage(user.telegramId, "\uD83D\uDD1C Soon Operator will connect with you. Please wait! \uD83D\uDD1C")
                sender.execute(sendMessage)

            } else {
                val operator = operatorList[0]

                session = Session(user, operator, true, null, null)
                session.active = true
                sessionRepository.save(session)


                operator.botStep = BotStep.FULL_SESSION
                userRepository.save(operator)

                user.botStep = BotStep.FULL_SESSION


                fileBotService.saveMessageAndFile(message, sender, true, operator.telegramId, session)

                var sendMessage = SendMessage(user.telegramId, "\uD83E\uDD16 You are connected with Operator \uD83E\uDD16")
                sender.execute(sendMessage)
                sendMessage = SendMessage(operator.telegramId, "\uD83E\uDD16 You are connected with User \uD83E\uDD16")
                sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(operator)
                sender.execute(sendMessage)
            }
            userRepository.save(user)
        }
    }

    fun findSessionByUserOrOperator(operatorId: String?, userId: String?): Session {
        return if (operatorId != null) {
            sessionRepository.findByOperatorTelegramIdAndActiveTrue(operatorId)
        } else {
            sessionRepository.findByUserTelegramIdAndActiveTrue(userId!!)
        }
    }

    fun save(session: Session) {
        sessionRepository.save(session)
    }
}

@Service
@Transactional
class FileBotService(

    @Lazy
    private val messageHandler: MessageHandlerImpl,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
) {
    fun saveMessageAndFile(
        message: Message,
        sender: AbsSender,
        executive: Boolean,
        telegramId: String?,
        session: Session
    ) {
        //save file
        //for document


        if (message.hasDocument()) {
            message.document.run {
                saveFileToDisk(
                    fileName, getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.DOCUMENT)
                val file = createFile(message, fileName, ContentType.DOCUMENT)

                if (executive) {
                    val sendDocument = SendDocument(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendDocument)
                }
            }
        }

        //for video
        else if (message.hasVideo()) {
            message.video.run {
                saveFileToDisk(
                    fileName, getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.VIDEO)
                val file = createFile(message, fileName, ContentType.VIDEO)

                if (executive) {
                    val sendVideo = SendVideo(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendVideo)
                }
            }
        }
        //for audio
        else if (message.hasAudio()) {
            message.audio.run {
                saveFileToDisk(
                    fileName, getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.AUDIO)
                val file = createFile(message, fileName, ContentType.AUDIO)

                if (executive) {
                    val sendAudio = SendAudio(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendAudio)
                }
            }

        }
        //for videoNote
        else if (message.hasVideoNote()) {
            message.videoNote.run {
                saveFileToDisk(
                    "${fileUniqueId}.mp4", getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.VIDEO_NOTE)
                val file = createFile(message, "${fileUniqueId}.mp4", ContentType.VIDEO_NOTE)

                if (executive) {
                    val sendVideoNote = SendVideoNote(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendVideoNote)
                }
            }
        }

        //for photo
        else if (message.hasPhoto()) {
            val photos = message.photo

            photos[1].run {
                saveFileToDisk(
                    "$fileUniqueId.png", getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.PHOTO)
                val file = createFile(message, "$fileUniqueId.png", ContentType.PHOTO)

                if (executive) {
                    val sendPhoto = SendPhoto(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendPhoto)
                }
            }


        }

        //for voice
        else if (message.hasAnimation()) {
            message.animation.run {
                saveFileToDisk(
                    "${fileUniqueId}.gif", getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.ANIMATION)
                val file = createFile(message, "${fileUniqueId}.gif", ContentType.ANIMATION)

                if (executive) {
                    val sendAnimation = SendAnimation(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendAnimation)
                }
            }

        }

        //for voice
        else if (message.hasVoice()) {
            message.voice.run {
                saveFileToDisk(
                    "${fileUniqueId}.ogg", getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.VOICE)
                val file = createFile(message, "${fileUniqueId}.ogg", ContentType.VOICE)

                if (executive) {
                    val sendVoice = SendVoice(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendVoice)
                }
            }

        }

        //for sticker
        else if (message.hasSticker()) {
            val sticker = message.sticker

            sticker.run {
                saveFileToDisk(
                    "${fileUniqueId}.webp", getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.STICKER)
                val file = createFile(message, "${fileUniqueId}.webp", ContentType.PHOTO)
                if (executive) {
                    val sendSticker = SendSticker(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sender.execute(sendSticker)
                }
            }


        } else if (message.hasText()) {
            if (executive) {
                val sendMessage = SendMessage(telegramId!!, message.text)
                sender.execute(sendMessage)
            }
            messageHandler.createMessage(message, session, MessageType.TEXT)
        }
    }

    fun getFromTelegram(fileId: String, token: String, sender: AbsSender) = sender.execute(GetFile(fileId)).run {
        RestTemplate().getForObject<ByteArray>("https://api.telegram.org/file/bot${token}/${filePath}")
    }

    fun saveFileToDisk(fileName: String, fileContent: ByteArray) {
        val file = File("C:\\files\\$fileName")
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.write(fileContent)
        fileOutputStream.close()
    }

    fun getBotToken() = "6044983688:AAFbj2YiwmJcT8l6IaaSVKEbEH9YKFuqrAo"

    fun createFile(message: Message, name: String, contentType: ContentType): uz.zerone.supporttelegrambot.File {
        val fileMessage = messageRepository.findByTelegramMessageIdAndDeletedFalse(message.messageId)
        val file = File(
            name,
            "C:\\files\\$name",
            contentType,
            fileMessage
        )
        return fileRepository.save(file)
    }


}