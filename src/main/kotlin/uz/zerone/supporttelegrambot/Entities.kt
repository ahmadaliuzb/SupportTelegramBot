package uz.zerone.supporttelegrambot


/**
16/07/2023 - 1:11 PM
Created by Akhmadali
 */


import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.repository.Temporal
import java.util.*
import javax.persistence.*


@MappedSuperclass
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false) @ColumnDefault(value = "false") var deleted: Boolean = false,
) {
}

@Entity
class Language(
    @Enumerated(EnumType.STRING)
    val languageEnum: LanguageEnum
) : BaseEntity(), Comparable<Language> {
    override fun compareTo(other: Language): Int {
        if (this.languageEnum == LanguageEnum.UZ && other.languageEnum == LanguageEnum.ENG) return 1
        if (this.languageEnum == LanguageEnum.UZ && other.languageEnum == LanguageEnum.RU) return 1
        if (this.languageEnum == LanguageEnum.RU && other.languageEnum == LanguageEnum.ENG) return 1
        if (this.languageEnum == LanguageEnum.RU && other.languageEnum == LanguageEnum.UZ) return -1
        if (this.languageEnum == LanguageEnum.ENG && other.languageEnum == LanguageEnum.UZ) return -1
        if (this.languageEnum == LanguageEnum.ENG && other.languageEnum == LanguageEnum.RU) return -1
        return 0
    }
}

@Entity(name = "users")
class User(
    var firstName: String,
    @Column(unique = true)
    var telegramId: String,
    @Column(unique = true)
    var username: String?,
    @Column(unique = true)
    var phoneNumber: String?,
    @Enumerated(EnumType.STRING)
    var botStep: BotStep,
    @Enumerated(EnumType.STRING)
    var role: Role,
    var online: Boolean,
    @ManyToMany(fetch = FetchType.EAGER)
    var languageList: MutableList<Language>?,
    var isBlocked: Boolean = false
) : BaseEntity()

@Entity
class Session(
    @ManyToOne val user: User,
    @ManyToOne var operator: User?,
    var active: Boolean,
    var rate: Short? = null,
    @Temporal(TemporalType.TIMESTAMP) var createdDate: Date,
) : BaseEntity()

@Entity
class Message(
    var telegramMessageId: Int,
    @ManyToOne var session: Session,
    @ManyToOne val sender: User,
    var messageType: MessageType,
    var edited: Boolean = false,
    var text: String?,
    val isReply: Boolean,
    val replyMessageId: Int?,
) : BaseEntity()

@Entity
class File(
    var name: String,
    var path: String,
    var contentType: ContentType,
    @ManyToOne val message: Message,
    val caption: String?
) : BaseEntity()

//

@Entity
class BotMessage(
    val receivedMessageId: Int,
    var telegramMessageId: Int
) : BaseEntity()


