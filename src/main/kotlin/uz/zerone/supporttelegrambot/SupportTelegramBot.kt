package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation
import org.telegram.telegrambots.meta.api.methods.send.SendAudio
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.send.SendSticker
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
import org.telegram.telegrambots.meta.api.methods.send.SendVideoNote
import org.telegram.telegrambots.meta.api.methods.send.SendVoice
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import java.io.File
import java.io.FileOutputStream

@Service
class SupportTelegramBot(
    private val messageService: MessageService,
    private val userService: UserService,
    private val fileService: FileService,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val messageHandler: MessageHandler,
    private val fileRepository: FileRepository,
    private val callbackQueryHandler: CallbackQueryHandler,
    private val sessionService: SessionService
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "session_support_bot"

    override fun getBotToken() = "6005965806:AAGx17eBrfH2z2DvIeYu2WZPe6d_BUfnJ4s"

    override fun onUpdateReceived(update: Update) {
//        when{
//            update.hasCallbackQuery() -> callbackQueryHandler.handle(update.callbackQuery,this)
//            update.hasMessage() -> messageHandler.handle(update.message,this)
//        }

        val user = userService.getOrCreateUser(update)

        when (user.botStep) {
            BotStep.START -> {
                if (update.hasMessage()) {
                    if (update.message.hasText()) {
                        if (update.message.text.equals("/start")) {
                            execute(messageService.start(update))
                        }
                    }
                }
            }

            BotStep.CHOOSE_LANGUAGE -> {
                if (update.hasCallbackQuery()) {
                    execute(messageService.chooseLanguage(update))
                }
            }

            BotStep.SHARE_CONTACT -> {
                if (update.hasMessage()) {
                    if (update.message.hasContact()) {
                        execute(messageService.confirmContact(update))
                        deleteReplyMarkup(update.message.chatId.toString())
                    }
                }
            }

            BotStep.ONLINE -> {
                if (user.role == Role.USER) {
                    saveMessageAndFile(update)
                    createSession(update)
                } else if (user.role == Role.OPERATOR) {
                    if (update.hasMessage() && update.message.text == "OFF") {
                        val chatId = getChatId(update)
                        val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)
                        operator.online = false
                        operator.botStep = BotStep.OFFLINE
                        userRepository.save(operator)
                        execute(SendMessage(chatId, "You are offline"))
                    }
                }
            }

            BotStep.OFFLINE -> {
                if (update.hasMessage() && update.message.text == "OFF") {
                    findWaitingUsers(update)
                }
            }

            BotStep.IN_SESSION -> {
                if (update.hasMessage()) {
                    saveMessageAndFile(update)
                }
            }

            BotStep.FULL_SESSION -> {
                if (update.hasMessage()) {
                    if (user.role == Role.USER) {
                        saveMessageAndFileAndExecute(update,false)
                    }
                    else if (user.role == Role.OPERATOR) {
                        when (update.message.text) {
                            "Close" -> {
                                findWaitingUsers(update)
                            }
                            "Close and OFF" -> {
                                val chatId = getChatId(update)
                                val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)
                                operator.online = false
                                operator.botStep = BotStep.OFFLINE
                                userRepository.save(operator)
                                execute(SendMessage(chatId, "You are offline"))
                            }
                            else -> {
                                saveMessageAndFileAndExecute(update,true)
                            }
                        }
                    }
                }
            }

            BotStep.ASSESSMENT -> TODO()

        }

    }

    fun findWaitingUsers(update: Update) {
        val chatId = getChatId(update)
        val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)
        val sessionsList = sessionRepository.findByActiveTrueAndOperatorIsNull()

        operator.botStep = BotStep.ONLINE
        operator.online = true

        for (session in sessionsList) {
            if (operator.languageList?.contains(session.user.languageList?.get(0)) == true) {
                val messages = session.id?.let { messageRepository.findBySessionIdAndDeletedFalse(it) }
                messages?.forEach {
                    when (it.messageType) {
                        MessageType.VIDEO -> {
                            val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                            val sendVideo =
                                SendVideo(chatId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}")))
                            execute(sendVideo)
                        }

                        MessageType.AUDIO -> {
                            val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                            val sendAudio =
                                SendAudio(chatId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}")))
                            execute(sendAudio)
                        }

                        MessageType.PHOTO -> {
                            val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                            val sendPhoto =
                                SendPhoto(chatId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}")))
                            execute(sendPhoto)
                        }

                        MessageType.DOCUMENT -> {
                            val file = it.id?.let { id -> fileRepository.findByMessageId(id) }
                            val sendDocument =
                                SendDocument(chatId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file?.name}")))
                            execute(sendDocument)
                        }

                        MessageType.TEXT -> {
                            val sendMessage = it.text?.let { text -> SendMessage(chatId, text) }
                            execute(sendMessage)
                        }
                    }
                }
                operator.botStep = BotStep.FULL_SESSION
                break
            }
        }
        userRepository.save(operator)
    }

    private fun saveMessageAndFileAndExecute(update: Update,operator:Boolean) {

        val chatId = getChatId(update)
        val session = sessionRepository.findByUserTelegramIdAndActiveTrue(chatId)
        val telegramId:String = if (operator) session.user.telegramId
        else session.operator?.telegramId.toString()
        messageService.createMessage(update)

        when {
            update.message.hasDocument() -> {
                val file = update.message.document.run {
                    saveFileToDisk(
                        fileName, getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, fileName, ContentType.DOCUMENT)
                }
                val sendDocument = SendDocument(
                    telegramId,
                    InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                )
                execute(sendDocument)
            }

            update.message.hasVideo() -> {
                val file = update.message.video.run {
                    saveFileToDisk(
                        fileName, getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, fileName, ContentType.VIDEO)
                }
                val sendVideo =
                    SendVideo(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                execute(sendVideo)
            }

            update.message.hasAudio() -> {
                val file = update.message.audio.run {
                    saveFileToDisk(
                        fileName, getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, fileName, ContentType.AUDIO)
                }
                val sendAudio =
                    SendAudio(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                execute(sendAudio)
            }

            update.message.hasVideoNote() -> {
                val file = update.message.videoNote.run {
                    saveFileToDisk(
                        "${fileUniqueId}.mp4", getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, "${fileUniqueId}.mp4", ContentType.VIDEO_NOTE)
                }
                val sendVideoNote = SendVideoNote(
                    telegramId,
                    InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                )
                execute(sendVideoNote)
            }

            update.message.hasPhoto() -> {
                val photos = update.message.photo
                for (photo in photos) {
                    val file = photo.run {
                        saveFileToDisk(
                            fileUniqueId, getFromTelegram(fileId, botToken)
                        )
                        fileService.createFile(update, fileUniqueId, ContentType.PHOTO)
                    }
                    val sendPhoto = SendPhoto(
                        telegramId,
                        InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                    )
                    execute(sendPhoto)
                }
            }

            update.message.hasAnimation() -> {
                val file = update.message.animation.run {
                    saveFileToDisk(
                        "${fileUniqueId}.gif", getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, "${fileUniqueId}.gif", ContentType.ANIMATION)
                }
                val sendAnimation = SendAnimation(
                    telegramId,
                    InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}"))
                )
                execute(sendAnimation)
            }

            update.message.hasVoice() -> {
                val file = update.message.voice.run {
                    saveFileToDisk(
                        "${fileUniqueId}.ogg", getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, "${fileUniqueId}.ogg", ContentType.VOICE)
                }

                val sendVoice =
                    SendVoice(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                execute(sendVoice)
            }

            update.message.hasSticker() -> {
                val file = update.message.voice.run {
                    saveFileToDisk(
                        "${fileId}.ogg", getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, "${fileId}.ogg", ContentType.VOICE)
                }
                val sendSticker =
                    SendSticker(telegramId, InputFile(File("D:\\Kotlin\\SupportTelegramBot\\${file.name}")))
                execute(sendSticker)
            }

            else -> {
                val sendMessage = SendMessage(telegramId, update.message.text)
                execute(sendMessage)
            }
        }
    }


    fun deleteReplyMarkup(chatId: String) {
        val sendMessage = SendMessage(
            chatId,
            "\uD83D\uDCAB"
        )
        sendMessage.replyMarkup = ReplyKeyboardRemove(true)
        val message = execute(sendMessage)
        execute(DeleteMessage(chatId, message.messageId))
    }

    fun getFromTelegram(fileId: String, token: String) = execute(GetFile(fileId)).run {
        RestTemplate().getForObject<ByteArray>("https://api.telegram.org/file/bot${token}/${filePath}")
    }

    fun saveMessageAndFile(update: Update) {
        //save file
        //for document

        messageService.createMessage(update)

        if (update.message.hasDocument()) {
            update.message.document.run {
                saveFileToDisk(
                    fileName, getFromTelegram(fileId, botToken)
                )
                fileService.createFile(update, fileName, ContentType.DOCUMENT)
            }
        }

        //for video
        else if (update.message.hasVideo()) {
            update.message.video.run {
                saveFileToDisk(
                    fileName, getFromTelegram(fileId, botToken)
                )
                fileService.createFile(update, fileName, ContentType.VIDEO)
            }
        }
        //for audio
        else if (update.message.hasAudio()) {
            update.message.audio.run {
                saveFileToDisk(
                    fileName, getFromTelegram(fileId, botToken)
                )
                fileService.createFile(update, fileName, ContentType.AUDIO)
            }
        }
        //for videoNote
        else if (update.message.hasVideoNote()) {
            update.message.videoNote.run {
                saveFileToDisk(
                    "${fileUniqueId}.mp4", getFromTelegram(fileId, botToken)
                )
                fileService.createFile(update, fileUniqueId, ContentType.VIDEO_NOTE)
            }
        }

        //for photo
        else if (update.message.hasPhoto()) {
            val photos = update.message.photo
            for (photo in photos) {
                photo.run {
                    saveFileToDisk(
                        fileUniqueId, getFromTelegram(fileId, botToken)
                    )
                    fileService.createFile(update, fileUniqueId, ContentType.PHOTO)
                }
            }

        }

        //for animation
        else if (update.message.hasAnimation()) {
            update.message.animation.run {
                saveFileToDisk(
                    "${fileUniqueId}.gif", getFromTelegram(fileId, botToken)
                )
                fileService.createFile(update, fileUniqueId, ContentType.ANIMATION)
            }
        }

        //for voice
        else if (update.message.hasVoice()) {
            update.message.voice.run {
                saveFileToDisk(
                    "${fileUniqueId}.ogg", getFromTelegram(fileId, botToken)
                )
                fileService.createFile(update, fileUniqueId, ContentType.VOICE)
            }
        }

        //for sticker
        else if (update.message.hasSticker()) {
            update.message.sticker.run {
                saveFileToDisk(
                    "${fileUniqueId}.webp", getFromTelegram(fileId, botToken)
                )
                fileService.createFile(update, fileUniqueId, ContentType.STICKER)
            }
        }
    }

    fun createSession(update: Update) {
        messageService.createMessage(update)
        val user = userService.getOrCreateUser(update)
        user.languageList?.let {
            val operatorList =
                userRepository.findByOnlineTrueAndRoleAndLanguageListContains(
                    Role.OPERATOR.name,
                    it[0].languageEnum.name
                )
            if (operatorList.isEmpty()) {
                sessionRepository.save(Session(user, null, true, null))
                val sendMessage = SendMessage(user.telegramId, "Soon Operator will connect with you. Please wait!")
                execute(sendMessage)
                user.botStep = BotStep.IN_SESSION
            } else {
                val operator = operatorList[0]
                sessionService.getOrCreateSession(user, operator)
                operator.botStep = BotStep.FULL_SESSION
                user.botStep = BotStep.FULL_SESSION
                userRepository.save(operator)
                var sendMessage = SendMessage(user.telegramId, "You are connected with Operator")
                execute(sendMessage)
                sendMessage = SendMessage(operator.telegramId,"You are connected with User")
                execute(sendMessage)
                sendMessage = SendMessage(operator.telegramId,update.message.text)
                execute(sendMessage)
            }
            userRepository.save(user)
        }
    }

}

fun saveFileToDisk(fileName: String, fileContent: ByteArray) {
    val file = File(fileName)
    val fileOutputStream = FileOutputStream(file)
    fileOutputStream.write(fileContent)
    fileOutputStream.close()
}


