package uz.zerone.supporttelegrambot

import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import java.util.*

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
    private val messageSourceService: MessageSourceService,
    private val languageService: LanguageService,
    private val sessionRepository: SessionRepository,
    private val messageHandler: MessageHandlerImpl
) : UserService {
    override fun getAll(pageable: Pageable): Page<UsersList> {
        return userRepository.findAllNotDeleted(pageable).map { UsersList.toDto(it) }
    }

    override fun update(dto: UserUpdateDto) {
        val languages = mutableListOf<Language>()
        for (languageDto in dto.languageList) {
            when (languageDto) {
                "ENG" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
                "RU" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.RU))
                "UZ" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.ENG))
            }
        }


        val user = userRepository.findByPhoneNumberAndDeletedFalse(dto.phoneNumber)
            ?: throw UserNotFoundException(dto.phoneNumber)

        dto.run {
            phoneNumber.let { user.phoneNumber = it }
            user.languageList = languages
            user.role = Role.OPERATOR
            user.botStep = BotStep.OFFLINE
            user.online = true
        }

        userRepository.save(user)
        val sessions =
            sessionRepository.findAllByUserTelegramIdAndActiveTrue(user.telegramId)
        sessions.forEach { it.active = false }
        sessionRepository.saveAll(sessions)

        val sessionList =
            sessionRepository.findAllByOperatorTelegramIdAndActiveTrue(user.telegramId)
        sessionList.forEach {
            it.active = false
            val sessionUser = it.user
            val sendUserMessage = SendMessage(
                sessionUser.telegramId,
                messageSourceService.getMessage(
                    LocalizationTextKey.SORRY_MESSAGE,
                    languageService.getLanguageOfUser(sessionUser.telegramId.toLong())
                )
            )
            telegramBot.execute(sendUserMessage)
            sessionUser.botStep = BotStep.ASSESSMENT
            userRepository.save(sessionUser)

            telegramBot.execute(
                messageHandler.sendRateSelection(
                    SendMessage(
                        sessionUser.telegramId,
                        messageSourceService.getMessage(
                            LocalizationTextKey.CHOOSE_RATE_MESSAGE,
                            languageService.getLanguageOfUser(sessionUser.telegramId.toLong())
                        )
                    ),
                    it.id
                )
            )
        }

        sessionRepository.saveAll(sessionList)

        val sendMessage = SendMessage(
            user.telegramId, messageSourceService.getMessage(
                LocalizationTextKey.ASSIGN_OPERATOR_MESSAGE,
                languageService.getLanguageOfUser(user.telegramId.toLong())
            )
        )
        sendMessage.replyMarkup = keyboardReplyMarkupHandler.generateReplyMarkup(user)
        telegramBot.execute(sendMessage)
    }

}

@Service
class MessageSourceService(val messageResourceBundleMessageSource: ResourceBundleMessageSource) {

    fun getMessage(sourceKey: LocalizationTextKey, language: Language): String {
        return messageResourceBundleMessageSource.getMessage(sourceKey.name, null, language.toLocale())
    }

    fun getMessage(sourceKey: LocalizationTextKey, any: Array<String>, language: Language): String {
        return messageResourceBundleMessageSource.getMessage(sourceKey.name, any, language.toLocale())
    }
}

@Service
class LanguageService(
    private val userRepository: UserRepository,
) {
    fun getLanguageOfUser(id: Long): Language {
        val user = userRepository.findByTelegramIdAndDeletedFalse(id.toString())
        val languageList = user.languageList

        languageList?.sort()


        return user.languageList?.get(0) ?: Language(LanguageEnum.UZ)
    }
}


//@Service
//class LocationService(
//
//) {
//    private fun saveLocationToFile(latitude: Double, longitude: Double) {
//        val locationJson = "{\"latitude\":$latitude, \"longitude\":$longitude}"
//
//        try {
//            // Write the data to a file on the file system
//            val file = File("C:\\files\\location_data.json")
//            file.writeText(locationJson)
//        } catch (e: Exception) {
//            // Handle any errors that may occur during file writing
//            e.printStackTrace()
//        }
//    }
//
//}
