package com.lt.dbcomparator.controller;

import com.lt.dbcomparator.dto.LoadRequest;
import com.lt.dbcomparator.dto.LoadStatusResponse;
import com.lt.dbcomparator.service.DataGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

  @Operation(summary = "Запустить генерацию", description = "Запускает генерацию тестовых данных в фоне.\n" +
      "Параметры задаются в теле запроса.\n" +
      "Каждый батч создаёт: N клиентов + N профилей + ~3N заказов + ~13.5N позиций.\n", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = {
          @ExampleObject(name = "Средняя нагрузка", summary = "500 записей/сек на 10 минут", value = "{\n" +
              "  \"batchSize\": 100,\n" +
              "  \"batchesPerSecond\": 5,\n" +
              "  \"durationMinutes\": 10\n" +
              "}\n"),
          @ExampleObject(name = "Высокая нагрузка", summary = "2000 записей/сек на 30 минут", value = "{\n" +
              "  \"batchSize\": 200,\n" +
              "  \"batchesPerSecond\": 10,\n" +
              "  \"durationMinutes\": 30\n" +
              "}\n"),
          @ExampleObject(name = "Быстрый тест", summary = "50 записей/сек на 1 минуту", value = "{\n" +
              "  \"batchSize\": 50,\n" +
              "  \"batchesPerSecond\": 1,\n" +
              "  \"durationMinutes\": 1\n" +
              "}\n")
      })))
  @PostMapping("/start")
  public ResponseEntity<String> start(@RequestBody LoadRequest request) {
    try {
      generatorService.start(request);
      return ResponseEntity.ok("Генерация запущена");
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
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
