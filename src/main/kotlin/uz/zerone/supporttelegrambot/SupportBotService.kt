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
import org.telegram.telegrambots.meta.api.objects.Update
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
                    keyboardReplyMarkupHandler.findWaitingUsers(message, sender)
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
                        sender.execute(SendMessage(chatId, "You are offline"))
                    }
                }
            }

            BotStep.IN_SESSION -> {
                fileBotService.saveMessageAndFile(message, sender, false)
            }

            BotStep.FULL_SESSION -> {
                if (user.role == Role.USER) {
                    fileBotService.saveMessageAndFileAndExecute(message, false, sender)
                } else if (user.role == Role.OPERATOR) {
                    when (message.text) {
                        "Close" -> {
                            val chatId = userBotService.getChatId(message)
                            val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)
                            operator.botStep = BotStep.ONLINE
                            userRepository.save(operator)
                            keyboardReplyMarkupHandler.findWaitingUsers(message, sender)
                        }

                        "Close and OFF" -> {
                            val chatId = userBotService.getChatId(message)
                            val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)
                            operator.online = false
                            operator.botStep = BotStep.OFFLINE
                            userRepository.save(operator)
                            sender.execute(SendMessage(chatId, "You are offline"))
                        }

                        else -> {
                            fileBotService.saveMessageAndFileAndExecute(message, true, sender)
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
            chatId, "Tilni tanlang!" +
                    "Choose languagle!"
        )
        val user = userBotService.getOrCreateUser(message)
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
) : CallbackQueryHandler {
    override fun handle(callbackQuery: CallbackQuery, sender: AbsSender) {
        val user = userBotService.getOrCreateUser(callbackQuery)
        when (user.botStep) {
            BotStep.CHOOSE_LANGUAGE -> {
                sender.execute(chooseLanguage(callbackQuery))
            }

            BotStep.ASSESSMENT -> TODO()
            else -> {}
        }
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

        val sendMessage = SendMessage(callbackQuery.message.chatId.toString(), "Please share your contact")
        sendMessage.replyMarkup = markup

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
        val sendMessage = SendMessage(message.chatId.toString(), "Thank you, you can start messaging!")
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
    private val fileRepository: FileRepository
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

    fun findWaitingUsers(message: Message, sender: AbsSender) {
        val chatId = userBotService.getChatId(message)
        val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)
        val sessionsList = sessionRepository.findByActiveTrueAndOperatorIsNull()

        val oldSession = sessionRepository.findByOperatorTelegramIdAndActiveTrue(chatId)
        oldSession.active = false
        sessionRepository.save(oldSession)

        val oldUser = oldSession.user
        oldUser.botStep = BotStep.ONLINE
        userRepository.save(oldUser)

        operator.botStep = BotStep.ONLINE
        operator.online = true

        if (sessionsList.isNotEmpty()) {
            var session = sessionsList[0]
            for (lang in operator.languageList!!) {
                if (lang.languageEnum == session.user.languageList!![0].languageEnum) {
                    val messages =
                        session.id?.let { messageRepository.findBySessionIdAndSessionUserId(it, session.user.id!!) }
                    val optionalSession = sessionRepository.findByUserIdAndOperatorId(session.user.id!!, operator.id!!)
                    var deleteSessionId: Long? = null
                    if (optionalSession.isPresent) {
                        deleteSessionId = session.id!!
                        session = optionalSession.get()
                    }
                    messages?.forEach {

                        when (it.messageType) {
                            MessageType.VIDEO -> {
                                val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                                val sendVideo =
                                    SendVideo(chatId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}")))
                                sender.execute(sendVideo)
                            }

                            MessageType.AUDIO -> {
                                val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                                val sendAudio =
                                    SendAudio(chatId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}")))
                                sender.execute(sendAudio)
                            }

                            MessageType.PHOTO -> {
                                val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                                val sendPhoto =
                                    SendPhoto(chatId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}")))
                                sender.execute(sendPhoto)
                            }

                            MessageType.DOCUMENT -> {
                                val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                                val sendDocument =
                                    SendDocument(
                                        chatId,
                                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}"))
                                    )
                                sender.execute(sendDocument)
                            }

                            MessageType.TEXT -> {
                                val sendMessage = it.text?.let { text -> SendMessage(chatId, text) }
                                sender.execute(sendMessage)
                            }
                        }
                        it.session = session
                        messageRepository.save(it)
                    }
                    if (deleteSessionId != null) {
                        sessionRepository.deleteById(deleteSessionId)
                    }

//                    val optionalSession = sessionRepository.findByUserIdAndOperatorId(session.user.id!!, operator.id!!)
//                    if (optionalSession.isPresent) {
//                        session = optionalSession.get()
//                    }

                    session.operator = operator
                    session.active = true
                    sessionRepository.save(session)

                    session.user.botStep = BotStep.FULL_SESSION
                    userRepository.save(session.user)

                    operator.botStep = BotStep.FULL_SESSION
                    break
                }
            }
        }
        userRepository.save(operator)
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
) {


    fun createSession(message: Message, sender: AbsSender) {
        val user = userBotService.getOrCreateUser(message)
        user.languageList?.let {
            val operatorList =
                userRepository.findByOnlineTrueAndRoleAndLanguageListContains(
                    Role.OPERATOR.name,
                    it[0].languageEnum.name,
                    BotStep.ONLINE.name
                )

            if (operatorList.isEmpty()) {
                val session = getOrCreateSession(user, null)
                session.active = true
                sessionRepository.save(session)
                val sendMessage = SendMessage(user.telegramId, "Soon Operator will connect with you. Please wait!")
                sender.execute(sendMessage)
                user.botStep = BotStep.IN_SESSION
                fileBotService.saveMessageAndFile(message, sender, false)
            } else {
                val operator = operatorList[0]
                val session = getOrCreateSession(user, operator)
                operator.botStep = BotStep.FULL_SESSION
                user.botStep = BotStep.FULL_SESSION
                session.active = true
                sessionRepository.save(session)
                userRepository.save(operator)
                var sendMessage = SendMessage(user.telegramId, "You are connected with Operator")
                sender.execute(sendMessage)
                sendMessage = SendMessage(operator.telegramId, "You are connected with User")
                sender.execute(sendMessage)
                fileBotService.saveMessageAndFile(message, sender, true)
            }
            userRepository.save(user)
        }
    }

    fun getOrCreateSession(user: User, operator: User?): Session {
        if (operator != null) {
            operator.id?.let {
                user.id?.let { uId ->
                    val optionalSession = sessionRepository.findByUserIdAndOperatorId(uId, it)
                    if (optionalSession.isPresent) return optionalSession.get()
                }
            }
        }
        return sessionRepository.save(Session(user, operator, true, null))
    }
}

@Service
@Transactional
class FileBotService(
    private val sessionRepository: SessionRepository,
    @Lazy
    private val messageHandler: MessageHandlerImpl,
    private val userBotService: UserBotService,
    private val messageRepository: MessageRepository,
    private val fileRepository: FileRepository,
) {
    fun saveMessageAndFile(message: Message, sender: AbsSender, executive: Boolean) {
        //save file
        //for document

        val session = sessionRepository.findByUserTelegramIdAndActiveTrue(message.chatId.toString())

        if (message.hasDocument()) {
            message.document.run {
                saveFileToDisk(
                    fileName, getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.DOCUMENT)
                val file = createFile(message, fileName, ContentType.DOCUMENT)

                if (executive) {
                    val sendDocument = SendDocument(
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
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
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
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
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
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
                val file = createFile(message, fileUniqueId, ContentType.VIDEO_NOTE)

                if (executive) {
                    val sendVideoNote = SendVideoNote(
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
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
                    fileUniqueId, getFromTelegram(fileId, getBotToken(), sender)
                )

                messageHandler.createMessage(message, session, MessageType.PHOTO)
                val file = createFile(message, fileUniqueId, ContentType.PHOTO)

                if (executive) {
                    val sendPhoto = SendPhoto(
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
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
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
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
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
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
                val file = createFile(message, fileUniqueId, ContentType.PHOTO)
                if (executive) {
                    val sendSticker = SendSticker(
                        session.operator!!.telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                    )
                    sender.execute(sendSticker)
                }
            }


        } else if (message.hasText()) {
            if (executive) {
                val sendMessage = SendMessage(session.operator!!.telegramId, message.text)
                sender.execute(sendMessage)
            }
            messageHandler.createMessage(message, session, MessageType.TEXT)
        }
    }

    fun getFromTelegram(fileId: String, token: String, sender: AbsSender) = sender.execute(GetFile(fileId)).run {
        RestTemplate().getForObject<ByteArray>("https://api.telegram.org/file/bot${token}/${filePath}")
    }

    fun saveFileToDisk(fileName: String, fileContent: ByteArray) {
        val file = File(fileName)
        val fileOutputStream = FileOutputStream(file)
        fileOutputStream.write(fileContent)
        fileOutputStream.close()
    }

    fun getBotToken() = "6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"

    fun createFile(message: Message, name: String, contentType: ContentType): uz.zerone.supporttelegrambot.File {
        val fileMessage = messageRepository.findByTelegramMessageIdAndDeletedFalse(message.messageId)
        val file = File(
            name, contentType, fileMessage
        )
        return fileRepository.save(file)
    }

    fun saveMessageAndFileAndExecute(message: Message, operator: Boolean, sender: AbsSender) {

        val chatId = userBotService.getChatId(message)
        val session: Session
        val telegramId: String = if (operator) {
            session = sessionRepository.findByOperatorTelegramIdAndActiveTrue(chatId)
            session.operator?.telegramId.toString()
            session.user.telegramId
        } else {
            session = sessionRepository.findByUserTelegramIdAndActiveTrue(chatId)
            session.operator?.telegramId.toString()
        }

        when {
            message.hasDocument() -> {
                val file = message.document.run {
                    saveFileToDisk(
                        fileName, getFromTelegram(fileId, getBotToken(), sender)
                    )
                    messageHandler.createMessage(message, session, MessageType.DOCUMENT)
                    createFile(message, fileName, ContentType.DOCUMENT)
                }

                val sendDocument = SendDocument(
                    telegramId,
                    InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                )
                sender.execute(sendDocument)

            }

            message.hasVideo() -> {
                val file = message.video.run {
                    saveFileToDisk(
                        fileName, getFromTelegram(fileId, getBotToken(), sender)
                    )
                    messageHandler.createMessage(message, session, MessageType.VIDEO)
                    createFile(message, fileName, ContentType.VIDEO)
                }
                val sendVideo =
                    SendVideo(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                sender.execute(sendVideo)

            }

            message.hasAudio() -> {
                val file = message.audio.run {
                    saveFileToDisk(
                        fileName, getFromTelegram(fileId, getBotToken(), sender)
                    )
                    messageHandler.createMessage(message, session, MessageType.AUDIO)
                    createFile(message, fileName, ContentType.AUDIO)
                }
                val sendAudio =
                    SendAudio(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                sender.execute(sendAudio)

            }

            message.hasVideoNote() -> {
                val file = message.videoNote.run {
                    saveFileToDisk(
                        "${fileUniqueId}.mp4", getFromTelegram(fileId, getBotToken(), sender)
                    )

                    messageHandler.createMessage(message, session, MessageType.VIDEO_NOTE)
                    createFile(message, "${fileUniqueId}.mp4", ContentType.VIDEO_NOTE)
                }

                val sendVideoNote = SendVideoNote(
                    telegramId,
                    InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                )
                sender.execute(sendVideoNote)

            }

            message.hasPhoto() -> {
                val photos = message.photo

                photos[1].run {
                    saveFileToDisk(
                        fileUniqueId, getFromTelegram(fileId, getBotToken(), sender)
                    )

                    messageHandler.createMessage(message, session, MessageType.PHOTO)
                    val file = createFile(message, fileUniqueId, ContentType.PHOTO)

                    val sendPhoto = SendPhoto(
                        telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                    )
                    sender.execute(sendPhoto)

                }
            }

            message.hasAnimation() -> {
                val file = message.animation.run {
                    saveFileToDisk(
                        "${fileUniqueId}.gif", getFromTelegram(fileId, getBotToken(), sender)
                    )

                    messageHandler.createMessage(message, session, MessageType.ANIMATION)
                    createFile(message, "${fileUniqueId}.gif", ContentType.ANIMATION)
                }
                val sendAnimation = SendAnimation(
                    telegramId,
                    InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                )
                sender.execute(sendAnimation)

            }

            message.hasVoice() -> {
                val file = message.voice.run {
                    saveFileToDisk(
                        "${fileUniqueId}.ogg", getFromTelegram(fileId, getBotToken(), sender)
                    )

                    messageHandler.createMessage(message, session, MessageType.VOICE)
                    createFile(message, "${fileUniqueId}.ogg", ContentType.VOICE)
                }
                val sendVoice =
                    SendVoice(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                sender.execute(sendVoice)

            }

            message.hasSticker() -> {
                val file = message.sticker.run {
                    saveFileToDisk(
                        "${fileUniqueId}.webp", getFromTelegram(fileId, getBotToken(), sender)
                    )
                    messageHandler.createMessage(message, session, MessageType.STICKER)
                    createFile(message, "${fileUniqueId}.webp", ContentType.STICKER)
                }
                val sendSticker =
                    SendSticker(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                sender.execute(sendSticker)

            }

            else -> {
                val sendMessage = SendMessage(telegramId, message.text)
                sender.execute(sendMessage)
                messageHandler.createMessage(message, session, MessageType.TEXT)
            }
        }
    }
}