package com.fherrmann.wahlen.repository;

/** Nur ID und Fingerabdruck — reicht dem Import, um Unveraendertes zu ueberspringen. */
public record SurveyHash(Integer id, String contentHash) {
}
