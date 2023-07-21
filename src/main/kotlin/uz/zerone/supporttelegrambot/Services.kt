package uz.zerone.supporttelegrambot

import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

/**
17/07/2023 - 1:36 PM
Created by Akhmadali
 */


@Service
class UserService(
    private val userRepository: UserRepository,
    private val languageRepository: LanguageRepository,
) {
    fun getAll(pageable: Pageable): Page<UsersList> {
        return userRepository.findAllNotDeleted(pageable).map { UsersList.toDto(it) }
    }

    fun update(dto: UserUpdateDto) {
        val languages = mutableListOf<Language>()
        for (languageDto in dto.languageList) {
            when (languageDto) {
                "UZ" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.UZ))
                "RU" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.RU))
                "ENG" -> languages.add(languageRepository.findByLanguageEnumAndDeletedFalse(LanguageEnum.ENG))
            }
        }

        dto.run {
            val user = userRepository.findByPhoneNumberAndDeletedFalse(phoneNumber)
            user.phoneNumber = phoneNumber
            user.languageList = languages
            user.role = Role.OPERATOR
            userRepository.save(user)
        }

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
    private val languageRepository: LanguageRepository,
    private val userRepository: UserRepository
) {
    fun getLanguageOfUser(id: Long): Language {
        val user = userRepository.findByTelegramIdAndDeletedFalse(id.toString())
        return user.languageList?.get(0) ?: Language(LanguageEnum.UZ)
    }
}