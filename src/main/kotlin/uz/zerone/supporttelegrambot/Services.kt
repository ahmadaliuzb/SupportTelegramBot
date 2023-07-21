package uz.zerone.supporttelegrambot

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

/**
17/07/2023 - 1:36 PM
Created by Akhmadali
 */

interface UserService {
    fun getAll(pageable: Pageable): Page<UsersList>
    fun update(dto: UserUpdateDto)
}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
    private val keyboardReplyMarkupHandler: KeyboardReplyMarkupHandler,
    private val telegramBot: SupportTelegramBot,

) : UserService {
    override fun getAll(pageable: Pageable): Page<UsersList> {
        return userRepository.findAllNotDeleted(pageable).map { UsersList.toDto(it) }
    }

    override fun update(dto: UserUpdateDto) {
        val languages = mutableListOf<Language>()
        for (languageDto in dto.languageList) {
            when (languageDto) {
                "UZ" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
                "RU" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.RU))
                "ENG" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.ENG))
            }
        }



        val user = userRepository.findByPhoneNumberAndDeletedFalse(dto.phoneNumber)
            ?:throw UserNotFoundException(dto.phoneNumber)

        dto.run {
            phoneNumber.let { user.phoneNumber = it }
            user.languageList = languages
            user.role = Role.OPERATOR
            user.botStep = BotStep.OFFLINE
        }
        userRepository.save(user)
        val sendMessage = SendMessage(user.telegramId,"You have been assigned to the position of operator ✅")
        sendMessage.replyMarkup=keyboardReplyMarkupHandler.generateReplyMarkup(user)
       telegramBot.execute(sendMessage)



    }

}

