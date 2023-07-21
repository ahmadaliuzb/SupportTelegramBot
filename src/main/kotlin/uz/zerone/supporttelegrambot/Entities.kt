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
)

@Entity
class Language(
    @Enumerated(EnumType.STRING)
    val languageEnum: LanguageEnum
) : BaseEntity()

@Entity(name = "users")
class User(
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
    var totalRate: Int = 0
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
    var active: Boolean,
    val text: String?,
) : BaseEntity()

@Entity
class File(
    var name: String,
    var path: String,
    var contentType: ContentType,
    @ManyToOne val message: Message,
) : BaseEntity()

//

@Entity
class BotMessage(
    val receivedMessageId: Int,
    var telegramMessageId: Int
) : BaseEntity()


@Entity
class Location(
    val path: String,
    @ManyToOne val message: Message
) : BaseEntity()