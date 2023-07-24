package uz.zerone.supporttelegrambot


import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import java.util.Optional
import javax.persistence.EntityManager
import javax.transaction.Transactional


/**
17/07/2023 - 1:06 PM
Created by Akhmadali
 */
@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): Page<T>
}

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>, entityManager: EntityManager,
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdOrNull(id)?.run {
        deleted = true
        save(this)

    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    override fun findAllNotDeleted(pageable: Pageable): Page<T> = findAll(isNotDeletedSpecification, pageable)
    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }
}


interface UserRepository : BaseRepository<User> {
    fun existsByUsername(username: String)
    fun findByTelegramId(telegramId: String): User

    fun existsByPhoneNumberAndDeletedFalse(phoneNumber: String):Boolean

    @Query(
        value = "select u.*\n" +
                "from users u\n" +
                "         join users_language ul on u.id = ul.users_id and u.online = true\n" +
                "         join language l on l.id = ul.language_list_id\n" +
                "where u.role = :role\n" +
                "  and l.language_enum = :languageName and u.bot_step =:botStep", nativeQuery = true
    )
    fun findByOnlineTrueAndRoleAndLanguageListContains(
        role: String,
        languageName: String,
        botStep: String
    ): MutableList<User>

    fun findByTelegramIdAndDeletedFalse(telegramId: String): User
    fun existsByTelegramIdAndDeletedFalse(telegramId: String): Boolean
    fun findByPhoneNumberAndDeletedFalse(phoneNumber: String): User?
}

interface SessionRepository : BaseRepository<Session> {


    fun findAllByUserTelegramIdOrderByCreatedDate(user_telegramId: String):MutableList<Session>

    fun findByUserTelegramIdAndActiveTrue(user_telegramId: String): Session

    fun findByOperatorTelegramIdAndActiveTrue(operator_telegramId: String): Session

    fun findAllByOperatorTelegramIdAndActiveTrue(operator_telegramId: String):MutableList<Session>

    fun findByActiveTrueAndOperatorIsNullOrderByCreatedDateAsc(): MutableList<Session>

    fun findAllByUserTelegramIdAndActiveTrue(user_telegramId: String):MutableList<Session>
}


interface MessageRepository : BaseRepository<Message> {
    fun findByTelegramMessageIdAndDeletedFalse(telegramMessageId: Int): Message
    fun findBySessionIdAndDeletedFalse(session_id: Long): MutableList<Message>
    fun findBySessionIdAndSessionUserId(session_id: Long, session_user_id: Long): MutableList<Message>
}

interface FileRepository : BaseRepository<File> {
    fun findByMessageId(message_id: Long): File
}


interface LanguageRepository : BaseRepository<Language> {
    fun findByLanguageEnumAndDeletedFalse(languageEnum: LanguageEnum): Language
}

interface BotMessageRepository : BaseRepository<BotMessage> {
    fun existsByReceivedMessageId(receivedMessageId: Int):Boolean

    fun findByReceivedMessageId(receivedMessageId: Int):BotMessage?

    fun findByTelegramMessageId(telegramMessageId: Int):BotMessage?
}


