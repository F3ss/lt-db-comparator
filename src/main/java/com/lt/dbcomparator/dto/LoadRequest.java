package com.lt.dbcomparator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запуска генерации данных.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Параметры нагрузки для генерации данных")
public class LoadRequest {

    @Schema(description = "Количество сущностей (Customer-графов) в одном батче", example = "100")
    private int batchSize;

    @Schema(description = "Количество батчей, отправляемых в секунду", example = "5")
    private int batchesPerSecond;

    @Schema(description = "Продолжительность генерации в минутах", example = "30")
    private int durationMinutes;
}
