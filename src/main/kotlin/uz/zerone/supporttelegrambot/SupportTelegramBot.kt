package uz.zerone.supporttelegrambot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove

@Service
class SupportTelegramBot(
    private val messageService: MessageService,
    private val userService: UserService
) : TelegramLongPollingBot() {

    override fun getBotUsername(): String = "session_support_bot"

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
                if(user.role==Role.USER)
                execute(messageService.createSession(update))
                else {
                  //
                }
            }
            BotStep.OFFLINE -> TODO()
            BotStep.IN_SESSION -> TODO()
            BotStep.ASSESSMENT -> TODO()

            //save file
            //for document

            else if (update.message.hasDocument()) {
                saveFileToDisk(
                    update.message.document.fileName, getFromTelegram(update.message.document.fileId, botToken)
                )

            }
            //for video
            else if (update.message.hasVideo()) {
                saveFileToDisk(
                    update.message.video.fileName, getFromTelegram(update.message.video.fileId, botToken)
                )
            }
            //for audio
            else if (update.message.hasAudio()) {
                saveFileToDisk(
                    update.message.audio.fileName, getFromTelegram(update.message.audio.fileId, botToken)
                )
            }
            //for videoNote
            else if (update.message.hasVideoNote()) {
                saveFileToDisk(
                    "${update.message.videoNote.fileUniqueId}.mp4",
                    getFromTelegram(update.message.videoNote.fileId, botToken)
                )
            }

            //for photo
            else if (update.message.hasPhoto()) {
                val photos = update.message.photo
                for (photo in photos) {
                    saveFileToDisk(
                        photo.fileUniqueId, getFromTelegram(photo.fileId, botToken)
                    )
                }

            }

            //for animation
            else if (update.message.hasAnimation()) {
                saveFileToDisk(
                    update.message.animation.fileUniqueId, getFromTelegram(update.message.animation.fileId, botToken)
                )
            }

            //for voice
            else if (update.message.hasVoice()) {
                saveFileToDisk(
                    "${update.message.voice.fileUniqueId}.ogg", getFromTelegram(update.message.voice.fileId, botToken)
                )
            }

            //for sticker
            else if (update.message.hasSticker()) {
                saveFileToDisk(
                    "${update.message.sticker.fileUniqueId}.webp",
                    getFromTelegram(update.message.sticker.fileId, botToken)
                )
            }


        } else if (update.hasCallbackQuery()) {
            execute(messageService.chooseLanguage(update))
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




}

fun saveFileToDisk(fileName: String, fileContent: ByteArray) {
    val file = File(fileName)
    val fileOutputStream = FileOutputStream(file)
    fileOutputStream.write(fileContent)
    fileOutputStream.close()
}


