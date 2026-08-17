package dcb.postgres

import dcb.Fact
import dcb.Subject
import dcb.subjects
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class NotePosted(val board: String, val text: String) : Fact {
    @Transient override val about = subjects(Subject("board:$board"))
}
