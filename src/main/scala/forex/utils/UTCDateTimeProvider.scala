package forex.utils

import java.time.ZonedDateTime

trait UTCDateTimeProvider {
  def now: ZonedDateTime
}
