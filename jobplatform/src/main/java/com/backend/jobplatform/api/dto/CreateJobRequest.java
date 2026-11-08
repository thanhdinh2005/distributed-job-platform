package com.backend.jobplatform.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * {@code jobType} is a free-form string on purpose: the handler registry in {@code worker/} is
 * keyed by it, and a new job type must not require a schema migration or a redeploy of an enum
 * (AGENTS.md §7).
 *
 * <p>Consequence: an unknown type is accepted here. It becomes processable when a
 * {@code JobHandler} registers for that name — until then the worker will fail it, which is a
 * runtime concern rather than a validation one.
 */
public record CreateJobRequest(
    @NotBlank
    String jobType,

    @NotNull
    Map<String, Object> payload
) {
}
