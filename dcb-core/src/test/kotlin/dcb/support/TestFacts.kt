package dcb.support

import dcb.Fact
import dcb.Subject
import dcb.subjects

data class BoxOpened(val box: String) : Fact {
    override val about = subjects(Subject("box:$box"))
}

data class BoxClosed(val box: String) : Fact {
    override val about = subjects(Subject("box:$box"))
}

data class BoxLabeled(val box: String, val label: String) : Fact {
    override val about = subjects(Subject("box:$box"))
}

data class ItemPlaced(val box: String, val item: String) : Fact {
    override val about = subjects(Subject("box:$box"), Subject("item:$item"))
}
