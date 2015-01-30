import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy

import static ch.qos.logback.classic.Level.*

scan()

appender("CONSOLE", ConsoleAppender) {
  encoder(PatternLayoutEncoder) {
    pattern = "%d{dd-MM-yyyy HH:mm:ss.SSS} %p [%t] %c{1}: %m%n"
  }
}

appender("FILE", RollingFileAppender) {
  file = "techlooper-imports.log"
  rollingPolicy(FixedWindowRollingPolicy) {
    fileNamePattern = "techlooper-imports_%i.log"
    minIndex = 1
    maxIndex = 24
  }
  triggeringPolicy(SizeBasedTriggeringPolicy) {
    maxFileSize = "12MB"
  }
  encoder(PatternLayoutEncoder) {
    pattern = "%d{dd-MM-yyyy HH:mm:ss.SSS} %p [%t] %c{1}: %m%n"
  }
}

logger("com.techlooper", ALL, ["CONSOLE", "FILE"])

root(ERROR, ["CONSOLE", "FILE"])