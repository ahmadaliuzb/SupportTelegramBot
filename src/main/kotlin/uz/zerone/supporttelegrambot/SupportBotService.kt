package uz.zerone.supporttelegrambot

import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
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
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.transaction.Transactional


interface MessageHandler {
    fun handle(message: Message, sender: AbsSender)
}

interface CallbackQueryHandler {
    fun handle(callbackQuery: CallbackQuery, sender: AbsSender)

}


interface EditedMessageHandler {
    fun handle(message: Message, sender: AbsSender)
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
    private val sessionRepository: SessionRepository,
    private val messageSourceService: MessageSourceService,
    private val languageService: LanguageService,
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
                }
            }

            BotStep.SHOW_MENU -> {
                user.isBlocked = false
                val sendMessage: SendMessage
                if (message.text == messageSourceService.getMessage(
                        LocalizationTextKey.QUESTION_BUTTON,
                        languageService.getLanguageOfUser(message.from.id)
                    )
                ) {

                    user.botStep = BotStep.ONLINE
                    userRepository.save(user)
                    sendMessage = SendMessage(
                        user.telegramId, messageSourceService.getMessage(
                            LocalizationTextKey.START_MESSAGING_MESSAGE,
                            languageService.getLanguageOfUser(message.from.id)
                        )
                    )
                    sender.execute(sendMessage)
                    keyboardReplyMarkupHandler.deleteReplyMarkup(message.chatId.toString(), sender)
                } else if (message.text == messageSourceService.getMessage(
                        LocalizationTextKey.SETTINGS_BUTTON,
                        languageService.getLanguageOfUser(message.from.id)
                    )
                ) {
                    user.botStep = BotStep.CHOOSE_LANGUAGE
                    userRepository.save(user)
                    sendMessage = SendMessage(
                        user.telegramId, messageSourceService.getMessage(
                            LocalizationTextKey.CHOOSE_LANGUAGE_MESSAGE,
                            languageService.getLanguageOfUser(message.from.id)
                        )
                    )
                    sender.execute(sendLanguageSelection(sendMessage))
                    keyboardReplyMarkupHandler.deleteReplyMarkup(user.telegramId, sender)
                } else {
                    sendMessage = SendMessage(
                        user.telegramId, messageSourceService.getMessage(
                            LocalizationTextKey.CHOOSE_SECTION_MESSAGE,
                            languageService.getLanguageOfUser(message.from.id)
                        )
                    )
                    sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
                    sender.execute(sendMessage)
                }
            }

            BotStep.OFFLINE -> {
                if (message.text == messageSourceService.getMessage(
                        LocalizationTextKey.ONLINE_BUTTON,
                        Language(LanguageEnum.UZ)
                    ) || message.text == messageSourceService.getMessage(
                        LocalizationTextKey.ONLINE_BUTTON,
                        Language(LanguageEnum.RU)
                    ) || message.text == messageSourceService.getMessage(
                        LocalizationTextKey.ONLINE_BUTTON,
                        Language(LanguageEnum.ENG)
                    )
                ) {

                    user.botStep = BotStep.ONLINE
                    userRepository.save(user)

                    val sendMessage = SendMessage(
                        user.telegramId,
                        messageSourceService.getMessage(
                            LocalizationTextKey.ONLINE_MESSAGE,
                            languageService.getLanguageOfUser(message.from.id)
                        )
                    )
                    sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
                    sender.execute(sendMessage)

                    val sessionsList = sessionRepository.findByActiveTrueAndOperatorIsNullOrderByCreatedDateAsc()
                    if (sessionsList.isNotEmpty())
                        keyboardReplyMarkupHandler.findWaitingUsers(message, sender, sessionsList)
                }
            }

            BotStep.ONLINE -> {
                if (user.role == Role.USER) {
                    sessionBotService.createSession(message, sender)
                } else if (user.role == Role.OPERATOR) {
                    if (message.text == messageSourceService.getMessage(
                            LocalizationTextKey.OFFLINE_BUTTON,
                            languageService.getLanguageOfUser(message.from.id)
                        )
                    ) {
                        val chatId = userBotService.getChatId(message)
                        val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)

                        operator.botStep = BotStep.OFFLINE
                        userRepository.save(operator)

                        val sendMessage = SendMessage(
                            chatId, messageSourceService.getMessage(
                                LocalizationTextKey.OFFLINE_MESSAGE,
                                languageService.getLanguageOfUser(message.from.id)
                            )
                        )
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
                        messageSourceService.getMessage(
                            LocalizationTextKey.OPERATOR_CLOSE_BUTTON,
                            languageService.getLanguageOfUser(message.from.id)
                        ),
                        -> {

                            val chatId = userBotService.getChatId(message)
                            val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)


                            operator.botStep = BotStep.ONLINE
                            userRepository.save(operator)

                            val session = sessionBotService.findSessionByUserOrOperator(operator.telegramId, null)
                            //Akh


                            val savedUser = session.user

                            if (!savedUser.isBlocked) {
                                savedUser.botStep = BotStep.ASSESSMENT
                                userRepository.save(savedUser)

                                sender.execute(
                                    sendRateSelection(
                                        SendMessage(
                                            savedUser.telegramId,
                                            messageSourceService.getMessage(
                                                LocalizationTextKey.CHOOSE_RATE_MESSAGE,
                                                languageService.getLanguageOfUser(savedUser.telegramId.toLong())
                                            )
                                        ),
                                        session.id
                                    )
                                )
                            }
                            //Akh
                            session.active = false
                            sessionBotService.save(session)


                            val sendMessage = SendMessage(
                                chatId, messageSourceService.getMessage(
                                    LocalizationTextKey.DISCONNECTED_MESSAGE,
                                    languageService.getLanguageOfUser(message.from.id)
                                )
                            )
                            sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
                            sender.execute(sendMessage)

                            val sessionsList =
                                sessionRepository.findByActiveTrueAndOperatorIsNullOrderByCreatedDateAsc()
                            if (sessionsList.isNotEmpty())
                                keyboardReplyMarkupHandler.findWaitingUsers(message, sender, sessionsList)
                        }

                        messageSourceService.getMessage(
                            LocalizationTextKey.OPERATOR_CLOSE_AND_OFFLINE_BUTTON,
                            languageService.getLanguageOfUser(message.from.id)
                        ),
                        -> {
                            val operator = userRepository.findByTelegramIdAndDeletedFalse(user.telegramId)
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
                                        messageSourceService.getMessage(
                                            LocalizationTextKey.CHOOSE_RATE_MESSAGE,
                                            languageService.getLanguageOfUser(message.from.id)
                                        )
                                    ),
                                    session.id
                                )
                            )

                            //Akh

                            session.active = false
                            sessionBotService.save(session)


                            val sendMessage = SendMessage(
                                user.telegramId, messageSourceService.getMessage(
                                    LocalizationTextKey.OFFLINE_MESSAGE,
                                    languageService.getLanguageOfUser(message.from.id)
                                )
                            )
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

        }
    }


    fun start(message: Message): SendMessage {
        val chatId = userBotService.getChatId(message)
        val sendMessage = SendMessage(
            chatId, "🤖 Qaysi tilda javob berasiz?" +
                    "\n\uD83E\uDD16 На каком языке вы отвечаете?" +
                    "\n\uD83E\uDD16 What language do you answer in?"

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


        val button = InlineKeyboardButton.builder().text("Uzbek\uD83C\uDDFA\uD83C\uDDFF").callbackData("uzbek").build()
        val button1 =
            InlineKeyboardButton.builder().text("Russian\uD83C\uDDF7\uD83C\uDDFA").callbackData("russian").build()
        val button2 =
            InlineKeyboardButton.builder().text("English\uD83C\uDDEC\uD83C\uDDE7").callbackData("english").build()


        inlineKeyboardButtonsRow.add(button)
        inlineKeyboardButtonsRow.add(button1)
        inlineKeyboardButtonsRow.add(button2)

        val inlineKeyboardButtons = ArrayList<List<InlineKeyboardButton>>()
        inlineKeyboardButtons.add(inlineKeyboardButtonsRow)
        inlineKeyboardMarkup.keyboard = inlineKeyboardButtons
        sendMessage.replyMarkup = inlineKeyboardMarkup
        return sendMessage
    }


    fun createMessage(message: Message, session: Session, messageType: MessageType): Int {
        val telegramId = message.from.id

        val user = userRepository.findByTelegramId(telegramId.toString())

        val messageId = message.messageId

        val saveMessage =
            Message(
                messageId, session, user, messageType, true, message.text, message.isReply,
                if (message.isReply) message.replyToMessage.messageId else null
            )
        messageRepository.save(saveMessage)
        return messageId
    }

}

@Service
@Transactional
class CallbackQueryHandlerImpl(
    @Lazy
    private val userBotService: UserBotService,
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val sessionRepository: SessionRepository,
    private val languageService: LanguageService,
    private val messageSourceService: MessageSourceService,
    @Lazy
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,
) : CallbackQueryHandler {
    override fun handle(callbackQuery: CallbackQuery, sender: AbsSender) {
        val user = userBotService.getOrCreateUser(callbackQuery)
        when (user.botStep) {
            BotStep.CHOOSE_LANGUAGE -> {
                sender.execute(chooseLanguage(callbackQuery, sender))
            }


            BotStep.ASSESSMENT -> sender.execute(chooseRate(callbackQuery, sender))
            else -> {}
        }
    }


    fun chooseRate(callbackQuery: CallbackQuery, sender: AbsSender): SendMessage {
        val data = callbackQuery.data
        val updatedSession = sessionRepository.findByIdAndDeletedFalse(data.substring(1).toLong())
        updatedSession!!.rate = data.elementAt(0).toString().toShort()

        val updatedUser = updatedSession.user

        updatedUser.botStep = BotStep.SHOW_MENU
        userRepository.save(updatedUser)
        val chatId = userBotService.getChatId(callbackQuery)

        sender.execute(
            SendMessage(
                callbackQuery.message.chatId.toString(), messageSourceService.getMessage(
                    LocalizationTextKey.THANK_MESSAGE,
                    languageService.getLanguageOfUser(callbackQuery.from.id)
                )
            )
        )

        val sendMessage = SendMessage(
            chatId, messageSourceService.getMessage(
                LocalizationTextKey.CHOOSE_SECTION_MESSAGE,
                languageService.getLanguageOfUser(callbackQuery.from.id)
            )
        )
        sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(updatedUser)
        keyboardReplyMarkupHandler.deleteInlineReplyMarkup(
            callbackQuery.message.chatId,
            callbackQuery.message.messageId,
            sender
        )
        return sendMessage

    }

    fun chooseLanguage(callbackQuery: CallbackQuery, sender: AbsSender): SendMessage {
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
        val sendMessage: SendMessage
        if (user.phoneNumber != null) {
            user.botStep = BotStep.SHOW_MENU
            userRepository.save(user)
            sendMessage = SendMessage(
                user.telegramId, messageSourceService.getMessage(
                    LocalizationTextKey.CHOOSE_SECTION_MESSAGE,
                    languageService.getLanguageOfUser(callbackQuery.from.id)
                )
            )
            sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
            keyboardReplyMarkupHandler.deleteInlineReplyMarkup(
                callbackQuery.message.chatId,
                callbackQuery.message.messageId,
                sender
            )
        } else {
            user.botStep = BotStep.SHARE_CONTACT
            userRepository.save(user)
            sendMessage = SendMessage(
                callbackQuery.message.chatId.toString(), messageSourceService.getMessage(
                    LocalizationTextKey.SHARE_CONTACT_MESSAGE,
                    languageService.getLanguageOfUser(callbackQuery.from.id)
                )
            )
            sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
            keyboardReplyMarkupHandler.deleteInlineReplyMarkup(
                callbackQuery.message.chatId,
                callbackQuery.message.messageId,
                sender
            )
        }
        return sendMessage

    }
}

@Service
@Transactional
class EditedMessageHandlerImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val sessionRepository: SessionRepository,
    private val botMessageRepository: BotMessageRepository,
    private val userBotService: UserBotService

) : EditedMessageHandler {
    override fun handle(editedMessage: Message, sender: AbsSender) {

        val editedUser = userRepository.findByTelegramId(editedMessage.from.id.toString())

        when (editedUser.botStep) {
            BotStep.IN_SESSION -> {
                val editedText: String = editedMessage.text

                // Muharrirlik (edit) qilingan xabarni bazada saqlash, o'zgartirishlarni bajarish yoki qaysi kerak bo'lsa ham
                // sizning loyihangizga qarab kerakli algoritmlarni qo'shing
                // Masalan: Bazada xabarni saqlash yoki javob qilib yuborish
                // ...

                // Muharrirlik qilingan xabarni xabar qilib qayta yuboring


                val editingMessage =
                    messageRepository.findByTelegramMessageIdAndDeletedFalse(editedMessage.messageId)
                editingMessage.text = editedText
                editingMessage.edited = true
                messageRepository.save(editingMessage)
            }

            BotStep.FULL_SESSION -> {

                val user = userBotService.getOrCreateUser(editedMessage)


                val editedText: String = editedMessage.text

                val editingMessage =
                    messageRepository.findByTelegramMessageIdAndDeletedFalse(editedMessage.messageId)
                editingMessage.text = editedText
                editingMessage.edited = true
                messageRepository.save(editingMessage)

                val chatId: String
                if (user.role == Role.USER) {
                    chatId = editingMessage.session.operator!!.telegramId
                } else {
                    chatId = editingMessage.session.user.telegramId
                }

                val botMessage =
                    botMessageRepository.findByReceivedMessageId(editingMessage.telegramMessageId)

                // Muharrirlik (edit) qilingan xabarni bazada saqlash, o'zgartirishlarni bajarish yoki qaysi kerak bo'lsa ham
                // sizning loyihangizga qarab kerakli algoritmlarni qo'shing
                // Masalan: Bazada xabarni saqlash yoki javob qilib yuborish
                // ...

                // Muharrirlik qilingan xabarni xabar qilib qayta yuboring

                try {
                    val editMessageText = EditMessageText()
                    editMessageText.messageId = botMessage?.telegramMessageId
                    editMessageText.chatId = chatId

                    editMessageText.text = "${editingMessage.text!!} ♻"


                    sender.execute(editMessageText)
                } catch (e: TelegramApiException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }


    }

}


@Service
@Transactional
class UserBotService(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    @Lazy
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,

    private val languageService: LanguageService,
    private val messageSourceService: MessageSourceService,
) {

    fun getOrCreateUser(message: Message): User {
        val chatId = getChatId(message)
        if (!userRepository.existsByTelegramIdAndDeletedFalse(chatId)) {
            return userRepository.save(
                User(
                    message.from.firstName,
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
                    callbackQuery.from.firstName,
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
        val user = getOrCreateUser(message)
        if (message.contact.userId == message.from.id) {

            val phoneNumber = message.contact.phoneNumber
            user.phoneNumber = phoneNumber
            user.botStep = BotStep.SHOW_MENU
            userRepository.save(user)
            val sendMessage = SendMessage(
                user.telegramId, messageSourceService.getMessage(
                    LocalizationTextKey.CHOOSE_SECTION_MESSAGE,
                    languageService.getLanguageOfUser(message.from.id)
                )
            )
            sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
            return sendMessage
        } else {
            val sendMessage = SendMessage(
                user.telegramId, messageSourceService.getMessage(
                    LocalizationTextKey.SHARE_CONTACT_MESSAGE,
                    languageService.getLanguageOfUser(user.telegramId.toLong())
                )
            )
            sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
            return sendMessage
        }
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
    private val botMessageRepository: BotMessageRepository,
    private val messageSourceService: MessageSourceService,
    private val languageService: LanguageService,
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

    fun deleteInlineReplyMarkup(
        chatId: Long, messageId: Int, sender: AbsSender,
    ) {
        val deleteMessage = DeleteMessage()
        deleteMessage.chatId = chatId.toString()
        deleteMessage.messageId = messageId
        sender.execute(deleteMessage)

    }

    fun findWaitingUsers(message: Message, sender: AbsSender, sessionsList: MutableList<Session>) {
        val chatId = userBotService.getChatId(message)
        val operator = userRepository.findByTelegramIdAndDeletedFalse(chatId)

        val session = sessionsList[0]
        val user = session.user
        for (lang in operator.languageList!!) {
            if (lang.languageEnum == user.languageList!![0].languageEnum) {

                try {
                    val userSendMessage = SendMessage(
                        user.telegramId, messageSourceService.getMessage(
                            LocalizationTextKey.CONNECT_OPERATOR_MESSAGE,
                            languageService.getLanguageOfUser(user.telegramId.toLong())
                        )
                    )
                    sender.execute(userSendMessage)
                }
                catch (e:Exception){
                    user.isBlocked = true
                    user.botStep = BotStep.SHOW_MENU
                    userRepository.save(user)
                    session.active=false
                    sessionRepository.save(session)
                }
                if(!user.isBlocked){

                    val operatorSendMessage = SendMessage(
                        chatId, messageSourceService.getMessage(
                            LocalizationTextKey.CONNECT_USER_MESSAGE,
                            languageService.getLanguageOfUser(operator.telegramId.toLong())
                        ) + " - ${user.firstName}"
                    )
                    sender.execute(operatorSendMessage)

                    val messages = messageRepository.findBySessionIdAndSessionUserId(session.id!!, user.id!!)

                    for (s_message in messages) {
                        operator.botStep = BotStep.FULL_SESSION
                        userRepository.save(operator)

                        when (s_message.messageType) {
                            MessageType.VIDEO -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendVideo =
                                    SendVideo(chatId, InputFile(File(file.path)))
                                sendVideo.replyMarkup = generateReplyMarkup(operator)
                                sendVideo.caption = file.caption

                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)
                                        sendVideo.replyToMessageId = botMessage?.telegramMessageId
                                    }
                                }

                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendVideo)
                                } catch (e: Exception) {
                                    sendVideo.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendVideo)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }

                            MessageType.AUDIO -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendAudio =
                                    SendAudio(chatId, InputFile(File(file.path)))
                                sendAudio.replyMarkup = generateReplyMarkup(operator)
                                sendAudio.caption = file.caption


                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)
                                        sendAudio.replyToMessageId = botMessage?.telegramMessageId
                                    }
                                }

                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendAudio)
                                } catch (e: Exception) {
                                    sendAudio.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendAudio)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)

                            }

                            MessageType.PHOTO -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendPhoto =
                                    SendPhoto(chatId, InputFile(File(file.path)))
                                sendPhoto.replyMarkup = generateReplyMarkup(operator)
                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendPhoto)
                                } catch (e: Exception) {
                                    sendPhoto.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendPhoto)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }

                            MessageType.DOCUMENT -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendDocument =
                                    SendDocument(
                                        chatId,
                                        InputFile(File(file.path))
                                    )
                                sendDocument.caption = file.caption

                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)

                                        sendDocument.replyToMessageId = botMessage?.telegramMessageId


                                    }
                                }
                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendDocument)
                                } catch (e: Exception) {
                                    sendDocument.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendDocument)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }

                            MessageType.ANIMATION -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendAnimation =
                                    SendAnimation(
                                        chatId,
                                        InputFile(File(file.path))
                                    )
                                sendAnimation.caption = file.caption

                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)
                                        sendAnimation.replyToMessageId = botMessage?.telegramMessageId
                                    }
                                }

                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendAnimation)
                                } catch (e: Exception) {
                                    sendAnimation.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendAnimation)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }

                            MessageType.VOICE -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendVoice =
                                    SendVoice(
                                        chatId,
                                        InputFile(File(file.path))
                                    )
                                sendVoice.caption = file.caption

                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)
                                        sendVoice.replyToMessageId = botMessage?.telegramMessageId
                                    }
                                }

                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendVoice)
                                } catch (e: Exception) {
                                    sendVoice.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendVoice)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }

                            MessageType.VIDEO_NOTE -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendVideoNote =
                                    SendVideoNote(
                                        chatId,
                                        InputFile(File(file.path))
                                    )


                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)
                                        sendVideoNote.replyToMessageId = botMessage?.telegramMessageId
                                    }
                                }

                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendVideoNote)
                                } catch (e: Exception) {
                                    sendVideoNote.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendVideoNote)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }

                            MessageType.STICKER -> {
                                val file = fileRepository.findByMessageId(s_message.id!!)
                                val sendSticker =
                                    SendSticker(
                                        chatId,
                                        InputFile(File(file.path))
                                    )

                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)
                                        sendSticker.replyToMessageId = botMessage?.telegramMessageId
                                    }
                                }

                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendSticker)
                                } catch (e: Exception) {
                                    sendSticker.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendSticker)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }


                            MessageType.TEXT -> {
                                val sendMessage = SendMessage(chatId, s_message.text!!)
                                sendMessage.replyMarkup = generateReplyMarkup(operator)

                                if (s_message.isReply) {
                                    if (botMessageRepository.existsByReceivedMessageId(s_message.replyMessageId!!)) {
                                        val botMessage =
                                            botMessageRepository.findByReceivedMessageId(s_message.replyMessageId!!)
                                        sendMessage.replyToMessageId = botMessage?.telegramMessageId
                                    }
                                }

                                var sendMessageByBot: Message
                                try {
                                    sendMessageByBot = sender.execute(sendMessage)
                                } catch (e: Exception) {
                                    sendMessage.replyToMessageId = null
                                    sendMessageByBot = sender.execute(sendMessage)
                                }
                                val botMessage = BotMessage(s_message.telegramMessageId, sendMessageByBot.messageId)
                                botMessageRepository.save(botMessage)
                            }
                        }
                        user.botStep = BotStep.FULL_SESSION
                        userRepository.save(user)

                        session.operator = operator
                        sessionRepository.save(session)
                    }
                    break

                }
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
            row1Button1.text = messageSourceService.getMessage(
                LocalizationTextKey.OPERATOR_CLOSE_BUTTON,
                languageService.getLanguageOfUser(user.telegramId.toLong())
            )
            val row1Button2 = KeyboardButton()
            row1Button2.text = messageSourceService.getMessage(
                LocalizationTextKey.OPERATOR_CLOSE_AND_OFFLINE_BUTTON,
                languageService.getLanguageOfUser(user.telegramId.toLong())
            )
            row1.add(row1Button1)
            row1.add(row1Button2)
            rowList.add(row1)
            rowList.add(row2)
        } else if (user.botStep == BotStep.ONLINE && user.role == Role.OPERATOR) {
            row1Button1.text = messageSourceService.getMessage(
                LocalizationTextKey.OFFLINE_BUTTON,
                languageService.getLanguageOfUser(user.telegramId.toLong())
            )
            row1.add(row1Button1)
            rowList.add(row1)
        } else if (user.botStep == BotStep.OFFLINE && user.role == Role.OPERATOR) {
            row1Button1.text = messageSourceService.getMessage(
                LocalizationTextKey.ONLINE_BUTTON,
                languageService.getLanguageOfUser(user.telegramId.toLong())
            )
            row1.add(row1Button1)
            rowList.add(row1)
        } else if (user.botStep == BotStep.SHARE_CONTACT) {
            val contactRequestButton = KeyboardButton(
                messageSourceService.getMessage(
                    LocalizationTextKey.SHARE_CONTACT_BUTTON,
                    languageService.getLanguageOfUser(user.telegramId.toLong())
                )
            )
            val keyboardRow = KeyboardRow()
            contactRequestButton.requestContact = true
            keyboardRow.add(contactRequestButton)
            rowList.add(keyboardRow)
        } else if (user.botStep == BotStep.SHOW_MENU && user.role == Role.USER) {
            row1Button1.text = messageSourceService.getMessage(
                LocalizationTextKey.QUESTION_BUTTON,
                languageService.getLanguageOfUser(user.telegramId.toLong())
            )
            val row1Button2 = KeyboardButton()
            row1Button2.text = messageSourceService.getMessage(
                LocalizationTextKey.SETTINGS_BUTTON,
                languageService.getLanguageOfUser(user.telegramId.toLong())
            )
            row1.add(row1Button1)
            row1.add(row1Button2)
            rowList.add(row1)
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
    private val messageSourceService: MessageSourceService,
    private val languageService: LanguageService,
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
                session = Session(user, null, true, 0, Date())
                session.active = true
                sessionRepository.save(session)

                user.botStep = BotStep.IN_SESSION
                fileBotService.saveMessageAndFile(message, sender, false, null, session)

                val sendMessage = SendMessage(
                    user.telegramId,
                    messageSourceService.getMessage(
                        LocalizationTextKey.WAIT_FOR_OPERATOR_MESSAGE,
                        languageService.getLanguageOfUser(message.from.id)
                    )
                )
                sender.execute(sendMessage)

            } else {
                val operator = operatorList[0]

                session = Session(user, operator, true, 0, Date())
                session.active = true
                sessionRepository.save(session)


                operator.botStep = BotStep.FULL_SESSION
                userRepository.save(operator)

                user.botStep = BotStep.FULL_SESSION


                fileBotService.saveMessageAndFile(message, sender, true, operator.telegramId, session)

                var sendMessage =
                    SendMessage(
                        user.telegramId, messageSourceService.getMessage(
                            LocalizationTextKey.CONNECT_OPERATOR_MESSAGE,
                            languageService.getLanguageOfUser(user.telegramId.toLong())
                        )
                    )
                sender.execute(sendMessage)
                sendMessage = SendMessage(
                    operator.telegramId, messageSourceService.getMessage(
                        LocalizationTextKey.CONNECT_USER_MESSAGE,
                        languageService.getLanguageOfUser(operator.telegramId.toLong())
                    ) + " - ${message.from.firstName}"
                )
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
    private val botMessageRepository: BotMessageRepository,
    private val messageSourceService: MessageSourceService,
    private val languageService: LanguageService,
    private val userRepository: UserRepository
) {
    fun saveMessageAndFile(
        message: Message,
        sender: AbsSender,
        executive: Boolean,
        telegramId: String?,
        session: Session,
    ) {
        //save file
        //for document


        if (message.hasDocument()) {
            message.document.run {

                saveFileToDisk(
                    "$fileUniqueId$fileName", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.DOCUMENT)

                val file = createFile(message, "$fileUniqueId$fileName", ContentType.DOCUMENT)

                if (executive) {

                    val sendDocument = SendDocument(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sendDocument.caption = file.caption

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendDocument.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendDocument.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendDocument)
                    } catch (e: Exception) {
                        try {
                            sendDocument.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendDocument)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }
        }

        //for video
        else if (message.hasVideo()) {
            message.video.run {
                saveFileToDisk(
                    "$fileUniqueId$fileName", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.VIDEO)
                val file = createFile(message, "$fileUniqueId$fileName", ContentType.VIDEO)

                if (executive) {
                    val sendVideo = SendVideo(
                        telegramId!!,
                        InputFile(File(file.path))
                    )

                    sendVideo.caption = file.caption

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendVideo.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendVideo.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendVideo)
                    } catch (e: Exception) {
                        try {
                            sendVideo.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendVideo)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }
        }
        //for audio
        else if (message.hasAudio()) {
            message.audio.run {
                saveFileToDisk(
                    "$fileUniqueId$fileName", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.AUDIO)
                val file = createFile(message, "$fileUniqueId$fileName", ContentType.AUDIO)

                if (executive) {
                    val sendAudio = SendAudio(
                        telegramId!!,
                        InputFile(File(file.path))
                    )

                    sendAudio.caption = file.caption

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendAudio.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendAudio.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendAudio)
                    } catch (e: Exception) {
                        try {
                            sendAudio.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendAudio)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }

        }
        //for videoNote
        else if (message.hasVideoNote()) {

            message.videoNote.run {
                saveFileToDisk(
                    "${fileUniqueId}.mp4", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.VIDEO_NOTE)
                val file = createFile(message, "${fileUniqueId}.mp4", ContentType.VIDEO_NOTE)

                if (executive) {
                    val sendVideoNote = SendVideoNote(
                        telegramId!!,
                        InputFile(File(file.path))
                    )

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendVideoNote.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendVideoNote.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendVideoNote)
                    } catch (e: Exception) {
                        try {
                            sendVideoNote.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendVideoNote)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }
        }

        //for photo
        else if (message.hasPhoto()) {
            val photos = message.photo

            photos[1].run {
                saveFileToDisk(
                    "$fileUniqueId.jpg", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.PHOTO)
                val file = createFile(message, "$fileUniqueId.jpg", ContentType.PHOTO)

                if (executive) {
                    val sendPhoto = SendPhoto(
                        telegramId!!,
                        InputFile(File(file.path))
                    )

                    sendPhoto.caption = file.caption

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendPhoto.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendPhoto.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendPhoto)
                    } catch (e: Exception) {
                        try {
                            sendPhoto.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendPhoto)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }


        }

        //for voice
        else if (message.hasAnimation()) {
            message.animation.run {
                saveFileToDisk(
                    "$fileUniqueId$fileName", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.ANIMATION)
                val file = createFile(message, "$fileUniqueId$fileName", ContentType.ANIMATION)

                if (executive) {
                    val sendAnimation = SendAnimation(
                        telegramId!!,
                        InputFile(File(file.path))
                    )
                    sendAnimation.caption = file.caption

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendAnimation.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendAnimation.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendAnimation)
                    } catch (e: Exception) {
                        try {
                            sendAnimation.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendAnimation)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }

        }

        //for voice
        else if (message.hasVoice()) {
            message.voice.run {

                saveFileToDisk(
                    "${fileUniqueId}.ogg", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.VOICE)
                val file = createFile(message, "${fileUniqueId}.ogg", ContentType.VOICE)

                if (executive) {
                    val sendVoice = SendVoice(
                        telegramId!!,
                        InputFile(File(file.path))
                    )

                    sendVoice.caption = file.caption

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendVoice.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendVoice.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendVoice)
                    } catch (e: Exception) {
                        try {
                            sendVoice.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendVoice)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }

        }

        //for sticker
        else if (message.hasSticker()) {
            val sticker = message.sticker

            val fileExtension = when {
                sticker.isAnimated -> ".tgs"
                sticker.isVideo -> ".webm"
                else -> ".webp"
            }



            sticker.run {
                saveFileToDisk(
                    "${fileUniqueId}${fileExtension}", getFromTelegram(fileId, getBotToken(), sender)
                )

                val receivedId = messageHandler.createMessage(message, session, MessageType.STICKER)
                val file = createFile(message, "${fileUniqueId}${fileExtension}", ContentType.STICKER)
                if (executive) {
                    val sendSticker = SendSticker(
                        telegramId!!,
                        InputFile(File(file.path))
                    )

                    if (message.isReply) {
                        if (botMessageRepository.existsByReceivedMessageId(
                                message.replyToMessage.messageId
                            )
                        ) {
                            val botMessage =
                                botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                            sendSticker.replyToMessageId = botMessage?.telegramMessageId
                        } else {
                            val botMessage =
                                botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                            sendSticker.replyToMessageId = botMessage?.receivedMessageId
                        }
                    }

                    var sendMessageByBot: Message
                    try {
                        sendMessageByBot = sender.execute(sendSticker)
                    } catch (e: Exception) {
                        try {
                            sendSticker.replyToMessageId = null
                            sendMessageByBot = sender.execute(sendSticker)
                        } catch (e: Exception) {
                            sendMessageByBot = sender.execute(
                                SendMessage(
                                    session.operator!!.telegramId,
                                    messageSourceService.getMessage(
                                        LocalizationTextKey.BLOCK_USER_MESSAGE,
                                        languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                    )
                                )
                            )

                            val user = session.user
                            user.isBlocked = true
                            user.botStep = BotStep.SHOW_MENU
                            userRepository.save(user)

                        }
                    }
                    val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                    botMessageRepository.save(botMessage)
                }
            }


        } else if (message.hasText()) {
            val receivedId = messageHandler.createMessage(message, session, MessageType.TEXT)
            if (executive) {

                val sendMessage = SendMessage(telegramId!!, message.text)

                if (message.isReply) {
                    if (botMessageRepository.existsByReceivedMessageId(
                            message.replyToMessage.messageId
                        )
                    ) {
                        val botMessage =
                            botMessageRepository.findByReceivedMessageId(message.replyToMessage.messageId)
                        sendMessage.replyToMessageId = botMessage?.telegramMessageId
                    } else {
                        val botMessage =
                            botMessageRepository.findByTelegramMessageId(message.replyToMessage.messageId)
                        sendMessage.replyToMessageId = botMessage?.receivedMessageId
                    }
                }

                var sendMessageByBot: Message
                try {
                    sendMessageByBot = sender.execute(sendMessage)
                } catch (e: Exception) {
                    try {
                        sendMessage.replyToMessageId = null
                        sendMessageByBot = sender.execute(sendMessage)
                    } catch (e: Exception) {
                        sendMessageByBot = sender.execute(
                            SendMessage(
                                session.operator!!.telegramId,
                                messageSourceService.getMessage(
                                    LocalizationTextKey.BLOCK_USER_MESSAGE,
                                    languageService.getLanguageOfUser(session.operator!!.telegramId.toLong())
                                )
                            )
                        )

                        val user = session.user
                        user.isBlocked = true
                        user.botStep = BotStep.SHOW_MENU
                        userRepository.save(user)

                    }
                }
                val botMessage = BotMessage(receivedId, sendMessageByBot.messageId)
                botMessageRepository.save(botMessage)
            }

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
            fileMessage,
            message.caption
        )
        return fileRepository.save(file)
    }


}