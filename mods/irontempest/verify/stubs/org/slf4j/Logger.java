package org.slf4j;

public interface Logger {
    void info(String msg);
    void info(String format, Object... arguments);
    void warn(String msg);
    void warn(String format, Object... arguments);
    void warn(String msg, Throwable t);
    void error(String msg);
    void error(String format, Object... arguments);
    void error(String msg, Throwable t);
    void debug(String format, Object... arguments);
}
