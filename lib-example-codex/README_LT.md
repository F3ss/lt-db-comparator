# README for LoadRunner 2022 (Java Vuser)

Инструкция как подключить и использовать библиотеку `kafka-meta-journal-lib` в LoadRunner Professional 2022 (VuGen, Java Vuser).

## 1. Сборка библиотеки

Требования:
- JDK 11+ для сборки (сама библиотека компилируется в Java 11 bytecode)
- JVM/JRE на стороне VuGen и Load Generator: 11+

Команда:

```bash
./gradlew clean test prepareLoadRunnerBundle
```

Для Windows:

```bat
gradlew.bat clean test prepareLoadRunnerBundle
```

После сборки получите папку:
- `build/loadrunner/lib`

В ней лежат:
- `kafka-meta-journal-lib-<version>.jar`
- все runtime-зависимости (Kafka client и транзитивные библиотеки)

## 2. Подключение JAR в VuGen (Java Vuser)

1. Создайте или откройте Java Vuser скрипт в VuGen.
2. Скопируйте все JAR из `build/loadrunner/lib` в папку скрипта, например:
   - `<LR_SCRIPT_DIR>/lib`
3. Откройте `Runtime Settings`.
4. Перейдите в `Java Environment`.
5. В `Java VM` выберите JDK для replay (встроенная авто-логика или явный путь к JDK 11+).
6. В секции `Classpath` добавьте JAR из папки скрипта (`Add` / `Add Recursive`).
7. Сохраните настройки и выполните компиляцию/verify скрипта.

Важно:
- Файлы из `Classpath` используются как ссылки и не копируются автоматически в script folder.
- Для запуска на Load Generator убедитесь, что JAR доступны на генераторе (обычно через script folder/Extra files в Controller).

## 3. Пример использования в Java Vuser

Ниже пример класса `Actions` для Java Vuser:

```java
import com.example.kafkalib.KafkaMetaJournalService;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Actions {

    private KafkaMetaJournalService service;

    public int init() throws Throwable {
        Properties props = new Properties();
        props.setProperty("kafka.bootstrap.servers", "localhost:9092");
        // optional:
        // props.setProperty("kafka.metadata.topic", "meta");
        // props.setProperty("kafka.journal.topic", "journal");
        // props.setProperty("kafka.send.batch-size", "1000");

        service = new KafkaMetaJournalService(props);
        return 0;
    }

    public int action() throws Throwable {
        // При необходимости принудительно перечитать метаданные
        service.refreshMetadata();

        Map<String, String> payload = new HashMap<>();
        payload.put("order-1", "created");
        payload.put("order-2", "updated");

        service.processAndSend(payload);
        return 0;
    }

    public int end() throws Throwable {
        if (service != null) {
            service.close();
        }
        return 0;
    }
}
```

## 4. Ключевые параметры конфигурации

- `kafka.bootstrap.servers` (default `localhost:9092`)
- `kafka.metadata.topic` (default `meta`)
- `kafka.journal.topic` (default `journal`)
- `kafka.send.batch-size` (default `1000`)
- `kafka.producer.linger-ms` (default `20`)
- `kafka.producer.batch-bytes` (default `65536`)
- `kafka.producer.compression-type` (default `lz4`)

## 5. Практические рекомендации для нагрузки

- Не вызывайте `refreshMetadata()` на каждой итерации, если метаданные меняются редко.
- Для высокой нагрузки увеличивайте `kafka.send.batch-size` и `kafka.producer.batch-bytes` постепенно, контролируя latency.
- Перед длительным тестом выполните smoke-run с 1 Vuser и проверьте доставку в `journal` topic.
