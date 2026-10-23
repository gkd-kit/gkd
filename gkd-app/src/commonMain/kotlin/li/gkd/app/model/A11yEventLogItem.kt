package li.gkd.app.model

import li.gkd.db.A11yEventLog

class A11yEventLogItem(
    val eventLog: A11yEventLog,
    val parsedText: Result<List<String>>,
)
