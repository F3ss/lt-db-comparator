package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ответ на GET /api/generator/status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Текущее состояние генератора данных")
public class LoadStatusResponse {

    @Schema(description = "Генератор работает?", example = "true")
    private boolean running;

    @Schema(description = "Параметры текущей/последней генерации")
    private LoadRequest config;

    @Schema(description = "Общее кол-во сгенерированных записей (все таблицы)", example = "54000")
    private long totalRecords;

    @Schema(description = "Батчей отправлено", example = "120")
    private long batchesSubmitted;

    @Schema(description = "Батчей успешно записано", example = "118")
    private long batchesCompleted;

    @Schema(description = "Батчей упало с ошибкой", example = "2")
    private long batchesFailed;

    @Schema(description = "Время работы (сек)", example = "45")
    private long elapsedSeconds;
}
