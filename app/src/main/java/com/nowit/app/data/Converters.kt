package com.nowit.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun statusToString(v: ReminderStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): ReminderStatus = ReminderStatus.valueOf(v)
    @TypeConverter fun methodToString(v: SourceMethod): String = v.name
    @TypeConverter fun stringToMethod(v: String): SourceMethod = SourceMethod.valueOf(v)
    @TypeConverter fun eventToString(v: EventType): String = v.name
    @TypeConverter fun stringToEvent(v: String): EventType = EventType.valueOf(v)
}
