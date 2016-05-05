package olx

import org.joda.time.format.PeriodFormatterBuilder

/**
  * Created by stanikol on 05.05.16.
  */
object DurationFormatters {
   val durationWithMillis = new PeriodFormatterBuilder()
    .appendDays().appendSuffix(" day", " days").appendSeparator(" ")
    .appendHours().appendSuffix(" hour", " hours").appendSeparator(" ")
    .appendMinutes().appendSuffix(" minute", " minutes").appendSeparator(" ")
    .appendSeconds().appendSuffix(" second", " seconds").appendSeparator(" ")
    .appendMillis3Digit().appendSuffix(" ms")
    .toFormatter

  val duration = new PeriodFormatterBuilder()
    .appendDays().appendSuffix(" day", " days").appendSeparator(" ")
    .appendHours().appendSuffix(" hour", " hours").appendSeparator(" ")
    .appendMinutes().appendSuffix(" minute", " minutes").appendSeparator(" ")
    .appendSeconds().appendSuffix(" second", " seconds").appendSeparator(" ")
    .toFormatter

}
