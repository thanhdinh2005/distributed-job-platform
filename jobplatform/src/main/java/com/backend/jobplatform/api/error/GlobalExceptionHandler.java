package com.backend.jobplatform.api.error;

import com.backend.jobplatform.domain.exception.IllegalTransitionException;
import com.backend.jobplatform.domain.exception.JobNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns exceptions into RFC 9457 problem responses, in one place.
 *
 * <p>This is the only layer that knows about HTTP statuses. Domain exceptions carry meaning and
 * data; the mapping to a status and an {@link ErrorCode} happens here.
 *
 * <p>Nothing here catches-and-returns: the exception has already propagated out of the
 * {@code @Transactional} method, so the transaction has rolled back before the response is built.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(JobNotFoundException.class)
  ProblemDetail handleJobNotFound(JobNotFoundException ex) {
    ProblemDetail problem = problem(ErrorCode.JOB_NOT_FOUND, ex.getMessage());
    problem.setProperty("jobId", ex.getJobId());
    return problem;
  }

  @ExceptionHandler(IllegalTransitionException.class)
  ProblemDetail handleIllegalTransition(IllegalTransitionException ex) {
    ProblemDetail problem = problem(ErrorCode.JOB_ILLEGAL_TRANSITION, ex.getMessage());
    problem.setProperty("jobId", ex.getJobId());
    problem.setProperty("from", ex.getFrom());
    problem.setProperty("to", ex.getTo());
    return problem;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(
          error.getField(),
          Optional.ofNullable(error.getDefaultMessage()).orElse("invalid value"));
    }
    ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
    problem.setProperty("fields", fieldErrors);
    return problem;
  }

  /** Unparseable JSON, or a value that does not match the target enum. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
    return problem(ErrorCode.VALIDATION_FAILED, "Request body is missing or malformed");
  }

  /** Unknown path: without this, the catch-all below would report it as a 500. */
  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail handleUnknownRoute(NoResourceFoundException ex) {
    return problem(ErrorCode.ROUTE_NOT_FOUND, "No handler for " + ex.getResourcePath());
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return problem(ErrorCode.INTERNAL_ERROR, "Unexpected server error");
  }

  private static ProblemDetail problem(ErrorCode code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
    problem.setTitle(code.title());
    problem.setProperty("code", code.code());
    return problem;
  }
}
