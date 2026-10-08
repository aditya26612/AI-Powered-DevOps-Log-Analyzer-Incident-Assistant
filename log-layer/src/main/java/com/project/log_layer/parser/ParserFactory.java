package com.project.log_layer.parser;

import com.project.log_layer.enums.LogSource;
import com.project.log_layer.exception.UnsupportedLogException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory responsible for returning the appropriate parser
 * implementation based on the requested {@link LogSource}.
 *
 * <p>
 * All parser implementations are automatically discovered
 * by Spring through component scanning.
 *
 * This implementation follows the Open/Closed Principle.
 * Adding a new parser requires only:
 *
 * <ul>
 *     <li>Creating a new {@code LogParser} implementation</li>
 *     <li>Annotating it with {@code @Component}</li>
 *     <li>Returning the supported {@code LogSource}</li>
 * </ul>
 *
 * No changes are required in this factory.
 */
@Component
public class ParserFactory {

    /**
     * Maps each supported log source to its parser.
     */
    private final Map<LogSource, LogParser> parsers;

    /**
     * Creates the parser registry.
     *
     * Spring automatically injects every bean implementing
     * {@link LogParser}.
     *
     * @param parserList available parser implementations
     */
    public ParserFactory(List<LogParser> parserList) {

        this.parsers = parserList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        LogParser::getSupportedSource,
                        Function.identity()
                ));

    }

    /**
     * Returns the parser responsible for the supplied log source.
     *
     * @param source log source
     * @return matching parser
     * @throws UnsupportedLogException if no parser is registered
     */
    public LogParser getParser(LogSource source) {

        LogParser parser = parsers.get(source);

        if (parser == null) {

            throw new UnsupportedLogException(
                    "No parser registered for log source: " + source
            );

        }

        return parser;

    }

    /**
     * Checks whether a parser exists for the supplied source.
     *
     * @param source log source
     * @return true if supported
     */
    public boolean supports(LogSource source) {
        return parsers.containsKey(source);
    }
}