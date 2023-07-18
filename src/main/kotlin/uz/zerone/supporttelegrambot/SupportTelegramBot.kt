package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendVideo
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
    private val fileService: FileService
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "zeroone4bot"

    override fun getBotToken() = "6044983688:AAFbj2YiwmJcT8l6IaaSVKEbEH9YKFuqrAo"

    override fun onUpdateReceived(update: Update) {
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
                if (user.role == Role.USER)
                    saveMessageAndFile(update)
                    execute(messageService.createSession(update))

            }

            BotStep.OFFLINE -> TODO()
            BotStep.IN_SESSION -> {
                if (update.hasMessage()) {
                    saveMessageAndFile(update)

                }
            }

            BotStep.ASSESSMENT -> TODO()

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
        val chatId = getChatId(update)
        messageService.createMessage(update)
        val  sendMessage = SendMessage(chatId,update.message.text)

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



}

fun saveFileToDisk(fileName: String, fileContent: ByteArray) {
    val file = File(fileName)
    val fileOutputStream = FileOutputStream(file)
    fileOutputStream.write(fileContent)
    fileOutputStream.close()
}


