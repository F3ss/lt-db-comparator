package com.lt.dbcomparator.controller;

import com.lt.dbcomparator.dto.LoadRequest;
import com.lt.dbcomparator.dto.LoadStatusResponse;
import com.lt.dbcomparator.service.DataGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Управление генерацией тестовых данных.
 */
@Tag(name = "Generator", description = "Запуск / остановка / статус генерации нагрузки")
@RestController
@RequestMapping("/api/generator")
@RequiredArgsConstructor
public class GeneratorController {

    private final DataGeneratorService generatorService;

    @Operation(summary = "Запустить генерацию", description = """
            Запускает генерацию тестовых данных в фоне.
            Параметры задаются в теле запроса.
            Каждый батч создаёт: N клиентов + N профилей + ~3N заказов + ~13.5N позиций.
            """, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
            @ExampleObject(name = "Средняя нагрузка", summary = "500 записей/сек на 10 минут", value = """
                    {
                      "batchSize": 100,
                      "batchesPerSecond": 5,
                      "durationMinutes": 10
                    }
                    """),
            @ExampleObject(name = "Высокая нагрузка", summary = "2000 записей/сек на 30 минут", value = """
                    {
                      "batchSize": 200,
                      "batchesPerSecond": 10,
                      "durationMinutes": 30
                    }
                    """),
            @ExampleObject(name = "Быстрый тест", summary = "50 записей/сек на 1 минуту", value = """
                    {
                      "batchSize": 50,
                      "batchesPerSecond": 1,
                      "durationMinutes": 1
                    }
                    """)
    })))
    @PostMapping("/start")
    public ResponseEntity<String> start(@RequestBody LoadRequest request) {
        generatorService.start(request);
        return ResponseEntity.ok("Генерация запущена");
    }

    @Operation(summary = "Остановить генерацию", description = "Останавливает фоновую генерацию данных.")
    @PostMapping("/stop")
    public ResponseEntity<String> stop() {
        generatorService.stop();
        return ResponseEntity.ok("Генерация остановлена");
    }

    @Operation(summary = "Статус генерации", description = "Возвращает текущее состояние и счётчики.")
    @GetMapping("/status")
    public ResponseEntity<LoadStatusResponse> status() {
        return ResponseEntity.ok(generatorService.getStatus());
    }
}
