package com.exemple.nexrag.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Date;
import java.util.List;

/** Réponse pour un rollback. */
@Value
@Builder
public class RollbackResponse {
    Boolean success;
    String  batchId;
    Integer deletedCount;
    String  message;
}