package io.github.wimdeblauwe.errorhandlingspringbootstarter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Locale;

@ControllerAdvice(annotations = RestController.class)
public class ErrorHandlingControllerAdvice {
    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandlingControllerAdvice.class);

    private final ErrorHandlingProperties properties;
    private final List<ApiExceptionHandler> handlers;
    private final FallbackApiExceptionHandler fallbackHandler;

    public ErrorHandlingControllerAdvice(ErrorHandlingProperties properties,
                                         List<ApiExceptionHandler> handlers,
                                         FallbackApiExceptionHandler fallbackHandler) {
        this.properties = properties;
        this.handlers = handlers;
        this.fallbackHandler = fallbackHandler;
        this.handlers.sort(AnnotationAwareOrderComparator.INSTANCE);

        LOGGER.info("Error Handling Spring Boot Starter active with {} handlers", this.handlers.size());
        LOGGER.debug("Handlers: {}", this.handlers);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleException(Throwable exception, WebRequest webRequest, Locale locale) {
        LOGGER.debug("webRequest: {}", webRequest);
        LOGGER.debug("locale: {}", locale);
        logException(exception);

        ApiErrorResponse errorResponse = null;
        for (ApiExceptionHandler handler : handlers) {
            if (handler.canHandle(exception)) {
                errorResponse = handler.handle(exception);
                break;
            }
        }

        if (errorResponse == null) {
            errorResponse = fallbackHandler.handle(exception);
        }

        if (!properties.getFullStacktraceHttpStatuses().isEmpty()) {
            logFullStacktraceIfNeeded(errorResponse.getHttpStatus(), exception);
        }

        return ResponseEntity.status(errorResponse.getHttpStatus())
                             .body(errorResponse);
    }

    private void logException(Throwable exception) {
        if (properties.getFullStacktraceClasses().contains(exception.getClass())) {
            LOGGER.error(exception.getMessage(), exception);
        } else {
            switch (properties.getExceptionLogging()) {
                case WITH_STACKTRACE:
                    LOGGER.error(exception.getMessage(), exception);
                    break;
                case MESSAGE_ONLY:
                    LOGGER.error(exception.getMessage());
                    break;
            }
        }
    }

    private void logFullStacktraceIfNeeded(HttpStatus httpStatus, Throwable exception) {
        String httpStatusValue = String.valueOf(httpStatus.value());
        if (properties.getFullStacktraceHttpStatuses().contains(httpStatusValue)) {
            LOGGER.error(exception.getMessage(), exception);
        } else if (properties.getFullStacktraceHttpStatuses().contains(httpStatusValue.replaceFirst("\\d$", "x"))) {
            LOGGER.error(exception.getMessage(), exception);
        } else if (properties.getFullStacktraceHttpStatuses().contains(httpStatusValue.replaceFirst("\\d\\d$", "xx"))) {
            LOGGER.error(exception.getMessage(), exception);
        }
    }
}
